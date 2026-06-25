SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'sys_user'
  AND column_name = 'openid';
SET @sql = IF(@cnt = 0, 'alter table sys_user add column openid varchar(128) null after password', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE sys_user
SET openid = SUBSTRING(user_name, 4)
WHERE (openid IS NULL OR openid = '')
  AND user_name LIKE 'wx!_%' ESCAPE '!';

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'sys_user'
  AND index_name = 'uk_sys_user_openid';
SET @sql = IF(@cnt = 0, 'alter table sys_user add unique key uk_sys_user_openid (openid)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'pregnancy_check_records'
  AND column_name = 'house_id';
SET @sql = IF(@cnt = 0, 'alter table pregnancy_check_records add column house_id bigint null after id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'parturition_records'
  AND column_name = 'house_id';
SET @sql = IF(@cnt = 0, 'alter table parturition_records add column house_id bigint null after id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'prepartum_records'
  AND column_name = 'house_id';
SET @sql = IF(@cnt = 0, 'alter table prepartum_records add column house_id bigint null after id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'weaning_records'
  AND column_name = 'house_id';
SET @sql = IF(@cnt = 0, 'alter table weaning_records add column house_id bigint null after id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'rabbit_status_history'
  AND column_name = 'house_id';
SET @sql = IF(@cnt = 0, 'alter table rabbit_status_history add column house_id bigint null after id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
WHERE h.house_id IS NULL
  AND h.batch_id IS NOT NULL;

UPDATE rabbit_status_history h
JOIN rabbits r ON r.id = h.rabbit_id
SET h.house_id = r.house_id
WHERE h.house_id IS NULL;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'pregnancy_check_records'
  AND index_name = 'idx_pcr_house_batch';
SET @sql = IF(@cnt = 0, 'alter table pregnancy_check_records add index idx_pcr_house_batch (house_id, batch_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'pregnancy_check_records'
  AND index_name = 'idx_pcr_house_rabbit';
SET @sql = IF(@cnt = 0, 'alter table pregnancy_check_records add index idx_pcr_house_rabbit (house_id, rabbit_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'parturition_records'
  AND index_name = 'idx_pr_house_batch';
SET @sql = IF(@cnt = 0, 'alter table parturition_records add index idx_pr_house_batch (house_id, batch_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'parturition_records'
  AND index_name = 'idx_pr_house_rabbit';
SET @sql = IF(@cnt = 0, 'alter table parturition_records add index idx_pr_house_rabbit (house_id, rabbit_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'prepartum_records'
  AND index_name = 'idx_ppr_house_batch';
SET @sql = IF(@cnt = 0, 'alter table prepartum_records add index idx_ppr_house_batch (house_id, batch_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'prepartum_records'
  AND index_name = 'idx_ppr_house_rabbit';
SET @sql = IF(@cnt = 0, 'alter table prepartum_records add index idx_ppr_house_rabbit (house_id, rabbit_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'weaning_records'
  AND index_name = 'idx_wr_house_batch';
SET @sql = IF(@cnt = 0, 'alter table weaning_records add index idx_wr_house_batch (house_id, batch_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'weaning_records'
  AND index_name = 'idx_wr_house_rabbit';
SET @sql = IF(@cnt = 0, 'alter table weaning_records add index idx_wr_house_rabbit (house_id, rabbit_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'rabbit_status_history'
  AND index_name = 'idx_rsh_house_rabbit_time';
SET @sql = IF(@cnt = 0, 'alter table rabbit_status_history add index idx_rsh_house_rabbit_time (house_id, rabbit_id, change_time, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'rabbit_status_history'
  AND index_name = 'idx_rsh_house_batch_time';
SET @sql = IF(@cnt = 0, 'alter table rabbit_status_history add index idx_rsh_house_batch_time (house_id, batch_id, change_time, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
