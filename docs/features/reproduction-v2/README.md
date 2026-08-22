# 母兔生产流程 V2

本专题记录 `doe-breeding-v2` 的状态机、数据结构、迁移和验收资料。

## 文档

- [design.md](design.md)：状态机、数据结构和交互契约。
- [implementation-plan.md](implementation-plan.md)：分阶段施工、切换和风险记录。
- [backfill-runbook.md](backfill-runbook.md)：V27 回填与停写窗口操作手册。
- [form-contract-check.md](form-contract-check.md)：人工表单字段和图片链路检查。
- [tracker-status.md](tracker-status.md)：飞书需求状态核对。该文件是时间敏感记录，使用前必须重新核对飞书。

当前行为以 `backend/rabbit-production/src/main/java/com/rabbit/app/modules/repro/`、相关 Flyway 迁移和回归测试为准。数据结构总览见 [后端数据结构资料](../../backend/data/README.md)。
