SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'parturition_records'
  AND index_name = 'idx_pr_house_birth_id';
SET @sql = IF(@cnt = 0, 'alter table parturition_records add index idx_pr_house_birth_id (house_id, birth_date, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'audit_logs'
  AND index_name = 'idx_audit_house_id';
SET @sql = IF(@cnt = 0, 'alter table audit_logs add index idx_audit_house_id (house_id, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'weaning_records'
  AND index_name = 'idx_wr_house_weaning_id';
SET @sql = IF(@cnt = 0, 'alter table weaning_records add index idx_wr_house_weaning_id (house_id, weaning_date, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
