-- Every OPEN reproduction cycle belongs to one in-progress batch and has an active
-- breeding membership for its mother. Closed historical cycles may remain unbound.

DROP TEMPORARY TABLE IF EXISTS v42_cycle_binding;
CREATE TEMPORARY TABLE v42_cycle_binding (
  cycle_id BIGINT PRIMARY KEY,
  house_id BIGINT NOT NULL,
  mother_rabbit_id BIGINT NOT NULL,
  batch_id BIGINT NULL
) ENGINE=InnoDB;

-- Keep a valid in-progress batch. Otherwise prefer the mother's most recently
-- joined active breeding membership in an in-progress batch in the same house.
INSERT INTO v42_cycle_binding (cycle_id, house_id, mother_rabbit_id, batch_id)
SELECT
  c.id,
  c.house_id,
  c.mother_rabbit_id,
  COALESCE(
    CASE
      WHEN current_batch.id IS NOT NULL
        AND current_batch.house_id = c.house_id
        AND current_batch.status = '进行中'
      THEN c.batch_id
      ELSE NULL
    END,
    (
      SELECT br.batch_id
      FROM batch_rabbits br
      JOIN batches b ON b.id = br.batch_id
      WHERE b.house_id = c.house_id
        AND b.status = '进行中'
        AND br.rabbit_id = c.mother_rabbit_id
        AND br.batch_role = 'breeding'
        AND br.is_active = TRUE
      ORDER BY COALESCE(br.join_date, br.create_time) DESC, br.id DESC
      LIMIT 1
    )
  )
FROM breeding_cycles c
LEFT JOIN batches current_batch ON current_batch.id = c.batch_id
WHERE c.lifecycle = 'OPEN'
  AND (
    c.batch_id IS NULL
    OR current_batch.id IS NULL
    OR current_batch.house_id <> c.house_id
    OR current_batch.status <> '进行中'
  );

-- Houses with no usable membership receive one visible recovery batch. The
-- request id and code are deterministic so the recovery intent is auditable.
INSERT INTO batches (
  house_id, batch_code, status, start_date, request_id, remark,
  create_by, create_time, update_by, update_time
)
SELECT
  binding.house_id,
  CONCAT('V42-RECOVERY-H', binding.house_id),
  '进行中',
  MIN(c.stage_entered_at),
  CONCAT('v42-recovery-house-', binding.house_id),
  'V42 open-cycle batch recovery',
  'v42', NOW(), 'v42', NOW()
FROM v42_cycle_binding binding
JOIN breeding_cycles c ON c.id = binding.cycle_id
WHERE binding.batch_id IS NULL
GROUP BY binding.house_id
ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);

UPDATE v42_cycle_binding binding
JOIN batches b
  ON b.house_id = binding.house_id
 AND b.request_id = CONCAT('v42-recovery-house-', binding.house_id)
SET binding.batch_id = b.id
WHERE binding.batch_id IS NULL;

-- Null-scoped cycle numbers can collide with numbers already used in the target
-- batch. Allocate each rebound cycle after the current per-batch maximum.
DROP TEMPORARY TABLE IF EXISTS v42_numbered_cycles;
CREATE TEMPORARY TABLE v42_numbered_cycles AS
SELECT
  binding.cycle_id,
  binding.house_id,
  binding.mother_rabbit_id,
  binding.batch_id,
  COALESCE(existing.max_cycle_no, 0)
    + ROW_NUMBER() OVER (
        PARTITION BY binding.house_id, binding.batch_id, binding.mother_rabbit_id
        ORDER BY binding.cycle_id
      ) AS cycle_no
FROM v42_cycle_binding binding
LEFT JOIN (
  SELECT house_id, batch_id, mother_rabbit_id, MAX(cycle_no) AS max_cycle_no
  FROM breeding_cycles
  WHERE batch_id IS NOT NULL
  GROUP BY house_id, batch_id, mother_rabbit_id
) existing
  ON existing.house_id = binding.house_id
 AND existing.batch_id = binding.batch_id
 AND existing.mother_rabbit_id = binding.mother_rabbit_id;

ALTER TABLE v42_numbered_cycles ADD PRIMARY KEY (cycle_id);

UPDATE breeding_cycles c
JOIN v42_numbered_cycles normalized ON normalized.cycle_id = c.id
SET c.batch_id = normalized.batch_id,
    c.cycle_no = normalized.cycle_no,
    c.update_by = 'v42',
    c.update_time = NOW();

