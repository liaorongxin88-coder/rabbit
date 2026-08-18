-- Additive fixture for the batch outbound golden scenario.
-- Target database: rabbit_app or rabbit_app_e2e. Every run creates isolated users and houses.

SET NAMES utf8mb4;
SET @run_id = DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f');
SET @actor = CONCAT('outbound_fixture_', @run_id, '_control');
SET @password_hash = '$2a$10$OIR2d8mdeNFv4Ddm.W.S6eKSB.fx2mCJ3G35eVdxxyedn9AyGCIA6';
SET @prefix = CONCAT('batch-outbound-fixture:', @run_id);

START TRANSACTION;

-- user_code 是 V30 之后的 NOT NULL 列，四个账号各给各的兔号（唯一键会挡重复）。
INSERT INTO sys_user (user_name, user_code, password, status)
VALUES
    (@actor, CONCAT('R', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, 'outbound-control'), 256), 1, 10))), @password_hash, 'ENABLED'),
    (CONCAT('outbound_fixture_', @run_id, '_edit'), CONCAT('R', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, 'outbound-edit'), 256), 1, 10))), @password_hash, 'ENABLED'),
    (CONCAT('outbound_fixture_', @run_id, '_view'), CONCAT('R', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, 'outbound-view'), 256), 1, 10))), @password_hash, 'ENABLED'),
    (CONCAT('outbound_fixture_', @run_id, '_concurrent'), CONCAT('R', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, 'outbound-concurrent'), 256), 1, 10))), @password_hash, 'ENABLED');

SET @user_control = (SELECT user_id FROM sys_user WHERE user_name = @actor);
SET @user_edit = (SELECT user_id FROM sys_user WHERE user_name = CONCAT('outbound_fixture_', @run_id, '_edit'));
SET @user_view = (SELECT user_id FROM sys_user WHERE user_name = CONCAT('outbound_fixture_', @run_id, '_view'));
SET @user_concurrent = (SELECT user_id FROM sys_user WHERE user_name = CONCAT('outbound_fixture_', @run_id, '_concurrent'));

INSERT INTO rabbit_houses (
    name, status, layout_rows, layout_cols, layout_layers,
    request_id, remark, create_by, update_by
)
VALUES (
    CONCAT('H-GOLDEN-', @run_id),
    'ENABLED',
    2,
    6,
    1,
    CONCAT('fixture-primary-', @run_id),
    CONCAT(@prefix, ':primary'),
    @actor,
    @actor
);
SET @primary_house_id = LAST_INSERT_ID();

INSERT INTO rabbit_houses (
    name, status, layout_rows, layout_cols, layout_layers,
    request_id, remark, create_by, update_by
)
VALUES (
    CONCAT('H-GOLDEN-BRANCH-', @run_id),
    'ENABLED',
    1,
    2,
    1,
    CONCAT('fixture-branch-', @run_id),
    CONCAT(@prefix, ':branch'),
    @actor,
    @actor
);
SET @branch_house_id = LAST_INSERT_ID();

INSERT INTO house_users (house_id, user_id, role, status, perms, is_admin, create_by, update_by)
VALUES
    (@primary_house_id, @user_control, 'OWNER', 'ENABLED', 'control', TRUE, @actor, @actor),
    (@primary_house_id, @user_edit, 'STAFF', 'ENABLED', 'edit', FALSE, @actor, @actor),
    (@primary_house_id, @user_view, 'VIEWER', 'ENABLED', 'view', FALSE, @actor, @actor),
    (@primary_house_id, @user_concurrent, 'MANAGER', 'ENABLED', 'control', FALSE, @actor, @actor),
    (@branch_house_id, @user_control, 'OWNER', 'ENABLED', 'control', TRUE, @actor, @actor),
    (@branch_house_id, @user_edit, 'STAFF', 'ENABLED', 'edit', FALSE, @actor, @actor),
    (@branch_house_id, @user_view, 'VIEWER', 'ENABLED', 'view', FALSE, @actor, @actor),
    (@branch_house_id, @user_concurrent, 'MANAGER', 'ENABLED', 'control', FALSE, @actor, @actor);

