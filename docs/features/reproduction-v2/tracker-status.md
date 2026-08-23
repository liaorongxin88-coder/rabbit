# doe-breeding-v2 与飞书需求表的状态核对

对照《鸿兔项目开发 需求收集与管理》中设计文档 §1.2 登记的单号，逐条核对**代码里真实存在的证据**。
口径是「拿得出证据才算完成」：每条都必须指到具体的测试、迁移或端点，指不到的一律不改状态。

核对时间 2026-08-17，对应真机验收 run `20260817181356093836`。

## 一、可判定「已完成」

| 单号 | 结论 | 证据 |
| --- | --- | --- |
| recvsrp9E2dqvB 种母兔繁育阶段和批次中不对应 | 已完成 | 状态三写收敛为单一写路径 `ReproStateMachineService.apply()`；母兔阶段改为 `rabbits.current_stage` 投影列，与周期同事务写入。`ReproParallelCycleIT`（并行周期互不干扰）、`ReproStateMachineIT`，以及真机用例每步操作后的**批次全员**状态校验 `_assertBatchState` |
| recvsrq7rGZHdi 兔子周期/批次周期/提醒绑定异常 | 已完成 | 三处 `next_event_*` 与后备成熟、出售提醒统一收敛到 `work_tasks`；V27 step 6 回填，V28 删除镜像列。`ReproApiIT.everyAdvanceRotatesTheTaskCentre` 钉住「每条开放周期恰好一条 PENDING 待办」 |
| recvsrpXPZd3Xg 备产提醒口径调整 | 已完成 | 按现场流程改为摸胎确认日 + `prepartum_days` 进入待备产，备产完成当天进入待分娩；`DueDateCalculatorTest` 与 `TransitionTableTest` 覆盖该锚点，预产期固定为配种日后 30 天且无配置项 |
| recvsrrPUz0djZ / recvss4qXnDEIX 六大人工操作表单 | 已完成 | 六个动作统一走 `POST /api/repro/cycles/{id}/actions`，合法性由转换表判定；操作留痕统一进 `repro_events`（含 `operator_name` 快照）。「未执行→改下次提醒」升为一等动作 POSTPONE。真机 19 张截图覆盖全部表单 |
| recvsrrTP2Rp0l 流产操作 | 已完成 | 转换表 T8 + `GET /api/repro/stage-actions` 阶段字典 + 两端入口；真机 `aborted_cycles=1`，事件载荷含死胎数，周期链 `EMPTY → ABORTED → 自动接续` |
| recvsrmZKv1cqp 首页提醒兔舍选择 | 已完成 | 首页、笼位 NFC、兔卡共用 `GET /api/tasks` 单端点，支持 `houseId/cageId/batchId/type/dueBefore` 过滤，口径不可能再互相打架 |

## 一之二、后一轮（同日）补齐的单号

上一轮列在「客户端待补」「待确认」「不属于本次范围」里的五条，本轮已交付并完成验收。

**验收后的最终状态（2026-08-18）**：下表五条中四条已置「已完成」，
`recvqh5TC8wd3y` 换笼位仍为「验收中」——对调/并笼两端已自动化跑通，
但 **App 端 NFC 碰标签选目标笼需实体标签人工验收**，该缺口不能用代码证明。

| 验收方式 | 证据 |
| --- | --- |
| 真机（OPPO A059） | `app/scripts/android_cage_ops_e2e.sh`，run `20260817233240414116`，13/13 截图 + 14 项数据库断言 |
| 浏览器（本机 Chrome） | `pnpm --dir admin e2e:browser`，run `20260818001511706795`，16 张截图 + console/page error 0 + 14 项数据库断言 |

两轮验收各抳出一个自动化单测看不见的缺陷：真机上录入完笼内列表不刷新
（类型页 pop 后用 post-frame 回调另开表单且不 await，调用方的刷新在创建前就跑完了）；
浏览器上 390px 两张表格把列挤成一列一个字（不触发横向溢出，所以旧检查放过）。
两处均已修复并补上防回归断言。

