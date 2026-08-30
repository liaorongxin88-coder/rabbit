-- V48 added operator_name, but rabbits and feed_logs did not persist the value filled by
-- OperationStampInterceptor. Backfill only missing snapshots. Prefer the immutable operation
-- event snapshot; fall back to the current user name only when no matching event exists.
UPDATE rabbits r
LEFT JOIN (
    SELECT house_id, target_id, MAX(operator_name) AS operator_name
    FROM repro_events
    WHERE operation_code = 'rabbit.create'
      AND target_type = 'RABBIT'
      AND operator_name IS NOT NULL
    GROUP BY house_id, target_id
) event_actor
  ON event_actor.house_id = r.house_id
 AND event_actor.target_id = r.id
LEFT JOIN sys_user creator
  ON CAST(creator.user_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci
   = r.create_by COLLATE utf8mb4_unicode_ci
SET r.operator_name = COALESCE(event_actor.operator_name, creator.user_name)
WHERE r.operator_name IS NULL;

UPDATE feed_logs f
LEFT JOIN (
    SELECT house_id, operator_id, occurred_at, MAX(operator_name) AS operator_name
    FROM repro_events
    WHERE operation_code = 'feed:add'
      AND operator_id IS NOT NULL
      AND operator_name IS NOT NULL
    GROUP BY house_id, operator_id, occurred_at
) event_actor
  ON event_actor.house_id = f.house_id
 AND CAST(event_actor.operator_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci
   = f.create_by COLLATE utf8mb4_unicode_ci
 AND event_actor.occurred_at = f.create_time
LEFT JOIN sys_user creator
  ON CAST(creator.user_id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci
   = f.create_by COLLATE utf8mb4_unicode_ci
SET f.operator_name = COALESCE(event_actor.operator_name, creator.user_name)
WHERE f.operator_name IS NULL;
