# Rabbit 项目文档

`docs/` 是 Rabbit 的工程文档入口。阅读顺序从项目总体开始，再进入后端、移动端或管理端，最后按需查阅业务专题、交付流程和历史资料。

## 阅读顺序

1. [项目级文档](project/README.md)：系统边界、总体架构、本地开发、测试、业务基准和演进说明。
2. [后端](backend/README.md)、[Flutter App](app/README.md) 或 [Admin](admin/README.md)：进入要修改的子项目。
3. [跨端业务专题](features/README.md)：查阅批量出库、母兔生产流程等跨子项目契约。
4. [部署与发布](operations/README.md)：处理 Compose、CI/CD、制品、生产部署和回滚。
5. [架构决策](adr/README.md) 与 [历史资料](archive/README.md)：追溯决策背景或旧版设计。

## 目录

```text
docs/
  README.md
  project/                 # 项目总体、开发、测试和业务基准
  backend/                 # Spring Boot 后端
  app/                     # Flutter Android 客户端
  admin/                   # React 管理端和业务工作台
  features/                # 跨端业务专题
  operations/              # 部署、发布和运维
  adr/                     # 架构决策记录
  archive/                 # 仅供追溯的历史资料
```

每个一级目录都有自己的 `README.md`。先读该入口，再进入模块或专题文件。

## 事实归属

| 内容 | 权威入口 |
| --- | --- |
| 系统边界、身份、租户和数据流 | [project/architecture.md](project/architecture.md) |
| 本地环境和启动方式 | [project/development.md](project/development.md) |
| 测试、E2E 和验收 | [project/testing.md](project/testing.md) |
| 新交互必须证明的性质 | [project/interaction-acceptance-spec.md](project/interaction-acceptance-spec.md) |
| 关键模型为何被推翻及重建 | [project/evolution.md](project/evolution.md) |
| 后端模块、API、权限和迁移 | [backend/README.md](backend/README.md) |
| 写操作追踪、切面顺序和事件流 | [backend/modules/operation-tracking.md](backend/modules/operation-tracking.md) |
| Flutter 分层、路由和业务流程 | [app/README.md](app/README.md) 与 `../app/.rule` |
| Admin 工程与交互规则 | [admin/README.md](admin/README.md)、`../admin/.rules` 与 `../admin/DESIGN.md` |
| 兔只类型、业务状态和批次操作 | [features/rabbit-lifecycle/README.md](features/rabbit-lifecycle/README.md) |
| CI/CD、镜像、生产部署和回滚 | [operations/README.md](operations/README.md) |
| 需求、实施计划和专题验收 | [features/README.md](features/README.md) |

源码目录中的 `AGENTS.md`、`.rule`、`.rules` 和 `DESIGN.md` 约束实现方式。这里的文档解释系统和工作流程，不复制这些规则的全部内容。

## 维护规则

- 项目级事实写入 `project/`，子项目事实写入对应子项目目录。
- 同时影响多个子项目的业务契约放入 `features/`，并在专题入口标明文档用途和状态。
- 部署步骤、运行时配置和发布证据放入 `operations/`。
- 已废弃但仍需追溯的资料移入 `archive/`，不要继续作为当前实现依据。
- 本目录只保留结论和演进说明。阶段计划、排期、任务拆分、工时估算、单次验收清单和
  外部工单状态快照不入库；它们属于项目管理工具。计划完成后，把结论写回对应文档。
- 已执行的 Flyway 迁移不能为更新文档路径而修改校验和；这类历史引用在原路径保留最小兼容入口。
- 数据库结构以 Flyway 迁移为准，路由和接口以当前源码为准，状态类文档必须标注核对时间。
- 移动或新增文档后运行 `node scripts/ci/check-markdown-links.mjs`。

## 仓库入口

- [项目 README](../README.md)
- [贡献指南](../CONTRIBUTING.md)
- [后端源码说明](../backend/README.md)
- [Flutter 源码说明](../app/README.md)
- [Admin 源码说明](../admin/README.md)