INSERT INTO cages (
    house_id, cage_number, row_code, layer_index, position_index,
    status, rabbit_count, is_fed, is_enabled, remark, create_by, update_by
)
VALUES
    (@primary_house_id, '1-1-1', 'R1', 1, 1, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':R1-C1'), @actor, @actor),
    (@primary_house_id, '1-2-1', 'R1', 1, 2, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':R1-C2'), @actor, @actor),
    (@primary_house_id, '1-3-1', 'R1', 1, 3, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':R1-C3'), @actor, @actor),
    (@primary_house_id, '1-4-1', 'R1', 1, 4, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':R1-C4'), @actor, @actor),
    (@primary_house_id, '1-5-1', 'R1', 1, 5, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':R1-C5'), @actor, @actor),
    (@primary_house_id, '1-6-1', 'R1', 1, 6, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':R1-C6'), @actor, @actor),
    (@primary_house_id, '2-1-1', 'R2', 1, 1, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':R2-C1'), @actor, @actor),
    (@primary_house_id, '2-2-1', 'R2', 1, 2, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':R2-C2'), @actor, @actor),
    (@primary_house_id, '2-3-1', 'R2', 1, 3, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':R2-C3'), @actor, @actor),
    (@primary_house_id, '2-4-1', 'R2', 1, 4, '0', 0, FALSE, FALSE, CONCAT(@prefix, ':R2-C4-disabled'), @actor, @actor),
    (@primary_house_id, '2-5-1', 'R2', 1, 5, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':R2-C5-reserved-G10'), @actor, @actor),
    (@primary_house_id, '2-6-1', 'R2', 1, 6, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':R2-C6'), @actor, @actor),
    (@branch_house_id, 'BR-1-1-1', 'R1', 1, 1, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':branch-G11'), @actor, @actor),
    (@branch_house_id, 'BR-1-2-1', 'R1', 1, 2, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':branch-G12'), @actor, @actor);

SET @c_g01 = (SELECT id FROM cages WHERE house_id = @primary_house_id AND cage_number = '1-1-1');
SET @c_g02 = (SELECT id FROM cages WHERE house_id = @primary_house_id AND cage_number = '1-2-1');
SET @c_g03 = (SELECT id FROM cages WHERE house_id = @primary_house_id AND cage_number = '1-3-1');
SET @c_g04 = (SELECT id FROM cages WHERE house_id = @primary_house_id AND cage_number = '1-4-1');
SET @c_g05 = (SELECT id FROM cages WHERE house_id = @primary_house_id AND cage_number = '1-5-1');
SET @c_g07 = (SELECT id FROM cages WHERE house_id = @primary_house_id AND cage_number = '1-6-1');
SET @c_g06 = (SELECT id FROM cages WHERE house_id = @primary_house_id AND cage_number = '2-1-1');
SET @c_g08 = (SELECT id FROM cages WHERE house_id = @primary_house_id AND cage_number = '2-3-1');
SET @c_g09 = (SELECT id FROM cages WHERE house_id = @primary_house_id AND cage_number = '2-4-1');
SET @c_g11 = (SELECT id FROM cages WHERE house_id = @branch_house_id AND cage_number = 'BR-1-1-1');
SET @c_g12 = (SELECT id FROM cages WHERE house_id = @branch_house_id AND cage_number = 'BR-1-2-1');

