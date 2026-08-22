DROP PROCEDURE IF EXISTS assert_single_merchant_binding;

DELIMITER //
CREATE PROCEDURE assert_single_merchant_binding()
BEGIN
  DECLARE ambiguous_user_count BIGINT DEFAULT 0;

  SELECT COUNT(1)
  INTO ambiguous_user_count
  FROM (
    SELECT user_id
    FROM merchant_users
    GROUP BY user_id
    HAVING COUNT(DISTINCT merchant_id) > 1
  ) ambiguous_users;

  IF ambiguous_user_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'V10 migration blocked: merchant_users contains users bound to multiple merchants';
  END IF;
END//
DELIMITER ;

CALL assert_single_merchant_binding();
DROP PROCEDURE assert_single_merchant_binding;

ALTER TABLE sys_user
  ADD COLUMN merchant_id BIGINT NULL AFTER user_id;

UPDATE sys_user u
JOIN merchant_users mu ON mu.user_id = u.user_id
SET u.merchant_id = mu.merchant_id
WHERE u.merchant_id IS NULL;

INSERT INTO merchants (
  name, contact_name, contact_phone, status, remark, create_by, update_by
)
SELECT
  CONCAT('待完善商户 #', u.user_id),
  u.user_name,
  NULL,
  'ENABLED',
  CONCAT('V10 migration: created for previously unbound account ', u.user_id),
  CONCAT('migration-user-', u.user_id),
  'migration'
FROM sys_user u
WHERE u.merchant_id IS NULL;

UPDATE sys_user u
JOIN merchants m
  ON m.create_by = CONCAT('migration-user-', u.user_id)
  AND m.remark = CONCAT('V10 migration: created for previously unbound account ', u.user_id)
SET u.merchant_id = m.id
WHERE u.merchant_id IS NULL;

INSERT INTO sys_user (merchant_id, user_name, password, openid)
SELECT
  m.id,
  CONCAT('migration_owner_', m.id, '_', LEFT(MD5(CONCAT('merchant:', m.id)), 8)),
  '!RESET_REQUIRED_BY_PLATFORM_ADMIN!',
  NULL
FROM merchants m
LEFT JOIN sys_user u ON u.merchant_id = m.id
WHERE u.user_id IS NULL;

ALTER TABLE sys_user
  MODIFY COLUMN merchant_id BIGINT NOT NULL,
  ADD KEY idx_sys_user_merchant (merchant_id, user_id),
  ADD CONSTRAINT fk_sys_user_merchant
    FOREIGN KEY (merchant_id) REFERENCES merchants (id);

DROP TABLE merchant_users;
