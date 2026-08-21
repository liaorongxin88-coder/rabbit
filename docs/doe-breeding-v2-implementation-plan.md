# 母兔生产流程 V2 施工计划（审查修订版）

状态：施工计划 v1（经复审修订）
上游设计：[doe-breeding-v2-design.md](doe-breeding-v2-design.md)
现状参考：[current-schema-overview.md](current-schema-overview.md)

---

## 0. 本次审查发现并已处置的问题

对设计稿原 §7 简表复审后，以下问题已修正回设计文档，施工按修正后口径执行：

| # | 问题 | 处置 |
| --- | --- | --- |
| A1 | 批次标签化后 §2/§4.4 仍残留 `BATCH_CLOSE` 任务、`subject=BATCH`、计数器表述 | 已删除；空批次角标为查询派生 |
| A2 | `uk_bc_batch_mother` 普通唯一键会禁止关闭后重新打标 | 改为 `batch_member_guard` 生成列（仅 OPEN 周期占名额）；空怀/流产/分笼的自动接续不继承旧批次，显式重新打标仍可重开 |
| A3 | `stage NOT NULL` 直接 ADD COLUMN 对存量数据不可执行 | V26 先可空，V27 回填后收紧 |
| A4 | `tenant_id NOT NULL` 但当前无租户表 | 全部改为可空预留列，租户模型独立立项 |
| A5 | `operator_id NOT NULL` 无法承载历史回填（旧数据只有 create_by 字符串） | operator_id 可空 + operator_name 快照列 |
| A6 | 回填遗漏：`待催情/待配种/催情中` 的活跃成员在 breeding_cycles **无行**（现状配种时才建周期），不能只做 UPDATE 映射 | V27 增加"为无周期活跃成员 INSERT 周期"步骤（§3.3） |
| A7 | 回填遗漏：replacement_records 成熟提醒、商品兔 SALE_READY 未列入 work_tasks 生成 | 已列入 §3.3 |
| A8 | 配置表改名 production_cycle_settings 与"迁移只加列"矛盾 | 物理表沿用 global_setting，仅加 gestation_days，列改名推迟 V29+ |
| A9 | T7 自动开启与血配冲突、分笼-预产期联动校验、双子宫决策未落文档 | 已补 §3.2 T7 注、§3.3、§3.4、T11 离场转换 |
| A10 | 旧 App 无 OTA（recvrqlA3OU53F 未落地），一刀切换端点会打断存量 APK | 兼容策略定为"旧端点内部适配新服务，响应形状不变"（§4） |
| A11 | 批量操作逐项幂等键派生规则未写 | 已补 §5.1（requestId + '-' + taskId） |

---

## 1. 施工总览

```
P1 V26 增量DDL ──► P2 后端新写路径(与旧并存,灰度关) ──► P3 停写窗口+V27回填+对账 ──► 
P4 打开新路径+旧端点适配化+客户端改造 ──► 观察期(≥2周) ──► P5 V28 清理退役
```

原则：每阶段可独立回滚；P3 前生产行为零变化；P4 是唯一行为切换点。

## 2. P1 — V26 增量迁移（纯加法，随时可上）

工作项：

1. 建表：`repro_events`、`work_tasks`、`litters`、`biz_attachments`（DDL 见设计 §4.1/4.3/4.4/4.8，tenant_id 一律可空）。
2. `breeding_cycles` 加列：`stage`(NULL)、`stage_entered_at`(NULL)、`lifecycle` DEFAULT 'OPEN'、`result`、`mating_method`、`state_version`、两个生成列 `pipeline_guard`/`batch_member_guard`——**本期不加唯一键**（存量数据可能违反）。
3. `rabbits` 加列：`current_stage`、`current_cycle_id`、`stage_entered_at`、`last_mating_date`。
4. `global_setting` 加列：`gestation_days INT NOT NULL DEFAULT 30`。
5. `batches` 加列：`is_archived BOOLEAN NOT NULL DEFAULT FALSE`（status 等旧列本期不动）。

验证：`mvn --file backend/pom.xml test`（Flyway 空库/存量库双跑）；对拷贝的生产库快照演练。

## 3. P2/P3 — 新写路径与数据回填

