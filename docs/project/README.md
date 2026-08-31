# 项目级文档

本目录描述 Rabbit 三个子项目共同遵守的系统边界和开发流程。子项目内部结构、接口和业务流程由各自的文档入口继续展开。

## 文档

- [architecture.md](architecture.md)：系统组成、运行时数据流、身份、兔场隔离和子项目职责。
- [development.md](development.md)：本地环境、Compose、后端、Flutter 和 Admin 启动方式。
- [testing.md](testing.md)：按改动范围选择单测、E2E、浏览器和真机验证。
- [business-baseline.md](business-baseline.md)：旧版业务资料的使用边界，以及它与当前实现的关系。
- [hongtu-bug-review.md](hongtu-bug-review.md)：飞书《鸿兔项目开发 需求收集与管理》的 BUG 状态、证据和验收闭环审阅稿。
- [hongtu-bug-execution-plan.md](hongtu-bug-execution-plan.md)：4 条未闭环 BUG 按 Base 原始详细描述逐点追踪的最小修改与验收计划。
- [hongtu-software-requirements-review.md](hongtu-software-requirements-review.md)：批次统计与页面按钮用途的软件需求设计审阅稿。
- [hongtu-software-requirements-execution-plan.md](hongtu-software-requirements-execution-plan.md)：生产统计 MVP 和页面按钮差距的后端、Admin、Flutter 修改计划。

## 子项目入口

- [后端](../backend/README.md)
- [Flutter App](../app/README.md)
- [Admin](../admin/README.md)

跨端业务规格放在 [features/](../features/README.md)，部署和发布放在 [operations/](../operations/README.md)。
