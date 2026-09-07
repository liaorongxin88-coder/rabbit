-- Run-scoped complex batch statistics matrix for V56 and later schemas.
-- Set @fixture_run_id before execution to reuse a caller-owned run identifier.

SET NAMES utf8mb4;
SET SESSION cte_max_recursion_depth = 100;
SET @run_id = LOWER(COALESCE(NULLIF(@fixture_run_id, ''), HEX(RANDOM_BYTES(10))));
SET @owner = CONCAT('bsx_', @run_id, '_owner');
SET @viewer = CONCAT('bsx_', @run_id, '_viewer');
SET @outsider = CONCAT('bsx_', @run_id, '_outsider');
SET @password_hash = '$2a$10$OIR2d8mdeNFv4Ddm.W.S6eKSB.fx2mCJ3G35eVdxxyedn9AyGCIA6';
SET @target_house_request = CONCAT('bsx-house-', @run_id, '-target');
SET @isolation_house_request = CONCAT('bsx-house-', @run_id, '-isolation');

START TRANSACTION;

INSERT INTO sys_user (user_name, user_code, password, status)
VALUES
    (@owner, CONCAT('X', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, ':owner'), 256), 1, 10))), @password_hash, 'ENABLED'),
    (@viewer, CONCAT('X', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, ':viewer'), 256), 1, 10))), @password_hash, 'ENABLED'),
    (@outsider, CONCAT('X', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, ':outsider'), 256), 1, 10))), @password_hash, 'ENABLED');
SET @owner_id = (SELECT user_id FROM sys_user WHERE user_name = @owner);
SET @viewer_id = (SELECT user_id FROM sys_user WHERE user_name = @viewer);
SET @outsider_id = (SELECT user_id FROM sys_user WHERE user_name = @outsider);

INSERT INTO rabbit_houses (
    name, status, layout_rows, layout_cols, layout_layers,
    request_id, remark, create_by, update_by
)
VALUES (
    CONCAT('复杂批次统计-', @run_id), 'ENABLED', 1, 1, 1,
    @target_house_request, CONCAT('batch-statistics-complex:', @run_id, ':target'),
    CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)
);
SET @house_id = LAST_INSERT_ID();

INSERT INTO rabbit_houses (
    name, status, layout_rows, layout_cols, layout_layers,
    request_id, remark, create_by, update_by
)
VALUES (
    CONCAT('复杂批次隔离-', @run_id), 'ENABLED', 1, 1, 1,
    @isolation_house_request, CONCAT('batch-statistics-complex:', @run_id, ':isolation'),
    CAST(@outsider_id AS CHAR), CAST(@outsider_id AS CHAR)
);
SET @isolation_house_id = LAST_INSERT_ID();

INSERT INTO house_users (
    house_id, user_id, role, status, perms, is_admin, create_by, update_by
)
VALUES
    (@house_id, @owner_id, 'OWNER', 'ENABLED', 'control', TRUE, CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, @viewer_id, 'VIEWER', 'ENABLED', 'view', FALSE, CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@isolation_house_id, @outsider_id, 'OWNER', 'ENABLED', 'control', TRUE, CAST(@outsider_id AS CHAR), CAST(@outsider_id AS CHAR));

INSERT INTO cages (
    house_id, cage_number, row_code, layer_index, position_index,
    status, rabbit_count, is_fed, is_enabled, remark, create_by, update_by
)
VALUES
    (@house_id, '1-1-1', 'R1', 1, 1, '0', 0, FALSE, TRUE, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@isolation_house_id, '1-1-1', 'R1', 1, 1, '0', 0, FALSE, TRUE, CONCAT('batch-statistics-complex:', @run_id), CAST(@outsider_id AS CHAR), CAST(@outsider_id AS CHAR));
SET @cage_id = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-1-1');