### 3.1 P2 后端代码（新旧并存，Feature Flag 关闭）

新模块 `modules/repro/`：

```
ReproStage / ReproAction / CycleResult      枚举（唯一词汇源，含中文映射）
TransitionTable                             §3.2 数据驱动转换表（含 T7 跳过、T8 范围、T11）
ReproStateMachineService.apply()            七步事务（设计 §5.1）
ReproCycleController                        POST /repro/cycles（openCycleAt 任意阶段入轨，
                                            含待分笼入轨同事务建 litter）、/repro/cycles/{id}/actions
WorkTaskService + WorkTaskController        GET /tasks、POST /repro/tasks/bulk-actions
LitterService                               窝创建/哺乳计数/分笼（复用 weaning_record_allocations 落笼）
StageProjector                              rabbits.current_stage 单写者投影
SettingResolver                             gestation_days 接入 + Caffeine 缓存 + 语义常量映射旧列
```

交付线：转换矩阵单测全绿（合法/非法全覆盖）+ 并发守卫 IT + 幂等重放 IT（仿 `RabbitStagesAndCageConcurrencyIT` 模式）。

### 3.2 P3 停写窗口（低峰，预计 < 30 分钟）

开维护开关 → 执行 V27 → 对账 → 加唯一键 → 关维护开关。

### 3.3 V27 回填清单（顺序敏感）

| 步 | 内容 | 备注 |
| --- | --- | --- |
| 1 | 旧 `breeding_cycles.status` → `stage/lifecycle/result` 映射 | 待配种→AWAIT_MATING；已配种/不确定→AWAIT_PALPATION；怀孕确认→按 next_event_type 分流 AWAIT_PREPARTUM(备产)/AWAIT_DELIVERY(分娩)；哺乳中→AWAIT_WEANING；已断奶→CLOSED/WEANED；空怀→CLOSED/EMPTY；分娩失败→CLOSED/FAILED |
| 2 | **为无周期的活跃成员补建周期行**（A6）：`batch_rabbits.is_active=1` 且 current_status ∈ {待催情,催情中,待配种} 且无 OPEN 周期 → 调用与 API 同一 `openCycleAt` 机制 INSERT（stage=AWAIT_ESTRUS/AWAIT_MATING, stage_entered_at=last_event_date, batch_id, request_id='v27-br-'+id） | 催情中→AWAIT_MATING（催情已执行）；与存量录入/后备转种母公用代码路径 |
| 3 | 哺乳中周期 → INSERT litters（birth_date/total/live/current_nursing 取周期列；nursing_cage 取母兔笼） | uk_lt_cycle 幂等 |
| 4 | 4 张记录表 → INSERT repro_events（occurred_at=记录时间，operator_name=create_by，request_id='v27-'+表名缩写+id） | **已确认：只迁近 6 个月**；更早历史留在只读旧表供查 |
| 5 | `stage_entered_at` 兜底 = 最近相关记录时间，否则 update_time | 收紧 NOT NULL 前置 |
| 6 | 生成 work_tasks：每 OPEN 周期按 stage 生成对应 PENDING 任务（due 取旧 next_event_date，缺失则按配置重算）；NURSING litter 生成 WEANING 任务；活跃后备兔按 expected_mature_date 生成 REPLACEMENT_MATURE；活跃商品兔按出生/入栏+sale_days 生成 SALE_READY（已到期的 due=today） | dedup_key 幂等可重跑 |
| 7 | rabbits 投影回填：current_stage/current_cycle_id（有管线周期取之；仅 NURSING 取 AWAIT_WEANING；否则 READY；非种母兔按类型规则清理 growth/reproductive 字段冲突，A 规则见设计 §4.6） | |
| 8 | 对账脚本：违反 `uk_bc_pipeline` 的存量清单（应为空，V21 曾保证单 open 妊娠）；周期↔任务 1:1 抽检；投影 vs 重放抽检 | 已落地为 [doe-breeding-v2-backfill-runbook.md](doe-breeding-v2-backfill-runbook.md)，停写前先跑 |
| 9 | `ALTER ... ADD UNIQUE KEY uk_bc_pipeline`；`stage/stage_entered_at` 收紧 NOT NULL | 终检 |

