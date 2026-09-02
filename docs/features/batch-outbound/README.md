# 批量出库

本专题描述批量出库的产品口径、服务端事务、客户端选择流程和验收方案。

## 文档

- [product-requirements.md](product-requirements.md)：产品需求、状态、交互、数据和验收口径。
- [implementation.md](implementation.md)：当前实现约束、事务边界、接口和迁移注意事项。
- [test-plan.md](test-plan.md)：人工、Flutter、后端 E2E 和数据库对账方案。

修改资格判断、选择范围、草稿、幂等、快照或提交事务时，需要同时核对后端 `outbound` 模块、Flutter `outbound` 领域与 UI，以及本专题的测试矩阵。
