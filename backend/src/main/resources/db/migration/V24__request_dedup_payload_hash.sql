ALTER TABLE request_dedup
  ADD COLUMN payload_hash VARCHAR(64) NULL AFTER request_id;