INSERT INTO batches (
    house_id, batch_code, status, start_date, end_date, is_archived,
    request_id, remark, create_by, update_by
)
VALUES
    (@house_id, CONCAT('BSX-COMPLEX-AVAILABLE-', @run_id), '已完成', '2024-04-01 00:00:00', '2024-08-01 23:59:59', FALSE, CONCAT('bsx-batch-', @run_id, '-complex-available'), CONCAT('batch-statistics-complex:', @run_id, ':complex-available'), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, CONCAT('BSX-MIXED-DATA-QUALITY-', @run_id), '已完成', '2024-05-01 00:00:00', '2024-08-01 23:59:59', FALSE, CONCAT('bsx-batch-', @run_id, '-mixed-data-quality'), CONCAT('batch-statistics-complex:', @run_id, ':mixed-data-quality'), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, CONCAT('BSX-MIXED-BATCH-ROUNDING-', @run_id), '已完成', '2024-06-01 00:00:00', '2024-08-01 23:59:59', FALSE, CONCAT('bsx-batch-', @run_id, '-mixed-batch-rounding'), CONCAT('batch-statistics-complex:', @run_id, ':mixed-batch-rounding'), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, CONCAT('BSX-TIME-CYCLE-BOUNDARIES-', @run_id), '已完成', '2024-04-10 00:00:00', '2024-04-20 23:59:59', FALSE, CONCAT('bsx-batch-', @run_id, '-time-and-cycle-boundaries'), CONCAT('batch-statistics-complex:', @run_id, ':time-and-cycle-boundaries'), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, CONCAT('BSX-SECURITY-RETRY-', @run_id), '已完成', '2024-07-01 00:00:00', '2024-08-01 23:59:59', FALSE, CONCAT('bsx-batch-', @run_id, '-security-and-retry'), CONCAT('batch-statistics-complex:', @run_id, ':security-and-retry'), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, CONCAT('BSX-ROUND-SUPPORT-', @run_id), '已完成', '2024-06-01 00:00:00', '2024-08-01 23:59:59', FALSE, CONCAT('bsx-batch-', @run_id, '-mixed-batch-rounding-support'), CONCAT('batch-statistics-complex:', @run_id, ':mixed-batch-rounding-support'), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR));

SET @ca_batch_id = (SELECT id FROM batches WHERE request_id = CONCAT('bsx-batch-', @run_id, '-complex-available'));
SET @mdq_batch_id = (SELECT id FROM batches WHERE request_id = CONCAT('bsx-batch-', @run_id, '-mixed-data-quality'));
SET @round_batch_id = (SELECT id FROM batches WHERE request_id = CONCAT('bsx-batch-', @run_id, '-mixed-batch-rounding'));
SET @time_batch_id = (SELECT id FROM batches WHERE request_id = CONCAT('bsx-batch-', @run_id, '-time-and-cycle-boundaries'));
SET @security_batch_id = (SELECT id FROM batches WHERE request_id = CONCAT('bsx-batch-', @run_id, '-security-and-retry'));
SET @round_support_batch_id = (SELECT id FROM batches WHERE request_id = CONCAT('bsx-batch-', @run_id, '-mixed-batch-rounding-support'));

-- Historical breeding rabbits have no commodity growth stage under V53.
INSERT INTO rabbits (
    house_id, cage_id, type, gender, breed, arrival_method, arrival_date, weight,
    growth_stage, growth_stage_entered_at, state_version, is_active, is_quarantined,
    request_id, create_by, update_by
)
SELECT
    @house_id, @cage_id, '0', rabbit_data.gender,
    CONCAT('BSX-', UPPER(rabbit_data.rabbit_key)), '0', '2024-01-01 00:00:00',
    IF(rabbit_data.gender = '0', 4.00, 4.50), NULL, NULL, 0, FALSE, FALSE,
    CONCAT('bsx-r-', @run_id, '-', rabbit_data.rabbit_key),
    CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)
FROM (
    SELECT 'ca-d1' AS rabbit_key, '0' AS gender UNION ALL
    SELECT 'ca-d2', '0' UNION ALL SELECT 'ca-d3', '0' UNION ALL
    SELECT 'ca-d4', '0' UNION ALL SELECT 'ca-d5', '0' UNION ALL
    SELECT 'ca-d6', '0' UNION ALL SELECT 'ca-b1', '1' UNION ALL
    SELECT 'ca-b2', '1' UNION ALL SELECT 'ca-b3', '1' UNION ALL
    SELECT 'mdq-d1', '0' UNION ALL SELECT 'mdq-d2', '0' UNION ALL
    SELECT 'mdq-d3', '0' UNION ALL
    SELECT 'round-d1', '0' UNION ALL SELECT 'round-b1', '1' UNION ALL
    SELECT 'support-d1', '0' UNION ALL SELECT 'support-b1', '1' UNION ALL
    SELECT 'time-d1', '0' UNION ALL SELECT 'time-d2', '0' UNION ALL
    SELECT 'time-b1', '1' UNION ALL
    SELECT 'security-d1', '0' UNION ALL SELECT 'security-b1', '1'
) rabbit_data;

