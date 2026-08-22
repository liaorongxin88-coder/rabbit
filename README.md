# Rabbit 养兔管理系统

Rabbit 是面向兔场生产管理的一体化系统。当前仓库维护一个后端和两个客户端，并包含数据库迁移、自动化验证、部署脚本和项目文档。

## 子项目

| 目录 | 定位 | 技术 |
| --- | --- | --- |
| `backend/` | 业务 API、权限、生产流程、Flyway 迁移和平台管理 API | Spring Boot 3.5、Java 21、MyBatis、MySQL |
| `app/` | 兔场现场使用的 Flutter Android 客户端 | Flutter、Riverpod、go_router、Dio |
| `admin/` | 平台管理端和业务工作台 | React 19、TypeScript、Vite、Tailwind、Radix |
| `scripts/` | CI、构建、E2E 和发布预检脚本 | Bash、Node.js |
| `deploy/` | 生产镜像激活和远端部署脚本 | Docker Compose、Bash |
| `docs/` | 从项目总体到子项目的工程文档 | Markdown |

## 文档

从 [docs/README.md](docs/README.md) 开始。文档按以下层级组织：

- [项目总体](docs/project/README.md)
- [后端](docs/backend/README.md)
- [Flutter App](docs/app/README.md)
- [Admin](docs/admin/README.md)
- [跨端业务专题](docs/features/README.md)
- [部署与发布](docs/operations/README.md)

## 快速启动

### Backend 和 MySQL

```bash
cp .env.example .env
# 将所有 change-me 占位值替换为分别生成的稳定随机值
docker compose up -d --build
```

默认 backend 地址是 `http://127.0.0.1:8080`。MySQL 只在 Compose 网络内开放，不映射宿主机端口。短信和应用缓存默认关闭，按需启用 Redis 或 Valkey profile。

### Flutter App

```bash
cd app
./rabbit bootstrap
./rabbit check
./rabbit apk dev --debug
```

Android 模拟器默认通过 `http://10.0.2.2:8080` 访问宿主机 backend。

### Admin

```bash
pnpm --dir admin install
pnpm --dir admin dev
```

Vite 开发服务器默认把 `/api` 代理到 `http://127.0.0.1:8080`。

## 验证

按改动范围运行对应入口：

```bash
# 后端单测，包含 Checkstyle
mvn --file backend/pom.xml test

# 后端 MySQL E2E
mvn --file backend/pom.xml -Pe2e verify

# Flutter 静态检查和测试
(cd app && ./rabbit check)

# Admin lint、测试和构建
pnpm --dir admin lint
pnpm --dir admin test
pnpm --dir admin build

# 三端快速质量门禁
./scripts/ci/check.sh
```

完整测试、浏览器和真机验收见 [docs/project/testing.md](docs/project/testing.md)。

## 关键约定

- 数据库结构只通过 `backend/rabbit-boot/src/main/resources/db/migration/` 下的 Flyway 迁移演进。
- 普通业务请求使用业务用户 JWT，兔场范围请求还必须携带并校验 `X-House-Id`。
- 平台管理 API 使用 `/api/admin/**` 和独立 JWT，不进入普通兔场上下文。
- 写接口通过 `requestId` 和服务端去重逻辑处理重复提交。
- Flutter 源码按 `config`、`data`、`domain`、`routing` 和 `ui` 分层。
- Admin 平台端与业务工作台共享组件，但会话、请求客户端和权限边界相互隔离。

贡献、提交和自测要求见 [CONTRIBUTING.md](CONTRIBUTING.md)。
