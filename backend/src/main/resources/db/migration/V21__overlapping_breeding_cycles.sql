CREATE TABLE breeding_cycles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  mother_rabbit_id BIGINT NOT NULL,
  male_rabbit_id BIGINT,
  cycle_no INT NOT NULL,
  status VARCHAR(30) NOT NULL,
  mating_date DATETIME,
  pregnancy_check_date DATETIME,
  pregnancy_result VARCHAR(10),
  expected_birth_date DATETIME,
  birth_date DATETIME,
  total_kits INT NOT NULL DEFAULT 0,
  live_kits INT NOT NULL DEFAULT 0,
  foster_in_kits INT NOT NULL DEFAULT 0,
  foster_out_kits INT NOT NULL DEFAULT 0,
  current_nursing_kits INT NOT NULL DEFAULT 0,
  weaned_kits INT NOT NULL DEFAULT 0,
  preweaning_loss_kits INT NOT NULL DEFAULT 0,
  weaning_date DATETIME,
  avg_weaning_weight DOUBLE,
  postpartum_remating_days INT,
  lactation_days INT,
  overlap_litter_cycle_no INT,
  overlap_start_date DATETIME,
  overlap_end_date DATETIME,
  overlap_days INT NOT NULL DEFAULT 0,
  next_event_date DATETIME,
  next_event_type VARCHAR(30),
  is_event_notified BOOLEAN NOT NULL DEFAULT FALSE,
  event_notify_date DATETIME,
  closed_at DATETIME,
  close_reason VARCHAR(100),
  request_id VARCHAR(64),
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_bc_mother_cycle (house_id, mother_rabbit_id, cycle_no),
  UNIQUE KEY uk_bc_request (house_id, request_id),
  KEY idx_bc_batch_mother (house_id, batch_id, mother_rabbit_id, cycle_no),
  KEY idx_bc_status (house_id, status, closed_at),
  KEY idx_bc_next_event (house_id, next_event_date, is_event_notified),
  CONSTRAINT fk_bc_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_bc_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
  CONSTRAINT fk_bc_mother FOREIGN KEY (mother_rabbit_id) REFERENCES rabbits (id),
  CONSTRAINT fk_bc_male FOREIGN KEY (male_rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE batch_rabbits
  ADD COLUMN latest_cycle_id BIGINT,
  ADD COLUMN current_nursing_kits INT NOT NULL DEFAULT 0,
  ADD COLUMN nursing_litter_count INT NOT NULL DEFAULT 0,
  ADD KEY idx_br_latest_cycle (latest_cycle_id);

ALTER TABLE pregnancy_check_records
  ADD COLUMN breeding_cycle_id BIGINT,
  ADD KEY idx_pcr_cycle (breeding_cycle_id);

ALTER TABLE prepartum_records
  ADD COLUMN breeding_cycle_id BIGINT,
  ADD KEY idx_ppr_cycle (breeding_cycle_id);

ALTER TABLE parturition_records
  ADD COLUMN breeding_cycle_id BIGINT,
  ADD KEY idx_pr_cycle (breeding_cycle_id);

ALTER TABLE weaning_records
  ADD COLUMN breeding_cycle_id BIGINT,
  ADD KEY idx_wr_cycle (breeding_cycle_id);

ALTER TABLE rabbits
  ADD COLUMN father_id BIGINT,
  ADD COLUMN birth_batch_id BIGINT,
  ADD COLUMN birth_cycle_id BIGINT,
  ADD KEY idx_rabbits_father (father_id),
  ADD KEY idx_rabbits_birth_cycle (birth_cycle_id);

-- Only active reproductive state is backfilled. Historical detail remains
-- nullable because the legacy model overwrote mating and sire information.
INSERT INTO breeding_cycles (
  house_id, batch_id, mother_rabbit_id, male_rabbit_id, cycle_no, status,
  mating_date, expected_birth_date, birth_date, total_kits, live_kits,
  current_nursing_kits, next_event_date, next_event_type, request_id,
  remark, create_by, update_by
)
SELECT
  b.house_id,
  br.batch_id,
  br.rabbit_id,
  br.male_rabbit_id,
  GREATEST(
    1,
    (SELECT COUNT(1)
       FROM parturition_records pr_count
      WHERE pr_count.house_id = b.house_id
        AND pr_count.rabbit_id = br.rabbit_id)
    + CASE WHEN br.current_status = '哺乳中' THEN 0 ELSE 1 END
  ),
  br.current_status,
  CASE
    WHEN br.current_status IN ('已配种', '不确定', '怀孕确认') THEN br.last_event_date
    ELSE NULL
  END,
  CASE
    WHEN br.current_status IN ('已配种', '不确定', '怀孕确认')
         AND br.last_event_date IS NOT NULL
      THEN DATE_ADD(br.last_event_date, INTERVAL 30 DAY)
    ELSE NULL
  END,
  CASE WHEN br.current_status = '哺乳中' THEN br.last_event_date ELSE NULL END,
  COALESCE((
    SELECT pr.total_kits
      FROM parturition_records pr
     WHERE pr.house_id = b.house_id
       AND pr.batch_id = br.batch_id
       AND pr.rabbit_id = br.rabbit_id
     ORDER BY pr.birth_date DESC, pr.id DESC
     LIMIT 1
  ), 0),
  COALESCE((
    SELECT pr.live_kits
      FROM parturition_records pr
     WHERE pr.house_id = b.house_id
       AND pr.batch_id = br.batch_id
       AND pr.rabbit_id = br.rabbit_id
     ORDER BY pr.birth_date DESC, pr.id DESC
     LIMIT 1
  ), 0),
  CASE WHEN br.current_status = '哺乳中' THEN COALESCE((
    SELECT pr.live_kits
      FROM parturition_records pr
     WHERE pr.house_id = b.house_id
       AND pr.batch_id = br.batch_id
       AND pr.rabbit_id = br.rabbit_id
     ORDER BY pr.birth_date DESC, pr.id DESC
     LIMIT 1
  ), 0) ELSE 0 END,
  br.next_event_date,
  br.next_event_type,
  CONCAT('legacy-active-', br.id),
  'V21 active-state backfill',
  'migration',
  'migration'
FROM batch_rabbits br
JOIN batches b ON b.id = br.batch_id
WHERE br.is_active = TRUE
  AND br.batch_role = 'breeding'
  AND br.current_status IN ('已配种', '不确定', '怀孕确认', '哺乳中');

UPDATE batch_rabbits br
JOIN breeding_cycles bc
  ON bc.request_id = CONCAT('legacy-active-', br.id)
SET br.latest_cycle_id = bc.id,
    br.current_nursing_kits = bc.current_nursing_kits,
    br.nursing_litter_count = CASE WHEN bc.status = '哺乳中' THEN 1 ELSE 0 END;

UPDATE parturition_records pr
JOIN breeding_cycles bc
  ON bc.house_id = pr.house_id
 AND bc.batch_id = pr.batch_id
 AND bc.mother_rabbit_id = pr.rabbit_id
 AND bc.birth_date = pr.birth_date
SET pr.breeding_cycle_id = bc.id
WHERE pr.breeding_cycle_id IS NULL;