-- Commodity rows provide immutable sale snapshots and one legacy replacement row.
INSERT INTO rabbits (
    house_id, cage_id, birth_batch_id, type, gender, breed, arrival_method,
    arrival_date, weight, growth_stage, growth_stage_entered_at, state_version,
    is_active, is_quarantined, request_id, departure_date, departure_reason,
    create_by, update_by
)
WITH RECURSIVE sequence_numbers(sequence_number) AS (
    SELECT 1
    UNION ALL
    SELECT sequence_number + 1 FROM sequence_numbers WHERE sequence_number < 35
)
SELECT
    @house_id, @cage_id,
    CASE
        WHEN sequence_number <= 20 THEN @ca_batch_id
        WHEN sequence_number <= 25 THEN @mdq_batch_id
        WHEN sequence_number = 26 THEN @round_batch_id
        WHEN sequence_number = 27 THEN @round_support_batch_id
        WHEN sequence_number = 28 THEN NULL
        WHEN sequence_number <= 32 THEN @time_batch_id
        ELSE IF(sequence_number <= 34, @security_batch_id, @mdq_batch_id)
    END,
    '2', IF(MOD(sequence_number, 2) = 0, '0', '1'), CONCAT('BSX-SALE-', LPAD(sequence_number, 2, '0')), '1',
    '2024-03-01 00:00:00', 2.00, 'FATTENING', '2024-03-01 00:00:00', 1, FALSE, FALSE,
    CASE
        WHEN sequence_number <= 10 THEN CONCAT('bsx-r-', @run_id, '-ca-a-', LPAD(sequence_number, 2, '0'))
        WHEN sequence_number <= 20 THEN CONCAT('bsx-r-', @run_id, '-ca-b-', LPAD(sequence_number - 10, 2, '0'))
        WHEN sequence_number <= 25 THEN CONCAT('bsx-r-', @run_id, '-mdq-s-', LPAD(sequence_number - 20, 2, '0'))
        WHEN sequence_number = 26 THEN CONCAT('bsx-r-', @run_id, '-round-main')
        WHEN sequence_number = 27 THEN CONCAT('bsx-r-', @run_id, '-round-support')
        WHEN sequence_number = 28 THEN CONCAT('bsx-r-', @run_id, '-round-unassigned')
        WHEN sequence_number <= 32 THEN CONCAT('bsx-r-', @run_id, '-time-s-', LPAD(sequence_number - 28, 2, '0'))
        WHEN sequence_number <= 34 THEN CONCAT('bsx-r-', @run_id, '-security-s-', LPAD(sequence_number - 32, 2, '0'))
        ELSE CONCAT('bsx-r-', @run_id, '-mdq-replacement')
    END,
    IF(sequence_number = 35, NULL, '2024-08-01 09:00:00'),
    IF(sequence_number = 35, NULL, '出售出栏'),
    CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)
FROM sequence_numbers;

INSERT INTO breeding_cycles (
    house_id, batch_id, mother_rabbit_id, male_rabbit_id, cycle_no,
    stage, stage_entered_at, lifecycle, result, mating_method,
    mating_date, pregnancy_check_date, pregnancy_result, closed_at, close_reason,
    request_id, create_by, update_by
)
SELECT
    @house_id, cycle_data.batch_id, mother.id, buck.id, cycle_data.cycle_no,
    'AWAIT_WEANING', cycle_data.mating_date, 'CLOSED', cycle_data.result,
    cycle_data.mating_method, cycle_data.mating_date,
    DATE_ADD(cycle_data.mating_date, INTERVAL 10 DAY), cycle_data.pregnancy_result,
    '2024-08-01 12:00:00', 'complex fixture historical cycle',
    CONCAT('bsx-c-', @run_id, '-', cycle_data.cycle_key),
    CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)
