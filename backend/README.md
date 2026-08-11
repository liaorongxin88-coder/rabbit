# Rabbit Backend

Rabbit 后端提供业务 REST API、平台管理 API、权限隔离、Flyway 迁移、审计、提醒扫描和硬件网关适配。

## 技术栈

- JDK 21
- Spring Boot 3.5
- MyBatis
- MySQL 8
- Flyway
- Maven 3.9+

## 本地运行

```bash
cd backend
set -a
source ../.env
set +a
mvn spring-boot:run
```

默认端口：`8080`。

macOS + Homebrew 建议固定 JDK 21：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

## Docker

仓库根目录：

```bash
cp .env.example .env
# 将所有 change-me 占位值替换为分别生成的稳定随机值
docker compose up -d --build
```

只刷新 backend 且保留 MySQL：

```bash
docker compose up -d --build --no-deps backend
```

## 数据库

- 迁移目录：`src/main/resources/db/migration/`
- 当前结构参考：`src/main/resources/db/schema.sql`
- 演示数据参考：`src/main/resources/db/seed_demo.sql`

首次启动由 Flyway 自动执行迁移。任何 schema 变更都必须新增 Flyway 迁移脚本。

## 配置

默认配置：`src/main/resources/application.yml`。

常用环境变量：

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `APP_JWT_SECRET`，必填，至少 32 字节，不接受公开占位值
- `APP_ADMIN_JWT_SECRET`，必填，且必须与应用 JWT 密钥不同
- `APP_ADMIN_BOOTSTRAP_ENABLED`
- `APP_ADMIN_BOOTSTRAP_USERNAME`
- `APP_ADMIN_BOOTSTRAP_PASSWORD`
- `APP_PHONE_HASH_SECRET`，必填的手机号唯一摘要 pepper，必须使用独立稳定随机值
- `APP_SMS_ENABLED`，短信登录开关，默认 `false`
- `APP_SMS_CODE_SECRET`，验证码 HMAC 密钥，启用短信时必须使用独立稳定随机值
- `ALIBABA_CLOUD_ACCESS_KEY_ID`
- `ALIBABA_CLOUD_ACCESS_KEY_SECRET`
- `APP_SMS_SIGN_NAME`
- `APP_SMS_TEMPLATE_CODE`
- `APP_SMS_TEMPLATE_PARAM_NAME`，默认 `code`
- `APP_PHONE_ONE_TAP_ENABLED`，运营商一键登录开关，默认 `false`
- `APP_PHONE_ONE_TAP_ALLOWED_PROVIDERS`，供应商白名单，当前为 `aliyun`
- `APP_PHONE_ONE_TAP_TOKEN_HASH_SECRET`，匿名凭证防重放 HMAC 密钥，启用时必须为独立稳定随机值
- `APP_PHONE_ONE_TAP_IP_MINUTE_LIMIT` / `APP_PHONE_ONE_TAP_IP_HOUR_LIMIT`，匿名接口 IP 限流
- `APP_PHONE_ONE_TAP_CONNECT_TIMEOUT_MS` / `APP_PHONE_ONE_TAP_READ_TIMEOUT_MS`，供应商单次调用超时
- `APP_PHONE_ONE_TAP_SUCCESS_RETRY_WINDOW_SECONDS`，成功请求可重签 JWT 的固定窗口，默认 30 秒，从首次成功起算且重试不会延长
- `APP_PHONE_ONE_TAP_PROCESSING_LEASE_SECONDS`，崩溃后处理租约接管时间，默认 15 秒；启用时必须严格大于连接超时、读取超时与 1000 ms 安全余量之和，否则后端拒绝启动
- `APP_PHONE_ONE_TAP_ATTEMPT_RETENTION_DAYS` / `APP_PHONE_ONE_TAP_RATE_BUCKET_RETENTION_HOURS`，尝试记录和原子限流桶保留期，默认 7 天 / 2 小时
- `APP_PHONE_ONE_TAP_CLEANUP_CRON`，过期尝试和限流桶清理计划，默认每天 `03:35`
- `APP_PHONE_ONE_TAP_ALIYUN_ACCESS_KEY_ID` / `APP_PHONE_ONE_TAP_ALIYUN_ACCESS_KEY_SECRET`，仅授予 `dypns:GetMobile`，不得复用短信凭证
- `APP_FORWARD_HEADERS_STRATEGY`，默认 `none`；仅在可信反向代理覆盖客户端转发头且后端不可直达时设为 `framework`
- `APP_NFC_TAG_ACTIVE_KEY_ID`
- `APP_NFC_TAG_SIGNING_KEYS`，必填，格式为 `1=<base64url-key>,2=<base64url-key>`

