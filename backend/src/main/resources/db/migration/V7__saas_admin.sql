CREATE TABLE IF NOT EXISTS merchants (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  contact_name VARCHAR(64),
  contact_phone VARCHAR(32),
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_merchants_status_id (status, id),
  KEY idx_merchants_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO merchants (name, contact_name, contact_phone, status, remark, create_by, update_by)
SELECT '默认商户', '系统初始化', '', 'ENABLED', '迁移自动创建，用于承接历史兔舍数据', 'migration', 'migration'
WHERE NOT EXISTS (SELECT 1 FROM merchants);

CREATE TABLE IF NOT EXISTS platform_admins (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_name VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'SUPER_ADMIN',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  last_login_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_platform_admin_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_user (merchant_id, user_id),
  KEY idx_merchant_users_user (user_id, merchant_id),
  CONSTRAINT fk_merchant_users_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
  CONSTRAINT fk_merchant_users_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'rabbit_houses'
  AND column_name = 'merchant_id';
SET @sql = IF(@cnt = 0, 'alter table rabbit_houses add column merchant_id bigint null after id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @default_merchant_id = (SELECT id FROM merchants ORDER BY id ASC LIMIT 1);
UPDATE rabbit_houses
SET merchant_id = @default_merchant_id
WHERE merchant_id IS NULL;

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'rabbit_houses'
  AND index_name = 'idx_rabbit_houses_merchant';
SET @sql = IF(@cnt = 0, 'alter table rabbit_houses add index idx_rabbit_houses_merchant (merchant_id, is_deleted, id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.table_constraints
WHERE table_schema = DATABASE()
  AND table_name = 'rabbit_houses'
  AND constraint_name = 'fk_rabbit_houses_merchant';
SET @sql = IF(@cnt = 0, 'alter table rabbit_houses add constraint fk_rabbit_houses_merchant foreign key (merchant_id) references merchants (id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO merchant_users (merchant_id, user_id, create_by, update_by)
SELECT DISTINCT h.merchant_id, hu.user_id, 'migration', 'migration'
FROM rabbit_houses h
JOIN house_users hu ON hu.house_id = h.id
WHERE h.merchant_id IS NOT NULL;