回滚：P3 内任一步失败 → 关维护开关前直接放弃（新列/新表未被旧代码读写，行为无变化），修复后重跑。

### 3.4 施工时对本计划的三处修正

**一、`uk_bc_batch_member` 改为不建。**
它的生成列覆盖所有 OPEN 周期（含哺乳中），而血配的定义就是母兔一边哺乳一边
怀下一胎；建了这个键，同一批次内的血配会被直接挡死，恰好抵消 `pipeline_guard`
排除 `AWAIT_WEANING` 所要达成的效果。退一步说，即使给它同样排掉哺乳阶段，它也
冗余于 `pipeline_guard`（后者是全厅唯一，范围更严）。生成列保留作排查手柄。
该矛盾由 `V27BackfillIT.pipelineCycleWinsOverNursingInTheRabbitProjection` 实测暴露。

**二、`stage` / `stage_entered_at` 的 NOT NULL 收紧从 V27 推迟到 V28。**
步骤 9 把收紧放在 V27，但 P3 结束到 P4 之间旧写路径仍在线，而批次模块的
`BreedingCycleMapper.xml` 插入周期时不写 `stage`——一旦收紧，线上每一次配种都会报
`Field 'stage' doesn't have a default value`。按计划写完后全量 e2e 直接红了 16 个
用例，全部是旧配种/分娩/分笼链路。用 DEFAULT 绕过同样不行：那等于替旧代码猜
阶段，猜错就是静默的状态损坏。正确顺序是等 P4 把旧端点变成适配器后再由 V28 收紧。

连带影响：V27 到 P4 之间新增的旧周期 `stage` 为 NULL，`pipeline_guard` 也为 NULL，
因此不受 `uk_bc_pipeline` 保护（这正是它对旧路径行为中性的原因）。P4 切换前需
重跑一次回填补定级，详见 runbook §5。建议 P3 与 P4 连着发，缩短窗口。

**三、回填用集合式 SQL，而非逐只调用 `openCycleAt`。**
“与 API 共用代码路径”这条约束防的是两套活代码各自演进；Flyway 迁移发布即冻结、
只跑一次，不存在漂移。反而逐只走状态机（加锁→写事件→upsert 任务→投影）在万只
规模下撞不进 30 分钟停写窗口。防漂移改由测试承担：`V27BackfillIT` 断言回填结果
与状态机口径一致（尤其是投影优先级与任务归属）。

## 4. P4 — 切换（唯一行为变更点）