| 单号 | 结论 | 证据 |
| --- | --- | --- |
| recvrpTL16SBwu 死亡记录 | 已完成 | 根因不在后端：`POST /api/rabbits/events` 一直只要 `rabbitId`，是两端表单自己把 `batchId` 写成必填，于是入口只挂在批次详情的「母兔离场」上，笼内商品兔无处可登记。现两端改为笼位详情逐只可登记；Flutter 191 全绿（含离场弹窗用例） |
| recvqh5TC8wd3y 换笼位 | 验收中（NFC 待人工） | 新增 `POST /api/rabbits/{id}/cage-transfer`，三种模式 MOVE / APPEND / SWAP，目标笼用途从**在栏兔实际类型**推导而非 `cages.status` 冷数据。`RabbitCageTransferIT` 6 条覆盖入笼/并笼/拒绝并笼/对调/拒绝对调/幂等重放。SWAP 绕开 `uk_rabbits_house_active_breeding_cage`（生成列唯一键，直接 CASE 互换必报 1062）：同事务内先把一只 `is_active=0` 让生成列归 NULL，移另一只，再落位复活，四条语句 |
| recvsrEA6TRuK6 笼内兔只管理 | 已完成 | `RabbitMapper.xml` 提取 `RabbitColumns` 片段，把 `current_stage` / `current_cycle_id` / `stage_entered_at` / `last_mating_date` 补进 resultMap 与全部 select——上一轮说的「能力具备、调用方未接」正是这里。两端笼内列表显示生产阶段并支持逐只编辑/换笼/离场 |
| recvsrnEJ8bKrk 录入时指定阶段+进入日期 | 已完成 | 新增 `GET /api/repro/entry-points` 下发「可入轨阶段 + 该阶段必须补录的事实」，客户端不抄第二份规则表（抄了就会漂移成「填完才 400」）。`ReproParallelCycleIT.entryPointDictionaryTellsClientsWhichFactsAreRequired` 钉住六个入轨点与各自必填项 |
| recvsrpMlvu2SC 母兔状态可选项缺少 | 已完成 | 两端「新增兔子」不再给种母兔渲染旧的怀孕/空怀/哺乳下拉（后端本就拒收，填了必定 400），改读上面的入轨字典 |

本轮顺带修掉一处未被任何单号记录的客户端故障：旧的换笼借 `PUT /api/rabbits/{id}` 把整行资料重发，
其中包含后端已拒收的种母兔 `reproductiveStage`——**种母兔换笼在客户端其实是坏的**，新端点一并解决。

验证：后端单测 116 全绿、e2e 141 全绿（新增 7 条）、admin lint+build 通过、Flutter analyze 干净 + 191 全绿。

## 二、**不能**判定完成（证据不足或明确未做）

| 单号 | 缺口 | 说明 |
| --- | --- | --- |
| recvsroMN5SslS / recvsrtXYAKnX1 种兔/后备兔阶段字段错误 | **已补齐** | 更正：校验一直存在于 `RabbitService.normalizeAndValidateStages`（创建与更新均调用），拦住商品兔录繁殖阶段、后备兔非 RESERVE、种公兔非 READY/RESTING。本轮补上设计里唯一缺的一条：**种母兔不得手工录入繁殖阶段**，改为录入时选生产阶段并自动入轨 |
| recvqh3EJXzmO1 批次新口径 | **只做了一半** | batches 已变纯标签（无 status/start/end 语义依赖）、成员关系改由 `breeding_cycles.batch_id` 派生。但建批次仍强制传母兔列表（`BatchService.java:231` 抛「母兔列表不能为空」），且「成员全退自动完成批次」仍在（`checkAndCompleteBatch`）——这两点正是原单里点名要改的 |
| recvsrnEJ8bKrk / recvqh6N0wWVjR 录入时指定阶段+进入日期 | ~~客户端待补~~ → 见上节 | 后端：`POST /api/rabbits` 新增 `reproStage` / `stageEnteredAt` / `matingDate` / `birthDate` / `liveKits`，与建兔同事务开周期并生成首个待办。客户端已于后一轮补齐 |
| recvsrpMlvu2SC 母兔状态可选项缺少 | ~~客户端待补~~ → 见上节 | 种母兔录入不再接受旧的 `reproductive_stage`（怀孕/空怀/哺乳……），中文名由服务端字典下发。客户端已于后一轮补齐 |
| recvsrEA6TRuK6 笼内兔子列表管理 | ~~待确认~~ → 见上节 | 当时的怀疑已坐实：投影列已建但 mapper 确实没读，后一轮已补进 resultMap 与全部 select |

## 三、不属于本次范围

recvrqnhXOyd5N 后备转种兔 —— 与母兔生产流程无关，本轮未触及，不应改状态。

recvrpTL16SBwu 死亡记录 —— 当时判定与生产流程无关而排除，后一轮已作为独立 P0 交付，见一之二。

## 四、写回飞书的前置条件

当前 lark-cli 身份（易新颢）已有 `base:record:update`，**改记录不缺权限**；缺的是定位这张表的
`search:docs:read`，申请后返回「所申请权限正在审批中」，属企业管理员审批，客户端点不动。

绕开方式：直接提供该多维表格的 URL（形如 `https://<租户>.feishu.cn/base/<app_token>?table=<table_id>`），
即可用现有权限读表结构并按上表写状态，无需等审批。
