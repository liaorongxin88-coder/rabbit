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

手机号已绑定账号时直接登录；首次验证成功时自动创建商户和账号，并返回与账号密码
登录相同的 JWT 响应。响应额外包含 `phoneBound=true` 和脱敏后的 `maskedPhone`。
验证码只能成功使用一次。

## 安全策略

- 用户表和验证码表只保存带服务端 pepper 的手机号 HMAC 摘要，不保存明文手机号。
- 验证码只保存 HMAC-SHA256 摘要，默认 5 分钟过期。
- 同一手机号默认 60 秒内只能发送一次。
- 同一手机号默认每小时最多 5 次、每天最多 10 次。
- 同一来源 IP 默认每小时最多 20 次。
- 单个验证码默认最多允许 5 次错误尝试。
- 阿里云发送失败的验证码不能用于登录。
- 验证码挑战记录默认保留 7 天，并由每日清理任务删除。

可以通过 `APP_SMS_CODE_TTL_SECONDS`、`APP_SMS_RESEND_SECONDS`、
`APP_SMS_MAX_ATTEMPTS`、`APP_SMS_PHONE_HOUR_LIMIT`、
`APP_SMS_PHONE_DAY_LIMIT` 和 `APP_SMS_IP_HOUR_LIMIT` 调整限制。
保留期和清理时间可通过 `APP_SMS_RETENTION_DAYS`、`APP_SMS_CLEANUP_CRON` 调整。

后端启用了 Spring Forwarded Header 处理。生产反向代理必须覆盖客户端传入的
`Forwarded`/`X-Forwarded-For`，并且后端端口不应直接暴露到公网，否则 IP 限流依据
可能被伪造。

## 当前兼容边界

本次实现保留现有用户名密码和微信登录契约，不强制历史账号立即绑定手机号。后续如要
落实 [强制手机号绑定设计](modules/auth-phone-wechat.md)，还需要单独实现短期
`bindingToken`、微信身份合并和历史账号迁移，不能在未处理业务数据冲突时静默合并。
