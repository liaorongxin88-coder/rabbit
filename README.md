# Rabbit 养兔管理系统

Rabbit 是面向兔场生产管理的一体化系统，当前仓库包含后端 API、Flutter Android 客户端、平台管理后台和演示/回归脚本。

当前维护入口以 `backend/`、`flutter_app/`、`admin/` 为主；历史原生 Android 客户端已不再作为当前开发入口。

## 当前组成

| 目录 | 定位 | 主要技术 |
| --- | --- | --- |
| `backend/` | 业务后端、权限隔离、Flyway 迁移、平台管理 API | Spring Boot 3.5、MyBatis、MySQL、JDK 21 |
| `flutter_app/` | 并行 Flutter Android 客户端，承接移动端重构 | Flutter、Riverpod、go_router、Dio |
| `admin/` | SaaS 平台管理控制台，面向平台管理员 | React、TypeScript、Vite、Tailwind、Radix |
| `tools/` | 接口演示和回归脚本 | PowerShell |
| `docs/` | 当前项目文档入口 | Markdown |

## 文档导航

- [docs/README.md](docs/README.md)：文档总入口和阅读顺序
- [docs/common/development.md](docs/common/development.md)：本地开发、运行和常用命令
- [docs/common/architecture.md](docs/common/architecture.md)：系统架构、仓库边界和模块职责
- [docs/common/testing.md](docs/common/testing.md)：验证、E2E 和回归测试
- [docs/common/business-baseline.md](docs/common/business-baseline.md)：业务设计基准、Word 文档抽取版和实现对齐说明
- [docs/common/operations.md](docs/common/operations.md)：Docker 部署、配置和运维注意事项

子项目入口：

- [backend/README.md](backend/README.md)
- [flutter_app/README.md](flutter_app/README.md)
- [admin/README.md](admin/README.md)

历史/基准资料：

- [docs/archive/legacy/README.md](docs/archive/legacy/README.md)
- [docs/archive/legacy/养兔管理系统完整技术文档.docx](docs/archive/legacy/养兔管理系统完整技术文档.docx)
- [docs/archive/legacy/养兔管理系统完整技术文档.extracted.md](docs/archive/legacy/养兔管理系统完整技术文档.extracted.md)
- [docs/archive/legacy/养兔管理系统完整技术文档.extracted.json](docs/archive/legacy/养兔管理系统完整技术文档.extracted.json)

## 快速启动

### 后端和 MySQL

```bash
docker compose up -d --build
```

默认服务：

- Backend: `http://localhost:8080`
- MySQL: `localhost:3306`
- MySQL root 密码: `rabbit_root`

生产环境必须覆盖 `APP_JWT_SECRET`、`APP_ADMIN_JWT_SECRET` 和平台管理员 bootstrap 密码。

### Flutter Android 客户端

```bash
cd flutter_app
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

Android 模拟器默认后端地址为 `http://10.0.2.2:8080`。

### 平台管理后台

```bash
pnpm --dir admin install
pnpm --dir admin dev
```

开发默认平台管理员账号由后端 bootstrap 创建：`admin / admin123456`。生产环境必须通过环境变量覆盖或关闭 bootstrap。

## 最小验证

按改动范围选择验证：

```bash
# 后端
mvn --file backend/pom.xml -DskipTests package

# 后端 API E2E
mvn --file backend/pom.xml -Pe2e verify

# Flutter
cd flutter_app
flutter analyze
flutter test
flutter build apk --debug

# Admin
pnpm --dir admin lint
pnpm --dir admin build
```

更多测试说明见 [docs/common/testing.md](docs/common/testing.md)。

## 关键约定

- 后端使用 Flyway 管理数据库结构，任何 schema 变更必须新增迁移脚本。
- 登录态使用 JWT；普通业务请求带 `Authorization: Bearer <token>`。
- 兔舍域请求必须带 `X-House-Id: <houseId>` 并校验 `view/edit/control` 权限。
- 写接口优先支持 `requestId` 幂等。
- 平台管理 API 使用 `/api/admin/**`，不走普通兔舍上下文。
- Flutter 客户端按 `config/data/domain/routing/ui` 分层，不再新增旧式 `features/` 顶层架构。

## 贡献规范

提交和自测要求见 [CONTRIBUTING.md](CONTRIBUTING.md)。
