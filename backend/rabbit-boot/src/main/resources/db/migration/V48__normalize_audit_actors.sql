-- create_by/update_by use the stable sys_user.user_id string. Keep an operator name snapshot
-- on records that represent a user-initiated animal or production operation.
ALTER TABLE rabbits ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE cages ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE batches ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE batch_rabbits ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE breeding_cycles ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE litters ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE work_tasks ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE weight_logs ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE treatment_records ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE vaccination_records ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE feed_logs ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE inventory_txs ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE sale_orders ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE sale_order_items ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE rabbit_departure_records ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE rabbit_status_history ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE rabbit_abnormal_conditions ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;
ALTER TABLE replacement_records ADD COLUMN operator_name VARCHAR(64) NULL AFTER update_by;

-- Preserve a readable historical actor before converting names to stable user IDs.
UPDATE rabbits t
LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name
LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR)
SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE cages t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE batches t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE batch_rabbits t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE breeding_cycles t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE litters t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE work_tasks t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE weight_logs t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE treatment_records t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE vaccination_records t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE feed_logs t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE inventory_txs t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE sale_orders t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE sale_order_items t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE rabbit_departure_records t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE rabbit_status_history t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE rabbit_abnormal_conditions t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);
UPDATE replacement_records t LEFT JOIN sys_user by_name ON t.create_by = by_name.user_name LEFT JOIN sys_user by_id ON t.create_by = CAST(by_id.user_id AS CHAR) SET t.operator_name = COALESCE(t.operator_name, by_name.user_name, by_id.user_name);

DELIMITER //
CREATE PROCEDURE normalize_audit_actor_ids()
BEGIN
  DECLARE done BOOLEAN DEFAULT FALSE;
  DECLARE audit_table VARCHAR(64);
  DECLARE audit_tables CURSOR FOR
    SELECT c.table_name
    FROM information_schema.columns c
    JOIN information_schema.columns u
      ON u.table_schema = c.table_schema
     AND u.table_name = c.table_name
     AND u.column_name = 'update_by'
    WHERE c.table_schema = DATABASE()
      AND c.column_name = 'create_by';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

  OPEN audit_tables;
  read_loop: LOOP
    FETCH audit_tables INTO audit_table;
    IF done THEN
      LEAVE read_loop;
    END IF;
    SET @sql = CONCAT(
      'UPDATE `', audit_table, '` t JOIN sys_user u ON t.create_by COLLATE utf8mb4_unicode_ci = u.user_name COLLATE utf8mb4_unicode_ci ',
      'SET t.create_by = CAST(u.user_id AS CHAR)'
    );
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
    SET @sql = CONCAT(
      'UPDATE `', audit_table, '` t JOIN sys_user u ON t.update_by COLLATE utf8mb4_unicode_ci = u.user_name COLLATE utf8mb4_unicode_ci ',
      'SET t.update_by = CAST(u.user_id AS CHAR)'
    );
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE audit_tables;
END//
DELIMITER ;

CALL normalize_audit_actor_ids();
DROP PROCEDURE normalize_audit_actor_ids;
