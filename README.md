# 养兔管理系统（Rabbit App）

面向兔场日常生产管理的一体化系统：
- 后端：Spring Boot + MyBatis + MySQL（REST API）
- 移动端：原生 Android（Java + XML）

本仓库把“业务可落地”作为目标：多兔舍权限隔离、幂等写入、审计与 TraceId、提醒闭环（定时扫描 + 通知）、可导出 CSV、可选硬件联动网关。

---

## 目录

- [功能概览](#功能概览)
- [仓库结构](#仓库结构)
- [快速开始（Docker 一键启动）](#快速开始docker-一键启动)
- [本地开发运行（Backend）](#本地开发运行backend)
- [Android 运行/构建](#android-运行构建)
- [配置说明（关键项）](#配置说明关键项)
- [数据库与迁移（Flyway）](#数据库与迁移flyway)
- [关键设计约定](#关键设计约定)
- [运维建议](#运维建议)
- [贡献与提交规范](#贡献与提交规范)
- [常见问题](#常见问题)

---

## 功能概览

核心业务（已实现）：
- 兔舍/成员/权限：多兔舍、成员管理、view/edit/control 三档权限
- 笼位与兔只：笼位维护、兔只录入/编辑/状态流转、NFC 绑定/解绑与离线缓存兜底
- 批次全流程：催情、配种、摸胎、备产、分娩、断奶、出售、转后备、后备成熟提醒
- 投喂/用药/异常/离场：现场高频录入与记录查询
- 提醒闭环：每日定时扫描 + notified 标记 + Android 后台通知 + 首页角标 + 扫描日志可核对
- 审计与追溯：X-Trace-Id 贯穿、审计表记录接口访问与业务码

报表/导出（CSV）：
- 报表：投喂记录导出、事件确认汇总导出
- 审计日志导出：`/api/audit-logs.csv`
- 库存导出：`/api/inventory/items.csv`、`/api/inventory/txs.csv`

硬件联动（可接入）：
- 后端提供硬件控制 API（催情 start/finish）
- 默认 Noop；支持配置为 HTTP 网关（对接真实设备服务）

---

## 仓库结构

- `backend/`：Spring Boot 后端（REST API）
- `android/`：原生 Android App（Java + XML）
- `docker-compose.yml`：MySQL + backend 一键部署
- `tools/`：演示/回归脚本（PowerShell）
- `养兔管理系统完整技术文档.*`：原始技术文档及抽取版（便于检索对照）

子模块说明：
- 后端模块 README：见 [backend/README.md](file:///d:/rabbit%20app/backend/README.md)
- Android 模块 README：见 [android/README.md](file:///d:/rabbit%20app/android/README.md)

---

## 快速开始（Docker 一键启动）

前置要求：
- Docker Desktop / Docker Engine
- 开放端口：`8080`（后端）、`3306`（MySQL，可选）

启动：

```bash
docker compose up -d --build
```

访问：
- 后端：`http://localhost:8080`
- MySQL：`localhost:3306`（root / rabbit_root）

首次启动说明：
- 后端启动后 Flyway 会自动执行 `backend/src/main/resources/db/migration/` 下的迁移脚本，完成建表与索引初始化

建议立刻修改的配置：
- `docker-compose.yml` 里的 `APP_JWT_SECRET`（生产必须更换）

---

## 本地开发运行（Backend）

### 环境要求

- JDK 8
- Maven 3
- MySQL 8（或兼容版本）

### 运行步骤

1) 准备数据库
- 新建数据库：`rabbit_app`
- 推荐直接启动 MySQL（本机或 Docker 均可）

2) 修改配置
- 默认配置：`backend/src/main/resources/application.yml`
- 本地建议通过环境变量覆盖（避免提交敏感信息）

3) 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认端口：`8080`

### 打包与运行

```bash
cd backend
mvn -DskipTests package
java -jar target/rabbit-backend-0.0.1-SNAPSHOT.jar
```

### 一键跑通主流程（接口驱动）

仓库提供两套脚本：
- 基础演示：[tools/demo_flow.ps1](file:///d:/rabbit%20app/tools/demo_flow.ps1)
- 全链路演示（配种→分娩→断奶→出售→后备→扫描提醒→繁殖性能重算）：[tools/demo_flow_full.ps1](file:///d:/rabbit%20app/tools/demo_flow_full.ps1)

示例（PowerShell）：

```powershell
.\tools\demo_flow_full.ps1 -BaseUrl "http://localhost:8080"
```

---

## Android 运行/构建

### 环境要求

- Android Studio（推荐）
- JDK（由 Android Studio 管理）

注意：
- `android/` 目录当前不包含 Gradle Wrapper（`gradlew/gradlew.bat`），建议在 Android Studio 中打开并构建。

### 配置后端地址

默认配置在：
- [Config.java](file:///d:/rabbit%20app/android/app/src/main/java/com/rabbit/app/Config.java)

网络说明：
- 模拟器访问本机后端：`http://10.0.2.2:8080`
- 真机调试：改成你电脑的局域网 IP（例如 `http://192.168.1.10:8080`），并保证同一网络可访问

---

## 配置说明（关键项）

后端核心配置（`backend/src/main/resources/application.yml`，可用环境变量覆盖）：

- **数据库**
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
- **JWT**
  - `APP_JWT_SECRET` → `app.jwt.secret`
- **MyBatis 写安全守卫**
  - `app.mybatis.write-guard.max-affected-rows`
  - `app.mybatis.write-guard.ignore-row-limit-ids`
  - `app.mybatis.write-guard.ignore-no-where-ids`
- **库存负库存保护（可选）**
  - `app.inventory.forbid-negative=true`
- **提醒扫描（已实现，默认开启定时扫描 Job）**
  - 扫描接口：`POST /api/maintenance/events/scan`
  - 扫描日志：`GET /api/event-reminder-logs`
- **繁殖性能全量重算（可选定时）**
  - 手动触发：`POST /api/maintenance/breeding-performance/recalc`
  - 定时开关：`app.breeding-performance.recalc.enabled=true`
- **硬件联动（可选）**
  - `app.hardware.enabled=true|false`
  - `app.hardware.gateway=noop|http`
  - `app.hardware.http.base-url`
  - `app.hardware.http.token`

---

## 数据库与迁移（Flyway）

数据库基线与迁移：
- 基线建表脚本：`backend/src/main/resources/db/migration/V1__init.sql`
- 后续迁移目录：`backend/src/main/resources/db/migration/`
- 辅助文件：
  - `db/schema.sql`：当前全量结构参考（用于新库/排障）
  - `db/seed_demo.sql`：演示数据（更推荐使用 `tools/demo_flow_full.ps1` 通过接口生成演示数据）

迁移编写规范（强制）：
- 新增/变更表结构一律写 Flyway 迁移脚本
- 迁移脚本必须可重复在新库执行、且在生产环境可回放
- 禁止把密钥/账号写入 SQL

---

## 关键设计约定

### 鉴权与兔舍上下文

- 登录态：JWT
- 请求头约定：
  - `Authorization: Bearer <token>`
  - `X-House-Id: <houseId>`
- 权限模型：`view/edit/control`
  - control 用于成员管理/审计/硬件/维护类入口

### 幂等与重复提交保护

写接口普遍支持 `requestId`（客户端生成），后端通过去重表保证重复提交不会造成重复写入。

### TraceId 与审计

- 请求可携带 `X-Trace-Id`，后端会透传/生成并写入日志上下文
- `audit_logs` 记录 `/api/**` 的访问审计（含业务码、耗时、IP、UA、traceId）
- 导出：`GET /api/audit-logs.csv`

### 提醒闭环

- 后端每日定时扫描 due 事件，并将对应记录标记为 notified
- Android 后台 Worker 拉取未通知提醒并系统通知
- 扫描结果可通过 `event_reminder_logs` 核对

---

## 运维建议

- **备份**：定期备份 MySQL（至少包含 `rabbit_app`），并演练恢复
- **安全**：
  - 生产必须更换 `APP_JWT_SECRET`
  - 生产环境禁止打印/上报 token、数据库密码、硬件 token
- **时间**：统一 `Asia/Shanghai`，避免 due 计算跨时区问题
- **升级**：
  - 升级后端版本时先跑 Flyway 迁移
  - 如果做了索引/约束迁移，建议在低峰执行

---

## 贡献与提交规范

详见 [CONTRIBUTING.md](file:///d:/rabbit%20app/CONTRIBUTING.md)。

最小要求：
- 所有提交必须可编译（后端 `mvn -DskipTests package`）
- 数据库变更必须带 Flyway 迁移脚本
- 提交信息必须结构化（推荐 Conventional Commits）

---

## 常见问题

### Docker 启动失败（Windows / Docker Desktop）

- 优先确认 Docker Desktop 引擎已启动，`docker ps` 正常。

### Android 真机无法访问后端

- 确保手机与电脑同一局域网
- 后端地址用电脑局域网 IP，不要用 `localhost`

### Flyway baseline / 迁移异常

- 新库建议直接使用 `docker compose up` 走 Flyway 初始化
- 老库接入时确认 baseline 配置与迁移版本一致