FROM (
    SELECT @ca_batch_id AS batch_id, 'ca-c1' AS cycle_key, 'ca-d1' AS mother_key, 'ca-b1' AS buck_key, 1 AS cycle_no, 'NATURAL' AS mating_method, '2024-04-01 09:00:00' AS mating_date, '怀孕' AS pregnancy_result, 'WEANED' AS result
    UNION ALL SELECT @ca_batch_id, 'ca-c2', 'ca-d2', 'ca-b2', 1, 'NATURAL', '2024-04-01 10:00:00', '怀孕', 'WEANED'
    UNION ALL SELECT @ca_batch_id, 'ca-c3', 'ca-d3', NULL, 1, 'AI', '2024-04-01 11:00:00', '怀孕', 'ABORTED'
    UNION ALL SELECT @ca_batch_id, 'ca-c4', 'ca-d4', 'ca-b3', 1, 'NATURAL', '2024-04-02 09:00:00', '怀孕', 'WEANED'
    UNION ALL SELECT @ca_batch_id, 'ca-c5', 'ca-d5', 'ca-b1', 1, 'NATURAL', '2024-04-02 10:00:00', '怀孕', 'WEANED'
    UNION ALL SELECT @ca_batch_id, 'ca-c6', 'ca-d5', NULL, 2, 'AI', '2024-04-03 09:00:00', '怀孕', 'WEANED'
    UNION ALL SELECT @ca_batch_id, 'ca-c7', 'ca-d1', 'ca-b2', 2, 'NATURAL', '2024-04-03 10:00:00', '空怀', 'EMPTY'
    UNION ALL SELECT @ca_batch_id, 'ca-c8', 'ca-d6', NULL, 1, 'AI', '2024-04-03 11:00:00', '空怀', 'EMPTY'
    UNION ALL SELECT @mdq_batch_id, 'mdq-c1', 'mdq-d1', NULL, 1, 'AI', '2024-05-01 09:00:00', '怀孕', 'WEANED'
    UNION ALL SELECT @mdq_batch_id, 'mdq-c2', 'mdq-d2', NULL, 1, 'AI', '2024-05-01 10:00:00', '怀孕', 'WEANED'
    UNION ALL SELECT @mdq_batch_id, 'mdq-c3', 'mdq-d3', NULL, 1, 'AI', '2024-05-01 11:00:00', '怀孕', 'WEANED'
    UNION ALL SELECT @round_batch_id, 'round-c1', 'round-d1', 'round-b1', 1, 'NATURAL', '2024-06-01 09:00:00', '怀孕', 'WEANED'
    UNION ALL SELECT @round_support_batch_id, 'support-c1', 'support-d1', 'support-b1', 1, 'NATURAL', '2024-06-01 09:30:00', '怀孕', 'WEANED'
    UNION ALL SELECT @time_batch_id, 'time-c1', 'time-d1', 'time-b1', 1, 'NATURAL', '2024-04-10 00:00:00', '怀孕', 'WEANED'
    UNION ALL SELECT @time_batch_id, 'time-c2', 'time-d2', 'time-b1', 1, 'NATURAL', '2024-04-11 09:00:00', '怀孕', 'ABORTED'
    UNION ALL SELECT @time_batch_id, 'time-c3', 'time-d1', NULL, 2, 'AI', '2024-04-11 10:00:00', '空怀', 'EMPTY'
    UNION ALL SELECT @security_batch_id, 'security-c1', 'security-d1', 'security-b1', 1, 'NATURAL', '2024-07-01 09:00:00', '怀孕', 'WEANED'
) cycle_data
INNER JOIN rabbits mother ON mother.house_id = @house_id AND mother.request_id = CONCAT('bsx-r-', @run_id, '-', cycle_data.mother_key)
LEFT JOIN rabbits buck ON buck.house_id = @house_id AND buck.request_id = CONCAT('bsx-r-', @run_id, '-', cycle_data.buck_key);

INSERT INTO repro_events (
    house_id, cycle_id, mother_rabbit_id, batch_id, operation_code,
    target_type, target_id, event_type, occurred_at, payload,
    operator_id, operator_name, request_id
)
SELECT
    @house_id, cycle.id, cycle.mother_rabbit_id, cycle.batch_id,
    'repro:state-machine', 'RABBIT', cycle.mother_rabbit_id, 'ABORTION',
    '2024-05-20 09:00:00', JSON_OBJECT('fixtureRunId', @run_id),
    @owner_id, @owner, CONCAT('bsx-a-', @run_id, '-', abortion.cycle_key)
FROM (SELECT 'ca-c3' AS cycle_key UNION ALL SELECT 'time-c2') abortion
INNER JOIN breeding_cycles cycle ON cycle.house_id = @house_id AND cycle.request_id = CONCAT('bsx-c-', @run_id, '-', abortion.cycle_key);

INSERT INTO litters (
    house_id, cycle_id, mother_rabbit_id, sire_rabbit_id, batch_id,
    birth_date, total_kits, live_kits, kept_kits, current_nursing,
    status, weaning_date, weaned_count, avg_weaning_weight,
    weaning_total_weight_kg, request_id, create_by, update_by
)
SELECT
    @house_id, cycle.id, cycle.mother_rabbit_id, cycle.male_rabbit_id,
    litter_data.batch_id, litter_data.birth_date, litter_data.total_kits,
    litter_data.live_kits, litter_data.kept_kits, 0,
    IF(litter_data.weaned_count > 0, 'WEANED', 'NURSING'),
    IF(litter_data.weaned_count > 0, DATE_ADD(litter_data.birth_date, INTERVAL 30 DAY), NULL),
    litter_data.weaned_count,
    CASE WHEN litter_data.weaning_weight IS NULL OR litter_data.weaned_count = 0 THEN NULL ELSE litter_data.weaning_weight / litter_data.weaned_count END,
    litter_data.weaning_weight,
    CONCAT('bsx-l-', @run_id, '-', litter_data.litter_key),
    CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)
