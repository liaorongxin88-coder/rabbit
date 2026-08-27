-- 每只母兔在同一批次内至多一条未结束（OPEN）的生产周期。
--
-- 这条约束推翻了 V27 的裁定。V27 当时刻意不建 uk_bc_batch_member，理由是它会挡掉
-- pipeline_guard 特意放行的血配（哺乳中的母兔重新配种）。新的业务定义把这个矛盾
-- 换了个解法：血配的第二条周期不再被禁止，而是必须落到<b>另一个批次</b>。于是
-- 「母兔同时在两个周期」与「母兔同时在两个批次」变成同一件事，批次也就真正成了
-- 需求里说的「一组繁殖母兔的生产管理容器」，而不是一个可以装下同一只兔两次的袋子。
--
-- pipeline_guard（全舍一条在途管线周期）仍然有效且更严；本约束是它在批次维度上的
-- 补充：哺乳周期不占管线，但它占批次。
--
-- 存量收敛策略：同一 (兔舍, 批次, 母兔) 下的多条 OPEN 周期，按 id 升序保留最早的一条
-- （血配场景里就是哺乳周期，它带着窝和分笼待办，留在原批次最贴合现场认知），其余
-- 逐条外迁到本迁移创建的恢复批次。恢复批次按「兔舍 + 该母兔的第几条多余周期」分组，
-- 保证同一只母兔的多条多余周期落进不同批次，同时让同一舍里同序位的母兔聚成一批，
-- 而不是每条周期造一个只有一只兔的批次。

DROP TEMPORARY TABLE IF EXISTS v44_surplus_cycles;
CREATE TEMPORARY TABLE v44_surplus_cycles (
  cycle_id BIGINT PRIMARY KEY,
  house_id BIGINT NOT NULL,
  mother_rabbit_id BIGINT NOT NULL,
  source_batch_id BIGINT NOT NULL,
  surplus_rank INT NOT NULL,
  stage VARCHAR(20) NOT NULL,
  stage_entered_at DATETIME NOT NULL
) ENGINE=InnoDB;

-- 两层 ROW_NUMBER 分工不同，不能合并：
--   内层按 (兔舍, 批次, 母兔) 排序，用来判定「谁是多余的那几条」；
--   外层按 (兔舍, 母兔) 重排，用来给多余周期分配互不相同的目标批次序位。
-- 只做内层会出错：母兔在批次 A 和批次 B 各有一条多余周期时，两条的批次内序位
-- 都是 2，会被一起塞进同一个恢复批次，当场再次违反本约束。
INSERT INTO v44_surplus_cycles (
  cycle_id, house_id, mother_rabbit_id, source_batch_id, surplus_rank, stage, stage_entered_at
)
SELECT
  cycle_id,
  house_id,
  mother_rabbit_id,
  batch_id,
  ROW_NUMBER() OVER (PARTITION BY house_id, mother_rabbit_id ORDER BY cycle_id),
  stage,
  stage_entered_at
FROM (
  SELECT
    c.id AS cycle_id,
    c.house_id,
    c.batch_id,
    c.mother_rabbit_id,
    c.stage,
    c.stage_entered_at,
    ROW_NUMBER() OVER (
      PARTITION BY c.house_id, c.batch_id, c.mother_rabbit_id ORDER BY c.id
    ) AS in_batch_rank
  FROM breeding_cycles c
  WHERE c.lifecycle = 'OPEN'
    AND c.batch_id IS NOT NULL
) ranked
WHERE ranked.in_batch_rank > 1;

-- 恢复批次的编号与 request_id 都是确定式的，便于事后审计「这批兔子为什么在这里」。
-- 没有多余周期时这条 INSERT 影响 0 行，不会凭空造出空批次。
INSERT INTO batches (
  house_id, batch_code, status, start_date, request_id, remark,
  create_by, create_time, update_by, update_time
)
SELECT
  s.house_id,
  CONCAT('V44-PARALLEL-H', s.house_id, '-R', s.surplus_rank),
  '进行中',
  MIN(s.stage_entered_at),
  CONCAT('v44-parallel-house-', s.house_id, '-r', s.surplus_rank),
  'V44 并行周期批次拆分',
  'v44', NOW(), 'v44', NOW()
FROM v44_surplus_cycles s
GROUP BY s.house_id, s.surplus_rank
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);

-- 目标批次里该母兔的周期号从当前最大值之后接着排：uk_bc_batch_mother_cycle 按
-- (兔舍, 批次, 母兔, 周期号) 去重，直接搬过去会撞号。
DROP TEMPORARY TABLE IF EXISTS v44_relocations;
CREATE TEMPORARY TABLE v44_relocations AS
SELECT
  s.cycle_id,
  s.house_id,
  s.mother_rabbit_id,
  s.source_batch_id,
  s.stage,
  s.stage_entered_at,
  target.id AS target_batch_id,
  COALESCE(existing.max_cycle_no, 0)
    + ROW_NUMBER() OVER (
        PARTITION BY s.house_id, target.id, s.mother_rabbit_id ORDER BY s.cycle_id
      ) AS cycle_no
