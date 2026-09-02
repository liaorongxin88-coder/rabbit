# 母兔生产流程 V2 设计：状态机、数据结构与交互逻辑优化

状态：方案设计稿（待评审）
范围：backend 数据结构与交互逻辑；admin/app 仅涉及接口契约
输入：现有实现（V1–V25 迁移 + BatchService 状态机）、母兔生产流程状态机图（Excalidraw）、飞书需求表《鸿兔项目开发 需求收集与管理》

---

## 1. 背景与问题诊断

### 1.1 现状

母兔繁育状态目前存在 **三处写点、三套词汇**：

| 存储位置 | 词汇 | 写入方 |
| --- | --- | --- |
| `rabbits.reproductive_stage` | `RESERVE/EMPTY/MATED/PREGNANT/LACTATING/RESTING/READY` | RabbitService（录入/编辑） |
| `batch_rabbits.current_status` | `待催情/待配种/已配种/不确定/怀孕确认/哺乳中/成长期/休整期(虚拟)` | BatchService.syncBreedingSummary |
| `breeding_cycles.status` | `待配种/已配种/不确定/怀孕确认/空怀/哺乳中/已断奶/分娩失败` | BatchService 各操作方法 |

同步依赖 `syncBreedingSummary()`（BatchService:2285）手工调用，漏调即漂移；且这三套词汇均与业务方认定的操作词汇（**待催情/待配种/待摸胎/待备产/待接产(待分娩)/待分笼/准备**）不一致。

### 1.2 与飞书需求表的 P0 问题对应

| 飞书需求 | 现状根因 |
| --- | --- |
| recvsrp9E2dqvB 种母兔繁育阶段和批次中不对应（P0 BUG） | 状态三写无单一事实源 |
| recvsrpMlvu2SC 母兔状态可选项缺少（P0 BUG） | 词汇表不统一，rabbit 侧枚举 ≠ 生产流程枚举 |
| recvsrq7rGZHdi 兔子周期/批次周期/提醒绑定异常（P0 BUG） | 提醒散落在 `batch_rabbits.next_event_*`、`breeding_cycles.next_event_*`、`replacement_records.expected_mature_date` 三处，商品兔出售提醒另算，无统一待办模型 |
| recvsrpXPZd3Xg 备产提前天数逻辑不对（P1 BUG） | 预产期 = 配种 + **硬编码 30 天**（BatchService:515、1091）；`prepartum_days` 既当"提前量"又当"等待时长"用，语义冲突 |
| recvqh3EJXzmO1 批次新口径（P0） | 现实现：建批次必须选母兔、一母兔一批次内可多周期、成员全退自动完成——三点都与新口径相反 |
| recvsrrPUz0djZ / recvss4qXnDEIX 六大人工操作表单（P0） | "未执行→选下次提醒时间"未一等公民化；操作留痕分散在 4 张记录表，催情/推迟无记录表 |
| recvsrrTP2Rp0l 流产操作（P0） | 无流产模型 |
| recvsroMN5SslS / recvsrtXYAKnX1 种兔/后备兔阶段字段错误 | `growth_stage`/`reproductive_stage` 对兔子类型无约束 |
| recvsrnEJ8bKrk / recvqh6N0wWVjR 录入时指定阶段+进入日期 | 周期表无 `stage_entered_at`，无法表达"已在该阶段 N 天" |

### 1.3 SaaS 视角的技术债

1. **提醒产生 = 夜间扫全业务表**（EventReminderScanJob 遍历所有 house × 3 张表），租户数增长后线性恶化，且当日内状态变化无法实时反映。
2. **长事务**：批量配种在单事务内循环上千母兔（BatchService:681）。
3. **幂等三段式**手工重复实现于每个操作方法。
4. **配置层级混乱**：`global_setting` 的 house/user 两列可空互斥，语义靠约定。
5. `batch_rabbits` 承载状态快照 + 哺乳计数 + 提醒，职责过载。

---

## 2. 设计目标

1. **单一事实源**：母兔繁育状态只有一个权威写点，其余为同事务投影（读模型）。
2. **统一词汇**：前后端、App、报表共用一套阶段/操作枚举，与业务方口径一致。
3. **操作全留痕**：所有人工操作（含"未执行推迟"与"取消"）append-only 记录人员/时间/母兔/批次。
4. **统一任务中心**：所有提醒（繁育六步 + 出售 + 后备成熟）收敛到一张按到期日索引的任务表，消灭扫表作业（批次标签化后无自身提醒，空批次角标为查询派生）。
5. **配置驱动**：可配置的间隔天数分层（平台默认 → 租户 → 兔舍），语义明确；预产期固定为配种日后 30 天。
6. **SaaS 就绪**：租户维度贯穿、索引前缀统一、计数器反范式、短事务、可归档、可重放。

---

## 3. 统一状态机定义

### 3.1 阶段枚举（唯一词汇表）