FROM (
    SELECT @ca_batch_id AS batch_id, 'ca-l1' AS litter_key, 'ca-c1' AS cycle_key, '2024-05-01 09:00:00' AS birth_date, 10 AS total_kits, 9 AS live_kits, 8 AS kept_kits, 7 AS weaned_count, CAST(5.250 AS DECIMAL(12,3)) AS weaning_weight
    UNION ALL SELECT @ca_batch_id, 'ca-l2', 'ca-c2', '2024-05-02 09:00:00', 9, 9, 9, 8, 6.240
    UNION ALL SELECT @ca_batch_id, 'ca-l3', 'ca-c4', '2024-05-03 09:00:00', 8, 7, 6, 6, 4.680
    UNION ALL SELECT @ca_batch_id, 'ca-l4', 'ca-c5', '2024-05-04 09:00:00', 11, 10, 0, 0, NULL
    UNION ALL SELECT @ca_batch_id, 'ca-l5', 'ca-c6', '2024-05-05 09:00:00', 7, 7, 7, 6, 4.620
    UNION ALL SELECT @mdq_batch_id, 'mdq-l1', 'mdq-c1', '2024-06-01 09:00:00', 10, 9, 8, 8, 6.400
    UNION ALL SELECT @mdq_batch_id, 'mdq-l2', 'mdq-c2', '2024-06-02 09:00:00', 10, 9, 8, 7, NULL
    UNION ALL SELECT @round_batch_id, 'round-l1', 'round-c1', '2024-07-01 09:00:00', 1, 1, 1, 1, 0.100
    UNION ALL SELECT @round_support_batch_id, 'support-l1', 'support-c1', '2024-07-01 09:30:00', 1, 1, 1, 1, 0.100
    UNION ALL SELECT @time_batch_id, 'time-l1', 'time-c1', '2024-04-12 09:00:00', 5, 5, 4, 3, 1.500
    UNION ALL SELECT @time_batch_id, 'time-l2', 'time-c2', '2024-04-13 09:00:00', 5, 5, 4, 3, 1.500
    UNION ALL SELECT @security_batch_id, 'security-l1', 'security-c1', '2024-07-10 09:00:00', 2, 2, 2, 2, 1.000
) litter_data
INNER JOIN breeding_cycles cycle ON cycle.house_id = @house_id AND cycle.request_id = CONCAT('bsx-c-', @run_id, '-', litter_data.cycle_key);

