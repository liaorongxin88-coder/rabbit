-- Attachment-scale batch statistics fixture for V56 and later schemas.
-- Set @fixture_run_id before execution to reuse a caller-owned run identifier.

SET NAMES utf8mb4;
SET SESSION cte_max_recursion_depth = 7000;
SET @run_id = LOWER(COALESCE(NULLIF(@fixture_run_id, ''), HEX(RANDOM_BYTES(10))));
SET @actor = CONCAT('bsf_', @run_id, '_owner');
SET @password_hash = '$2a$10$OIR2d8mdeNFv4Ddm.W.S6eKSB.fx2mCJ3G35eVdxxyedn9AyGCIA6';
SET @target_house_request = CONCAT('bsf-house-', @run_id, '-target');
SET @isolation_house_request = CONCAT('bsf-house-', @run_id, '-isolation');
SET @target_batch_request = CONCAT('bsf-batch-', @run_id, '-target');
SET @isolation_batch_request = CONCAT('bsf-batch-', @run_id, '-isolation');

START TRANSACTION;

INSERT INTO sys_user (user_name, user_code, password, status)
VALUES (
    @actor,
    CONCAT('R', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, ':owner'), 256), 1, 10))),
    @password_hash,
    'ENABLED'
);
SET @user_id = LAST_INSERT_ID();

INSERT INTO rabbit_houses (
    name, status, layout_rows, layout_cols, layout_layers,
    request_id, remark, create_by, update_by
)
VALUES (
    CONCAT('批次统计验收-', @run_id),
    'ENABLED',
    1,
    1,
    1,
    @target_house_request,
    CONCAT('batch-statistics-fixture:', @run_id, ':target'),
    @actor,
    @actor
);
SET @house_id = LAST_INSERT_ID();

INSERT INTO rabbit_houses (
    name, status, layout_rows, layout_cols, layout_layers,
    request_id, remark, create_by, update_by
)
VALUES (
    CONCAT('批次统计隔离-', @run_id),
    'ENABLED',
    1,
    1,
    1,
    @isolation_house_request,
    CONCAT('batch-statistics-fixture:', @run_id, ':isolation'),
    @actor,
    @actor
);
SET @isolation_house_id = LAST_INSERT_ID();

INSERT INTO house_users (
    house_id, user_id, role, status, perms, is_admin, create_by, update_by
)
VALUES
    (@house_id, @user_id, 'OWNER', 'ENABLED', 'control', TRUE, @actor, @actor),
    (@isolation_house_id, @user_id, 'OWNER', 'ENABLED', 'control', TRUE, @actor, @actor);

INSERT INTO cages (
    house_id, cage_number, row_code, layer_index, position_index,
    status, rabbit_count, is_fed, is_enabled, remark, create_by, update_by
)
VALUES
    (@house_id, '1-1-1', 'R1', 1, 1, '0', 0, FALSE, TRUE,
        CONCAT('batch-statistics-fixture:', @run_id, ':historical'), @actor, @actor),
    (@isolation_house_id, '1-1-1', 'R1', 1, 1, '0', 0, FALSE, TRUE,
        CONCAT('batch-statistics-fixture:', @run_id, ':isolation'), @actor, @actor);
SET @target_cage_id = (
    SELECT id
    FROM cages
    WHERE house_id = @house_id AND cage_number = '1-1-1'
);

INSERT INTO batches (
    house_id, batch_code, status, start_date, end_date, is_archived,
    request_id, remark, create_by, update_by
)
VALUES (
    @house_id,
    CONCAT('BSF-', @run_id),
    '已完成',
    '2024-04-22 00:00:00',
    '2024-08-01 23:59:59',
    FALSE,
    @target_batch_request,
    CONCAT('batch-statistics-fixture:', @run_id, ':target'),
    @actor,
    @actor
);
SET @batch_id = LAST_INSERT_ID();

