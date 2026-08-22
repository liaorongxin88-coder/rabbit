-- 完成商品兔出售、后备成熟任务中心迁移，并补齐商品兔三阶段设置。

ALTER TABLE global_setting
  ADD COLUMN adaptation_days INT NOT NULL DEFAULT 3 AFTER postpartum_days,
  ADD COLUMN growing_days INT NOT NULL DEFAULT 18 AFTER adaptation_days,
  ADD COLUMN fattening_days INT NOT NULL DEFAULT 12 AFTER growing_days;

-- 旧默认值跟随本轮确认后的业务口径更新；非默认自定义值保持不动。
UPDATE global_setting SET prepartum_days = 15 WHERE prepartum_days = 3;
UPDATE global_setting SET weaning_days = 30 WHERE weaning_days = 25;
UPDATE global_setting SET replacement_days = 90 WHERE replacement_days = 45;
UPDATE global_setting
SET sale_days = adaptation_days + growing_days + fattening_days;

ALTER TABLE rabbits
  ADD COLUMN growth_stage_entered_at DATETIME NULL AFTER growth_stage;

UPDATE rabbits
SET growth_stage_entered_at = COALESCE(arrival_date, create_time)
WHERE type = '2'
  AND growth_stage IS NOT NULL
  AND growth_stage_entered_at IS NULL;

ALTER TABLE replacement_records
  ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING' AFTER mature_notify_date,
  ADD COLUMN promoted_at DATETIME NULL AFTER status,
  ADD KEY idx_rr_house_status_due (house_id, status, expected_mature_date, id);

UPDATE replacement_records rr
INNER JOIN rabbits r ON r.id = rr.rabbit_id AND r.house_id = rr.house_id
SET rr.status = CASE
      WHEN r.is_active = TRUE AND r.type = '1' THEN 'PENDING'
      ELSE 'PROMOTED'
    END,
    rr.promoted_at = CASE
      WHEN r.is_active = TRUE AND r.type = '1' THEN NULL
      ELSE COALESCE(rr.update_time, NOW())
    END;

-- 商品兔成熟任务：无论是否带批次标签，兔只本身都必须能收到出售提醒。
INSERT INTO work_tasks (
  house_id, task_type, subject_type, subject_id, cycle_id, rabbit_id,
  batch_id, cage_id, due_date, due_time, status, snooze_count,
  dedup_key, remark, create_by, create_time, update_by, update_time
)
SELECT
  r.house_id,
  'SALE_READY',
  'RABBIT',
  r.id,
  NULL,
  r.id,
  (
    SELECT br.batch_id
    FROM batch_rabbits br
    INNER JOIN batches b ON b.id = br.batch_id AND b.house_id = r.house_id
    WHERE br.rabbit_id = r.id
      AND br.batch_role = 'fattening'
      AND br.is_active = TRUE
    ORDER BY br.id DESC
    LIMIT 1
  ),
  r.cage_id,
  DATE(DATE_ADD(
    COALESCE(r.growth_stage_entered_at, r.arrival_date, r.create_time),
    INTERVAL COALESCE(gs.adaptation_days + gs.growing_days + gs.fattening_days, 33) DAY
  )),
  DATE_ADD(
    COALESCE(r.growth_stage_entered_at, r.arrival_date, r.create_time),
    INTERVAL COALESCE(gs.adaptation_days + gs.growing_days + gs.fattening_days, 33) DAY
  ),
  'PENDING',
  0,
  CONCAT('rabbit:', r.id, ':SALE_READY'),
  '商品兔成熟后可进入出售流程',
  'v34', NOW(), 'v34', NOW()
FROM rabbits r
LEFT JOIN global_setting gs ON gs.house_id = r.house_id
WHERE r.is_active = TRUE
  AND r.type = '2'
ON DUPLICATE KEY UPDATE dedup_key = VALUES(dedup_key);

-- 后备兔成熟任务：使用留种记录上的明确日期，不从兔只录入日期反推。
INSERT INTO work_tasks (
  house_id, task_type, subject_type, subject_id, cycle_id, rabbit_id,
  batch_id, cage_id, due_date, due_time, status, snooze_count,
  dedup_key, remark, create_by, create_time, update_by, update_time
)
SELECT
  rr.house_id,
  'REPLACEMENT_MATURE',
  'RABBIT',
  rr.rabbit_id,
  NULL,
  rr.rabbit_id,
  NULL,
  r.cage_id,
  DATE(rr.expected_mature_date),
  rr.expected_mature_date,
  'PENDING',
  0,
  CONCAT('rabbit:', rr.rabbit_id, ':REPLACEMENT_MATURE'),
  '后备兔成熟后可转为种兔',
  'v34', NOW(), 'v34', NOW()
FROM replacement_records rr
INNER JOIN rabbits r
  ON r.id = rr.rabbit_id
 AND r.house_id = rr.house_id
 AND r.is_active = TRUE
 AND r.type = '1'
WHERE rr.status = 'PENDING'
  AND rr.id = (
    SELECT MAX(rr2.id)
    FROM replacement_records rr2
    WHERE rr2.house_id = rr.house_id
      AND rr2.rabbit_id = rr.rabbit_id
      AND rr2.status = 'PENDING'
  )
ON DUPLICATE KEY UPDATE dedup_key = VALUES(dedup_key);