INSERT INTO feed_logs (
    house_id, feeding_rabbits, feed_time, feed_type, unit, request_id,
    amount, remark, create_by, update_by
)
VALUES
    (@house_id, NULL, '2024-04-01 12:00:00', 'ca-breeding-a', 'kg', CONCAT('bsx-f-', @run_id, '-ca-b1'), 40.25, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-04-02 12:00:00', 'ca-breeding-b', 'kg', CONCAT('bsx-f-', @run_id, '-ca-b2'), 40.00, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-06-01 12:00:00', 'ca-fattening-a', 'kg', CONCAT('bsx-f-', @run_id, '-ca-f1'), 60.25, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-06-02 12:00:00', 'ca-fattening-b', 'kg', CONCAT('bsx-f-', @run_id, '-ca-f2'), 60.25, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-05-01 12:00:00', 'mdq-allocated', 'kg', CONCAT('bsx-f-', @run_id, '-mdq-allocated'), 5.00, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-05-02 12:00:00', 'mdq-unallocated', 'kg', CONCAT('bsx-f-', @run_id, '-mdq-unallocated'), 7.00, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-05-03 12:00:00', 'mdq-non-kg', 'bag', CONCAT('bsx-f-', @run_id, '-mdq-non-kg'), 1.00, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-06-01 12:00:00', 'round-breeding', 'kg', CONCAT('bsx-f-', @run_id, '-round-b'), 0.10, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-07-01 12:00:00', 'round-fattening', 'kg', CONCAT('bsx-f-', @run_id, '-round-f'), 0.20, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-06-01 12:30:00', 'support-breeding', 'kg', CONCAT('bsx-f-', @run_id, '-support-b'), 0.10, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-07-01 12:30:00', 'support-fattening', 'kg', CONCAT('bsx-f-', @run_id, '-support-f'), 0.20, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-04-09 23:59:59', 'time-before', 'kg', CONCAT('bsx-f-', @run_id, '-time-before'), 999.00, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-04-10 00:00:00', 'time-start', 'kg', CONCAT('bsx-f-', @run_id, '-time-start'), 2.00, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-04-20 23:59:59', 'time-end', 'kg', CONCAT('bsx-f-', @run_id, '-time-end'), 3.00, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-04-21 00:00:00', 'time-after', 'kg', CONCAT('bsx-f-', @run_id, '-time-after'), 999.00, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-07-01 12:00:00', 'security-breeding', 'kg', CONCAT('bsx-f-', @run_id, '-security-b'), 1.00, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, NULL, '2024-07-20 12:00:00', 'security-fattening', 'kg', CONCAT('bsx-f-', @run_id, '-security-f'), 2.00, CONCAT('batch-statistics-complex:', @run_id), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR));

INSERT INTO feed_log_batch_allocations (feed_log_id, house_id, batch_id, phase, amount_kg)
SELECT feed.id, @house_id,
    CASE
        WHEN feed.request_id LIKE CONCAT('bsx-f-', @run_id, '-ca-%') THEN @ca_batch_id
        WHEN feed.request_id = CONCAT('bsx-f-', @run_id, '-mdq-allocated') THEN @mdq_batch_id
        WHEN feed.request_id LIKE CONCAT('bsx-f-', @run_id, '-round-%') THEN @round_batch_id
        WHEN feed.request_id LIKE CONCAT('bsx-f-', @run_id, '-support-%') THEN @round_support_batch_id
        WHEN feed.request_id LIKE CONCAT('bsx-f-', @run_id, '-time-%') THEN @time_batch_id
        ELSE @security_batch_id
    END,
    CASE
        WHEN feed.request_id LIKE '%-b1' OR feed.request_id LIKE '%-b2'
          OR feed.request_id LIKE '%-round-b' OR feed.request_id LIKE '%-support-b'
          OR feed.request_id LIKE '%-time-before' OR feed.request_id LIKE '%-time-start'
          OR feed.request_id LIKE '%-security-b' OR feed.request_id LIKE '%-mdq-allocated'
        THEN 'BREEDING' ELSE 'FATTENING'
    END,
    feed.amount
FROM feed_logs feed
WHERE feed.house_id = @house_id
  AND feed.request_id LIKE CONCAT('bsx-f-', @run_id, '-%')
  AND feed.request_id NOT IN (CONCAT('bsx-f-', @run_id, '-mdq-unallocated'), CONCAT('bsx-f-', @run_id, '-mdq-non-kg'));

INSERT INTO feed_log_rabbits (house_id, feed_log_id, rabbit_id, cage_id)
SELECT @house_id, feed.id, rabbit.id, @cage_id
FROM feed_logs feed
INNER JOIN rabbits rabbit ON rabbit.house_id = @house_id AND rabbit.request_id = CONCAT('bsx-r-', @run_id, '-mdq-s-01')
WHERE feed.house_id = @house_id
  AND feed.request_id IN (CONCAT('bsx-f-', @run_id, '-mdq-unallocated'), CONCAT('bsx-f-', @run_id, '-mdq-non-kg'));

INSERT INTO sale_orders (
    house_id, sale_time, customer, total_weight, unit_price, total_amount,
    remark, request_id, create_by, update_by
)
VALUES
    (@house_id, '2024-08-01 09:00:00', 'complex customer A', 10.000, 10.10, 101.00, CONCAT('batch-statistics-complex:', @run_id), CONCAT('bsx-sale-', @run_id, '-ca-a'), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, '2024-08-01 09:05:00', 'complex customer B', 32.500, 12.17, 395.53, CONCAT('batch-statistics-complex:', @run_id), CONCAT('bsx-sale-', @run_id, '-ca-b'), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, '2024-08-01 09:10:00', 'legacy customer', 10.000, NULL, NULL, CONCAT('batch-statistics-complex:', @run_id), CONCAT('bsx-sale-', @run_id, '-mdq'), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, '2024-08-01 09:15:00', 'rounding customer', 1.501, 12.01, 18.03, CONCAT('batch-statistics-complex:', @run_id), CONCAT('bsx-sale-', @run_id, '-round'), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, '2024-04-20 12:00:00', 'time customer', 8.000, 10.00, 80.00, CONCAT('batch-statistics-complex:', @run_id), CONCAT('bsx-sale-', @run_id, '-time'), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)),
    (@house_id, '2024-08-01 09:20:00', 'security customer', 4.000, 10.00, 40.00, CONCAT('batch-statistics-complex:', @run_id), CONCAT('bsx-sale-', @run_id, '-security'), CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR));

SET @ca_sale_a_id = (SELECT id FROM sale_orders WHERE house_id = @house_id AND request_id = CONCAT('bsx-sale-', @run_id, '-ca-a'));
SET @ca_sale_b_id = (SELECT id FROM sale_orders WHERE house_id = @house_id AND request_id = CONCAT('bsx-sale-', @run_id, '-ca-b'));
SET @mdq_sale_id = (SELECT id FROM sale_orders WHERE house_id = @house_id AND request_id = CONCAT('bsx-sale-', @run_id, '-mdq'));
SET @round_sale_id = (SELECT id FROM sale_orders WHERE house_id = @house_id AND request_id = CONCAT('bsx-sale-', @run_id, '-round'));
SET @time_sale_id = (SELECT id FROM sale_orders WHERE house_id = @house_id AND request_id = CONCAT('bsx-sale-', @run_id, '-time'));
SET @security_sale_id = (SELECT id FROM sale_orders WHERE house_id = @house_id AND request_id = CONCAT('bsx-sale-', @run_id, '-security'));

