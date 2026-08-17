# V27 回填操作手册（P3 停写窗口）

配套迁移：`backend/src/main/resources/db/migration/V27__doe_breeding_v2_backfill.sql`
施工计划：[doe-breeding-v2-implementation-plan.md](doe-breeding-v2-implementation-plan.md) §3.2 / §3.3

> **前提**：V26 已上线且稳定运行；`app.repro.v2.enabled` 仍为 `false`。
> V27 只改数据与约束，不改行为——开关翻转是 P4 的事。

---

## 0. 一句话流程

```text
预检查（可在停写前做）→ 开维护开关 → 备份 → 跑 V27 → 抽检 → 关维护开关
```

预检查务必提前做。它的唯一目的是：**别让迁移在停写窗口里才发现数据脏**。

---

## 1. 预检查（停写前，只读，可随时执行）

### 1.1 一只母兔是否有多个在途管线周期

这是 V27 唯一会硬失败的场景。`uk_bc_pipeline` 建不上时迁移中止。

```sql
SELECT house_id, mother_rabbit_id, COUNT(*) AS open_pipeline_cycles,
       GROUP_CONCAT(id ORDER BY id) AS cycle_ids
FROM breeding_cycles
WHERE closed_at IS NULL
  AND status IN ('计划中','待催情','催情中','待配种','已配种','不确定','怀孕确认')
GROUP BY house_id, mother_rabbit_id
HAVING COUNT(*) > 1;
```

**期望：0 行。** V21 曾保证单个在途妊娠周期，所以正常库应当为空。

若不为空，逐条人工裁决——保留最新一条，其余按实际情况补 `closed_at` + `close_reason`：

```sql
-- 示例：保留 id 最大的一条，其余标记为历史遗留终止
UPDATE breeding_cycles SET closed_at = NOW(), close_reason = 'V27 对账：重复在途周期'
WHERE id IN (/* 裁决后确定要关闭的 id */);
```

> 哺乳中（`哺乳中`）的周期**不在**上面的清单里，这是刻意的：血配允许母兔一边哺乳
> 一边怀下一胎，两条 OPEN 周期并存是合法状态。

### 1.2 待接管的提醒量级（估算停写时长）

```sql
SELECT
  (SELECT COUNT(*) FROM breeding_cycles WHERE closed_at IS NULL)            AS open_cycles,
  (SELECT COUNT(*) FROM batch_rabbits WHERE is_active = TRUE
     AND current_status IN ('待催情','催情中','待配种'))                     AS members_needing_cycle,
  (SELECT COUNT(*) FROM parturition_records
     WHERE birth_date >= DATE_SUB(NOW(), INTERVAL 6 MONTH))                 AS recent_births,
  (SELECT COUNT(*) FROM rabbits WHERE is_active = TRUE AND type = '2')      AS commodity_rabbits;
```

全部是集合式 SQL，万级数据通常在分钟内完成。

### 1.3 房级配置是否齐备

缺房级配置的兔舍会退到内置默认值（催情 2 / 摸胎 12 / 妊娠 30 / 备产 3 / 断奶 25 / 出售 30），
与 `SettingService` 的默认值一致。确认这对各兔舍可接受：

```sql
SELECT h.id, h.name, g.id IS NOT NULL AS has_house_setting
FROM rabbit_houses h LEFT JOIN global_setting g ON g.house_id = h.id;
```

---

## 2. 执行

```bash
# 1) 开维护开关（挡住写流量）
# 2) 备份
mysqldump -u root -p rabbit_app > backup-before-v27-$(date +%Y%m%d%H%M).sql
# 3) 迁移
java -jar backend.jar   # 或 mvn flyway:migrate，取决于部署方式
```

V27 全程幂等，可安全重跑。

---

## 3. 抽检（关维护开关之前）

