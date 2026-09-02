# 批次统计指标

- 状态：方案设计中，口径待业务确认
- 核对日期：2026-09-02
- 适用范围：Backend、Flutter App、Admin

本专题记录生产批次可查看的统计指标、每个指标的计算口径和取数来源。

## 文档

- [design.md](design.md)：指标清单与版面、取数映射、三处数据缺口、与现场表格的口径差异、接口设计和待确认项。
- [interaction.md](interaction.md)：面板落点、八组版面、数值的四种状态、共享指标构件和口径说明入口。

需求来源是飞书「鸿兔项目开发 需求收集与管理」表中的 `recvsV7QhkxxvD`（批次可以查看的统计数据及相关计算方式），
附件为现场在用的《信达兔业繁殖计划》表格。

当前实现以 `GET /api/batches/{batchId}/statistics`、
`backend/rabbit-production/src/main/resources/mapper/modules/batch/BatchStatisticsMapper.xml`
和相关 Flyway 迁移为准。
