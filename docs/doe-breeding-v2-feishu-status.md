# doe-breeding-v2 与飞书需求表的状态核对

对照《鸿兔项目开发 需求收集与管理》中设计文档 §1.2 登记的单号，逐条核对**代码里真实存在的证据**。
口径是「拿得出证据才算完成」：每条都必须指到具体的测试、迁移或端点，指不到的一律不改状态。

核对时间 2026-08-17，对应真机验收 run `20260817181356093836`。

## 一、可判定「已完成」

| 单号 | 结论 | 证据 |
| --- | --- | --- |
| recvsrp9E2dqvB 种母兔繁育阶段和批次中不对应 | 已完成 | 状态三写收敛为单一写路径 `ReproStateMachineService.apply()`；母兔阶段改为 `rabbits.current_stage` 投影列，与周期同事务写入。`ReproParallelCycleIT`（并行周期互不干扰）、`ReproStateMachineIT`，以及真机用例每步操作后的**批次全员**状态校验 `_assertBatchState` |
| recvsrq7rGZHdi 兔子周期/批次周期/提醒绑定异常 | 已完成 | 三处 `next_event_*` 与后备成熟、出售提醒统一收敛到 `work_tasks`；V27 step 6 回填，V28 删除镜像列。`ReproApiIT.everyAdvanceRotatesTheTaskCentre` 钉住「每条开放周期恰好一条 PENDING 待办」 |
| recvsrpXPZd3Xg 备产提前天数逻辑不对 | 已完成 | 预产期改为 `mating_date + gestation_days`（可配，默认 30），`prepartum_lead_days` 只表示提前量。`DueDateCalculatorTest` 故意用 31 天，硬编码 30 会立刻失败 |
| recvsrrPUz0djZ / recvss4qXnDEIX 六大人工操作表单 | 已完成 | 六个动作统一走 `POST /api/repro/cycles/{id}/actions`，合法性由转换表判定；操作留痕统一进 `repro_events`（含 `operator_name` 快照）。「未执行→改下次提醒」升为一等动作 POSTPONE。真机 19 张截图覆盖全部表单 |
| recvsrrTP2Rp0l 流产操作 | 已完成 | 转换表 T8 + `GET /api/repro/stage-actions` 阶段字典 + 两端入口；真机 `aborted_cycles=1`，事件载荷含死胎数，周期链 `EMPTY → ABORTED → 自动接续` |
| recvsrmZKv1cqp 首页提醒兔舍选择 | 已完成 | 首页、笼位 NFC、兔卡共用 `GET /api/tasks` 单端点，支持 `houseId/cageId/batchId/type/dueBefore` 过滤，口径不可能再互相打架 |

## 二、**不能**判定完成（证据不足或明确未做）

| 单号 | 缺口 | 说明 |
| --- | --- | --- |
| recvsroMN5SslS / recvsrtXYAKnX1 种兔/后备兔阶段字段错误 | **已补齐** | 更正：校验一直存在于 `RabbitService.normalizeAndValidateStages`（创建与更新均调用），拦住商品兔录繁殖阶段、后备兔非 RESERVE、种公兔非 READY/RESTING。本轮补上设计里唯一缺的一条：**种母兔不得手工录入繁殖阶段**，改为录入时选生产阶段并自动入轨 |
| recvqh3EJXzmO1 批次新口径 | **只做了一半** | batches 已变纯标签（无 status/start/end 语义依赖）、成员关系改由 `breeding_cycles.batch_id` 派生。但建批次仍强制传母兔列表（`BatchService.java:231` 抛「母兔列表不能为空」），且「成员全退自动完成批次」仍在（`checkAndCompleteBatch`）——这两点正是原单里点名要改的 |
| recvsrnEJ8bKrk / recvqh6N0wWVjR 录入时指定阶段+进入日期 | **后端已接入录入表单，客户端待补** | 本轮把入轨接进了兔只录入：`POST /api/rabbits` 新增 `reproStage` / `stageEnteredAt` / `matingDate` / `birthDate` / `liveKits`，与建兔同事务开周期并生成首个待办。仍缺 Flutter 与 admin 的录入界面 |
| recvsrpMlvu2SC 母兔状态可选项缺少 | **后端已收口，客户端待补** | 种母兔录入不再接受旧的 `reproductive_stage`（怀孕/空怀/哺乳……），改用统一的 `ReproStage` 九值，中文名由 `GET /api/repro/stage-actions` 下发。录入表单的阶段下拉需改用该字典 |
| recvsrEA6TRuK6 笼内兔子列表管理 | **待确认** | `rabbits` 投影列已建（含 `idx_rabbits_house_current_stage`），但笼位/兔只列表的 mapper 里没查到读取 `current_stage`，即能力具备而调用方未接 |

## 三、不属于本次范围

recvrpTL16SBwu 死亡记录、recvrqnhXOyd5N 后备转种兔 —— 与母兔生产流程无关，本轮未触及，不应改状态。

## 四、写回飞书的前置条件

当前 lark-cli 身份（易新颢）已有 `base:record:update`，**改记录不缺权限**；缺的是定位这张表的
`search:docs:read`，申请后返回「所申请权限正在审批中」，属企业管理员审批，客户端点不动。

绕开方式：直接提供该多维表格的 URL（形如 `https://<租户>.feishu.cn/base/<app_token>?table=<table_id>`），
即可用现有权限读表结构并按上表写状态，无需等审批。
