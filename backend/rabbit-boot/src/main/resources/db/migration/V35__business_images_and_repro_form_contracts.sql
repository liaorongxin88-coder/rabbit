CREATE TABLE business_files (
  id VARCHAR(64) PRIMARY KEY,
  house_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(64) NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  content LONGBLOB NOT NULL,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_business_files_house_sha (house_id, sha256),
  KEY idx_business_files_house_created (house_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE replacement_records
  ADD COLUMN request_id VARCHAR(64) NULL AFTER rabbit_id,
  ADD UNIQUE KEY uk_replacement_house_request_rabbit (house_id, request_id, rabbit_id);
