ALTER TABLE sys_user
  DROP FOREIGN KEY fk_sys_user_merchant,
  DROP INDEX idx_sys_user_merchant,
  DROP COLUMN merchant_id;

ALTER TABLE rabbit_houses
  DROP FOREIGN KEY fk_rabbit_houses_merchant,
  DROP INDEX idx_rabbit_houses_merchant,
  DROP INDEX idx_rabbit_houses_owner,
  DROP COLUMN merchant_id,
  DROP COLUMN owner_user_id;

DROP TABLE merchant_house_policies;
DROP TABLE merchant_users;
DROP TABLE merchants;