```
DoeStage（母兔繁育阶段，存储值用英文，展示映射中文）
  READY            准备        （周期间歇态，通常瞬时）
  AWAIT_ESTRUS     待催情
  AWAIT_MATING     待配种
  AWAIT_PALPATION  待摸胎
  AWAIT_PREPARTUM  待备产
  AWAIT_DELIVERY   待分娩（待接产）
  AWAIT_WEANING    待分笼（哺乳中）
非管线态（母兔个体，不属于周期）：
  SUSPENDED        暂停（隔离/治疗）
  RETIRED          离场（死亡/淘汰/出售）
```

### 3.2 转换表（数据驱动，配置项见 §5）

| # | 触发操作 | 前置阶段 | 结果 | 目标阶段 | 下一任务到期 = |
| --- | --- | --- | --- | --- | --- |
| T1 | 开始周期 START_CYCLE | READY | 可指定**任意入轨阶段**（默认待催情） | 指定阶段 | 按入轨阶段锚点计算（见下表）；分笼/流产后的自动 T1 为无批次待催情，due=当天+`postpartum_recovery_days` |
| T2 | 催情 ESTRUS | AWAIT_ESTRUS | 执行 | AWAIT_MATING | 当天 + `estrus_duration_days` |
| T3 | 配种 MATING | AWAIT_MATING | 执行 | AWAIT_PALPATION | 配种日 + `palpation_wait_days` |
| T4a | 摸胎 PALPATION | AWAIT_PALPATION | 怀孕 | AWAIT_PREPARTUM | 摸胎确认日 + `prepartum_days`（待备产时长） |
| T4b | 摸胎 PALPATION | AWAIT_PALPATION | 空怀 | （周期关闭 result=EMPTY）→ AWAIT_ESTRUS | 立即（提醒员工立刻催情） |
| T4c | 摸胎 PALPATION | AWAIT_PALPATION | 不确定 | AWAIT_PALPATION（不变） | 用户选择的复查日期（今天及以后） |
| T5 | 备产 PREPARTUM | AWAIT_PREPARTUM | 执行 | AWAIT_DELIVERY | 操作当天 |
| T6 | 接产 DELIVERY | AWAIT_DELIVERY | 产仔（产/活/留） | AWAIT_WEANING（建 Litter） | 分娩日 + `weaning_days` |
| T6x | 接产 DELIVERY | AWAIT_DELIVERY | 分娩失败 | （周期关闭 result=FAILED）→ AWAIT_ESTRUS | 当天 + `postpartum_recovery_days` |
| T7 | 分笼 WEANING | AWAIT_WEANING | 断奶数 | （周期关闭 result=WEANED）→ READY → 自动 T1；**若母兔已有管线周期（血配提前开启）则跳过自动 T1，仅关窝** | 当天 + `postpartum_recovery_days` |
| T8 | 流产 ABORTION | AWAIT_PALPATION / AWAIT_PREPARTUM / AWAIT_DELIVERY | 照片+死胎数+状态 | （周期关闭 result=ABORTED）→ AWAIT_ESTRUS | 当天 + `postpartum_recovery_days` |
| T9 | 推迟 POSTPONE | 任意 AWAIT_* | 未执行 | 原阶段不变 | 用户提交的下次提醒时间 |
| T10 | 取消弹窗 CANCEL | 任意 | — | 不变，任务保留 | 不变（仅客户端行为，不落事件） |
| T11 | 离场 RETIRE（死亡/淘汰/出售） | 任意 | 关联 departure 记录 | 周期关闭 result=REMOVED；NURSING 窝需选择寄养(foster_out)或随场处置 | 级联 CANCELLED 该母兔全部 PENDING 任务 |

业务流程口径：`prepartum_days` 表示摸胎确认后进入待备产前的等待时长；备产完成后当天进入待分娩。预产期固定为配种日后 30 天，不提供配置项。

**任意阶段入周期（T1 泛化，业务确认口径）**：母兔可在任何阶段加入周期（存量录入/兔场初始化/后备转种母/V27 回填公用同一机制 `openCycleAt(stage, facts)`）。各入轨阶段需补录事实与首任务锚点：

| 入轨阶段 | 必要补录事实 | 首任务 due = |
| --- | --- | --- |
| 待催情 | stage_entered_at（或已在阶段天数） | 用户指定，缺省当天 |
| 待配种 | stage_entered_at | stage_entered_at + `estrus_duration_days` |
| 待摸胎 | matingDate（可选 sire） | matingDate + `palpation_wait_days` |
| 待备产 | stage_entered_at | 进入阶段当天 |
| 待分娩 | stage_entered_at | 进入阶段当天 |
| 待分笼 | birthDate + 活仔数（同事务建 NURSING litter） | birthDate + `weaning_days` |

计算结果早于当天时 due 拉到当天（立即提醒）。CYCLE_START 事件 payload 完整记录入轨阶段与补录事实，保持事件流可重放。入轨管线阶段受 `uk_bc_pipeline` 管线互斥约束；直接入轨待分笼不占管线（与血配规则一致）。