INSERT INTO rabbits (
    house_id, cage_id, type, gender, breed, arrival_method, arrival_date, weight,
    state_version, is_active, is_quarantined, quarantine_time, quarantine_reason,
    request_id, departure_date, departure_reason, create_by, update_by
)
VALUES
    (@primary_house_id, @c_g01, '2', '0', 'FIXTURE-G01', '0', NOW() - INTERVAL 60 DAY, 3.20, 0, TRUE, FALSE, NULL, NULL, CONCAT('fixture-rabbit-', @run_id, '-G01'), NULL, NULL, @actor, @actor),
    (@primary_house_id, @c_g02, '2', '1', 'FIXTURE-G02', '0', NOW() - INTERVAL 53 DAY, 3.10, 0, TRUE, FALSE, NULL, NULL, CONCAT('fixture-rabbit-', @run_id, '-G02'), NULL, NULL, @actor, @actor),
    (@primary_house_id, @c_g03, '2', '0', 'FIXTURE-G03', '0', NOW() - INTERVAL 60 DAY, 3.00, 1, TRUE, TRUE, NOW() - INTERVAL 1 DAY, '黄金场景：隔离阻断', CONCAT('fixture-rabbit-', @run_id, '-G03'), NULL, NULL, @actor, @actor),
    (@primary_house_id, @c_g04, '2', '1', 'FIXTURE-G04', '0', NOW() - INTERVAL 60 DAY, 3.30, 0, TRUE, FALSE, NULL, NULL, CONCAT('fixture-rabbit-', @run_id, '-G04'), NULL, NULL, @actor, @actor),
    (@primary_house_id, @c_g05, '2', '0', 'FIXTURE-G05', '0', NOW() - INTERVAL 60 DAY, 2.90, 0, TRUE, FALSE, NULL, NULL, CONCAT('fixture-rabbit-', @run_id, '-G05'), NULL, NULL, @actor, @actor),
    (@primary_house_id, @c_g07, '0', '1', 'FIXTURE-G07', '0', NOW() - INTERVAL 180 DAY, 4.20, 0, TRUE, FALSE, NULL, NULL, CONCAT('fixture-rabbit-', @run_id, '-G07'), NULL, NULL, @actor, @actor),
    (@primary_house_id, @c_g06, '2', '1', 'FIXTURE-G06', '0', NOW() - INTERVAL 60 DAY, 3.30, 0, TRUE, FALSE, NULL, NULL, CONCAT('fixture-rabbit-', @run_id, '-G06'), NULL, NULL, @actor, @actor),
    (@primary_house_id, @c_g08, '2', '0', 'FIXTURE-G08', '0', NOW() - INTERVAL 40 DAY, 2.70, 0, TRUE, FALSE, NULL, NULL, CONCAT('fixture-rabbit-', @run_id, '-G08'), NULL, NULL, @actor, @actor),
    (@primary_house_id, @c_g09, '2', '1', 'FIXTURE-G09', '0', NOW() - INTERVAL 60 DAY, 3.40, 0, TRUE, FALSE, NULL, NULL, CONCAT('fixture-rabbit-', @run_id, '-G09'), NULL, NULL, @actor, @actor),
    (@branch_house_id, @c_g11, '2', '0', 'FIXTURE-G11', '0', NOW() - INTERVAL 40 DAY, 2.80, 0, TRUE, FALSE, NULL, NULL, CONCAT('fixture-rabbit-', @run_id, '-G11'), NULL, NULL, @actor, @actor),
    (@branch_house_id, @c_g12, '2', '1', 'FIXTURE-G12', '0', NOW() - INTERVAL 90 DAY, 3.50, 1, FALSE, FALSE, NULL, NULL, CONCAT('fixture-rabbit-', @run_id, '-G12'), NOW() - INTERVAL 1 DAY, '出售出栏', @actor, @actor);

SET @g04_id = (SELECT id FROM rabbits WHERE house_id = @primary_house_id AND request_id = CONCAT('fixture-rabbit-', @run_id, '-G04'));
SET @g05_id = (SELECT id FROM rabbits WHERE house_id = @primary_house_id AND request_id = CONCAT('fixture-rabbit-', @run_id, '-G05'));

INSERT INTO treatment_records (
    house_id, rabbit_id, start_date, diagnosis, drug, dose, days,
    next_review_date, status, remark, request_id, create_by, update_by
)
VALUES (
    @primary_house_id,
    @g04_id,
    NOW() - INTERVAL 2 DAY,
    '黄金场景：呼吸道观察',
    'fixture-drug',
    'fixture-dose',
    5,
    NOW() + INTERVAL 1 DAY,
    'OPEN',
    CONCAT(@prefix, ':G04-open-treatment'),
    CONCAT('fixture-treatment-', @run_id, '-G04'),
    @actor,
    @actor
);

