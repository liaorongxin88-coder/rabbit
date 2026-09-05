# 批次统计指标

- 状态：实施中，交付验收未完成
- 核对日期：2026-09-05
- 适用范围：Backend、Admin、Flutter App

本专题记录生产批次 28 项统计指标的最终口径、数据来源、交互和 Excel 导出约定。

需求来源是飞书多维表格「鸿兔项目开发 需求收集与管理」中的记录 `recvsV7QhkxxvD`，附件为《信达兔业繁殖计划2501228.xlsx》。主记录决定 MVP 的 28 项范围，附件用于核对算法、单位和验收数据。附件独有的产仔率、选留产仔率、精液贡献率、公斤造肉成本和商品料肉比不在本期范围内。

## 文档

- [design.md](design.md)：28 项口径、数据来源、状态、统计接口、写入快照、Excel 和兼容策略。
- [interaction.md](interaction.md)：Admin 与 Flutter 的页面落点、排列、权限、加载失败、录入和导出交互。

## 接口与权限

| 接口 | 权限 |
| --- | --- |
| `GET /api/batches/{batchId}/statistics` | `rabbit:batches:query` |
| `GET /api/reports/batches/{batchId}/statistics.xlsx` | `rabbit:reports:export` |
| `POST /api/batches/{batchId}/carcass-yields` | `rabbit:batches:edit` |
| `GET /api/batches/{batchId}/carcass-yields` | `rabbit:audit:list` |

四个接口都属于业务用户和单兔舍范围。后端必须校验 `X-House-Id`、批次归属和对应权限；前端隐藏无权限操作不能代替后端授权。统计接口返回 `{code, message, data}`；`data` 是 `BatchStatistics`，包含 `schemaVersion`、`batchId`、`houseName`、`batchCode`、`calculatedAt`、固定顺序的 28 项指标和一个兼容周期内保留的四个汇总字段。Excel 端点使用同一份统计快照中的兔舍名称、批次编号、取数时间和指标，不在报表模块重复查询或计算业务数据。

## 取数原则

- 每次查询同时校验 `batchId`、`X-House-Id`、批次归属和权限。
- 历史归属以业务记录或操作时保存的快照为准，不使用兔只当前位置或当前体重反推。
- 来源不完整时只影响依赖该来源的指标，不用零值或部分合计掩盖缺失。
- 后端负责公式、状态和格式化；Admin、Flutter 和 Excel 只消费同一份统计契约。
- “怀孕数量”固定使用 `PREGNANT_DOE_COUNT`，按确认怀孕周期中的 `mother_rabbit_id` 去重；受胎率和流产率仍按 `cycle_id` 去重。
- 页面保持配种、怀孕、产崽、选留、断奶、出栏、销售和料肉比八组，出肉率位于料肉比组末尾。

任务期内的完整决策、来源差异和验收数据保存在 `.trellis/tasks/09-04-feishu-batch-statistics/`。合并后以生产代码、自动化测试和本专题文档共同约束后续修改。