> ## ⚠️ 策略变更（2026-08-16，业务裁定）：放弃兼容，直接删旧端点
>
> 下文 §4.1 的适配矩阵**已作废**，保留仅供追溯。
>
> **作废原因**：原策略建立在 A10「旧 App 无 OTA」上。实际核实（代码层面）：
> App 无版本检查、无升级提示（0 处命中），后端无版本协商（0 处命中）——
> 前提成立。但业务侧确认**当前仍在试点，无真实存量用户**，因此兼容层是
> 在为不存在的用户付费。
>
> **新策略**：旧 HTTP 端点、兼容镜像列、compat 包、V28 清理迁移全部不再需要。
>
> **代价（必须写明）**：适配器的作用是允许客户端滞后上线。删掉后，
> **后端 + admin + App 必须同步发布**，不再存在灰度窗口。
>
> ### ⚠️ 关键修正：这不是纯删除
>
> 实测旧方法的 mapper 调用后确认：新状态机只碰周期/事件/窝/任务/投影，
> 旧方法里以下能力**新路径完全没有**，必须迁移而不能删：
>
> | 能力 | 旧实现位置 |
> | --- | --- |
> | 笼位容量原子递增（并发不变式） | `cageMapper.incrementCommodityRabbitCountWithinCapacity` |
> | 分笼去向记录、自动选笼、性别分配 | `weaningRecordAllocationMapper` / `pickCommodityCageAllocations` / `pickKidGender` |
> | 死胎与母兔离场、异常记录 | `rabbitDepartureRecordMapper` / `rabbitAbnormalConditionMapper` |
> | 绩效统计 | `breedingPerformanceMapper.addParturition/addWeaning` |
>
> 因此约 1240 行旧写逻辑里，约一半是可删的重复状态逻辑，
> 另一半是必须迁移的真实领域逻辑。`WeaningCageConsistencyIT` 的三条并发
> 测试守的就是笼位容量不变式，不能随端点一起删掉。
>
> ### 修订后的 P4 顺序
>
> 1. **补齐新路径缺失能力**（分笼选笼/容量/去向、接产离场/异常、绩效）
>    —— 副作用不得进状态机，否则它会变成上帝对象；放在编排层。
> 2. 删旧 HTTP 端点与重复状态逻辑，旧 e2e 测试改写到新 API（不是删掉）
> 3. 拆除 compat 包与镜像列双写
> 4. V28：删镜像列、收紧 stage/stage_entered_at NOT NULL
> 5. admin + App 同步改造并发布
>
> **完成情况：五步均已落地。** 验收：后端 113 单测 + 121 e2e、admin lint/build、
> Flutter analyze + 188 测试、以及 A059 真机全场景生命周期端到端（含 18 项数据库断言
> 与 18 张截图）全部通过。
>
> ### 施工中发现并修复的四个真实缺陷
>
> 这四个都是「不报错的静默错误」，共同根因是旧写路径删除后，仍有代码在读
> 那条路径才会维护的列。它们都已补上回归测试：
>
> | 缺陷 | 影响 | 修法 |
> | --- | --- | --- |
> | 建批次不开生产周期 | 母兔无阶段、无待办，生产流程从界面无法开始 | `BatchService.createBatch` 同事务入轨 |
> | 兔子离场不结周期 | 周期在新视角永远 OPEN，占着 uk_bc_pipeline、待办永久 PENDING | 离场先走 `retireMother` |
> | 种公兔未落库 | 每只仔兔 `father_id` 为空，谱系永久丢失 | `applyTransition` 补写 `male_rabbit_id` |
> | 仪表盘指标失真 | 「已配种」把刚入轨的母兔也算进去；「在哺仔兔」恒为 0 | 改读 `mating_date` 与 `litters` |

### 并行周期专项验收发现的三个缺陷

血配把「一兔一周期」变成「一兔两周期」，而母兔身上只有一个 `current_stage`
投影列——冲突就出在这里。`ReproParallelCycleIT`（8 个用例）专盯「操作 A 周期
会不会伤到 B 周期」，跑出了三个真缺陷：

| 缺陷 | 影响 | 修法 |
| --- | --- | --- |
| 单条周期离场留下孤儿周期 | T11 按兔取消全部待办却只关一条周期，另一条 OPEN 着且无待办：崽子等不到分笼，批次被永久卡住 | 编排层 `closeRemainingCyclesAfterRetire` 结清剩余周期 |
| 散养母兔无法分笼 | `weaning_records.batch_id` 仍是 NOT NULL，散养母兔分笼整个事务回滚，卡在待分笼出不来 | V29 放开为可空 |
| 流产死胎数静默丢弃 | 设计 §5.2 明列的 `stillbirthCount` 在 DTO 与 Command 里都不存在 | 补字段并写入事件载荷 |

前两个同样是「不报错的静默错误」，但根因与上表不同：不是读了旧列，
而是**新模型允许的形状超出了旧约束的假设**（一兔只有一条周期、每条周期必属一个批次）。
放宽一处约束时，要把依赖这条约束的地方一并走一遍。

### 流产入口：按阶段显隐

流产是**非计划事件**——不对应任何待办，因此不能从今日清单进入，只能对着一头具体
母兔记录。它与「母兔离场」同类，所以单列入口，而不是塞进推进流程的表单。

**显隐规则的唯一来源是服务端。** 新增 `GET /api/repro/stage-actions` 下发阶段→可执行
动作字典（含中文名），由 `TransitionTable.actionsFrom` 推导：

| 阶段 | 可执行动作 |
| --- | --- |
| 待催情 / 待配种 | 对应推进动作、推迟、离场 |
| **待摸胎 / 待备产 / 待分娩** | ……加 **流产** |
| 待分笼 | 分笼、推迟、离场（已经生完了，无流产） |

两端都按字典显隐，不在客户端写死阶段名：旧实现里 App 与后端各存一张映射表并
最终漂移，代价是用户看到一个点下去必定 409 的按钮。字典拉不到时宁可不显示——
少给一个入口只是不便，给一个必定失败的按钮是欺骗。

