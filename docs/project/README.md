# 项目级文档

本目录描述 Rabbit 三个子项目共同遵守的系统边界和开发流程。子项目内部结构、接口和业务流程由各自的文档入口继续展开。

这里只写结论和演进说明。阶段计划、排期和单次验收清单属于项目管理工具，不入库。

## 文档

- [architecture.md](architecture.md)：系统组成、运行时数据流、身份、兔场隔离和子项目职责。
- [development.md](development.md)：本地环境、Compose、后端、Flutter 和 Admin 启动方式。
- [testing.md](testing.md)：按改动范围选择单测、E2E、浏览器和真机验证。
- [business-baseline.md](business-baseline.md)：旧版业务资料的使用边界，以及它与当前实现的关系。
- [evolution.md](evolution.md)：账号模型、认证、生产流程和操作追踪的关键转折，以及为何推翻前一版。
- [interaction-acceptance-spec.md](interaction-acceptance-spec.md)：新交互必须证明的七条性质和验收产物要求。

## 子项目入口

- [后端](../backend/README.md)
- [Flutter App](../app/README.md)
- [Admin](../admin/README.md)

跨端业务规格放在 [features/](../features/README.md)，部署和发布放在 [operations/](../operations/README.md)。
