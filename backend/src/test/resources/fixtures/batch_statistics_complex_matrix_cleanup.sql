-- Remove one complex batch statistics matrix run, including client-created side effects.
-- The caller must set @fixture_run_id before execution.

SET NAMES utf8mb4;
SET @run_id = LOWER(NULLIF(@fixture_run_id, ''));
SET @owner = CONCAT('bsx_', @run_id, '_owner');
SET @viewer = CONCAT('bsx_', @run_id, '_viewer');
SET @outsider = CONCAT('bsx_', @run_id, '_outsider');
SET @target_house_request = CONCAT('bsx-house-', @run_id, '-target');
SET @isolation_house_request = CONCAT('bsx-house-', @run_id, '-isolation');
SET @owner_id = (
    SELECT user_id FROM sys_user WHERE user_name = @owner ORDER BY user_id LIMIT 1
);
SET @viewer_id = (
    SELECT user_id FROM sys_user WHERE user_name = @viewer ORDER BY user_id LIMIT 1
);
SET @outsider_id = (
    SELECT user_id FROM sys_user WHERE user_name = @outsider ORDER BY user_id LIMIT 1
);
SET @house_id = (
    SELECT id FROM rabbit_houses
    WHERE request_id = @target_house_request
      AND create_by = CAST(@owner_id AS CHAR)
    ORDER BY id
    LIMIT 1
);
SET @isolation_house_id = (
    SELECT id FROM rabbit_houses
    WHERE request_id = @isolation_house_request
      AND create_by = CAST(@outsider_id AS CHAR)
    ORDER BY id
    LIMIT 1
);

START TRANSACTION;

DELETE FROM audit_logs
WHERE house_id IN (@house_id, @isolation_house_id)
   OR user_id IN (@owner_id, @viewer_id, @outsider_id);

DELETE FROM request_dedup
WHERE house_id IN (@house_id, @isolation_house_id)
   OR user_id IN (@owner_id, @viewer_id, @outsider_id);

DELETE FROM outbound_task_batch_allocations
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM outbound_requests
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM outbound_task_items
WHERE task_id IN (
    SELECT task_id FROM outbound_tasks
    WHERE house_id IN (@house_id, @isolation_house_id)
);

DELETE FROM outbound_tasks
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM batch_carcass_yield_versions
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM business_files
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM replacement_batch_allocations
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM sale_order_batch_allocations
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM sale_order_items
WHERE sale_order_id IN (
    SELECT id FROM sale_orders WHERE house_id IN (@house_id, @isolation_house_id)
);

DELETE FROM sale_orders
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM feed_log_batch_allocations
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM feed_log_rabbits
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM feed_logs
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM biz_attachments
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM work_tasks
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM repro_events
WHERE house_id IN (@house_id, @isolation_house_id)
   OR operator_id IN (@owner_id, @viewer_id, @outsider_id);

DELETE FROM litters
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM rabbit_status_history
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM batch_rabbits
WHERE batch_id IN (
    SELECT id FROM batches WHERE house_id IN (@house_id, @isolation_house_id)
);

DELETE FROM replacement_records
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM rabbit_departure_records
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM breeding_cycles
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM rabbits
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM cages
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM batches
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM reminder_preferences
WHERE house_id IN (@house_id, @isolation_house_id)
   OR user_id IN (@owner_id, @viewer_id, @outsider_id);

DELETE FROM house_users
WHERE house_id IN (@house_id, @isolation_house_id)
   OR user_id IN (@owner_id, @viewer_id, @outsider_id);

DELETE FROM rabbit_houses
WHERE id IN (@house_id, @isolation_house_id);

DELETE FROM sys_user
WHERE user_id IN (@owner_id, @viewer_id, @outsider_id);

COMMIT;

SELECT JSON_OBJECT(
    'run_id', @run_id,
    'remaining_users', (
        SELECT COUNT(*) FROM sys_user
        WHERE user_name IN (@owner, @viewer, @outsider)
    ),
    'remaining_houses', (
        SELECT COUNT(*) FROM rabbit_houses
        WHERE (request_id = @target_house_request
               AND create_by = CAST(@owner_id AS CHAR))
           OR (request_id = @isolation_house_request
               AND create_by = CAST(@outsider_id AS CHAR))
    ),
    'remaining_batches', (
        SELECT COUNT(*) FROM batches
        WHERE request_id LIKE CONCAT('bsx-batch-', @run_id, '-%')
          AND create_by = CAST(@owner_id AS CHAR)
    ),
    'remaining_dedup', (
        SELECT COUNT(*) FROM request_dedup
        WHERE house_id IN (@house_id, @isolation_house_id)
           OR user_id IN (@owner_id, @viewer_id, @outsider_id)
    ),
    'remaining_events', (
        SELECT COUNT(*) FROM repro_events
        WHERE house_id IN (@house_id, @isolation_house_id)
           OR operator_id IN (@owner_id, @viewer_id, @outsider_id)
    )
) AS cleanup_manifest;
