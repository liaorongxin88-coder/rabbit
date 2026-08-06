ALTER TABLE sys_user
  ADD COLUMN phone_country_code VARCHAR(8) NULL AFTER openid,
  ADD COLUMN phone_hash CHAR(64) NULL AFTER phone_country_code,
  ADD COLUMN phone_masked VARCHAR(32) NULL AFTER phone_hash,
  ADD COLUMN phone_bound_time DATETIME NULL AFTER phone_masked,
  ADD UNIQUE KEY uk_sys_user_phone_hash (phone_hash);

CREATE TABLE sms_verification_codes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone_hash CHAR(64) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  code_hash CHAR(64) NOT NULL,
  request_ip VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  send_bucket BIGINT NOT NULL,
  expires_time DATETIME NOT NULL,
  consumed_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sms_phone_purpose_bucket (phone_hash, purpose, send_bucket),
  KEY idx_sms_phone_purpose_status_time (phone_hash, purpose, status, create_time),
  KEY idx_sms_ip_status_time (request_ip, status, create_time),
  KEY idx_sms_expiry (expires_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