四层各守一边：`TransitionTableTest.dictionaryMatchesWhatRequireActuallyAccepts`
钉住字典与转换表不得分家；`ReproParallelCycleIT` 验证字典端点本身与「绕过界面
直接调接口仍被 409 拒绝」；`test/ui/batches/screens/detail_abortion_test.dart` 钉住界面显隐；真机用例
`integration_test/batches/lifecycle_android_test.dart` 在同一屏上同时断言「孕期母兔有入口」与「非孕期母兔没有入口」，
并以 `aborted_cycles` 计数器确认流产真的落库（run `20260817181356093836`）。

### 4.1 旧端点适配矩阵（已作废，仅供追溯）

| 旧端点（App+admin 在用） | 适配方式 |
| --- | --- |
| POST `/batches/{id}/aphrodisiac/start | finish` | 适配器：解析 batchId+rabbitIds → OPEN 周期 → apply(ESTRUS…)；响应形状不变 |
| POST `/batches/{id}/mating` `/mating/bulk` | → apply(MATING)，bulk 走分块；无周期时先隐式 START_CYCLE（兼容老语义"配种即建周期"） |
| POST `/batches/{id}/pregnancy-check` | → apply(PALPATION, result 映射) |
| POST `/batches/{id}/prepartum/finish` | → apply(PREPARTUM) |
| POST `/batches/{id}/parturition` | → apply(DELIVERY / DELIVERY_FAILED) |
| POST `/batches/{id}/weaning` | → apply(WEANING)（allocations 逻辑不变） |
| POST `/batches/{id}/complete` | → 归档校验语义：无 OPEN 周期→is_archived=true；有→保留旧 force 行为逐周期 result=REMOVED（老 App 兼容） |
| GET `/batches` `/batches/{id}` `/batch-rabbits` `/breeding-cycles` | 读适配：由周期表派生拼旧响应（current_status 用新 stage 反映射中文） |
| GET `/events`、POST `/events/ack` | 读适配 work_tasks 拼旧 EventItem；ack→任务 snooze/完成映射；新客户端直接用 `/tasks` |
| 10 个无人调用的查询端点 | 直接标记 Deprecated，不适配（`/prepartum-records` 等） |

适配器落位：BatchService 六大方法体替换为对新服务的调用（保留方法签名与 DTO），`syncBreedingSummary`、`EventReminderScanJob`、`markProcessing` 三段式幂等在旧路径中随之废弃。

### 4.2 客户端改造项

- **App（Flutter）**：`batch_repository.dart` 增补新端点；六大操作表单对齐 §5.2 契约（含"未执行→选日期"三态、流产入口、批量催情）；首页待办改 `/tasks`（兔舍/笼位/批次筛选，修 recvsrmZKv1cqp）；录入表单支持阶段+进入日期；批次页改标签模型：展示周期列表+可归档角标，并作为**批量操作的圈选入口**（bulk filter：batchId+taskType，对应口径“批次=批量选择集”）。视图模型 ~4700 行为主要工作量。
- **admin（React）**：`workspace.ts` 同步端点；批次/周期/任务列表页；`admin/DESIGN.md` 走查。
- 双端共用枚举中文映射由后端常量接口下发（杜绝词汇漂移）。

### 4.3 灰度顺序

1. 后端上线（旧端点=适配器，新端点开放）→ 老 APK 行为回归验证
2. admin 切新端点（内部用户先验）
3. 新版 App 发布（走现有安装包渠道；OTA 上线后收敛版本）

## 5. P5 — V28 清理（观察期 ≥2 周后）

- `batch_rabbits` 数据核对后转只读归档表并从代码移除；`batches` 删 status/start_date/end_date。
- `breeding_cycles` 删旧列（status、next_event_*、is_event_notified、nursing 计数、overlap_* 改由报表派生）。
- 退役：`EventReminderScanJob`、`event_acks`、`event_reminder_logs`、`rabbits.reproductive_stage`（保留 growth_stage 给商品/后备）。
- 4 张记录表转只读；`BreedingPerformanceRecalcService` 改读 repro_events/litters。

