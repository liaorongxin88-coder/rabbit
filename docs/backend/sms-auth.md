# 手机号短信登录

Rabbit 使用阿里云短信服务 `SendSms` API 发送验证码。客户端只调用 Rabbit 后端，
AccessKey、短信签名和模板编号不会下发到 Flutter。

## 阿里云准备

1. 在短信服务控制台创建并审核国内短信签名。
2. 创建验证码模板，模板变量默认使用 `${code}`，有效期文案应与后端默认的 5 分钟一致。
3. 创建独立 RAM 用户或角色，只授予 `dysms:SendSms`，不要使用主账号 AccessKey。
4. 在部署环境设置下列变量，不要写入仓库或镜像。

```bash
export APP_PHONE_HASH_SECRET="$(openssl rand -hex 32)"
export APP_SMS_CODE_SECRET="$(openssl rand -hex 32)"
export ALIBABA_CLOUD_ACCESS_KEY_ID="<RAM AccessKey ID>"
export ALIBABA_CLOUD_ACCESS_KEY_SECRET="<RAM AccessKey Secret>"
export APP_SMS_SIGN_NAME="<审核通过的签名>"
export APP_SMS_TEMPLATE_CODE="SMS_xxxxxxxxx"
export APP_SMS_TEMPLATE_PARAM_NAME="code"
export APP_CACHE_PROVIDER="redis"
export APP_CACHE_HOST="<redis-or-valkey-host>"
export APP_SMS_ENABLED=true
```

先配置 Redis/Valkey 和所有短信参数，最后再启用 `APP_SMS_ENABLED`。短信开启但
`APP_CACHE_PROVIDER` 不是 `redis`/`valkey` 时，后端会拒绝启动。模板变量不是 `code` 时，只修改
`APP_SMS_TEMPLATE_PARAM_NAME`。短信服务中国站默认 Endpoint 为
`dysmsapi.aliyuncs.com`，需要覆盖时使用 `APP_SMS_ALIYUN_ENDPOINT`。

## 接口

发送验证码：

```http
POST /api/auth/sms/code
Content-Type: application/json

{"phone":"13800138000","purpose":"LOGIN_OR_REGISTER"}
```

成功响应中的 `expiresInSeconds` 默认是 `300`，`retryAfterSeconds` 默认是 `60`。

purpose 支持：

| purpose | 用途 |
| --- | --- |
| `LOGIN_OR_REGISTER` | 兼容现有客户端，存在则登录、不存在则注册 |
| `LOGIN` | 仅登录已有手机号账号 |
| `REGISTER` | 仅注册新手机号账号 |
| `RESET_PASSWORD` | 仅用于短信重置密码 |
| `BIND_PHONE` | 仅用于验证准备绑定的新手机号 |
| `VERIFY_CURRENT_PHONE` | 无密码账号换绑时验证原手机号 |

purpose 会进入缓存 key 和验证码 HMAC。相同手机号的不同 purpose 相互隔离，验证码不能跨用途
消费。请求不带 purpose 时默认 `LOGIN_OR_REGISTER`，用于兼容旧客户端。

登录或注册：

```http
POST /api/auth/sms/login
Content-Type: application/json

{"phone":"13800138000","code":"123456","purpose":"LOGIN_OR_REGISTER"}
```

手机号已绑定账号时直接登录；首次验证成功时只创建 `sys_user`，不会自动创建兔场。响应返回
正式 JWT，并包含 `phoneBound=true`、脱敏后的 `maskedPhone` 和
`hasPassword=false`。验证码只能成功使用一次。

短信重置密码：

```http
POST /api/auth/sms/reset-password
Content-Type: application/json

{"phone":"13800138000","code":"123456","newPassword":"new-secure-password"}
```

验证码必须先通过 `/api/auth/sms/code` 以 `RESET_PASSWORD` purpose 获取。重置成功后旧密码
失效；当前 JWT 机制尚无 token version，因此已签发 JWT 不会由本接口主动吊销。

登录态手机号绑定 / 换绑使用 `PUT /api/auth/phone`。新手机号必须使用 `BIND_PHONE` 验证码；
无可用密码的已有手机号账号还必须使用 `VERIFY_CURRENT_PHONE` 验证原手机号。目标手机号已绑定
其他账号时拒绝操作，不执行账号合并。

登录完成后客户端调用 `GET /api/houses`：

- 返回空数组：账号已认证但尚无兔场，进入创建或加入兔场流程。
- 返回一个兔场：可以直接进入该兔场。
- 返回多个兔场：恢复最近选择或展示兔场选择页。

没有兔场不是认证失败。零兔场账号仍可以访问资料、首次设置密码、创建兔场和工作空间列表，
但不能访问任何需要 `X-House-Id` 的生产接口。

## 邀请成员：手机号或账号

`POST /api/house-invitations` 接受两种标识，新客户端传 `identifier`，
老客户端只传 `phone` 也继续有效（服务端回退到 `phone`）。
服务端自己识别形态，因为账号带 `R` 前缀而手机号是纯数字，不会混。

### 账号（user_code）

