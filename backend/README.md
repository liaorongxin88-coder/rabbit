# rabbit-backend

## 环境

- JDK 8
- Maven 3
- MySQL 8（或兼容版本）

## 数据库初始化

1. 创建并进入数据库：`rabbit_app`
2. 执行建表脚本：
   - [schema.sql](file:///d:/rabbit%20app/backend/src/main/resources/db/schema.sql)
3. （可选）执行索引与约束优化脚本：
   - [index.sql](file:///d:/rabbit%20app/backend/src/main/resources/db/index.sql)
4. （可选）导入演示数据脚本（建议先通过接口注册 demo 用户，或保证库里已有 id=1 的用户）：
   - [seed_demo.sql](file:///d:/rabbit%20app/backend/src/main/resources/db/seed_demo.sql)

默认连接配置见：
- [application.yml](file:///d:/rabbit%20app/backend/src/main/resources/application.yml)

## 迁移体系（Flyway）

已引入 Flyway，用于对后续数据库变更做“可追踪版本”迁移：
- 版本脚本目录：`backend/src/main/resources/db/migration/`
- 默认开启 `baseline-on-migrate=true`，用于把既有库作为基线后继续演进

## 启动

- `mvn spring-boot:run`
- 默认端口：`8080`

## 打包

- `mvn -DskipTests package`
- 产物：`target/rabbit-backend-0.0.1-SNAPSHOT.jar`
- 运行：`java -jar target/rabbit-backend-0.0.1-SNAPSHOT.jar`

## Docker 部署

仓库根目录提供 `docker-compose.yml`（MySQL + backend）：

- 启动：`docker compose up -d --build`
- 后端端口：`http://localhost:8080`
- MySQL 端口：`localhost:3306`（root / rabbit_root）

初始化：
- 首次启动由 Flyway 自动执行版本迁移（`db/migration/`），完成建表与索引初始化

配置：
- 可在 `docker-compose.yml` 修改：
  - `APP_JWT_SECRET`（对应 `app.jwt.secret`）
  - `SPRING_DATASOURCE_*`

## 一键演示脚本

仓库根目录提供 [demo_flow.ps1](file:///d:/rabbit%20app/tools/demo_flow.ps1)，用于自动跑通：
- 注册/登录 → 创建兔舍 → 初始化周期(全部 0 天) → 建公母兔 → 建批次/配种 → 转后备 → 拉取提醒

## 请求约定

- `Authorization: Bearer <token>`
- `X-House-Id: <houseId>`

## 主要接口

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/houses`
- `POST /api/houses`
- `GET /api/house-members`
- `POST /api/house-members`
- `PUT /api/house-members/{memberUserId}`
- `DELETE /api/house-members/{memberUserId}`
- `GET /api/cages`
- `POST /api/rabbits`
- `GET /api/rabbits`
- `GET /api/rabbits/{id}`
- `PUT /api/rabbits/{id}`
- `POST /api/rabbits/replacement`
- `GET /api/batches`
- `POST /api/batches`
- `GET /api/batches/{batchId}/batch-rabbits`
- `POST /api/batches/{batchId}/mating`
- `POST /api/batches/{batchId}/aphrodisiac/start`
- `POST /api/batches/{batchId}/aphrodisiac/finish`
- `POST /api/batches/{batchId}/pregnancy-check`
- `POST /api/batches/{batchId}/prepartum/finish`
- `POST /api/batches/{batchId}/parturition`
- `POST /api/batches/{batchId}/weaning`
- `POST /api/batches/{batchId}/sale`
- `GET /api/prepartum-records`
- `GET /api/events`
- `POST /api/events/ack`
- `GET /api/event-reminder-logs`
- `GET /api/hardware/status`
- `POST /api/hardware/aphrodisiac/start`
- `POST /api/hardware/aphrodisiac/finish`
- `GET /api/reports/feed-summary`
- `GET /api/reports/feed-logs.csv`
- `GET /api/reports/breeding-summary`
- `GET /api/reports/event-ack-summary`
- `GET /api/reports/event-ack-summary.csv`
- `GET /api/settings`
- `PUT /api/settings`
- `POST /api/maintenance/events/scan`
- `GET /api/breeding-performance`
- `GET /api/rabbit-status-history`
- `POST /api/feed-logs`
- `GET /api/feed-logs`
- `GET /api/abnormal`
- `POST /api/abnormal/{id}/deal`
- `POST /api/replacement-records/mark-notified`
- `GET /api/replacement-records`