### 3.3 重叠哺乳配种（血配）与"母兔同时在两个批次"

拆出 **Litter（窝）** 实体后，重叠不再需要双开管线周期：

- 周期的**管线段** = AWAIT_ESTRUS → AWAIT_DELIVERY；分娩后周期进入 AWAIT_WEANING，但管线锁释放。
- 唯一性约束：**一只母兔最多一个"管线段 OPEN"的周期**（生成列 + 唯一键，DB 兜底，见 §4.2）。
- 血配场景：上一周期处于 AWAIT_WEANING（其 Litter 仍 NURSING，分笼任务挂在 Litter 上），新周期在另一批次开启并推进管线。母兔展示阶段取管线周期阶段；无管线周期时若有 NURSING litter 则展示 AWAIT_WEANING。
- 满足新口径：一批次一母兔一周期（唯一键），母兔可同时出现在两个批次（两条周期分属两批次）。
- **分笼-预产期联动校验**：存在管线周期时，分笼任务 due 不得晚于管线周期 `expected_birth_date − buffer_days`（默认 2，可配）；超出拒绝推迟并提示“新窝临产，须先分笼”。

### 3.4 生理边界：双子宫与异期复孕（设计决策）

- 兔双子宫 + 刺激性排卵带来的两个真实生产能力已显式建模：哺乳∥下一轮妊娠（§3.3 管线只锁催情→分娩段）；子宫复旧（READY + `postpartum_recovery_days`，即状态机图中“母兔子宫回到准备”）。
- **异期复孕（superfetation）有意不建模为双管线**：生产管理上属于应预防的事故，状态机在怀孕段禁止 MATING 恰与规范一致。事故兜底路径：`repro_events(DELIVERY_EXTRA)` 异常事件（payload 补产仔数/日期）→ litters 计数修正 + rabbit_abnormal_conditions 留档；不开第二管线、不破坏 uk_lt_cycle。未来若需一等公民化（实验兔场景）：`litters` 加 `litter_seq` 放宽一周期一窝，核心守卫不动。单侧流产按 T8 或产仔数折减记录。

---

## 4. 数据结构设计

原则：`house_id` 恒为第一过滤列并冗余进所有唯一键/索引前缀（沿用现约定）；新增 `tenant_id` 冗余列为未来按租户分片预留；金额/天数配置不硬编码。

### 4.1 repro_events —— 操作事件流（新表，append-only，权威留痕）

```sql
CREATE TABLE repro_events (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NULL,           -- 预留：待租户模型落地后回填，不阻塞本期
  house_id        BIGINT NOT NULL,
  cycle_id        BIGINT,                -- 周期外事件（如死亡）可空
  litter_id       BIGINT,
  mother_rabbit_id BIGINT NOT NULL,
  batch_id        BIGINT,
  event_type      VARCHAR(32) NOT NULL,  -- ESTRUS_DONE / MATING_DONE / PALPATION_PREGNANT /
                                         -- PALPATION_EMPTY / PALPATION_UNSURE / PREPARTUM_DONE /
                                         -- DELIVERY_DONE / DELIVERY_FAILED / WEANING_DONE /
                                         -- ABORTION / POSTPONE / CYCLE_START / CYCLE_CLOSE ...
  from_stage      VARCHAR(20),
  to_stage        VARCHAR(20),
  occurred_at     DATETIME NOT NULL,     -- 业务时间（允许回填录入）
  payload         JSON,                  -- 操作差异字段：配种方式/公兔、产仔数/活仔/留仔、
                                         -- 断奶数、流产死胎数/照片fileIds、下次提醒时间…
  operator_id     BIGINT NULL,           -- 历史回填事件无 user_id，故可空
  operator_name   VARCHAR(64) NOT NULL,  -- 冗余快照，兼容现有 create_by 体系与历史回填
  request_id      VARCHAR(64) NOT NULL,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_re_request (house_id, request_id),
  KEY idx_re_cycle (house_id, cycle_id, id),
  KEY idx_re_mother_time (house_id, mother_rabbit_id, occurred_at),
  KEY idx_re_house_time (house_id, occurred_at, id)
) ENGINE=InnoDB;
```

作用：

- 满足"所有操作记录人员/时间/母兔/批次"（recvsrrPUz0djZ）与各操作必填字段（recvss4qXnDEIX）。
- **幂等简化**：先插事件（唯一键兜底），DuplicateKey → 回查返回原结果，替代现有三段式手工幂等。
- 可重放：投影（rabbits.current_stage、统计）损坏时可由事件流重建。
- 现有 `pregnancy_check_records`/`prepartum_records`/`parturition_records`/`weaning_records` 降级为只读历史（迁移期回填成事件），报表切换到 repro_events + litters。`weaning_record_allocations` 保留（笼位分配是结构化库存逻辑）。

### 4.2 breeding_cycles V2 —— 周期主状态（改造）

