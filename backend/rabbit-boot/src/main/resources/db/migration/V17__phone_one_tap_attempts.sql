CREATE TABLE phone_one_tap_attempts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(64) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  token_hash CHAR(64) NOT NULL,
  request_ip VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  response_code INT,
  response_message VARCHAR(128),
  user_id BIGINT,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_phone_one_tap_request_id (request_id),
  UNIQUE KEY uk_phone_one_tap_token_hash (token_hash),
  KEY idx_phone_one_tap_ip_time (request_ip, create_time),
  KEY idx_phone_one_tap_status_time (status, update_time),
  KEY idx_phone_one_tap_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
