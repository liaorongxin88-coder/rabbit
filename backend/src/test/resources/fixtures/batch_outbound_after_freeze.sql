-- Run only after the primary outbound task has reached CONFIRMING.
-- Adds G10 to the latest golden fixture without changing its frozen selection.

SET NAMES utf8mb4;
SET @house_id = (
    SELECT id
    FROM rabbit_houses
    WHERE remark LIKE 'batch-outbound-fixture:%:primary'
    ORDER BY id DESC
    LIMIT 1
);
SET @house_remark = (SELECT remark FROM rabbit_houses WHERE id = @house_id);
SET @run_id = SUBSTRING_INDEX(SUBSTRING_INDEX(@house_remark, ':', 2), ':', -1);
SET @actor = CONCAT('outbound_fixture_', @run_id, '_concurrent');
SET @cage_id = (
    SELECT id
    FROM cages
    WHERE house_id = @house_id AND cage_number = 'R2-C5-L1'
);
SET @rabbit_request_id = CONCAT('fixture-rabbit-', @run_id, '-G10');
SET @batch_request_id = CONCAT('fixture-batch-', @run_id, '-G10');

START TRANSACTION;

INSERT INTO rabbits (
    house_id, cage_id, type, gender, breed, arrival_method, arrival_date, weight,
    state_version, is_active, is_quarantined, request_id, create_by, update_by
)
SELECT
    @house_id, @cage_id, '2', '0', 'FIXTURE-G10', '0', NOW() - INTERVAL 60 DAY, 3.25,
    0, TRUE, FALSE, @rabbit_request_id, @actor, @actor
WHERE @house_id IS NOT NULL
  AND @cage_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM rabbits WHERE house_id = @house_id AND request_id = @rabbit_request_id
  );

SET @rabbit_id = (
    SELECT id FROM rabbits WHERE house_id = @house_id AND request_id = @rabbit_request_id
);

INSERT INTO batches (
    house_id, batch_code, status, start_date, request_id, remark, create_by, update_by
)
SELECT
    @house_id,
    CONCAT('B-G10-', @run_id),
    '进行中',
    NOW() - INTERVAL 30 DAY,
    @batch_request_id,
    CONCAT('batch-outbound-fixture:', @run_id, ':G10'),
    @actor,
    @actor
WHERE @rabbit_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM batches WHERE house_id = @house_id AND request_id = @batch_request_id
  );

SET @batch_id = (
    SELECT id FROM batches WHERE house_id = @house_id AND request_id = @batch_request_id
);

INSERT INTO batch_rabbits (
    batch_id, rabbit_id, join_reason, batch_role, current_status,
    last_event_date, next_event_date, next_event_type, is_active, join_date,
    remark, create_by, update_by
)
SELECT
    @batch_id,
    @rabbit_id,
    '断奶',
    '商品兔',
    '成长期',
    NOW() - INTERVAL 7 DAY,
    NOW() - INTERVAL 1 DAY,
    '出售',
    TRUE,
    NOW() - INTERVAL 30 DAY,
    CONCAT('batch-outbound-fixture:', @run_id, ':G10'),
    @actor,
    @actor
WHERE @batch_id IS NOT NULL
  AND @rabbit_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM batch_rabbits
      WHERE batch_id = @batch_id AND rabbit_id = @rabbit_id AND is_active = TRUE
  );

UPDATE cages
SET rabbit_count = (
    SELECT COUNT(*) FROM rabbits WHERE cage_id = @cage_id AND is_active = TRUE
)
WHERE id = @cage_id;

COMMIT;

SELECT
    @run_id AS run_id,
    @house_id AS primary_house_id,
    @rabbit_id AS g10_rabbit_id,
    @cage_id AS g10_cage_id,
    'G10 is added after freeze; the frozen task must still contain its original selection.' AS assertion;