```sql
ALTER TABLE breeding_cycles
  ADD COLUMN tenant_id BIGINT NULL,               -- 预留
  ADD COLUMN stage VARCHAR(20) NULL,              -- V26 先可空，V27 回填后收紧 NOT NULL
  ADD COLUMN stage_entered_at DATETIME NULL,      -- 支持"录入时已处于该阶段 N 天"；同上收紧
  ADD COLUMN lifecycle VARCHAR(10) NOT NULL DEFAULT 'OPEN',   -- OPEN / CLOSED
  ADD COLUMN result VARCHAR(10),                  -- WEANED/EMPTY/ABORTED/FAILED/REMOVED
  ADD COLUMN mating_method VARCHAR(10),           -- NATURAL 体配 / AI 人工授精
  ADD COLUMN state_version BIGINT NOT NULL DEFAULT 0,   -- 乐观锁
  -- 管线互斥守卫（沿用 V25 生成列+唯一键模式，DB 层兜底并发）：
  ADD COLUMN pipeline_guard BIGINT GENERATED ALWAYS AS (
    CASE WHEN lifecycle = 'OPEN'
          AND stage IN ('AWAIT_ESTRUS','AWAIT_MATING','AWAIT_PALPATION',
                        'AWAIT_PREPARTUM','AWAIT_DELIVERY')
         THEN mother_rabbit_id END) STORED,
  ADD UNIQUE KEY uk_bc_pipeline (house_id, pipeline_guard),
  -- 标签口径：同批次同母兔同时只能有一个 OPEN 周期。关闭后仍可由用户显式
  -- 重新打入该批次；自动接续周期默认 batch_id=NULL，不让旧批次活动无限延续：
  ADD COLUMN batch_member_guard VARCHAR(64) GENERATED ALWAYS AS (
    CASE WHEN lifecycle = 'OPEN' AND batch_id IS NOT NULL
         THEN CONCAT(batch_id, ':', mother_rabbit_id) END) STORED,
  ADD UNIQUE KEY uk_bc_batch_member (house_id, batch_member_guard);
-- cycle_no 按母兔全局递增：UNIQUE (house_id, mother_rabbit_id, cycle_no)（恢复 V21 原键）
-- batch_id 改为可空（散养母兔无标签）；旧列 status/nursing 计数列迁移后弃用，哺乳计数移至 litters
```

### 4.3 litters —— 窝（新表，分娩产物与哺乳/分笼管理）

```sql
CREATE TABLE litters (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT NULL,
  house_id        BIGINT NOT NULL,
  cycle_id        BIGINT NOT NULL,
  mother_rabbit_id BIGINT NOT NULL,
  sire_rabbit_id  BIGINT,
  batch_id        BIGINT,
  birth_date      DATETIME NOT NULL,
  total_kits      INT NOT NULL,
  live_kits       INT NOT NULL,
  kept_kits       INT NOT NULL,              -- 留崽数（接产表单）
  foster_in       INT NOT NULL DEFAULT 0,
  foster_out      INT NOT NULL DEFAULT 0,
  loss_count      INT NOT NULL DEFAULT 0,
  current_nursing INT NOT NULL DEFAULT 0,    -- 计数器，事务内维护
  status          VARCHAR(10) NOT NULL,      -- NURSING / WEANED
  weaning_date    DATETIME,
  weaned_count    INT,
  avg_weaning_weight DOUBLE,
  nursing_cage_id BIGINT,
  request_id      VARCHAR(64),
  UNIQUE KEY uk_lt_cycle (house_id, cycle_id),
  KEY idx_lt_status (house_id, status, birth_date)
) ENGINE=InnoDB;
```

### 4.4 work_tasks —— 统一任务/提醒中心（新表，核心）

```sql
CREATE TABLE work_tasks (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id     BIGINT NULL,
  house_id      BIGINT NOT NULL,
  task_type     VARCHAR(32) NOT NULL,   -- ESTRUS/MATING/PALPATION/PREPARTUM/DELIVERY/WEANING/
                                        -- SALE_READY/REPLACEMENT_MATURE/CUSTOM...
  subject_type  VARCHAR(16) NOT NULL,   -- CYCLE / LITTER / RABBIT / CAGE
  subject_id    BIGINT NOT NULL,
  cycle_id      BIGINT, rabbit_id BIGINT, batch_id BIGINT, cage_id BIGINT,  -- 冗余定位
  due_date      DATE NOT NULL,
  due_time      DATETIME NOT NULL,
  status        VARCHAR(12) NOT NULL DEFAULT 'PENDING',  -- PENDING/DONE/CANCELLED/EXPIRED
  snooze_count  INT NOT NULL DEFAULT 0,
  completed_event_id BIGINT,            -- 完成时回链 repro_events
  dedup_key     VARCHAR(96) NOT NULL,   -- 'cycle:{id}:PALPATION' / 'litter:{id}:WEANING'
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wt_dedup (house_id, dedup_key),
  KEY idx_wt_due (house_id, status, due_date, task_type),   -- 首页今日待办
  KEY idx_wt_subject (house_id, subject_type, subject_id, status),
  KEY idx_wt_cage (house_id, cage_id, status)               -- NFC 碰笼直查该笼待办
) ENGINE=InnoDB;
```