```sql
-- 3.1 不应再有未定级的周期
SELECT COUNT(*) FROM breeding_cycles WHERE stage IS NULL OR stage_entered_at IS NULL;
-- 期望 0（若非 0，NOT NULL 收紧会先一步失败，迁移不会走到这里）

-- 3.2 每个 OPEN 周期恰好一条 PENDING 待办
SELECT c.stage, COUNT(*) AS cycles,
       SUM(t.id IS NOT NULL) AS with_task
FROM breeding_cycles c
LEFT JOIN work_tasks t ON t.cycle_id = c.id AND t.status = 'PENDING'
WHERE c.lifecycle = 'OPEN'
GROUP BY c.stage;
-- 期望：cycles 与 with_task 相等（哺乳中的任务挂在窝上，cycle_id 同样有值）

-- 3.3 兔子投影与周期是否一致
SELECT COUNT(*) AS drifted
FROM rabbits r
JOIN breeding_cycles c ON c.id = r.current_cycle_id
WHERE r.current_stage <> c.stage;
-- 期望 0

-- 3.4 哺乳中周期都建了窝
SELECT COUNT(*) AS nursing_without_litter
FROM breeding_cycles c
LEFT JOIN litters l ON l.cycle_id = c.id
WHERE c.lifecycle = 'OPEN' AND c.stage = 'AWAIT_WEANING' AND l.id IS NULL;
-- 期望 0

-- 3.5 事件流只含近 6 个月
SELECT MIN(occurred_at), MAX(occurred_at), COUNT(*) FROM repro_events;
```

---

## 4. 失败处置

| 现象 | 含义 | 处置 |
| --- | --- | --- |
| `Duplicate entry 'X-Y' for key 'uk_bc_pipeline'` | 某母兔有多个在途管线周期 | 跑 §1.1 定位并裁决，`flyway repair` 后重跑 |
| `Invalid use of NULL value`（stage / stage_entered_at） | 有周期未被任何规则定级 | 查 §3.1 找出该行，补 status 或手工定级后重跑 |
| 迁移中途超时 | 数据量超预期 | 直接重跑；每步都带存在性判据，不会重复写入 |

**回滚**：P3 内任一步失败，在关维护开关前放弃即可。新列/新表旧代码不读不写，
行为零变化；修复后重跑。

---

## 5. P3 → P4 期间的缺口（必读）

V27 跑完后、P4 翻开关之前，**旧写路径仍在线**。旧代码插入周期时不写
`stage`，因此这段时间新增的周期：

- `stage` / `stage_entered_at` 为 NULL；
- `pipeline_guard` 随之为 NULL，**不受 `uk_bc_pipeline` 保护**；
- 不会自动生成 `work_tasks`。

这是有意为之的取舍：计划规定 P4 是唯一行为变更点，V27 不得让任何原本能成功
的旧请求失败。代价就是这段窗口期的新数据暂时不入新模型。

**因此 P4 切换前必须补一次定级**（重跑 V27 的步骤 1/2/4/6/7 即可，它们幂等）：

```sql
-- 先看窗口期攻下了多少未定级的周期
SELECT COUNT(*) FROM breeding_cycles WHERE stage IS NULL;
```

窗口期越短，需要补的越少。建议 P3 与 P4 连着发。

---

## 6. 关于 uk_bc_batch_member

计划 §3.3 步骤 9 曾要求同时建 `uk_bc_batch_member`，**V27 故意没有建**。

原因：`batch_member_guard` 覆盖所有 OPEN 周期（含哺乳中），而血配的定义就是
母兔一边哺乳一边怀下一胎。若建此键，同一批次内的血配会被唯一键挡死——恰好
抵消了 `pipeline_guard` 排除 `AWAIT_WEANING` 所要达成的效果。

退一步说，即使给它同样排除哺乳阶段，它也是冗余的：`pipeline_guard` 已保证
「一只母兔全厅只能有一个在途管线周期」，比「同批次内唯一」更严。

生成列本身保留，作为排查「同批次同母兔多个 OPEN 周期」的现成手柄。
