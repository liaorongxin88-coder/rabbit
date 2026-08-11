# 手机号认证现状与后续适配契约

手机号是 Rabbit 移动账号的稳定绑定锚点，`phone_hash` 是服务端查找账号和匹配精确手机号
邀请的唯一键。认证账号与兔场成员关系相互独立；完成认证但没有兔场是合法状态。

## 实现状态

以下状态以当前后端代码为准：

| 能力 | 状态 | 当前接口 |
| --- | --- | --- |
| 短信验证码发送与登录 | 已实现 | `POST /api/auth/sms/code`、`POST /api/auth/sms/login` |
| 手机号 HMAC 账号、零兔场登录 | 已实现 | 短信登录、`GET /api/auth/me`、`GET /api/houses` |
| 精确手机号邀请 | 已实现 | `POST /api/house-invitations` |
| `hasPassword` 首次设置密码 | 已实现 | `PUT /api/auth/password` |
| 登录态绑定 / 更换手机号 | 已实现 | `PUT /api/auth/phone` |
| 短信找回密码 | 已实现 | `POST /api/auth/sms/reset-password` |
| 用户名密码登录 | 已实现并保留 | `POST /api/auth/register`、`POST /api/auth/login` |
| 旧微信登录兼容 | 已实现并保留 | `POST /api/auth/wechat-login` |
| 阿里云本机号码一键登录后端 | 已实现，默认关闭 | `POST /api/auth/phone-one-tap-login` |
| Android 官方号码认证 SDK | **待控制台 AAR 和真机接入** | Flutter 入口默认隐藏 |
| 微信快捷登录前强制绑定手机 | **未实现** | 无 `wechat-quick-login`、`bindingToken` 或账号合并接口 |

一键登录后端已经具备真实 token 换号、幂等和账号归一能力，但只有 Android 官方 SDK、控制台
方案和后端凭证都配置完成时才能开启。普通登录态已可维护手机号；微信快捷登录前的强制绑定与
已有手机号账号合并仍是后续契约，不能据此调用尚不存在的 API。

## 当前账号数据

`sys_user` 保存手机号身份和账号状态：

```text
phone_country_code
phone_hash             unique, server-side HMAC
phone_masked           display only
phone_bound_time
password_initialized   exposed as hasPassword
status                 ENABLED / DISABLED
```

- `phone_hash` 用于唯一约束、短信登录查找和精确手机号邀请匹配。
- `phone_masked` 仅用于展示，例如 `138****8000`。
- 完整手机号不写入用户表、日志、审计详情或异常消息。
- `password_initialized=false` 表示内部随机哈希不是用户可用密码。
- 账号可以没有任何兔场；兔场访问只由 `house_users` 决定。

## 当前手机号短信登录

```http
POST /api/auth/sms/code
Content-Type: application/json

{"phone":"13800138000","purpose":"LOGIN_OR_REGISTER"}
```

```http
POST /api/auth/sms/login
Content-Type: application/json

{"phone":"13800138000","code":"123456","purpose":"LOGIN_OR_REGISTER"}
```

登录事务：

1. 消费一次性验证码并标准化手机号。
2. 以服务端 pepper 计算 `phone_hash`，加锁查询账号。
3. 账号不存在时只创建 `sys_user`，不创建兔场，并设置
   `status=ENABLED`、`password_initialized=false`。
4. 接受该 `phone_hash` 尚在有效期内的精确手机号邀请。
5. 返回正式 JWT、`phoneBound=true`、`maskedPhone` 和 `hasPassword`。
6. 客户端读取兔场列表，决定进入业务、选择兔场或显示零兔场页。

并发首次登录依靠 `phone_hash` 唯一约束和冲突后回查，不能创建重复账号。

## 当前登录态手机号绑定与换绑

已登录业务账号通过独立用途的验证码绑定或更换手机号：

```http
PUT /api/auth/phone
Authorization: Bearer <token>
Content-Type: application/json

{
  "phone": "13800138001",
  "code": "123456",
  "currentPassword": "current-password"
}
```

- 新手机号验证码必须以 `BIND_PHONE` purpose 获取，不能复用登录或重置密码验证码。
- 首次绑定只要求正式 JWT 和新手机号验证码。
- 已绑定手机号且已有密码时，换绑必须同时验证当前密码。
- 已绑定手机号但尚未设置密码时，提交原手机号及以 `VERIFY_CURRENT_PHONE` purpose 获取的
  原手机号验证码，再验证新手机号。
- 新手机号已经属于其他账号时返回冲突，不静默合并账号或迁移兔场成员关系。
- 最终写入再次校验原绑定状态和 `phone_hash` 唯一约束；并发换绑导致状态变化时要求刷新重试。
- 绑定成功会接受新手机号仍有效的精确邀请；旧手机号已经建立的兔场成员关系不受影响。
- 用户表仍只保存 `phone_hash`、脱敏值和绑定时间，不保存完整手机号。