不变式与规则：

- **每个 OPEN 周期恰有 1 条关联 PENDING 任务**：管线段（催情→接产）挂 `subject=CYCLE`；哺乳段分笼任务挂 `subject=LITTER`（cycle_id 冗余回链）。血配时同一母兔可见两条任务：新周期管线任务 + 旧窝分笼任务，互不干扰。状态机转换在同事务内"完成旧任务 + 创建新任务"。
- 推迟（T9）= `due_date/due_time` 更新 + `snooze_count++` + 追加 POSTPONE 事件；任务不删除（取消弹窗不落库，提醒天然保留）。
- 出售提醒、后备成熟收敛为任务行 —— 修复 recvsrq7rGZHdi、recvsrmZKv1cqp（首页按 house/cage/type 过滤直查此表）。
- **EventReminderScanJob 退役**：今日待办 = `WHERE house_id=? AND status='PENDING' AND due_date<=CURDATE()`（索引直达）。推送通道（未来）只消费此表，不再回扫业务表。`event_acks`/`event_reminder_logs` 由任务状态取代。
- 批量操作（批量催情/配种）= 按 task id 列表分块（每块独立短事务）执行，逐项返回结果。

### 4.5 batches —— 标签化（业务确认口径：批次 = 标签，本身无状态）

```sql
-- batches 退化为纯标签实体：
--   id, tenant_id, house_id, batch_code, remark, is_archived, request_id + 审计列
--   UNIQUE (house_id, batch_code)
-- 无 status / start_date / end_date / 计数器；起止时间由周期事件派生（MIN/MAX）

-- batch_rabbits 整表退役（不更名保留）：
--   成员关系 = breeding_cycles.batch_id 推导；
--   uk_bc_batch_mother(house_id, batch_id, mother_rabbit_id) 天然保证一批次一母兔一周期
```

- 打标时机：开启周期时可选携带 `batch_id`（可空；业务已确认散养母兔开放）；改标走 `repro_events(BATCH_RETAG)` 留痕。
- **批次 = 批量选择集**（业务确认的核心用途）：批量催情/配种等操作可按 batchId 圈选目标（见 §5.1 bulk filter）；任务/周期列表均支持 batchId 过滤（work_tasks.batch_id 冗余列 + idx）。
- **追踪覆盖商品兔**：仔兔出生时沿用 `rabbits.birth_batch_id/birth_cycle_id` 回填，出栏快照沿用 `outbound_task_items.batch_id_snapshot`，使标签串起「母兔周期 → 窝 → 商品兔 → 出栏/销售」全链路流转追踪，批次自身始终无状态。
- 「母兔同时在两个批次」自动成立：两条周期各挂不同 `batch_id`。
- 归档：`is_archived=true` 仅作 UI 过滤；**硬校验**——仍有 OPEN 周期挂此标签时拒绝归档。
- 空批次提示：无 OPEN 周期的批次在列表页显示「可归档」角标（查询时派生，无需任务与计数器）。
- 批次报表 = `GROUP BY batch_id` 聚合周期/窝/事件（idx: house_id+batch_id），**批次自身无任何可不一致的状态**。

### 4.6 rabbits —— 读模型投影 + 类型约束（改造）

```sql
ALTER TABLE rabbits
  ADD COLUMN current_stage VARCHAR(20),      -- 种母：DoeStage；商品/后备：growth 阶段继续用 growth_stage
  ADD COLUMN current_cycle_id BIGINT,
  ADD COLUMN stage_entered_at DATETIME,
  ADD COLUMN last_mating_date DATETIME;      -- 种公兔专用（录入需求）
```

- `current_stage` **只有状态机服务一个写者**，与周期变更同事务投影；`state_version` 乐观锁已有。
- 类型-阶段约束（服务层强校验，修复 recvsroMN5SslS/recvsrtXYAKnX1/recvsrpMlvu2SC）：
  - 种母（type=0, 母）：仅 DoeStage，无 growth_stage，无 RESERVE；
  - 种公：仅 last_mating_date；
  - 后备兔：仅 RESERVE 轨道 + 成熟倒计时（转种兔走 T1 初始化，覆盖 recvrqnhXOyd5N）；
  - 商品兔：仅 growth_stage + SALE_READY 任务。
- 录入表单支持"阶段 + 进入该阶段日期/天数"（recvsrnEJ8bKrk、recvqh6N0wWVjR）：走 `openCycleAt` 任意阶段入轨（§3.2 入轨表），首任务按阶段锚点计算并折减已过天数。

### 4.7 配置：生产周期设置（物理表沿用 global_setting，语义映射）

