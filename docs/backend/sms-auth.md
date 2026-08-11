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
export APP_SMS_ENABLED=true
```

先配置所有参数，最后再启用 `APP_SMS_ENABLED`。模板变量不是 `code` 时，只修改
`APP_SMS_TEMPLATE_PARAM_NAME`。短信服务中国站默认 Endpoint 为
`dysmsapi.aliyuncs.com`，需要覆盖时使用 `APP_SMS_ALIYUN_ENDPOINT`。

## 接口

发送验证码：

```http
POST /api/auth/sms/code
Content-Type: application/json

{"phone":"13800138000"}
```

成功响应中的 `expiresInSeconds` 默认是 `300`，`retryAfterSeconds` 默认是 `60`。

登录或注册：

```http
POST /api/auth/sms/login
Content-Type: application/json

{"phone":"13800138000","code":"123456"}
```

手机号已绑定账号时直接登录；首次验证成功时只创建 `sys_user`，不会自动创建兔场。响应返回
正式 JWT，并包含 `phoneBound=true`、脱敏后的 `maskedPhone` 和
`hasPassword=false`。验证码只能成功使用一次。

登录完成后客户端调用 `GET /api/houses`：

- 返回空数组：账号已认证但尚无兔场，进入创建或加入兔场流程。
- 返回一个兔场：可以直接进入该兔场。
- 返回多个兔场：恢复最近选择或展示兔场选择页。

没有兔场不是认证失败。零兔场账号仍可以访问资料、首次设置密码、创建兔场和工作空间列表，
但不能访问任何需要 `X-House-Id` 的生产接口。

## 精确手机号邀请

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

- `sys_user` 和验证码表只保存带服务端 pepper 的手机号 HMAC 摘要，不保存明文手机号。
- 验证码只保存 HMAC-SHA256 摘要，默认 5 分钟过期。
- 同一手机号默认 60 秒内只能发送一次。
- 同一手机号默认每小时最多 5 次、每天最多 10 次。
- 同一来源 IP 默认每小时最多 20 次。
- 单个验证码默认最多允许 5 次错误尝试。
- 阿里云发送失败的验证码不能用于登录。
- 验证码挑战记录默认保留 7 天，并由每日清理任务删除。
- 账号、兔场或成员处于停用状态时，手机号验证不能绕过相应状态检查。

可以通过 `APP_SMS_CODE_TTL_SECONDS`、`APP_SMS_RESEND_SECONDS`、
`APP_SMS_MAX_ATTEMPTS`、`APP_SMS_PHONE_HOUR_LIMIT`、
`APP_SMS_PHONE_DAY_LIMIT` 和 `APP_SMS_IP_HOUR_LIMIT` 调整限制。
保留期和清理时间可通过 `APP_SMS_RETENTION_DAYS`、`APP_SMS_CLEANUP_CRON` 调整。

后端启用了 Spring Forwarded Header 处理。生产反向代理必须覆盖客户端传入的
`Forwarded`/`X-Forwarded-For`，并且后端端口不应直接暴露到公网，否则 IP 限流依据可能
被伪造。

## 账号兼容边界

现有用户名密码和 `/api/auth/wechat-login` 兼容接口继续保留。强制手机号绑定、短期
`bindingToken`、微信身份合并和冲突处理当前尚未实现，后续契约见
[手机号认证现状与后续适配契约](modules/auth-phone-wechat.md)。两个已有账号都存在兔场成员
关系时，未来绑定流程不能静默合并。
