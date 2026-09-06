-- Remove one batch statistics acceptance fixture run.
-- The caller must set @fixture_run_id to the run identifier returned by the load script.

SET NAMES utf8mb4;
SET @run_id = LOWER(NULLIF(@fixture_run_id, ''));
SET @actor = CONCAT('bsf_', @run_id, '_owner');
SET @user_id = (
    SELECT user_id FROM sys_user WHERE user_name = @actor
);
SET @house_id = (
    SELECT id FROM rabbit_houses
    WHERE request_id = CONCAT('bsf-house-', @run_id, '-target')
      AND create_by = @actor
);
SET @isolation_house_id = (
    SELECT id FROM rabbit_houses
    WHERE request_id = CONCAT('bsf-house-', @run_id, '-isolation')
      AND create_by = @actor
);

START TRANSACTION;

DELETE FROM audit_logs
WHERE house_id IN (@house_id, @isolation_house_id)
   OR user_id = @user_id;

DELETE FROM request_dedup
WHERE house_id IN (@house_id, @isolation_house_id)
   OR user_id = @user_id;

DELETE FROM batch_carcass_yield_versions
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM replacement_batch_allocations
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM sale_order_batch_allocations
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM sale_order_items
WHERE sale_order_id IN (
    SELECT id FROM sale_orders
    WHERE house_id IN (@house_id, @isolation_house_id)
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
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM litters
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM rabbit_status_history
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM batch_rabbits
WHERE batch_id IN (
    SELECT id FROM batches
    WHERE house_id IN (@house_id, @isolation_house_id)
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
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM house_users
WHERE house_id IN (@house_id, @isolation_house_id);

DELETE FROM rabbit_houses
WHERE id IN (@house_id, @isolation_house_id);

DELETE FROM sys_user
WHERE user_id = @user_id;

COMMIT;

SELECT JSON_OBJECT(
    'run_id', @run_id,
    'remaining_users', (
        SELECT COUNT(*) FROM sys_user WHERE user_name = @actor
    ),
    'remaining_houses', (
        SELECT COUNT(*) FROM rabbit_houses
        WHERE request_id IN (
            CONCAT('bsf-house-', @run_id, '-target'),
            CONCAT('bsf-house-', @run_id, '-isolation')
        )
          AND create_by = @actor
    ),
    'remaining_batches', (
        SELECT COUNT(*) FROM batches
        WHERE request_id IN (
            CONCAT('bsf-batch-', @run_id, '-target'),
            CONCAT('bsf-batch-', @run_id, '-isolation')
        )
          AND create_by = @actor
    )
) AS cleanup_manifest;
