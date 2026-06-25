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
- `APP_JWT_SECRET`
- `APP_ADMIN_JWT_SECRET`
- `APP_ADMIN_BOOTSTRAP_ENABLED`
- `APP_ADMIN_BOOTSTRAP_USERNAME`
- `APP_ADMIN_BOOTSTRAP_PASSWORD`

开发默认平台管理员为 `admin / admin123456`。生产环境必须覆盖或关闭 bootstrap。

## 模块

业务模块位于 `src/main/java/com/rabbit/app/modules/`：

- `auth`、`house`、`cage`、`rabbit`、`batch`
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

## 常用接口

认证和兔舍：

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/wechat-login`
- `GET /api/houses`
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

E2E 说明见 [../docs/common/testing.md](../docs/common/testing.md)。

## 更多文档

- [../docs/backend/README.md](../docs/backend/README.md)
- [../docs/common/development.md](../docs/common/development.md)
- [../docs/common/testing.md](../docs/common/testing.md)
- [../docs/common/operations.md](../docs/common/operations.md)
- [../docs/common/business-baseline.md](../docs/common/business-baseline.md)
