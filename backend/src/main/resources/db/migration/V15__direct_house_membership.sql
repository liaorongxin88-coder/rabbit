ALTER TABLE sys_user
  ADD COLUMN password_initialized BOOLEAN NOT NULL DEFAULT TRUE AFTER password,
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' AFTER phone_bound_time,
  ADD KEY idx_sys_user_status (status, user_id);

UPDATE sys_user
SET password_initialized = FALSE
WHERE password = '!RESET_REQUIRED_BY_PLATFORM_ADMIN!';

UPDATE sys_user
SET status = 'DISABLED'
WHERE password = '!RESET_REQUIRED_BY_PLATFORM_ADMIN!';

ALTER TABLE rabbit_houses
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' AFTER owner_user_id,
  ADD KEY idx_rabbit_houses_status (status, is_deleted, id);

ALTER TABLE house_users
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' AFTER role,
  ADD KEY idx_house_users_user_status (user_id, status, house_id);

-- Preserve the legacy merchant gate before it is removed. A direct membership that
-- was disabled by either its merchant or merchant membership must stay disabled.
UPDATE house_users hu
JOIN rabbit_houses h ON h.id = hu.house_id
LEFT JOIN merchants m ON m.id = h.merchant_id
LEFT JOIN merchant_users mu
  ON mu.merchant_id = h.merchant_id
  AND mu.user_id = hu.user_id
SET hu.status = CASE
  WHEN h.merchant_id IS NULL THEN 'ENABLED'
  WHEN m.id IS NULL OR m.status <> 'ENABLED' THEN 'DISABLED'
  WHEN mu.id IS NULL OR mu.status <> 'ENABLED' THEN 'DISABLED'
  ELSE 'ENABLED'
END;

-- Preserve the explicit legacy house owner as a direct owner membership.
INSERT INTO house_users (
  house_id, user_id, role, status, perms, is_admin, create_by, update_by
)
SELECT
  h.id,
  h.owner_user_id,
  'OWNER',
  CASE
    WHEN h.merchant_id IS NULL THEN 'ENABLED'
    WHEN m.id IS NULL OR m.status <> 'ENABLED' THEN 'DISABLED'
    WHEN mu.id IS NULL OR mu.status <> 'ENABLED' THEN 'DISABLED'
    ELSE 'ENABLED'
  END,
  'control',
  TRUE,
  'migration-v15',
  'migration-v15'
FROM rabbit_houses h
LEFT JOIN merchants m ON m.id = h.merchant_id
LEFT JOIN merchant_users mu
  ON mu.merchant_id = h.merchant_id
  AND mu.user_id = h.owner_user_id
WHERE h.owner_user_id IS NOT NULL
ON DUPLICATE KEY UPDATE
  role = 'OWNER',
  status = VALUES(status),
  perms = 'control',
  is_admin = TRUE,
  update_by = 'migration-v15';

-- Merchant owners previously had implicit full access to every merchant house.
-- Materialize that access. Multiple direct OWNER rows are intentionally supported.
INSERT INTO house_users (
  house_id, user_id, role, status, perms, is_admin, create_by, update_by
)
SELECT
  h.id,
  mu.user_id,
  'OWNER',
  CASE
    WHEN m.status = 'ENABLED' AND mu.status = 'ENABLED' THEN 'ENABLED'
    ELSE 'DISABLED'
  END,
  'control',
  TRUE,
  'migration-v15',
  'migration-v15'
FROM rabbit_houses h
JOIN merchants m ON m.id = h.merchant_id
JOIN merchant_users mu
  ON mu.merchant_id = h.merchant_id
  AND mu.role = 'OWNER'
ON DUPLICATE KEY UPDATE
  role = 'OWNER',
  status = VALUES(status),
  perms = 'control',
  is_admin = TRUE,
  update_by = 'migration-v15';

-- Only the explicit legacy ownership above is materialized; ordinary members
-- are never promoted implicitly. Without an enabled owner, retain the house for
-- platform recovery as ORPHANED. Merchant suspension still takes precedence.
UPDATE rabbit_houses h
LEFT JOIN merchants m ON m.id = h.merchant_id
SET h.status = CASE
  WHEN h.merchant_id IS NOT NULL AND (m.id IS NULL OR m.status <> 'ENABLED') THEN 'SUSPENDED'
  WHEN NOT EXISTS (
    SELECT 1
    FROM house_users hu
    JOIN sys_user owner_user ON owner_user.user_id = hu.user_id
    WHERE hu.house_id = h.id
      AND hu.role = 'OWNER'
      AND hu.status = 'ENABLED'
      AND owner_user.status = 'ENABLED'
  ) THEN 'ORPHANED'
  ELSE 'ENABLED'
END;

CREATE TABLE house_invitations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  phone_hash CHAR(64) NOT NULL,
  phone_masked VARCHAR(32) NOT NULL,
  role VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  request_id VARCHAR(64) NOT NULL,
  invited_by_user_id BIGINT NOT NULL,
  accepted_user_id BIGINT,
  expires_time DATETIME NOT NULL,
  accepted_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_house_invitations_request (house_id, invited_by_user_id, request_id),
  KEY idx_house_invitations_house_phone_status (house_id, phone_hash, status, id),
  KEY idx_house_invitations_phone_status_expiry (phone_hash, status, expires_time),
  KEY idx_house_invitations_inviter (invited_by_user_id, status, house_id),
  KEY idx_house_invitations_accepted_user (accepted_user_id, status, house_id),
  CONSTRAINT fk_house_invitations_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_house_invitations_inviter FOREIGN KEY (invited_by_user_id) REFERENCES sys_user (user_id),
  CONSTRAINT fk_house_invitations_accepted_user FOREIGN KEY (accepted_user_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