INSERT INTO batches (
    house_id, batch_code, status, start_date, end_date, is_archived,
    request_id, remark, create_by, update_by
)
VALUES (
    @isolation_house_id,
    CONCAT('BSF-ISO-', @run_id),
    '已完成',
    '2024-04-22 00:00:00',
    '2024-08-01 23:59:59',
    FALSE,
    @isolation_batch_request,
    CONCAT('batch-statistics-fixture:', @run_id, ':isolation'),
    @actor,
    @actor
);
SET @isolation_batch_id = LAST_INSERT_ID();

INSERT INTO rabbits (
    house_id, cage_id, type, gender, breed, arrival_method, arrival_date, weight,
    growth_stage, growth_stage_entered_at, state_version, is_active, is_quarantined,
    request_id, create_by, update_by
)
WITH RECURSIVE sequence_numbers(sequence_number) AS (
    SELECT 1
    UNION ALL
    SELECT sequence_number + 1
    FROM sequence_numbers
    WHERE sequence_number < 1230
)
SELECT
    @house_id,
    @target_cage_id,
    '0',
    '0',
    CONCAT('BSF-DOE-', LPAD(sequence_number, 4, '0')),
    '0',
    '2024-01-01 00:00:00',
    4.00,
    NULL,
    NULL,
    0,
    FALSE,
    FALSE,
    CONCAT('bsf-r-', @run_id, '-d-', LPAD(sequence_number, 4, '0')),
    @actor,
    @actor
FROM sequence_numbers;

INSERT INTO rabbits (
    house_id, cage_id, type, gender, breed, arrival_method, arrival_date, weight,
    growth_stage, growth_stage_entered_at, state_version, is_active, is_quarantined,
    request_id, create_by, update_by
)
WITH RECURSIVE sequence_numbers(sequence_number) AS (
    SELECT 1
    UNION ALL
    SELECT sequence_number + 1
    FROM sequence_numbers
    WHERE sequence_number < 60
)
SELECT
    @house_id,
    @target_cage_id,
    '0',
    '1',
    CONCAT('BSF-BUCK-', LPAD(sequence_number, 2, '0')),
    '0',
    '2024-01-01 00:00:00',
    4.50,
    NULL,
    NULL,
    0,
    FALSE,
    FALSE,
    CONCAT('bsf-r-', @run_id, '-b-', LPAD(sequence_number, 2, '0')),
    @actor,
    @actor
FROM sequence_numbers;

INSERT INTO rabbits (
    house_id, cage_id, birth_batch_id, type, gender, breed, arrival_method,
    arrival_date, weight, growth_stage, growth_stage_entered_at, state_version,
    is_active, is_quarantined, request_id, departure_date, departure_reason,
    create_by, update_by
)
WITH RECURSIVE sequence_numbers(sequence_number) AS (
    SELECT 1
    UNION ALL
    SELECT sequence_number + 1
    FROM sequence_numbers
    WHERE sequence_number < 6834
)
SELECT
    @house_id,
    @target_cage_id,
    @batch_id,
    '2',
    IF(MOD(sequence_number, 2) = 0, '0', '1'),
    CONCAT('BSF-SALE-', LPAD(sequence_number, 4, '0')),
    '1',
    '2024-05-15 00:00:00',
    1.92,
    'FATTENING',
    '2024-06-01 00:00:00',
    1,
    FALSE,
    FALSE,
    CONCAT('bsf-r-', @run_id, '-s-', LPAD(sequence_number, 4, '0')),
    '2024-08-01 09:00:00',
    '出售出栏',
    @actor,
    @actor
FROM sequence_numbers;