```sql
-- V38 删除 gestation_days；预产期固定为配种日后 30 天。
-- 列改名推迟到 V29+，代码层先用新语义常量映射旧列。三层解析（平台默认 → 用户 → 兔舍）不变：
--   estrus_duration_days                  -- 原 aphrodisiac_days，催情→配种
--   palpation_wait_days                   -- 原 palpation_days，配种→摸胎
--   prepartum_duration_days               -- 原 prepartum_days，摸胎确认后待备产时长
--   weaning_days                          -- 分娩→分笼
--   postpartum_recovery_days              -- 原 postpartum_days，分笼/流产→待催情
--   adaptation_days                       -- 幼兔适应期，允许 2–3 天
--   growing_days                          -- 生长期，允许 15–18 天
--   fattening_days                        -- 育肥期，允许 12–15 天
--   sale_days                             -- 旧客户端兼容镜像；新出售日取上述三段之和
--   replacement_days                      -- 后备成熟，默认 90 天（喂给 work_tasks）
```

提醒日期选择使用兔场生效配置给出本地日历默认值；服务端仍校验提醒日期不得早于当天。配置读取加进程内缓存（Caffeine，按 house 键，更新时失效广播），消除每操作一次的 `requireSetting()` 查询。

### 4.8 附件：biz_attachments（新表，通用）

流产照片（recvsrrTP2Rp0l）等场景：`(house_id, biz_type, biz_id, file_id, ...)`，事件 payload 仅存 file_id 引用，避免 JSON 膨胀。

---

## 5. 交互逻辑设计

### 5.1 统一操作入口（状态机服务）

```
POST /api/repro/cycles                        开启周期（START_CYCLE；可选 batchId 打标；
                                              可选 stage+补录事实任意阶段入轨，见 §3.2 入轨表；
                                              待分笼入轨时同事务创建 litter）
POST /api/repro/cycles/{cycleId}/actions      单只操作
POST /api/repro/tasks/bulk-actions            批量（分块短事务；逐项事件 request_id =
                                              客户端 requestId + '-' + taskId，
                                              沿用现有 deriveBoundedRequestId 截断规则）
body: { requestId, action, occurredAt, payload, nextRemindAt?,
        target: { taskIds[] } | { filter: { batchId, taskType } } }
      -- filter 形式服务端解析为该批次当前 PENDING 任务集（批次=批量选择集）

response: { cycleId, currentCycleId?, eventId, nextTaskId?, stage, lifecycle,
            nextDueTime?, followUpCycleId?, replayed }
      -- cycleId 是本次被操作周期；currentCycleId / stage / lifecycle 是事务完成后
         rabbits 的权威投影；followUpCycleId 仅表示本次新建的接续周期

GET  /api/repro/stage-actions                 阶段 → 可执行动作字典（含中文名）
GET  /api/repro/entry-points                  兔只可用的操作入口
```

**动作显隐的唯一来源是服务端。** `stage-actions` 由 `TransitionTable.actionsFrom` 推导，
客户端不得自行硬编码阶段与动作的对应关系。

流产是**非计划事件**——不对应任何待办，因此不能从今日清单进入，只能对着一头具体母兔记录。
它与「母兔离场」同类，所以单列入口，而不是塞进推进流程的表单。待摸胎、待备产和待分娩
三个阶段的可执行动作里才包含流产。

`ReproStateMachineService.apply()` 唯一写路径（替代 BatchService 六个 2000 行级方法）：

```
1. INSERT repro_events（唯一键幂等；冲突→回查返回首次结果）
2. SELECT cycle FOR UPDATE + state_version 校验
3. 查转换表（§3.2，数据驱动 Map<Stage, Map<Action, Transition>>）：非法转换 → 409
4. UPDATE breeding_cycles: stage / stage_entered_at / result / lifecycle
5. 投影 UPDATE rabbits: current_stage / current_cycle_id / stage_entered_at
6. UPDATE work_tasks: 旧任务 → DONE(completed_event_id)；INSERT 下一任务（dedup_key 幂等）
7. 需要时：INSERT litters / UPDATE 窝哺乳计数器
```

事务边界 = 单只母兔单操作，毫秒级短事务；锁顺序固定 cycle → rabbit → task，批量时按 rabbit_id 排序取锁（沿用现有防死锁约定）。

### 5.2 六大操作表单契约（对齐 recvss4qXnDEIX）

母兔、操作人、批次和周期不由客户端重复填写：服务端从已加锁的周期、JWT 登录态和
`X-House-Id` 推导并写入 `repro_events`。这样既满足留痕要求，也避免客户端篡改关联对象。
客户端必须提交执行时间和动作事实；执行时间支持日期与时分，默认当前时间，也允许补录过去。