INSERT INTO rabbit_abnormal_conditions (
    rabbit_id, house_id, warning_status, warning_time, remark,
    is_deal, create_by, update_by
)
VALUES (
    @g05_id,
    @primary_house_id,
    '体重异常',
    NOW() - INTERVAL 1 DAY,
    CONCAT(@prefix, ':G05-unresolved-abnormal'),
    FALSE,
    @actor,
    @actor
);

INSERT INTO batches (
    house_id, batch_code, status, start_date, request_id, remark, create_by, update_by
)
VALUES
    (@primary_house_id, CONCAT('B-G01-', @run_id), '进行中', NOW() - INTERVAL 30 DAY, CONCAT('fixture-batch-', @run_id, '-G01'), CONCAT(@prefix, ':G01'), @actor, @actor),
    (@primary_house_id, CONCAT('B-G02-', @run_id), '进行中', NOW() - INTERVAL 30 DAY, CONCAT('fixture-batch-', @run_id, '-G02'), CONCAT(@prefix, ':G02'), @actor, @actor),
    (@primary_house_id, CONCAT('B-G03-', @run_id), '进行中', NOW() - INTERVAL 30 DAY, CONCAT('fixture-batch-', @run_id, '-G03'), CONCAT(@prefix, ':G03'), @actor, @actor),
    (@primary_house_id, CONCAT('B-G04-', @run_id), '进行中', NOW() - INTERVAL 30 DAY, CONCAT('fixture-batch-', @run_id, '-G04'), CONCAT(@prefix, ':G04'), @actor, @actor),
    (@primary_house_id, CONCAT('B-G05-', @run_id), '进行中', NOW() - INTERVAL 30 DAY, CONCAT('fixture-batch-', @run_id, '-G05'), CONCAT(@prefix, ':G05'), @actor, @actor),
    (@primary_house_id, CONCAT('B-G06-', @run_id), '进行中', NOW() - INTERVAL 30 DAY, CONCAT('fixture-batch-', @run_id, '-G06'), CONCAT(@prefix, ':G06'), @actor, @actor),
    (@primary_house_id, CONCAT('B-G09-', @run_id), '进行中', NOW() - INTERVAL 30 DAY, CONCAT('fixture-batch-', @run_id, '-G09'), CONCAT(@prefix, ':G09'), @actor, @actor),
    (@branch_house_id, CONCAT('B-G11-', @run_id), '进行中', NOW() - INTERVAL 30 DAY, CONCAT('fixture-batch-', @run_id, '-G11'), CONCAT(@prefix, ':G11'), @actor, @actor);

INSERT INTO batch_rabbits (
    batch_id, rabbit_id, join_reason, batch_role, current_status,
    last_event_date, next_event_date, next_event_type, is_active, join_date,
    remark, create_by, update_by
)
SELECT
    b.id,
    r.id,
    '断奶',
    'fattening',
    '成长期',
    NOW() - INTERVAL 7 DAY,
    CASE
        WHEN r.breed IN ('FIXTURE-G02', 'FIXTURE-G11') THEN NOW() + INTERVAL 7 DAY
        ELSE NOW() - INTERVAL 1 DAY
    END,
    '出售',
    TRUE,
    NOW() - INTERVAL 30 DAY,
    CONCAT(@prefix, ':', r.breed),
    @actor,
    @actor
FROM rabbits r
INNER JOIN batches b
    ON b.house_id = r.house_id
    AND b.request_id = CONCAT('fixture-batch-', @run_id, '-', SUBSTRING_INDEX(r.breed, '-', -1))
