ALTER TABLE audit_logs
  ADD COLUMN batch_id BIGINT NULL AFTER house_id,
  ADD COLUMN cage_id BIGINT NULL AFTER batch_id,
  ADD COLUMN rabbit_id BIGINT NULL AFTER cage_id,
  ADD KEY idx_audit_batch_time (batch_id, create_time),
  ADD KEY idx_audit_cage_time (cage_id, create_time),
  ADD KEY idx_audit_rabbit_time (rabbit_id, create_time);
