-- Generalize the reproductive event stream so tracked operations outside breeding can use it.
-- Existing reproductive rows keep their original request-level uniqueness through the backfilled
-- operation and target coordinates below.
ALTER TABLE repro_events
  DROP INDEX uk_re_request,
  MODIFY COLUMN mother_rabbit_id BIGINT NULL,
  MODIFY COLUMN operator_name VARCHAR(64) NULL,
  MODIFY COLUMN request_id VARCHAR(64) NULL,
  ADD COLUMN cage_id BIGINT NULL AFTER batch_id,
  ADD COLUMN operation_code VARCHAR(64) NOT NULL DEFAULT 'repro:state-machine' AFTER cage_id,
  ADD COLUMN target_type VARCHAR(32) NOT NULL DEFAULT 'RABBIT' AFTER operation_code,
  ADD COLUMN target_id BIGINT NULL AFTER target_type;

UPDATE repro_events
SET target_id = mother_rabbit_id
WHERE target_id IS NULL;

ALTER TABLE repro_events
  ADD UNIQUE KEY uk_re_request_target (
    house_id, request_id, operation_code, target_type, target_id, event_type
  ),
  ADD KEY idx_re_target_time (house_id, target_type, target_id, occurred_at, id),
  ADD KEY idx_re_cage_time (house_id, cage_id, occurred_at, id);
