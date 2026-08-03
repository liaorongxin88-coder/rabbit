# Rabbit 文档索引

本目录按 harness 工程文档方式组织：先读公共约定，再进入对应子项目和模块索引。根目录 README 只做项目入口，本文件是维护者和 agent 的主导航。

## 快速路由

| 任务 | 先读 | 再读 |
| --- | --- | --- |
| 本地启动或调试环境 | [common/development.md](common/development.md) | 对应子项目 README |
| 判断系统边界和数据流 | [common/architecture.md](common/architecture.md) | [backend/README.md](backend/README.md)、[flutter_app/README.md](flutter_app/README.md)、[admin/README.md](admin/README.md) |
| 后端 API、权限、数据库或迁移 | [backend/README.md](backend/README.md) | [backend/modules/api-and-permissions.md](backend/modules/api-and-permissions.md)、[backend/modules/data-and-migrations.md](backend/modules/data-and-migrations.md) |
| Flutter Android 客户端 | [flutter_app/README.md](flutter_app/README.md) | [flutter_app/modules/rabbit-management-flow.md](flutter_app/modules/rabbit-management-flow.md)、`../flutter_app/.rule` |
| SaaS 平台管理后台 | [admin/README.md](admin/README.md) | [admin/modules/platform-admin.md](admin/modules/platform-admin.md)、`../admin/.rules`、`../admin/DESIGN.md` |
| 测试、E2E 或验收 | [common/testing.md](common/testing.md) | [批量出库完整业务场景测试方案](batch-outbound-test-plan.md)、对应子项目 README |
| Docker 部署或运维 | [common/operations.md](common/operations.md) | [backend/README.md](backend/README.md) |
| 对照原始业务设计 | [common/business-baseline.md](common/business-baseline.md) | [archive/legacy/README.md](archive/legacy/README.md) |

## 目录结构

```text
docs/
  README.md
  common/
    architecture.md
    business-baseline.md
    development.md
    operations.md
    testing.md
  backend/
    README.md
    modules/
      api-and-permissions.md
      data-and-migrations.md
      domain-modules.md
  flutter_app/
    README.md
    modules/
      rabbit-management-flow.md
  admin/
    README.md
    modules/
      platform-admin.md
  archive/
    legacy/
      README.md
      ...
```

## 当前应用入口

- 后端源码 README：[../backend/README.md](../backend/README.md)
- Flutter 客户端源码 README：[../flutter_app/README.md](../flutter_app/README.md)
- Admin 源码 README：[../admin/README.md](../admin/README.md)
- 提交和自测规范：[../CONTRIBUTING.md](../CONTRIBUTING.md)

## 文档维护规则

- 新的跨项目规则放在 `docs/common/`。
- 子项目特有规则放在 `docs/<subproject>/README.md` 或 `docs/<subproject>/modules/`。
- 业务原始资料、抽取产物和过时入口放在 `docs/archive/legacy/`，只能作为背景参考。
- 根目录 README 不承载长篇细节，只指向本索引和常用命令。
- 修改路径后必须检查 Markdown 链接是否仍然能解析。
