ALTER TABLE phone_one_tap_attempts
  ADD COLUMN lease_id VARCHAR(64) NULL AFTER status,
  ADD COLUMN lease_expires_time DATETIME(3) NULL AFTER lease_id,
  ADD COLUMN success_time DATETIME(3) NULL AFTER user_id;

UPDATE phone_one_tap_attempts
SET lease_id = CONCAT('legacy-', id)
WHERE lease_id IS NULL;

UPDATE phone_one_tap_attempts
SET lease_expires_time = update_time
WHERE status = 'PROCESSING' AND lease_expires_time IS NULL;

UPDATE phone_one_tap_attempts
SET success_time = update_time
WHERE status = 'SUCCEEDED' AND success_time IS NULL;

ALTER TABLE phone_one_tap_attempts
  MODIFY COLUMN lease_id VARCHAR(64) NOT NULL;

CREATE TABLE phone_one_tap_rate_buckets (
  request_ip VARCHAR(64) NOT NULL,
  bucket_type VARCHAR(16) NOT NULL,
  bucket_start DATETIME NOT NULL,
  request_count INT NOT NULL DEFAULT 0,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (request_ip, bucket_type, bucket_start),
  KEY idx_phone_one_tap_bucket_start (bucket_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