## 当前精确手机号邀请

```http
POST /api/house-invitations
Authorization: Bearer <token>
X-House-Id: <houseId>
Content-Type: application/json

{
  "phone": "13800138000",
  "role": "STAFF",
  "requestId": "uuid"
}
```

当前防枚举契约：

- 接口统一返回 `{"status":"SUBMITTED","role":"STAFF"}`，不透露目标手机号是否已有账号。
- 无论目标手机号是否已经注册，都先追加一条 `PENDING` 邀请；邀请不会在提交时直接建立成员关系。
- 目标账号下一次通过短信验证码完成可信手机号登录时，后端接受仍有效的邀请并建立成员关系。
- 同一手机号和兔场存在多条有效邀请时，以最新一条的角色为准，并一次性解决此前待处理记录。
- 邀请只保存 `phone_hash` 和脱敏值，不保存明文手机号。
- 角色只能是 `MANAGER`、`STAFF`、`VIEWER`；共同 `OWNER` 必须在加入后明确授予。
- 不提供手机号模糊搜索，也不通过响应或错误消息枚举平台账号。
- `(house_id, invited_by_user_id, request_id)` 保证追加式幂等；同一 `requestId` 不能改投其他手机号或角色。

短信验证码和运营商服务端换号都是可信手机号入口，并复用同一个邀请接受事务；没有独立的
运营商账号或另一套邀请状态。

## 当前零兔场和密码语义

手机号首次登录且没有有效邀请时，`GET /api/houses` 返回空数组。账号仍可访问账号资料、设置
密码、创建兔场和工作空间列表，但不能访问任何需要 `X-House-Id` 的生产接口。

手机号账号首次创建时返回 `hasPassword=false`。内部随机哈希仅保证密码列不可直接使用，不是
用户密码。

```http
PUT /api/auth/password
Authorization: Bearer <token>
Content-Type: application/json

{
  "oldPassword": "",
  "newPassword": "new-secure-password"
}
```

- `hasPassword=false`：允许省略或传空 `oldPassword`，设置后原子更新密码哈希和
  `password_initialized=true`。
- `hasPassword=true`：必须提供并校验旧密码。
- `GET /api/auth/me` 和登录响应都返回 `hasPassword`。
- 设置密码不会创建新账号，也不会改变任何 `house_users` 关系。

V15 之前的历史账号无法从密码哈希证明密码是系统随机值还是用户已设置的真实密码，因此迁移
时保守保持 `password_initialized=true`；只有明确的重置哨兵账号会设为 `false`，并同时停用。
这避免持有旧 JWT 的会话在不知道旧密码时覆盖真实密码。V15 之后新建的短信账号由后端明确
写入 `password_initialized=false`，仍可使用上述首次设置密码流程。

## 当前微信兼容边界

`POST /api/auth/wechat-login` 是现有兼容接口，可使用微信 `code`，并在开发兼容模式下接收
`openid`。它按 `sys_user.openid` 查找或创建账号，当前会直接返回正式 JWT；它**不会**返回
`bindingToken`，也不会强制绑定手机号。

因此当前不能声称“所有微信账号已经绑定手机”。已取得正式 JWT 的微信账号可以在账号设置中
通过 `PUT /api/auth/phone` 绑定一个尚未属于其他账号的手机号；若手机号已有账号，后端拒绝
绑定，不得根据微信身份猜测手机号或静默合并账号。

## 当前：阿里云运营商一键登录后端

后端使用阿里云号码认证服务 `GetMobile`，只接受 Android SDK 取得的不透明一次性 token：

```http
POST /api/auth/phone-one-tap-login
Content-Type: application/json

{
  "provider": "aliyun",
  "accessToken": "short-lived-token-from-sdk",
  "requestId": "uuid"
}
```

处理规则：

1. `provider` 必须在服务端白名单；客户端不能选择任意 endpoint，也不能提交手机号。
2. 后端只保存 `provider + accessToken` 的 HMAC 摘要，不保存原 token 或换取到的明文手机号。
3. `requestId` 和 token 摘要各自唯一；同一请求只在成功后的短幂等窗口内可重新签发 JWT，
   窗口从首次成功起算且不会被重试延长，过期后固定拒绝。
4. 同一 `requestId` 改投其他 token 返回冲突，同一 token 换 `requestId` 被视为重放。
5. 供应商换号在数据库事务之外执行；成功后进入现有 `loginOrRegisterPhone` 事务，复用
   `phone_hash`、停用检查、邀请接受、零兔场和 JWT 响应。
6. 匿名请求通过数据库时间桶做原子的分钟和小时 IP 限流，并在幂等命中前计数；默认不信任
   客户端转发头。供应商错误、超时和内部异常只返回固定脱敏消息。