INSERT INTO sale_order_items (
    sale_order_id, rabbit_id, cage_id_snapshot, cage_number_snapshot,
    rabbit_type_snapshot, stage_snapshot, state_version_snapshot,
    batch_id_snapshot, create_by, update_by
)
SELECT
    CASE
        WHEN rabbit.request_id LIKE CONCAT('bsx-r-', @run_id, '-ca-a-%') THEN @ca_sale_a_id
        WHEN rabbit.request_id LIKE CONCAT('bsx-r-', @run_id, '-ca-b-%') THEN @ca_sale_b_id
        WHEN rabbit.request_id LIKE CONCAT('bsx-r-', @run_id, '-mdq-s-%') THEN @mdq_sale_id
        WHEN rabbit.request_id LIKE CONCAT('bsx-r-', @run_id, '-round-%') THEN @round_sale_id
        WHEN rabbit.request_id LIKE CONCAT('bsx-r-', @run_id, '-time-s-%') THEN @time_sale_id
        ELSE @security_sale_id
    END,
    rabbit.id, @cage_id, '1-1-1', '2', 'FATTENING', rabbit.state_version,
    rabbit.birth_batch_id, CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)
FROM rabbits rabbit
WHERE rabbit.house_id = @house_id
  AND rabbit.request_id LIKE CONCAT('bsx-r-', @run_id, '-%')
  AND rabbit.request_id NOT LIKE CONCAT('bsx-r-', @run_id, '%-replacement')
  AND rabbit.type = '2';

INSERT INTO sale_order_batch_allocations (
    sale_order_id, house_id, batch_id, rabbit_count, actual_weight_kg,
    unit_price_per_kg, amount
)
VALUES
    (@ca_sale_a_id, @house_id, @ca_batch_id, 10, 10.000, 10.10, 101.00),
    (@ca_sale_b_id, @house_id, @ca_batch_id, 10, 32.500, 12.17, 395.53),
    (@round_sale_id, @house_id, @round_batch_id, 1, 0.500, 12.01, 6.00),
    (@round_sale_id, @house_id, @round_support_batch_id, 1, 0.500, 12.01, 6.01),
    (@round_sale_id, @house_id, NULL, 1, 0.501, 12.01, 6.02),
    (@time_sale_id, @house_id, @time_batch_id, 4, 8.000, 10.00, 80.00),
    (@security_sale_id, @house_id, @security_batch_id, 2, 4.000, 10.00, 40.00);

INSERT INTO replacement_batch_allocations (
    house_id, request_id, source_batch_id, rabbit_count,
    total_weight_kg, created_by, created_at
)
VALUES
    (@house_id, CONCAT('bsx-replacement-', @run_id, '-ca'), @ca_batch_id, 3, 7.500, @owner_id, '2024-08-01 10:00:00'),
    (@house_id, CONCAT('bsx-replacement-', @run_id, '-time'), @time_batch_id, 1, 2.000, @owner_id, '2024-04-20 10:00:00');

INSERT INTO replacement_records (
    house_id, rabbit_id, request_id, original_type, replacement_date,
    expected_mature_date, is_mature_notified, status, remark, create_by, update_by
)
SELECT
    @house_id, rabbit.id, CONCAT('bsx-replacement-', @run_id, '-mdq'), '2',
    '2024-08-01 10:00:00', '2024-11-01 10:00:00', FALSE, 'PROMOTED',
    CONCAT('batch-statistics-complex:', @run_id, ':missing-weight'),
    CAST(@owner_id AS CHAR), CAST(@owner_id AS CHAR)
FROM rabbits rabbit
WHERE rabbit.house_id = @house_id AND rabbit.request_id = CONCAT('bsx-r-', @run_id, '-mdq-replacement');