FROM v44_surplus_cycles s
JOIN batches target
  ON target.house_id = s.house_id
 AND target.request_id = CONCAT('v44-parallel-house-', s.house_id, '-r', s.surplus_rank)
LEFT JOIN (
  SELECT house_id, batch_id, mother_rabbit_id, MAX(cycle_no) AS max_cycle_no
  FROM breeding_cycles
  WHERE batch_id IS NOT NULL
  GROUP BY house_id, batch_id, mother_rabbit_id
) existing
  ON existing.house_id = s.house_id
 AND existing.batch_id = target.id
 AND existing.mother_rabbit_id = s.mother_rabbit_id;

ALTER TABLE v44_relocations ADD PRIMARY KEY (cycle_id);

UPDATE breeding_cycles c
JOIN v44_relocations r ON r.cycle_id = c.id
SET c.batch_id = r.target_batch_id,
    c.cycle_no = r.cycle_no,
    c.update_by = 'v44',
    c.update_time = NOW();

-- 周期换批次后，挂在它身上的待办、事件与窝必须跟着换，否则批次维度的查询
-- （待办中心按 batchId 过滤、批次详情的窝列表）会把这条周期整条漏掉。
UPDATE work_tasks task
JOIN v44_relocations r
  ON r.house_id = task.house_id
 AND r.cycle_id = task.cycle_id
SET task.batch_id = r.target_batch_id,
    task.update_by = 'v44',
    task.update_time = NOW()
WHERE task.status = 'PENDING';

UPDATE repro_events event
JOIN v44_relocations r
  ON r.house_id = event.house_id
 AND r.cycle_id = event.cycle_id
SET event.batch_id = r.target_batch_id;

UPDATE litters litter
JOIN v44_relocations r
  ON r.house_id = litter.house_id
 AND r.cycle_id = litter.cycle_id
SET litter.batch_id = r.target_batch_id,
    litter.update_by = 'v44',
    litter.update_time = NOW();

-- 原批次的成员投影可能还指向刚被搬走的周期。重算成 batch_id 换过之后
-- 仍留在原批次里的那条 OPEN 周期。
DROP TEMPORARY TABLE IF EXISTS v44_source_pairs;
CREATE TEMPORARY TABLE v44_source_pairs AS
SELECT DISTINCT r.house_id, r.source_batch_id AS batch_id, r.mother_rabbit_id
FROM v44_relocations r;

ALTER TABLE v44_source_pairs ADD PRIMARY KEY (batch_id, mother_rabbit_id);

DROP TEMPORARY TABLE IF EXISTS v44_source_members;
CREATE TEMPORARY TABLE v44_source_members AS
SELECT
  p.house_id,
  p.batch_id,
  p.mother_rabbit_id,
  MAX(c.id) AS kept_cycle_id,
  SUBSTRING_INDEX(GROUP_CONCAT(c.stage ORDER BY c.id DESC), ',', 1) AS kept_stage,
  SUM(COALESCE(c.current_nursing_kits, 0)) AS current_nursing_kits,
  SUM(CASE WHEN c.stage = 'AWAIT_WEANING' THEN 1 ELSE 0 END) AS nursing_litter_count
FROM v44_source_pairs p
JOIN breeding_cycles c
  ON c.house_id = p.house_id
 AND c.batch_id = p.batch_id
 AND c.mother_rabbit_id = p.mother_rabbit_id
 AND c.lifecycle = 'OPEN'
GROUP BY p.house_id, p.batch_id, p.mother_rabbit_id;

ALTER TABLE v44_source_members ADD PRIMARY KEY (batch_id, mother_rabbit_id);

UPDATE batch_rabbits br
JOIN v44_source_members m
  ON m.batch_id = br.batch_id
 AND m.mother_rabbit_id = br.rabbit_id
SET br.latest_cycle_id = m.kept_cycle_id,
    br.current_nursing_kits = m.current_nursing_kits,
    br.nursing_litter_count = m.nursing_litter_count,
    br.current_status = CASE m.kept_stage
      WHEN 'AWAIT_ESTRUS' THEN '待催情'
      WHEN 'AWAIT_MATING' THEN '待配种'
      WHEN 'AWAIT_PALPATION' THEN '待摸胎'
      WHEN 'AWAIT_PREPARTUM' THEN '待备产'
      WHEN 'AWAIT_DELIVERY' THEN '待分娩'
      WHEN 'AWAIT_WEANING' THEN '待分笼'
      ELSE br.current_status
    END,
    br.update_by = 'v44',
    br.update_time = NOW()
WHERE br.batch_role = 'breeding'
  AND br.is_active = TRUE;

