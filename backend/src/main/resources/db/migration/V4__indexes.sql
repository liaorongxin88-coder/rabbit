SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'house_users'
  AND index_name = 'idx_house_users_user_house';
SET @sql = IF(@cnt = 0, 'alter table house_users add index idx_house_users_user_house (user_id, house_id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'feed_logs'
  AND index_name = 'idx_feed_logs_house_time_id';
SET @sql = IF(@cnt = 0, 'alter table feed_logs add index idx_feed_logs_house_time_id (house_id, feed_time, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'replacement_records'
  AND index_name = 'idx_rr_house_notified_expected_id';
SET @sql = IF(@cnt = 0, 'alter table replacement_records add index idx_rr_house_notified_expected_id (house_id, is_mature_notified, expected_mature_date, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'rabbit_abnormal_conditions'
  AND index_name = 'idx_rac_house_deal_time_id';
SET @sql = IF(@cnt = 0, 'alter table rabbit_abnormal_conditions add index idx_rac_house_deal_time_id (house_id, is_deal, warning_time, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'rabbit_status_history'
  AND index_name = 'idx_rsh_rabbit_time_id';
SET @sql = IF(@cnt = 0, 'alter table rabbit_status_history add index idx_rsh_rabbit_time_id (rabbit_id, change_time, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'batch_rabbits'
  AND index_name = 'idx_br_batch_rabbit_active_id';
SET @sql = IF(@cnt = 0, 'alter table batch_rabbits add index idx_br_batch_rabbit_active_id (batch_id, rabbit_id, is_active, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'batch_rabbits'
  AND index_name = 'idx_br_rabbit_active_id';
SET @sql = IF(@cnt = 0, 'alter table batch_rabbits add index idx_br_rabbit_active_id (rabbit_id, is_active, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'batch_rabbits'
  AND index_name = 'idx_br_batch_active_nextdate_id';
SET @sql = IF(@cnt = 0, 'alter table batch_rabbits add index idx_br_batch_active_nextdate_id (batch_id, is_active, next_event_date, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'cages'
  AND index_name = 'idx_cages_is_fed';
SET @sql = IF(@cnt = 0, 'alter table cages add index idx_cages_is_fed (is_fed)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'breeding_performance'
  AND index_name = 'idx_bp_house_updatetime_id';
SET @sql = IF(@cnt = 0, 'alter table breeding_performance add index idx_bp_house_updatetime_id (house_id, update_time, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'rabbits'
  AND index_name = 'idx_rabbits_house_active_type_id';
SET @sql = IF(@cnt = 0, 'alter table rabbits add index idx_rabbits_house_active_type_id (house_id, is_active, type, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'batches'
  AND index_name = 'uk_batches_house_code';
SET @sql = IF(@cnt = 0, 'alter table batches add unique key uk_batches_house_code (house_id, batch_code)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'rabbit_abnormal_conditions'
  AND column_name = 'is_deal';
SET @sql = IF(@cnt > 0, 'alter table rabbit_abnormal_conditions modify is_deal boolean not null default false', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'replacement_records'
  AND column_name = 'is_mature_notified';
SET @sql = IF(@cnt > 0, 'alter table replacement_records modify is_mature_notified boolean not null default false', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