后端不会为 JWT 或手机号摘要密钥提供仓库内置默认值；空值、短值和 `change-me` 等公开占位值会
导致启动失败。开发默认平台管理员为 `admin / admin123456`。生产环境必须覆盖或关闭 bootstrap，
并替换 NFC 标签签名密钥；轮换时保留旧 key，只提升 active key id。

## 模块

业务模块位于 `src/main/java/com/rabbit/app/modules/`：

- `auth`、`workspace`、`house`、`cage`、`rabbit`、`batch`
- `event`、`feed`、`treatment`、`weight`
- `inventory`、`sale`、`nfc`
- `audit`、`dedup`、`hardware`
- `admin`、`report`、`setting`

详细边界见 [../docs/backend/README.md](../docs/backend/README.md) 和 [../docs/common/architecture.md](../docs/common/architecture.md)。

## 请求约定

普通业务 API：

- `Authorization: Bearer <token>`
- `X-House-Id: <houseId>` 用于兔舍域请求
- 权限分为 `view`、`edit`、`control`

平台管理 API：

- 路径前缀 `/api/admin/**`
- 使用平台管理员 JWT
- 不使用 `X-House-Id`

写接口优先支持 `requestId` 幂等，避免重复提交造成重复数据。
一键登录会在查询 requestId/token 历史状态前原子占用 IP 分钟与小时额度，因此成功重签、处理中轮询、冲突和重放同样计入限流。

## 常用接口

认证和兔舍：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/sms/code`
- `POST /api/auth/sms/login`
- `POST /api/auth/phone-one-tap-login`
- `POST /api/auth/wechat-login`
- `GET /api/houses`
- `GET /api/workspaces`
- `POST /api/houses`
- `GET /api/house-members`

生产业务：

- `GET /api/cages`
- `POST /api/rabbits`
- `GET /api/rabbits`
- `GET /api/batches`
- `POST /api/batches`
- `GET /api/events`
- `POST /api/events/ack`
- `POST /api/maintenance/events/scan`
- `GET /api/nfc/cages/write-queue`
- `POST /api/nfc/cages/bind`
- `POST /api/nfc/cages/resolve`

平台管理：

- `POST /api/admin/auth/login`
- `GET /api/admin/merchants`
- `POST /api/admin/merchants`
- `PUT /api/admin/merchants/{id}`
- `GET /api/admin/merchants/{id}`

## 验证

```bash
cd backend
mvn -DskipTests package
mvn -Pe2e verify
```

E2E 说明见 [../docs/common/testing.md](../docs/common/testing.md)。手机号登录的阿里云短信配置、
接口契约和限流规则见 [../docs/backend/sms-auth.md](../docs/backend/sms-auth.md)；运营商取号、
防重放和客户端接入边界见 [../docs/backend/modules/auth-phone-wechat.md](../docs/backend/modules/auth-phone-wechat.md)。
多养殖业务的工作空间兼容层和后续模块化路线见
[../docs/backend/modules/farming-workspaces.md](../docs/backend/modules/farming-workspaces.md)。

## 更多文档

- [../docs/backend/README.md](../docs/backend/README.md)
- [../docs/common/development.md](../docs/common/development.md)
- [../docs/common/testing.md](../docs/common/testing.md)
- [../docs/common/operations.md](../docs/common/operations.md)
- [../docs/common/business-baseline.md](../docs/common/business-baseline.md)