7. 阿里云调用设置短连接/读取超时，关闭 SDK 自动重试；结果不确定时客户端必须重新取 token。
8. `PROCESSING` 使用短租约和 lease id；进程崩溃后可由新请求安全接管，旧调用结果不能覆盖接管者。
   租约必须严格大于连接超时、读取超时与 1000 ms 安全余量之和，否则启用时后端拒绝启动。
   终态尝试和限流桶按保留期定时清理，避免记录永久增长。

V17 新增 `phone_one_tap_attempts`，只保存幂等、防重放需要的摘要及状态，不保存手机号；V18 为
处理租约增加 lease id，并新增原子 IP 限流桶。
相关环境变量：

```text
APP_PHONE_ONE_TAP_ENABLED=false
APP_PHONE_ONE_TAP_ALLOWED_PROVIDERS=aliyun
APP_PHONE_ONE_TAP_TOKEN_HASH_SECRET=<stable-random-secret>
APP_PHONE_ONE_TAP_IP_MINUTE_LIMIT=10
APP_PHONE_ONE_TAP_IP_HOUR_LIMIT=60
APP_PHONE_ONE_TAP_CONNECT_TIMEOUT_MS=2000
APP_PHONE_ONE_TAP_READ_TIMEOUT_MS=3000
APP_PHONE_ONE_TAP_SUCCESS_RETRY_WINDOW_SECONDS=30
APP_PHONE_ONE_TAP_PROCESSING_LEASE_SECONDS=15
APP_PHONE_ONE_TAP_ATTEMPT_RETENTION_DAYS=7
APP_PHONE_ONE_TAP_RATE_BUCKET_RETENTION_HOURS=2
APP_PHONE_ONE_TAP_CLEANUP_CRON="0 35 3 * * ?"
APP_PHONE_ONE_TAP_ALIYUN_ENDPOINT=dypnsapi.aliyuncs.com
APP_PHONE_ONE_TAP_ALIYUN_ACCESS_KEY_ID=<ram-access-key-id>
APP_PHONE_ONE_TAP_ALIYUN_ACCESS_KEY_SECRET=<ram-access-key-secret>
```

号码认证 RAM 身份只需 `dypns:GetMobile`，必须与短信
`ALIBABA_CLOUD_ACCESS_KEY_ID/SECRET` 分离。所有配置默认关闭；启用时缺少独立 HMAC 密钥、
provider 或凭证，或者处理租约不满足供应商总超时加 1000 ms 安全余量，都会阻止后端启动。

`APP_FORWARD_HEADERS_STRATEGY` 默认 `none`。仅当后端不允许客户端直达、可信代理会覆盖所有
转发头时才可设为 `framework`，否则来源 IP 限流必须使用实际 TCP 对端地址。

Android 仍需从阿里云控制台下载官方 AAR，为 `dev/staging/prod` 的包名及对应 debug/release
签名分别登记方案，并完成隐私授权页和三网真机验证。在此之前 Flutter 的一键登录开关保持
关闭，用户继续使用短信验证码。

官方参考：[Android 客户端接入](https://help.aliyun.com/zh/pnvs/developer-reference/the-android-client-access)、
[GetMobile API](https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-getmobile)、
[合规指南](https://help.aliyun.com/zh/pnvs/security-and-compliance/number-certification-service-compliance-guidelines)。

## 后续：微信绑定手机号契约（未实现）

> 状态：当前没有 `POST /api/auth/wechat-quick-login`、`bindingToken`、
> 未登录强制绑定或账号合并实现。普通正式登录态的 `PUT /api/auth/phone` 不替代本契约。

后续实现应满足：

- 微信 code 验证成功但手机号未绑定时，只签发 5 到 10 分钟、
  `purpose=bind_phone` 的 `bindingToken`；普通业务鉴权不得接受该 token。
- 绑定接口必须用短信验证码或运营商服务端换号结果取得可信手机号。
- 手机号尚无账号时可绑定到当前身份；手机号已有账号时必须显式处理身份归并。
- 两个账号任一侧存在 `house_users` 时都属于有业务数据；两侧均有归属时不得静默迁移，需进入
  明确的冲突处理流程。
- 绑定成功后复用同一个 `phone_hash` 邀请接受事务并签发正式 JWT。
- 共同 `OWNER` 也是业务归属，账号合并不得扩大、缩小或重写兔场权限。

这部分落地时必须同步修改后端拦截器、Flutter 路由守卫、短期 token 存储、测试和本文的实现
状态表；在此之前，`bindingToken` 仅是规划术语。

## 审计和安全

- 记录登录方式、邀请处理结果、失败原因和 traceId，但只写脱敏手机号。
- 运营商 token、微信 code、短信验证码和完整手机号不得写入日志。
- 登录、发送验证码和邀请需要限流；邀请写接口保持 `requestId` 幂等。
- 账号 `DISABLED` 时，已有 JWT 和新的手机号登录都必须被拒绝。
- 兔场业务始终通过 `X-House-Id` 和实时 `house_users` 授权；认证方式不能扩大兔场权限。