| 操作 | 执行 payload（必填） | 服务端派生/校验 | 未执行（POSTPONE） |
| --- | --- | --- | --- |
| 催情 | occurredAt | 母兔、人员、批次、周期 | nextRemindAt |
| 配种 | occurredAt, matingMethod(NATURAL/AI)；体配必填 sireRabbitId，人工授精可选 | 一旦选择种公兔就校验资格并记录系谱 | nextRemindAt |
| 摸胎 | occurredAt, result(PREGNANT/EMPTY/UNSURE) | UNSURE 必带复查时间 | nextRemindAt |
| 备产 | occurredAt | 母兔、人员、批次、周期 | nextRemindAt |
| 接产 | occurredAt, totalKits, liveKits, keptKits | `0 <= 留仔 <= 活仔 <= 总产仔` | nextRemindAt |
| 难产 | occurredAt, detail, photoFileIds[] | 仔数三项固定为 0；图片必须属于当前兔舍 | nextRemindAt |
| 分笼 | occurredAt, weanedCount(+ 目标笼分配沿用 allocations) | 公母数同时为 0 或相加等于断奶数；手工分配时校验笼位容量 | nextRemindAt |
| 留崽调整 | occurredAt, keptKits；增加时 sourceMotherRabbitId | 原值、新值、来源母兔、人员、批次和周期写入追加事件 | —（阶段与待办不变） |
| 流产 | occurredAt, stillbirthCount, detail, photoFileIds[] | stageAtAbortion 从周期读取；图片必须属于当前兔舍 | —（直接执行类操作） |
| 商品兔留后备 | 目标商品兔 | 返回 `replacementRecordId` 和目标笼位；人员与执行时间由服务端记录 | 不适用 |

执行成功并生成下一条待办的单只操作也可携带 `nextRemindAt`：不传时按兔场配置计算，
传入时覆盖本次新待办日期；日期不得早于当天。不会生成下一条待办的结果若携带该字段，
服务端返回 400，不能静默丢弃。`POSTPONE` 仍只改当前 PENDING 待办，不推进阶段。

“取消”仅关闭弹窗，无请求；任务保持 PENDING，符合"提醒不消失"。

#### 契约保护

- 单只和批量动作请求都要求 `occurredAt`；App 即使调用方不显式传值也会发送当前时间。
- 配种方式始终必填；体配必须选择种公兔，人工授精的种公兔为可选。
- `POSTPONE` 必须选择今天或未来的提醒时间，且不会推进阶段或清除待办。
- 难产与流产在图片、详情或数量缺失时由服务端返回中文 400，不能绕过界面提交。
- 商品兔留后备接口通过 `replacement_records.request_id` 支持结果级幂等回查。

#### 留崽数调整

`POST /api/repro/cycles/{cycleId}/kept-kits-adjustments` 只允许哺乳中的窝。增加留崽数必须
选择另一只种母兔作为来源；减少时不能伪填来源母兔。接口在同一事务内更新
`litters.kept_kits/current_nursing/foster_in/foster_out` 并追加 `KEPT_KITS_ADJUSTED` 事件，
重复 `requestId` 返回首次结果。

#### 图片链路

- `POST /api/business-files/images` 接收 `multipart/form-data`，单张最大 5 MB。
- 只接受实际内容为 JPEG、PNG、WebP 或 HEIC 的文件，不能只靠伪造 Content-Type 绕过。
- 每个动作最多 6 张；文件按兔舍隔离，相同内容在同一兔舍内按 SHA-256 去重。
- 图片内容和元数据保存在 `business_files`，动作只在 `biz_attachments` 与事件 payload 中
  保存 `fileId` 引用。
- 读取图片仍要求业务 JWT、`X-House-Id` 和兔舍查看权限。

#### 表单契约的回归覆盖

- 后端：`ReproRequiredFieldsIT`、`ReproMatingEligibilityIT`、`ReproDeliveryIT`。
- Flutter：`mating_test.dart`、`parturition_test.dart`、`abortion_test.dart`、
  `form_contract_test.dart`、`required_images_test.dart`。

### 5.3 批次交互（标签模型）

- 创建：`POST /api/batches {code}` —— 纯建标签，不带成员。
- 打标：开周期时 `POST /api/repro/cycles {motherId, batchId?}`；batchId 可空；改标 `BATCH_RETAG` 事件留痕。
- 归档：`POST /api/batches/{id}/archive`，仍有 OPEN 周期 → 409。
- 批次视图：`GET /api/batches/{id}/cycles` 派生查询（周期表单查），无成员表。

### 5.4 首页/待办查询

- 今日待办（首页）：`GET /api/tasks?dueBefore=today&status=PENDING&houseId=…&type=…&cageId=…`，单索引查询，支持兔舍/笼位/类型过滤（修复 recvsrmZKv1cqp）。
- 兔只详情：`GET /api/tasks?rabbitId=…&includeFuture=true`，读取该兔全部未来 `PENDING` 待办；
  默认 `includeFuture=false`，首页原有截至日期语义不变。血配时保留新周期任务与旧窝分笼任务两条结果。
- NFC 碰笼：`idx_wt_cage` 直出该笼全部待办与可执行操作。
- 兔笼页兔子列表管理（recvsrEA6TRuK6）：rabbits 投影列使列表页免 join 周期表。

---

## 6. SaaS 性能与扩展性