-- 成员关系由生产周期派生，所以周期搬进恢复批次后必须补出对应的成员行，
-- 否则状态机的 lockOpenCycleWithInvariant 会因为「成员关系不存在」把这条周期
-- 上的每一次操作都拒掉，母兔从此卡死。
DROP TEMPORARY TABLE IF EXISTS v44_target_members;
CREATE TEMPORARY TABLE v44_target_members AS
SELECT
  r.house_id,
  r.target_batch_id AS batch_id,
  r.mother_rabbit_id,
  MAX(r.cycle_id) AS latest_cycle_id,
  MIN(r.stage_entered_at) AS join_date,
  SUBSTRING_INDEX(GROUP_CONCAT(r.stage ORDER BY r.cycle_id DESC), ',', 1) AS latest_stage
FROM v44_relocations r
GROUP BY r.house_id, r.target_batch_id, r.mother_rabbit_id;

ALTER TABLE v44_target_members ADD PRIMARY KEY (batch_id, mother_rabbit_id);

DROP TEMPORARY TABLE IF EXISTS v44_missing_members;
CREATE TEMPORARY TABLE v44_missing_members AS
SELECT t.*
FROM v44_target_members t
WHERE NOT EXISTS (
  SELECT 1
  FROM batch_rabbits br
  WHERE br.batch_id = t.batch_id
    AND br.rabbit_id = t.mother_rabbit_id
    AND br.batch_role = 'breeding'
    AND br.is_active = TRUE
);

ALTER TABLE v44_missing_members ADD PRIMARY KEY (batch_id, mother_rabbit_id);

INSERT INTO batch_rabbits (
  batch_id, rabbit_id, latest_cycle_id, join_reason, batch_role, current_status,
  is_active, join_date, remark, create_by, create_time, update_by, update_time
)
SELECT
  missing.batch_id,
  missing.mother_rabbit_id,
  missing.latest_cycle_id,
  'V44拆分',
  'breeding',
  CASE missing.latest_stage
    WHEN 'AWAIT_ESTRUS' THEN '待催情'
    WHEN 'AWAIT_MATING' THEN '待配种'
    WHEN 'AWAIT_PALPATION' THEN '待摸胎'
    WHEN 'AWAIT_PREPARTUM' THEN '待备产'
    WHEN 'AWAIT_DELIVERY' THEN '待分娩'
    WHEN 'AWAIT_WEANING' THEN '待分笼'
    ELSE '待催情'
  END,
  TRUE,
  missing.join_date,
  'V44 并行周期批次拆分',
  'v44', NOW(), 'v44', NOW()
FROM v44_missing_members missing;

INSERT INTO rabbit_status_history (
  house_id, rabbit_id, batch_id, from_status, to_status, change_time,
  reason, create_by, create_time, update_by, update_time
)
SELECT
  missing.house_id,
  missing.mother_rabbit_id,
  missing.batch_id,
  NULL,
  CASE missing.latest_stage
    WHEN 'AWAIT_ESTRUS' THEN '待催情'
    WHEN 'AWAIT_MATING' THEN '待配种'
    WHEN 'AWAIT_PALPATION' THEN '待摸胎'
    WHEN 'AWAIT_PREPARTUM' THEN '待备产'
    WHEN 'AWAIT_DELIVERY' THEN '待分娩'
    WHEN 'AWAIT_WEANING' THEN '待分笼'
    ELSE '待催情'
  END,
  missing.join_date,
  'V44拆分并行生产周期到独立批次',
  'v44', NOW(), 'v44', NOW()
FROM v44_missing_members missing;

-- V28 曾把这一列当作「没有约束依赖的诊断列」删掉。现在它重新承担约束，
-- 定义与 V26 逐字一致，避免两处判据漂移。
ALTER TABLE breeding_cycles
  ADD COLUMN batch_member_guard VARCHAR(64) GENERATED ALWAYS AS (
    CASE WHEN lifecycle = 'OPEN' AND batch_id IS NOT NULL
         THEN CONCAT(batch_id, ':', mother_rabbit_id) END) STORED
  COMMENT 'OPEN 周期的 批次:母兔 唯一性守卫；已结束或无批次的周期为 NULL 不参与去重';

-- 收敛没做干净时这里会直接失败并回滚，好过带着重复行上线：应用层的
-- assertBatchCycleFree 只挡新写入，挡不住已经存在的重复。
ALTER TABLE breeding_cycles
  ADD UNIQUE KEY uk_bc_batch_member (house_id, batch_member_guard);

DROP TEMPORARY TABLE IF EXISTS v44_missing_members;
DROP TEMPORARY TABLE IF EXISTS v44_target_members;
DROP TEMPORARY TABLE IF EXISTS v44_source_members;
DROP TEMPORARY TABLE IF EXISTS v44_source_pairs;
DROP TEMPORARY TABLE IF EXISTS v44_relocations;
DROP TEMPORARY TABLE IF EXISTS v44_surplus_cycles;
