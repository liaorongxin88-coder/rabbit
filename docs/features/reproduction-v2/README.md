# 母兔生产流程 V2

本专题记录 `doe-breeding-v2` 的状态机、数据结构和交互契约。

## 文档

- [design.md](design.md)：阶段词汇表、转换表、数据结构、表单契约和图片链路。

V26 到 V29 的迁移路径、切换后暴露的缺陷，以及批次归属约束被推翻又重建的过程，
见 [系统演进说明](../../project/evolution.md)。

当前行为以 `backend/rabbit-production/src/main/java/com/rabbit/app/modules/repro/`、相关 Flyway 迁移和回归测试为准。数据结构总览见 [后端数据结构资料](../../backend/data/README.md)。