INSERT INTO repro_events (
    house_id, batch_id, operation_code, target_type, target_id,
    event_type, occurred_at, payload, operator_id, operator_name, request_id
)
VALUES
    (@house_id, @mdq_batch_id, 'sale:add', 'BATCH', @mdq_batch_id, 'LEGACY_SALE_ALLOCATION_GAP', '2024-08-01 09:10:00', JSON_OBJECT('clientBuild', 'fixture'), @owner_id, @owner, CONCAT('bsx-gap-', @run_id, '-sale-allocation')),
    (@house_id, @mdq_batch_id, 'sale:add', 'BATCH', @mdq_batch_id, 'LEGACY_SALE_PRICE_GAP', '2024-08-01 09:10:00', JSON_OBJECT('clientBuild', 'fixture'), @owner_id, @owner, CONCAT('bsx-gap-', @run_id, '-sale-price')),
    (@house_id, @mdq_batch_id, 'feed:add', 'BATCH', @mdq_batch_id, 'LEGACY_FEED_ALLOCATION_GAP', '2024-05-02 12:00:00', JSON_OBJECT('clientBuild', 'fixture'), @owner_id, @owner, CONCAT('bsx-gap-', @run_id, '-feed-allocation')),
    (@house_id, @mdq_batch_id, 'rabbit:replacement', 'BATCH', @mdq_batch_id, 'LEGACY_REPLACEMENT_WEIGHT_GAP', '2024-08-01 10:00:00', JSON_OBJECT('clientBuild', 'fixture'), @owner_id, @owner, CONCAT('bsx-gap-', @run_id, '-replacement-weight'));

INSERT INTO batch_carcass_yield_versions (
    house_id, batch_id, yield_rate, source_unit, measured_date,
    change_reason, request_id, payload_hash, created_by, created_at
)
VALUES
    (@house_id, @ca_batch_id, 0.510000, '复杂矩阵测试场', '2024-07-01', '首次录入', CONCAT('bsx-carcass-', @run_id, '-ca-v1'), SHA2(CONCAT(@run_id, ':ca:v1'), 256), @owner_id, '2024-07-01 11:00:00'),
    (@house_id, @ca_batch_id, 0.573500, '复杂矩阵测试场', '2024-08-01', '复测更正', CONCAT('bsx-carcass-', @run_id, '-ca-v2'), SHA2(CONCAT(@run_id, ':ca:v2'), 256), @owner_id, '2024-08-01 11:00:00'),
    (@house_id, @round_batch_id, 0.500000, '复杂矩阵测试场', '2024-08-01', '首次录入', CONCAT('bsx-carcass-', @run_id, '-round'), SHA2(CONCAT(@run_id, ':round'), 256), @owner_id, '2024-08-01 11:00:00'),
    (@house_id, @round_support_batch_id, 0.500000, '复杂矩阵测试场', '2024-08-01', '首次录入', CONCAT('bsx-carcass-', @run_id, '-support'), SHA2(CONCAT(@run_id, ':support'), 256), @owner_id, '2024-08-01 11:00:00'),
    (@house_id, @time_batch_id, 0.600000, '复杂矩阵测试场', '2024-04-20', '首次录入', CONCAT('bsx-carcass-', @run_id, '-time'), SHA2(CONCAT(@run_id, ':time'), 256), @owner_id, '2024-04-20 11:00:00');

COMMIT;

SELECT JSON_OBJECT(
    'schema_version', 1,
    'run_id', @run_id,
    'credential_profile', 'e2e-default',
    'target_house_id', @house_id,
    'isolation_house_id', @isolation_house_id,
    'accounts', JSON_ARRAY(
        JSON_OBJECT('role', 'OWNER', 'user_name', @owner),
        JSON_OBJECT('role', 'READ_ONLY', 'user_name', @viewer),
        JSON_OBJECT('role', 'OUTSIDER', 'user_name', @outsider)
    ),
    'scenarios', JSON_ARRAY(
        JSON_OBJECT('id', 'complex-available', 'batch_role', 'primary', 'batch_id', @ca_batch_id, 'batch_code', CONCAT('BSX-COMPLEX-AVAILABLE-', @run_id)),
        JSON_OBJECT('id', 'mixed-data-quality', 'batch_role', 'primary', 'batch_id', @mdq_batch_id, 'batch_code', CONCAT('BSX-MIXED-DATA-QUALITY-', @run_id)),
        JSON_OBJECT('id', 'mixed-batch-rounding', 'batch_role', 'primary', 'batch_id', @round_batch_id, 'batch_code', CONCAT('BSX-MIXED-BATCH-ROUNDING-', @run_id)),
        JSON_OBJECT('id', 'time-and-cycle-boundaries', 'batch_role', 'primary', 'batch_id', @time_batch_id, 'batch_code', CONCAT('BSX-TIME-CYCLE-BOUNDARIES-', @run_id)),
        JSON_OBJECT('id', 'security-and-retry', 'batch_role', 'primary', 'batch_id', @security_batch_id, 'batch_code', CONCAT('BSX-SECURITY-RETRY-', @run_id)),
        JSON_OBJECT('id', 'mixed-batch-rounding-support', 'batch_role', 'support', 'batch_id', @round_support_batch_id, 'batch_code', CONCAT('BSX-ROUND-SUPPORT-', @run_id))
    )
) AS fixture_manifest;
