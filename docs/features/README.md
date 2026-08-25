# 跨端业务专题

本目录保存同时影响后端、Flutter App 或 Admin 的业务规格。专题文档负责记录需求、契约、实施计划和验收，不替代当前源码、Flyway 迁移或实时项目管理状态。

## 专题

- [兔只业务状态与批次操作](rabbit-lifecycle/README.md)
- [兔舍统一入笼与生产分笼](cage-rabbit-intake/README.md)
- [批量出库](batch-outbound/README.md)
- [母兔生产流程 V2](reproduction-v2/README.md)

新专题应建立独立目录和 `README.md`，至少说明业务词汇、状态维度、允许操作、异常边界、当前实现依据和核对日期。若实现中仍有不同口径，应单列待确认项，不要在文档中替业务方选定规则。