## 6. 测试与验收

| 层 | 内容 |
| --- | --- |
| 单测 | 转换矩阵（7 阶段 × 11 动作全组合）；到期日计算（含摸胎后待备产时长、备产当天进入待分娩、血配联动校验）；**任意阶段入轨**：6 个入轨点 × 补录事实缺失拒绝 × due 折减/拉平当天 × 待分笼入轨建 litter |
| IT (`*IT.java`) | 并发管线守卫（双开周期/同批次重开）；幂等重放（单只+bulk 派生键）；V27 回填对账（快照库全流程）；旧端点适配回归（六操作旧请求/响应逐字段） |
| 客户端 | `./rabbit check`；六表单三态交互；360px/200% 字号；`pnpm --dir admin lint && build` |
| 业务验收 | 飞书 P0 逐条：recvsrp9E2dqvB（阶段一致性）、recvsrpMlvu2SC（词汇）、recvsrq7rGZHdi（SALE_READY 出现在待办）、recvsrpXPZd3Xg（摸胎后按待备产时长、备产当天进入待分娩）、recvqh3EJXzmO1（标签批次）、recvsrrPUz0djZ/recvss4qXnDEIX（表单）、recvsrrTP2Rp0l（流产+照片） |

## 7. 风险登记

| 风险 | 等级 | 缓解 |
| --- | --- | --- |
| 存量数据违反新唯一键（脏数据双 open 周期） | 中 | §3.3-8 对账前置 + 人工裁决清单；键收紧放最后一步 |
| 老 APK 长期存活（无 OTA） | 高 | 旧端点适配器永不改响应形状；适配层保留至 OTA 覆盖率达标 |
| 回填量 | 低（已降级） | 已确认只迁近 6 个月，分 house 分批 |
| App 视图模型改造量（4700 行） | 中 | 旧端点兼容使 App 可分页面渐进切换，不必一次重写 |
| 双写窗口不一致（P4 期间旧读端点派生逻辑 bug） | 中 | 读适配单测逐字段比对旧响应快照 |
| 性能回归（任务表热点） | 低 | idx_wt_due 压测；批量分块 500 沿用 |

## 8. 工期粗估（单人全栈口径）

P1 0.5d ｜ P2 4–5d（含测试）｜ P3 1d（含演练）｜ P4 后端 2d + App 4–6d + admin 2d ｜ P5 1d
合计 ~15–18 人日，P2/P4-App 为关键路径。

## 9. 业务口径（已全部确认，2026-08-16）

1. 同批次同母兔：**同时唯一**——`batch_member_guard` 仅约束 OPEN 周期；历史标签保留，空怀/流产/分笼后的自动接续周期默认无批次，用户显式重新打标时仍可重开。
2. 散养母兔**开放**（batch_id 可空）；批次不与母兔管理直接挂钩，定位为**批量选择集 + 流转追踪标记**（bulk filter 已入设计 §5.1）。
3. repro_events 历史回填：**近 6 个月**，更早历史留只读旧表。
4. 血配节奏（remating_mode）：**本期不实装**，保持默认断奶后配；设计预留不变。
5. 总口径重申：批次只做标记追踪，状态追踪主体始终是母兔（周期/任务）与商品兔（生长/出栏）本身；标签链路：周期 → 窝 → birth_batch_id → 出栏快照（设计 §4.5）。

## 10. V34 补齐项（2026-08-21）

- `work_tasks` 正式支持 `subject_type=RABBIT`：分笼或录入商品兔生成 `SALE_READY`，转后备或直接录入后备兔生成 `REPLACEMENT_MATURE`。
- 商品兔转后备时完成旧出售任务；出售、出库、后备转种时完成对应兔只任务。
- `replacement_records` 增加 `PENDING/PROMOTED` 状态和转种时间；转种母兔在原笼进入无批次待催情周期，种公兔进入 `READY`。
- 商品兔生长参数拆为适应期、生长期、育肥期，成熟日取三段之和；夜间任务按兔舍设置推进 `JUVENILE → GROWING → FATTENING → MATURE`。
- 迁移从存量在栏商品兔和后备兔幂等回填任务，首页兼容接口不再遗漏无 `cycle_id` 的兔只任务。