-- Rebuild the active member projection from every OPEN cycle in a valid batch,
-- including cycles whose batch binding was already correct before this migration.
DROP TEMPORARY TABLE IF EXISTS v42_member_state;
CREATE TEMPORARY TABLE v42_member_state AS
SELECT
  c.house_id,
  c.batch_id,
  c.mother_rabbit_id,
  MAX(c.id) AS latest_cycle_id,
  MIN(c.stage_entered_at) AS join_date,
  SUBSTRING_INDEX(GROUP_CONCAT(c.stage ORDER BY c.id DESC), ',', 1) AS latest_stage,
  SUM(COALESCE(c.current_nursing_kits, 0)) AS current_nursing_kits,
  SUM(CASE WHEN c.stage = 'AWAIT_WEANING' THEN 1 ELSE 0 END) AS nursing_litter_count
FROM breeding_cycles c
JOIN batches b
  ON b.id = c.batch_id
 AND b.house_id = c.house_id
 AND b.status = '进行中'
WHERE c.lifecycle = 'OPEN'
GROUP BY c.house_id, c.batch_id, c.mother_rabbit_id;

ALTER TABLE v42_member_state
  ADD PRIMARY KEY (batch_id, mother_rabbit_id);

UPDATE batch_rabbits br
JOIN v42_member_state state
  ON state.batch_id = br.batch_id
 AND state.mother_rabbit_id = br.rabbit_id
SET br.latest_cycle_id = state.latest_cycle_id,
    br.current_nursing_kits = state.current_nursing_kits,
    br.nursing_litter_count = state.nursing_litter_count,
    br.current_status = CASE state.latest_stage
      WHEN 'AWAIT_ESTRUS' THEN '待催情'
      WHEN 'AWAIT_MATING' THEN '待配种'
      WHEN 'AWAIT_PALPATION' THEN '待摸胎'
      WHEN 'AWAIT_PREPARTUM' THEN '待备产'
      WHEN 'AWAIT_DELIVERY' THEN '待分娩'
      WHEN 'AWAIT_WEANING' THEN '待分笼'
      ELSE '待催情'
    END,
    br.update_by = 'v42',
    br.update_time = NOW()
WHERE br.batch_role = 'breeding'
  AND br.is_active = TRUE;

-- Capture missing memberships before inserting them so history is written only
-- for links created by this migration.
DROP TEMPORARY TABLE IF EXISTS v42_missing_members;
CREATE TEMPORARY TABLE v42_missing_members AS
SELECT state.*
FROM v42_member_state state
WHERE NOT EXISTS (
  SELECT 1
  FROM batch_rabbits br
  WHERE br.batch_id = state.batch_id
    AND br.rabbit_id = state.mother_rabbit_id
    AND br.batch_role = 'breeding'
    AND br.is_active = TRUE
);

ALTER TABLE v42_missing_members
  ADD PRIMARY KEY (batch_id, mother_rabbit_id);

INSERT INTO batch_rabbits (
  batch_id, rabbit_id, latest_cycle_id, current_nursing_kits,
  nursing_litter_count, join_reason, batch_role, current_status,
  is_active, join_date, remark, create_by, create_time, update_by, update_time
)
SELECT
  missing.batch_id,
  missing.mother_rabbit_id,
  missing.latest_cycle_id,
  missing.current_nursing_kits,
  missing.nursing_litter_count,
  'V42恢复',
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
  'V42 open-cycle membership recovery',
  'v42', NOW(), 'v42', NOW()
FROM v42_missing_members missing;

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
  'V42恢复进行中生产周期的批次成员关系',
  'v42', NOW(), 'v42', NOW()
FROM v42_missing_members missing;

UPDATE work_tasks task
JOIN v42_numbered_cycles normalized
  ON normalized.house_id = task.house_id
 AND normalized.cycle_id = task.cycle_id
SET task.batch_id = normalized.batch_id,
    task.update_by = 'v42',
    task.update_time = NOW()
WHERE task.status = 'PENDING';

UPDATE repro_events event
JOIN v42_numbered_cycles normalized
  ON normalized.house_id = event.house_id
 AND normalized.cycle_id = event.cycle_id
SET event.batch_id = normalized.batch_id;

UPDATE litters litter
JOIN v42_numbered_cycles normalized
  ON normalized.house_id = litter.house_id
 AND normalized.cycle_id = litter.cycle_id
SET litter.batch_id = normalized.batch_id,
    litter.update_by = 'v42',
    litter.update_time = NOW();

ALTER TABLE breeding_cycles
  ADD CONSTRAINT ck_bc_open_batch
  CHECK (lifecycle <> 'OPEN' OR batch_id IS NOT NULL);

DROP TEMPORARY TABLE IF EXISTS v42_missing_members;
DROP TEMPORARY TABLE IF EXISTS v42_member_state;
DROP TEMPORARY TABLE IF EXISTS v42_numbered_cycles;
DROP TEMPORARY TABLE IF EXISTS v42_cycle_binding;
