CREATE TABLE IF NOT EXISTS merchant_users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_merchant_user (merchant_id, user_id),
  KEY idx_merchant_users_user_status (user_id, status, merchant_id),
  KEY idx_merchant_users_merchant_role (merchant_id, role, status),
  CONSTRAINT fk_merchant_users_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id),
  CONSTRAINT fk_merchant_users_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO merchant_users (
  merchant_id, user_id, role, status, create_by, update_by
)
SELECT
  u.merchant_id,
  u.user_id,
  CASE
    WHEN u.user_id = owners.owner_user_id THEN 'OWNER'
    ELSE 'MEMBER'
  END,
  'ENABLED',
  'migration',
  'migration'
FROM sys_user u
JOIN (
  SELECT merchant_id, MIN(user_id) AS owner_user_id
  FROM sys_user
  GROUP BY merchant_id
) owners ON owners.merchant_id = u.merchant_id;

ALTER TABLE merchants
  ADD COLUMN owner_user_id BIGINT NULL AFTER id;

UPDATE merchants m
JOIN merchant_users mu
  ON mu.merchant_id = m.id
  AND mu.role = 'OWNER'
SET m.owner_user_id = mu.user_id
WHERE m.owner_user_id IS NULL;

CREATE TABLE IF NOT EXISTS merchant_house_policies (
  merchant_id BIGINT PRIMARY KEY,
  house_creation_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  house_member_management_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  max_house_count INT NOT NULL DEFAULT 20,
  max_members_per_house INT NOT NULL DEFAULT 50,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_merchant_house_policies_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO merchant_house_policies (
  merchant_id,
  house_creation_enabled,
  house_member_management_enabled,
  max_house_count,
  max_members_per_house,
  create_by,
  update_by
)
SELECT id, TRUE, TRUE, 20, 50, 'migration', 'migration'
FROM merchants;

ALTER TABLE rabbit_houses
  ADD COLUMN owner_user_id BIGINT NULL AFTER merchant_id;

UPDATE rabbit_houses h
JOIN (
  SELECT hu.house_id, MIN(hu.user_id) AS owner_user_id
  FROM house_users hu
  WHERE hu.is_admin = TRUE
  GROUP BY hu.house_id
) owners ON owners.house_id = h.id
SET h.owner_user_id = owners.owner_user_id
WHERE h.owner_user_id IS NULL;

UPDATE rabbit_houses h
JOIN (
  SELECT hu.house_id, MIN(hu.user_id) AS owner_user_id
  FROM house_users hu
  GROUP BY hu.house_id
) members ON members.house_id = h.id
SET h.owner_user_id = members.owner_user_id
WHERE h.owner_user_id IS NULL;

ALTER TABLE house_users
  ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'VIEWER' AFTER user_id;

UPDATE house_users hu
JOIN rabbit_houses h ON h.id = hu.house_id
SET hu.role = CASE
  WHEN hu.user_id = h.owner_user_id THEN 'OWNER'
  WHEN hu.is_admin = TRUE OR hu.perms = 'control' THEN 'MANAGER'
  WHEN hu.perms = 'edit' THEN 'STAFF'
  ELSE 'VIEWER'
END;

UPDATE house_users
SET is_admin = role = 'OWNER';

ALTER TABLE rabbit_houses
  ADD KEY idx_rabbit_houses_owner (owner_user_id, is_deleted, id);

ALTER TABLE house_users
  ADD KEY idx_house_users_house_role (house_id, role, user_id);