每个账号建号时分配一个账号，形如 `R3F9A0C21B7`（`R` + 10 位十六进制），
在 App「我的 → 账号设置」和后台「账号安全」里可见可复制。

- **不用 `user_name` 充当这个角色**：`user_name` 是 `POST /api/auth/login` 的账号，
  报给别人等于公开凭证的一半；而且手机号注册的用户 `user_name` 是
  `mobile_xxxxxxxx` 这种自动串，根本没法口头报。
- 选十六进制是因为它的字母表里没有 O/I/L，所以服务端可以把输入里的
  O/I/L 归一化成 0/1/1，口头传达写错也能对上；大小写、空格、连字符同理。
- 16^10 约一万亿，随机取值，不可枚举，也不暴露平台上有多少账号。
- 两条通道的结局不同，回执也不同：账号的主人一定已注册，所以**当场入伙**
  并返回 `status=JOINED`；手机号可能还没注册，只能挂起，返回 `SUBMITTED`。
- 重复邀请只抬权限不降权限：已是设备管理员的人被再次按「生产人员」邀请，
  仍然保持设备管理员，回执里的 `role` 会告诉客户端真实结果。
- 按账号邀请的请求体里没有任何手机号字段，这正是它存在的意义。

### 精确手机号邀请

兔场 `OWNER` 可以通过 `POST /api/house-invitations` 邀请一个精确手机号：

- 无论目标手机号是否已注册，都追加默认 7 天有效的 `PENDING` 邀请，不在提交时建立成员关系。
- 响应始终为 `status=SUBMITTED`，不泄露平台是否已有该手机号账号。
- 目标手机号下一次通过本短信流程完成可信登录时，自动接受仍有效的邀请。
- 运营商一键登录后端换号已实现，并复用同一手机号账号和邀请接受流程；官方 Android SDK
  与真机接入状态见 [modules/auth-phone-wechat.md](modules/auth-phone-wechat.md)。
- 邀请只保存手机号 HMAC 和脱敏值，不保存明文手机号。
- 邀请不能直接授予 `OWNER`，共同所有权必须由现有 `OWNER` 在成员加入后明确授予。

## 首次设置密码

手机号账号内部的随机密码哈希不是可用密码，`password_initialized=false`，API 对外返回
`hasPassword=false`。

```http
PUT /api/auth/password
Authorization: Bearer <token>
Content-Type: application/json

{"oldPassword":"","newPassword":"new-secure-password"}
```

- `hasPassword=false` 时允许不提供旧密码，成功后切换为 `hasPassword=true`。
- `hasPassword=true` 时必须校验旧密码，防止已登录设备静默覆盖现有密码。
- 设置成功后，用户可以使用用户名和新密码登录同一个账号，兔场成员关系不发生变化。

## 安全策略

- MySQL 不再保存验证码、请求 IP 或验证码发送历史。
- Redis/Valkey key 只包含手机号 HMAC、请求 IP HMAC、purpose 和随机 token，不含明文手机号/IP。
- 验证码只以 HMAC-SHA256 摘要进入缓存，默认 5 分钟自动过期。
- 同一手机号默认 60 秒内只能发送一次。
- 同一手机号默认每小时最多 5 次、每天最多 10 次。
- 同一来源 IP 默认每小时最多 20 次。
- 单个验证码默认最多允许 5 次错误尝试。
- 阿里云发送失败的验证码不能用于登录。
- 发送、限流、错误次数和单次消费均由 Redis/Valkey Lua 脚本原子执行。
- 缓存不可用时发送和校验均返回 503，不回退到 MySQL，也不绕过校验。
- 账号、兔场或成员处于停用状态时，手机号验证不能绕过相应状态检查。

可以通过 `APP_SMS_CODE_TTL_SECONDS`、`APP_SMS_RESEND_SECONDS`、
`APP_SMS_MAX_ATTEMPTS`、`APP_SMS_PHONE_HOUR_LIMIT`、
`APP_SMS_PHONE_DAY_LIMIT` 和 `APP_SMS_IP_HOUR_LIMIT` 调整限制。

## 从 MySQL 迁移

Flyway `V19__move_sms_verification_to_cache.sql` 会删除旧的 `sms_verification_codes` 表。部署前必须
先准备 Redis/Valkey 并更新环境变量。迁移时旧表内尚未消费的验证码不会复制到缓存，发布后的
首次验证需要重新获取验证码。

后端启用了 Spring Forwarded Header 处理。生产反向代理必须覆盖客户端传入的
`Forwarded`/`X-Forwarded-For`，并且后端端口不应直接暴露到公网，否则 IP 限流依据可能
被伪造。

## 账号兼容边界

现有用户名密码和 `/api/auth/wechat-login` 兼容接口继续保留。普通登录态绑定 / 换绑已经
实现；微信快捷登录前的强制手机号绑定、短期 `bindingToken`、身份合并和冲突处理仍未实现，后续契约见
[手机号认证现状与后续适配契约](modules/auth-phone-wechat.md)。两个已有账号都存在兔场成员
关系时，未来绑定流程不能静默合并。