WHERE r.request_id LIKE CONCAT('fixture-rabbit-', @run_id, '-%')
  AND r.breed IN (
      'FIXTURE-G01', 'FIXTURE-G02', 'FIXTURE-G03', 'FIXTURE-G04',
      'FIXTURE-G05', 'FIXTURE-G06', 'FIXTURE-G09', 'FIXTURE-G11'
  );

UPDATE cages c
SET c.rabbit_count = (
    SELECT COUNT(*)
    FROM rabbits r
    WHERE r.cage_id = c.id AND r.is_active = TRUE
)
WHERE c.house_id IN (@primary_house_id, @branch_house_id);

COMMIT;

SELECT
    @run_id AS run_id,
    NULL AS retired_scope_id,
    @primary_house_id AS primary_house_id,
    @branch_house_id AS branch_house_id,
    '123456' AS fixture_password;

SELECT 'U-A' AS fixture_role, @actor AS user_name, 'control/admin' AS permission
UNION ALL SELECT 'U-B', CONCAT('outbound_fixture_', @run_id, '_edit'), 'edit'
UNION ALL SELECT 'U-C', CONCAT('outbound_fixture_', @run_id, '_view'), 'view'
UNION ALL SELECT 'U-D', CONCAT('outbound_fixture_', @run_id, '_concurrent'), 'control';

SELECT
    r.breed AS fixture_rabbit,
    r.id AS rabbit_id,
    r.house_id,
    c.cage_number,
    r.is_active,
    r.state_version
FROM rabbits r
INNER JOIN cages c ON c.id = r.cage_id
WHERE r.request_id LIKE CONCAT('fixture-rabbit-', @run_id, '-%')
ORDER BY r.breed;

SELECT
    SUM(classification = 'NORMAL') AS normal,
    SUM(classification = 'EARLY_SALE') AS early_sale,
    SUM(classification = 'NEEDS_ACTION') AS needs_action,
    SUM(classification = 'BLOCKED') AS blocked
FROM (
    SELECT CASE
        WHEN r.is_active <> TRUE THEN 'BLOCKED'
        WHEN r.type <> '2' THEN 'BLOCKED'
        WHEN c.id IS NULL THEN 'BLOCKED'
        WHEN c.is_enabled <> TRUE THEN 'BLOCKED'
        WHEN r.is_quarantined = TRUE THEN 'BLOCKED'
        WHEN EXISTS (
            SELECT 1 FROM treatment_records tr
            WHERE tr.house_id = r.house_id AND tr.rabbit_id = r.id AND tr.status = 'OPEN'
        ) THEN 'BLOCKED'
        WHEN EXISTS (
            SELECT 1 FROM rabbit_abnormal_conditions ac
            WHERE ac.house_id = r.house_id AND ac.rabbit_id = r.id AND ac.is_deal = FALSE
        ) THEN 'NEEDS_ACTION'
        WHEN br.current_status IS NULL OR br.current_status = '' OR br.current_status LIKE '%待分配%' THEN 'BLOCKED'
        WHEN br.current_status LIKE '%可出售%'
          OR br.current_status LIKE '%待出售%'
          OR (br.next_event_type LIKE '%出售%' AND br.next_event_date <= NOW()) THEN 'NORMAL'
        WHEN br.current_status LIKE '%适应%'
          OR br.current_status LIKE '%生长%'
          OR br.current_status LIKE '%育肥%'
          OR br.next_event_type LIKE '%出售%' THEN 'EARLY_SALE'
        ELSE 'BLOCKED'
    END AS classification
    FROM rabbits r
    LEFT JOIN cages c ON c.id = r.cage_id AND c.house_id = r.house_id
    LEFT JOIN batch_rabbits br ON br.id = (
        SELECT br2.id
        FROM batch_rabbits br2
        INNER JOIN batches b2 ON b2.id = br2.batch_id AND b2.house_id = r.house_id
        WHERE br2.rabbit_id = r.id AND br2.is_active = TRUE
        ORDER BY br2.id DESC
        LIMIT 1
    )
    WHERE r.house_id = @primary_house_id AND r.is_active = TRUE
) eligibility;