INSERT INTO breeding_cycles (
    house_id, batch_id, mother_rabbit_id, male_rabbit_id, cycle_no,
    stage, stage_entered_at, lifecycle, result, mating_method,
    mating_date, pregnancy_check_date, pregnancy_result, closed_at, close_reason,
    request_id, create_by, update_by
)
SELECT
    @house_id,
    @batch_id,
    mother.id,
    buck.id,
    1,
    'AWAIT_WEANING',
    '2024-07-01 00:00:00',
    'CLOSED',
    CASE
        WHEN CAST(SUBSTRING_INDEX(mother.request_id, '-', -1) AS UNSIGNED) <= 21
            THEN 'ABORTED'
        WHEN CAST(SUBSTRING_INDEX(mother.request_id, '-', -1) AS UNSIGNED) <= 1025
            THEN 'WEANED'
        WHEN CAST(SUBSTRING_INDEX(mother.request_id, '-', -1) AS UNSIGNED) <= 1059
            THEN 'REMOVED'
        ELSE 'EMPTY'
    END,
    'NATURAL',
    '2024-04-22 09:00:00',
    '2024-05-05 09:00:00',
    CASE
        WHEN CAST(SUBSTRING_INDEX(mother.request_id, '-', -1) AS UNSIGNED) <= 1059
            THEN '怀孕'
        ELSE '空怀'
    END,
    '2024-08-01 12:00:00',
    'fixture historical cycle',
    CONCAT('bsf-c-', @run_id, '-', SUBSTRING_INDEX(mother.request_id, '-', -1)),
    @actor,
    @actor
FROM rabbits mother
INNER JOIN rabbits buck
    ON buck.house_id = mother.house_id
   AND buck.request_id = CONCAT(
       'bsf-r-',
       @run_id,
       '-b-',
       LPAD(
           MOD(CAST(SUBSTRING_INDEX(mother.request_id, '-', -1) AS UNSIGNED) - 1, 60) + 1,
           2,
           '0'
       )
   )
WHERE mother.house_id = @house_id
  AND mother.request_id LIKE CONCAT('bsf-r-', @run_id, '-d-%');

INSERT INTO repro_events (
    house_id, cycle_id, mother_rabbit_id, batch_id, operation_code,
    target_type, target_id, event_type, occurred_at, payload,
    operator_id, operator_name, request_id
)
SELECT
    @house_id,
    cycle.id,
    cycle.mother_rabbit_id,
    @batch_id,
    'repro:state-machine',
    'RABBIT',
    cycle.mother_rabbit_id,
    'ABORTION',
    '2024-05-20 09:00:00',
    JSON_OBJECT('fixtureRunId', @run_id),
    @user_id,
    @actor,
    CONCAT('bsf-a-', @run_id, '-', LPAD(cycle.mother_rabbit_id, 8, '0'))
FROM breeding_cycles cycle
WHERE cycle.house_id = @house_id
  AND cycle.batch_id = @batch_id
  AND cycle.result = 'ABORTED';

INSERT INTO litters (
    house_id, cycle_id, mother_rabbit_id, sire_rabbit_id, batch_id,
    birth_date, total_kits, live_kits, kept_kits, current_nursing,
    status, weaning_date, weaned_count, avg_weaning_weight,
    weaning_total_weight_kg, request_id, create_by, update_by
)
SELECT
    @house_id,
    cycle.id,
    cycle.mother_rabbit_id,
    cycle.male_rabbit_id,
    @batch_id,
    '2024-05-22 09:00:00',
    10,
    IF(cycle_sequence - 21 <= 834, 10, 9),
    CASE
        WHEN cycle_sequence - 21 <= 607 THEN 10
        WHEN cycle_sequence - 21 <= 987 THEN 9
        ELSE 0
    END,
    CASE
        WHEN cycle_sequence - 21 <= 956 THEN 0
        WHEN cycle_sequence - 21 <= 987 THEN 9
        ELSE 0
    END,
    IF(cycle_sequence - 21 <= 956, 'WEANED', 'NURSING'),
    IF(cycle_sequence - 21 <= 956, '2024-06-25 09:00:00', NULL),
    IF(cycle_sequence - 21 <= 956, 9, 0),
    IF(cycle_sequence - 21 <= 956, 0.735, NULL),
    IF(cycle_sequence - 21 <= 956, 6.615, NULL),
    CONCAT('bsf-l-', @run_id, '-', LPAD(cycle_sequence - 21, 4, '0')),
    @actor,
    @actor
FROM (
    SELECT
        breeding_cycles.*,
        CAST(SUBSTRING_INDEX(request_id, '-', -1) AS UNSIGNED) AS cycle_sequence
    FROM breeding_cycles
    WHERE house_id = @house_id
      AND batch_id = @batch_id
) cycle
WHERE cycle_sequence BETWEEN 22 AND 1025;

