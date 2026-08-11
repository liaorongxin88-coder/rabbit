-- Consolidate the former startup-time DbSchemaMigrator into Flyway.
-- Every operation is idempotent so databases previously repaired at startup
-- and databases created only from migrations converge on the same schema.

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'openid';
SET @sql = IF(@cnt = 0, 'alter table sys_user add column openid varchar(128) null after password', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'rabbit_houses' AND column_name = 'is_deleted';
SET @sql = IF(@cnt = 0, 'alter table rabbit_houses add column is_deleted boolean not null default false', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'cages' AND column_name = 'is_enabled';
SET @sql = IF(@cnt = 0, 'alter table cages add column is_enabled boolean not null default true', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'pregnancy_check_records' AND column_name = 'house_id';
SET @sql = IF(@cnt = 0, 'alter table pregnancy_check_records add column house_id bigint null after id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'parturition_records' AND column_name = 'house_id';
SET @sql = IF(@cnt = 0, 'alter table parturition_records add column house_id bigint null after id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'prepartum_records' AND column_name = 'house_id';
SET @sql = IF(@cnt = 0, 'alter table prepartum_records add column house_id bigint null after id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'weaning_records' AND column_name = 'house_id';
SET @sql = IF(@cnt = 0, 'alter table weaning_records add column house_id bigint null after id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'rabbit_status_history' AND column_name = 'house_id';
SET @sql = IF(@cnt = 0, 'alter table rabbit_status_history add column house_id bigint null after id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'weaning_records' AND column_name = 'target_cage_id';
SET @sql = IF(@cnt = 0, 'alter table weaning_records add column target_cage_id bigint', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'weaning_records' AND column_name = 'in_cage_id';
SET @sql = IF(@cnt = 0, 'alter table weaning_records add column in_cage_id bigint', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'batch_rabbits' AND column_name = 'is_event_notified';
SET @sql = IF(@cnt = 0, 'alter table batch_rabbits add column is_event_notified boolean not null default false', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'batch_rabbits' AND column_name = 'event_notify_date';
SET @sql = IF(@cnt = 0, 'alter table batch_rabbits add column event_notify_date datetime', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'global_setting' AND column_name = 'user_id';
SET @sql = IF(@cnt = 0, 'alter table global_setting add column user_id bigint null after house_id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS feed_log_rabbits (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  feed_log_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  cage_id BIGINT,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_flr (feed_log_id, rabbit_id),
  KEY idx_flr_house_cage (house_id, cage_id, feed_log_id),
  KEY idx_flr_rabbit (rabbit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trace_id VARCHAR(64),
  user_id BIGINT,
  house_id BIGINT,
  method VARCHAR(10),
  path VARCHAR(255),
  query_string VARCHAR(1000),
  status INT,
  api_code INT,
  api_message VARCHAR(255),
  cost_ms BIGINT,
  error_message VARCHAR(500),
  ip VARCHAR(64),
  user_agent VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_house_time (house_id, create_time),
  KEY idx_audit_user_time (user_id, create_time),
  KEY idx_audit_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_reminder_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  category VARCHAR(32) NOT NULL,
  record_id BIGINT NOT NULL,
  event_date DATETIME,
  notify_date DATE NOT NULL,
  notify_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_erl_house_cat_record_date (house_id, category, record_id, notify_date),
  KEY idx_erl_house_date_id (house_id, notify_date, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS weaning_record_allocations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  weaning_record_id BIGINT NOT NULL,
  cage_id BIGINT NOT NULL,
  alloc_count INT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wra_record_cage (weaning_record_id, cage_id),
  KEY idx_wra_record (weaning_record_id),
  KEY idx_wra_cage (cage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'audit_logs' AND column_name = 'api_code';
SET @sql = IF(@cnt = 0, 'alter table audit_logs add column api_code int', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'audit_logs' AND column_name = 'api_message';
SET @sql = IF(@cnt = 0, 'alter table audit_logs add column api_message varchar(255)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE sys_user
SET openid = SUBSTRING(user_name, 4)
WHERE (openid IS NULL OR openid = '') AND user_name LIKE 'wx!_%' ESCAPE '!';

UPDATE pregnancy_check_records p
JOIN batches b ON b.id = p.batch_id
SET p.house_id = b.house_id
WHERE p.house_id IS NULL;

UPDATE parturition_records p
JOIN batches b ON b.id = p.batch_id
SET p.house_id = b.house_id
WHERE p.house_id IS NULL;

UPDATE prepartum_records p
JOIN batches b ON b.id = p.batch_id
SET p.house_id = b.house_id
WHERE p.house_id IS NULL;

UPDATE weaning_records w
JOIN batches b ON b.id = w.batch_id
SET w.house_id = b.house_id
WHERE w.house_id IS NULL;

UPDATE rabbit_status_history h
JOIN batches b ON b.id = h.batch_id
SET h.house_id = b.house_id
WHERE h.house_id IS NULL AND h.batch_id IS NOT NULL;

UPDATE rabbit_status_history h
JOIN rabbits r ON r.id = h.rabbit_id
SET h.house_id = r.house_id
WHERE h.house_id IS NULL;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'global_setting'
  AND column_name = 'house_id' AND is_nullable = 'NO';
SET @sql = IF(@cnt > 0, 'alter table global_setting modify column house_id bigint null', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO global_setting (
  house_id, user_id, aphrodisiac_days, palpation_days, prepartum_days, weaning_days,
  postpartum_days, sale_days, replacement_days, remark, create_by, update_by
)
SELECT NULL, picked.user_id, picked.aphrodisiac_days, picked.palpation_days, picked.prepartum_days,
       picked.weaning_days, picked.postpartum_days, picked.sale_days, picked.replacement_days,
       picked.remark, 'migration', 'migration'
FROM (
  SELECT hu.user_id, gs.aphrodisiac_days, gs.palpation_days, gs.prepartum_days,
         gs.weaning_days, gs.postpartum_days, gs.sale_days, gs.replacement_days, gs.remark
  FROM house_users hu
  JOIN rabbit_houses h ON h.id = hu.house_id AND h.is_deleted = FALSE
  JOIN global_setting gs ON gs.house_id = h.id
  JOIN (
    SELECT hu2.user_id, MIN(h2.id) AS house_id
    FROM house_users hu2
    JOIN rabbit_houses h2 ON h2.id = hu2.house_id AND h2.is_deleted = FALSE
    JOIN global_setting gs2 ON gs2.house_id = h2.id
    GROUP BY hu2.user_id
  ) first_house ON first_house.user_id = hu.user_id AND first_house.house_id = h.id
) picked
WHERE NOT EXISTS (
  SELECT 1 FROM global_setting existing WHERE existing.user_id = picked.user_id
);

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_sys_user_openid';
SET @sql = IF(@cnt = 0, 'alter table sys_user add unique key uk_sys_user_openid (openid)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'pregnancy_check_records' AND index_name = 'idx_pcr_house_batch';
SET @sql = IF(@cnt = 0, 'alter table pregnancy_check_records add index idx_pcr_house_batch (house_id, batch_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'pregnancy_check_records' AND index_name = 'idx_pcr_house_rabbit';
SET @sql = IF(@cnt = 0, 'alter table pregnancy_check_records add index idx_pcr_house_rabbit (house_id, rabbit_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'parturition_records' AND index_name = 'idx_pr_house_batch';
SET @sql = IF(@cnt = 0, 'alter table parturition_records add index idx_pr_house_batch (house_id, batch_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'parturition_records' AND index_name = 'idx_pr_house_rabbit';
SET @sql = IF(@cnt = 0, 'alter table parturition_records add index idx_pr_house_rabbit (house_id, rabbit_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'prepartum_records' AND index_name = 'idx_ppr_house_batch';
SET @sql = IF(@cnt = 0, 'alter table prepartum_records add index idx_ppr_house_batch (house_id, batch_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'prepartum_records' AND index_name = 'idx_ppr_house_rabbit';
SET @sql = IF(@cnt = 0, 'alter table prepartum_records add index idx_ppr_house_rabbit (house_id, rabbit_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'weaning_records' AND index_name = 'idx_wr_house_batch';
SET @sql = IF(@cnt = 0, 'alter table weaning_records add index idx_wr_house_batch (house_id, batch_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'weaning_records' AND index_name = 'idx_wr_house_rabbit';
SET @sql = IF(@cnt = 0, 'alter table weaning_records add index idx_wr_house_rabbit (house_id, rabbit_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'rabbit_status_history' AND index_name = 'idx_rsh_house_rabbit_time';
SET @sql = IF(@cnt = 0, 'alter table rabbit_status_history add index idx_rsh_house_rabbit_time (house_id, rabbit_id, change_time, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'rabbit_status_history' AND index_name = 'idx_rsh_house_batch_time';
SET @sql = IF(@cnt = 0, 'alter table rabbit_status_history add index idx_rsh_house_batch_time (house_id, batch_id, change_time, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'global_setting' AND index_name = 'uk_setting_user';
SET @sql = IF(@cnt = 0, 'alter table global_setting add unique key uk_setting_user (user_id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