1. **租户模型**：`tenant_id`（组织）加入热表冗余列；当前单库行级隔离（house_id 强制过滤，X-House-Id 中间件不变），未来可按 tenant_id 垂直分库/分片，索引前缀无需重建。
2. **读写路径**：
   - 写：全部毫秒级短事务；批量走分块（500/块，沿用 BULK_WRITE_SIZE）。
   - 读：待办、批次成员、笼位视图均为单表索引查询；报表由 repro_events/litters 聚合，`breeding_performance` 保留为夜间物化（现 Job 改读新表）。
3. **提醒可扩展**：work_tasks 是唯一提醒源。推送通道（App push/短信/飞书）作为消费者按 `(status, due_date)` 拉取，可加 `notified_at` 列做投递去重；不需要改业务表。
4. **数据可维护性**：
   - 事件流 append-only + 投影可重放（校验脚本：由 repro_events 重算 current_stage 比对）。
   - 归档：`lifecycle='CLOSED'` 且 `closed_at < now()-N` 的周期与其事件按租户归档表转储。
   - 枚举中文映射收敛到单一常量模块（backend enum + admin/app 由 OpenAPI 常量生成），杜绝词汇漂移。
5. **扩展点**：
   - 新阶段/新操作 = 转换表数据 + 枚举项，无表结构变更；
   - 操作差异字段进 payload JSON，无 DDL；
   - 多物种/多工艺 = 每 house 绑定一套 stage_config 版本。

---

## 7. 迁移路径（已完成）

| 步骤 | 迁移 | 内容 | 风险控制 |
| --- | --- | --- | --- |
| 1 | V26 | 建 repro_events / work_tasks / litters / biz_attachments；breeding_cycles 加列（stage/lifecycle/result/state_version/guard 列先不加唯一键）；rabbits 加投影列；settings 加 gestation_days | 纯增量，无破坏 |
| 2 | V27 | 数据回填：旧 status → 新 stage 映射（待配种→AWAIT_MATING、已配种/不确定→AWAIT_PALPATION、怀孕确认→AWAIT_PREPARTUM/AWAIT_DELIVERY(按 next_event_type)、哺乳中→AWAIT_WEANING+建 litter、已断奶→CLOSED/WEANED…）；4 张记录表 → repro_events；next_event_* → work_tasks；校验后加 uk_bc_pipeline / uk_bc_batch_mother | 迁移前脚本检测违反新唯一键的数据并出清单 |
| 3 | 代码 | ReproStateMachineService 上线，新端点启用；BatchService 旧写路径切只读兼容（读新表拼旧响应），App/admin 灰度切换 | 旧接口保留一个版本期 |
| 4 | V28 | `batch_rabbits` 整表退役（数据回填进 breeding_cycles.batch_id 后转只读归档）；batches 删除 status/start/end 列、加 is_archived；EventReminderScanJob/事件 ack 表退役；旧记录表转只读归档 | 观察期后执行 |
| 5 | V38 | 删除 `global_setting.gestation_days`；预产期固定为配种日后 30 天 | 先删除 API、Mapper 与 schema 残留，再迁移并验证存量设置保留 |

配套测试：状态机转换矩阵单测（合法/非法全覆盖）、并发管线守卫 IT（仿 RabbitStagesAndCageConcurrencyIT）、幂等重放 IT、迁移回填对账脚本。

---

## 8. 需求映射清单（飞书 → 设计点）

| 飞书记录 | 设计答复 |
| --- | --- |
| recvqh3EJXzmO1 批次新口径 | §4.5、§5.3；产品口径已进一步确认：批次 = 纯标签，「结束」落地为「归档」，空批次提醒降为派生角标 |
| recvsrrPUz0djZ 人工操作表单 | §3.2、§5.2、repro_events |
| recvss4qXnDEIX 表单必填字段 | §5.2 表 |
| recvsrrTP2Rp0l 流产 | T8 + biz_attachments + §5.2 |
| recvsrp9E2dqvB 阶段不对应 | 单写者投影 §4.6、批次快照列删除 §4.5 |
| recvsrpMlvu2SC 状态可选项缺少 | §3.1 统一词汇 |
| recvsrq7rGZHdi 提醒绑定异常 | §4.4 任务中心（SALE_READY 等统一建模） |
| recvsrpXPZd3Xg 备产提醒口径 | §4.7 prepartum_days 作为摸胎后待备产时长；备产完成当天进入待分娩 |
| recvsroMN5SslS / recvsrtXYAKnX1 阶段字段错误 | §4.6 类型-阶段约束 |
| recvsrnEJ8bKrk / recvqh6N0wWVjR 录入阶段+日期 | stage_entered_at + 到期折算 |
| recvsrmZKv1cqp 首页提醒兔舍选择 | §5.4 任务查询参数化 |
| recvrqnhXOyd5N 后备转种兔 | §4.6 类型约束 + T1 初始化 |
| recvrpTL16SBwu 死亡记录 | repro_events(DEATH) + departure 记录联动，任务级联 CANCELLED |
| recvsrEA6TRuK6 笼内兔子列表管理 | §5.4 投影列 |