INSERT INTO feed_logs (
    house_id, feeding_rabbits, feed_time, feed_type, unit, request_id,
    amount, remark, create_by, update_by
)
VALUES
    (@house_id, NULL, '2024-04-22 10:00:00', 'fixture-breeding', 'kg',
        CONCAT('bsf-f-', @run_id, '-breeding'), 22050.00,
        CONCAT('batch-statistics-fixture:', @run_id), @actor, @actor),
    (@house_id, NULL, '2024-06-25 10:00:00', 'fixture-fattening', 'kg',
        CONCAT('bsf-f-', @run_id, '-fattening'), 30070.00,
        CONCAT('batch-statistics-fixture:', @run_id), @actor, @actor);

INSERT INTO feed_log_batch_allocations (
    feed_log_id, house_id, batch_id, phase, amount_kg
)
SELECT
    feed.id,
    @house_id,
    @batch_id,
    IF(feed.request_id LIKE '%-breeding', 'BREEDING', 'FATTENING'),
    feed.amount
FROM feed_logs feed
WHERE feed.house_id = @house_id
  AND feed.request_id IN (
      CONCAT('bsf-f-', @run_id, '-breeding'),
      CONCAT('bsf-f-', @run_id, '-fattening')
  );

INSERT INTO sale_orders (
    house_id, sale_time, customer, total_weight, unit_price, total_amount,
    remark, request_id, create_by, update_by
)
VALUES (
    @house_id,
    '2024-08-01 09:00:00',
    'fixture customer',
    13095.000,
    12.00,
    157140.00,
    CONCAT('batch-statistics-fixture:', @run_id),
    CONCAT('bsf-sale-', @run_id),
    @actor,
    @actor
);
SET @sale_order_id = LAST_INSERT_ID();

INSERT INTO sale_order_items (
    sale_order_id, rabbit_id, cage_id_snapshot, cage_number_snapshot,
    rabbit_type_snapshot, stage_snapshot, state_version_snapshot,
    batch_id_snapshot, create_by, update_by
)
SELECT
    @sale_order_id,
    rabbit.id,
    @target_cage_id,
    '1-1-1',
    '2',
    'FATTENING',
    rabbit.state_version,
    @batch_id,
    @actor,
    @actor
FROM rabbits rabbit
WHERE rabbit.house_id = @house_id
  AND rabbit.request_id LIKE CONCAT('bsf-r-', @run_id, '-s-%');

INSERT INTO sale_order_batch_allocations (
    sale_order_id, house_id, batch_id, rabbit_count, actual_weight_kg,
    unit_price_per_kg, amount
)
VALUES (
    @sale_order_id,
    @house_id,
    @batch_id,
    6834,
    13095.000,
    12.00,
    157140.00
);

INSERT INTO replacement_batch_allocations (
    house_id, request_id, source_batch_id, rabbit_count,
    total_weight_kg, created_by, created_at
)
VALUES (
    @house_id,
    CONCAT('bsf-replacement-', @run_id),
    @batch_id,
    600,
    1050.000,
    @user_id,
    '2024-08-01 10:00:00'
);

INSERT INTO batch_carcass_yield_versions (
    house_id, batch_id, yield_rate, source_unit, measured_date,
    change_reason, request_id, payload_hash, created_by, created_at
)
VALUES (
    @house_id,
    @batch_id,
    0.560000,
    '测试屠宰场',
    '2024-08-01',
    '首次录入',
    CONCAT('bsf-carcass-', @run_id),
    SHA2(CONCAT('batch-statistics-fixture:', @run_id, ':carcass'), 256),
    @user_id,
    '2024-08-01 11:00:00'
);

COMMIT;

SELECT JSON_OBJECT(
    'run_id', @run_id,
    'user_name', @actor,
    'credential_profile', 'e2e-default',
    'house_id', @house_id,
    'batch_id', @batch_id,
    'isolation_house_id', @isolation_house_id,
    'isolation_batch_id', @isolation_batch_id,
    'mated_cycle_count', 1230,
    'litter_count', 1004,
    'sold_rabbit_count', 6834
) AS fixture_manifest;
