-- Additive fixture for the Android whole-batch lifecycle scenario.
-- Every run creates an isolated owner, house, cages, and three breeders.

SET NAMES utf8mb4;
SET @run_id = DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f');
SET @actor = CONCAT('batch_lifecycle_fixture_', @run_id, '_control');
SET @password_hash = '$2a$10$OIR2d8mdeNFv4Ddm.W.S6eKSB.fx2mCJ3G35eVdxxyedn9AyGCIA6';
SET @prefix = CONCAT('batch-lifecycle-fixture:', @run_id);

START TRANSACTION;

-- user_code 是 V30 之后的 NOT NULL 列：兔号由建号方负责给，fixture 也不例外。
INSERT INTO sys_user (user_name, user_code, password, status)
VALUES (@actor, CONCAT('R', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, 'lifecycle'), 256), 1, 10))), @password_hash, 'ENABLED');
SET @user_id = (SELECT user_id FROM sys_user WHERE user_name = @actor);

INSERT INTO rabbit_houses (
    name, status, layout_rows, layout_cols, layout_layers,
    request_id, remark, create_by, update_by
)
VALUES (
    CONCAT('H-LIFECYCLE-', @run_id),
    'ENABLED',
    2,
    6,
    1,
    CONCAT('lifecycle-house-', @run_id),
    CONCAT(@prefix, ':house'),
    @actor,
    @actor
);
SET @house_id = LAST_INSERT_ID();

INSERT INTO house_users (
    house_id, user_id, role, status, perms, is_admin, create_by, update_by
)
VALUES (
    @house_id, @user_id, 'OWNER', 'ENABLED', 'control', TRUE, @actor, @actor
);

INSERT INTO global_setting (
    house_id, user_id, aphrodisiac_days, palpation_days, prepartum_days,
    weaning_days, postpartum_days, sale_days, replacement_days,
    remark, create_by, update_by
)
VALUES (
    @house_id, @user_id, 0, 0, 30, 0, 0, 0, 30,
    CONCAT(@prefix, ':zero-day-e2e-settings'), @actor, @actor
);

INSERT INTO cages (
    house_id, cage_number, row_code, layer_index, position_index,
    status, rabbit_count, is_fed, is_enabled, remark, create_by, update_by
)
VALUES
    (@house_id, 'R1-C1-L1', 'R1', 1, 1, '1', 1, FALSE, TRUE, CONCAT(@prefix, ':doe-a'), @actor, @actor),
    (@house_id, 'R1-C2-L1', 'R1', 1, 2, '1', 1, FALSE, TRUE, CONCAT(@prefix, ':doe-b'), @actor, @actor),
    (@house_id, 'R1-C3-L1', 'R1', 1, 3, '1', 1, FALSE, TRUE, CONCAT(@prefix, ':buck'), @actor, @actor),
    (@house_id, 'R1-C4-L1', 'R1', 1, 4, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':commodity-a'), @actor, @actor),
    (@house_id, 'R1-C5-L1', 'R1', 1, 5, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':commodity-b'), @actor, @actor),
    (@house_id, 'R1-C6-L1', 'R1', 1, 6, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':spare-1'), @actor, @actor),
    (@house_id, 'R2-C1-L1', 'R2', 1, 1, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':spare-2'), @actor, @actor),
    (@house_id, 'R2-C2-L1', 'R2', 1, 2, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':spare-3'), @actor, @actor),
    (@house_id, 'R2-C3-L1', 'R2', 1, 3, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':spare-4'), @actor, @actor),
    (@house_id, 'R2-C4-L1', 'R2', 1, 4, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':spare-5'), @actor, @actor),
    (@house_id, 'R2-C5-L1', 'R2', 1, 5, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':spare-6'), @actor, @actor),
    (@house_id, 'R2-C6-L1', 'R2', 1, 6, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':spare-7'), @actor, @actor);

SET @doe_a_cage = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = 'R1-C1-L1');
SET @doe_b_cage = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = 'R1-C2-L1');
SET @buck_cage = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = 'R1-C3-L1');

INSERT INTO rabbits (
    house_id, cage_id, type, gender, breed, arrival_method, arrival_date, weight,
    state_version, is_active, is_quarantined, request_id,
    create_by, update_by
)
VALUES
    (@house_id, @doe_a_cage, '0', '0', 'LIFECYCLE-DOE-A', '0', NOW() - INTERVAL 240 DAY, 4.10, 0, TRUE, FALSE, CONCAT('lifecycle-rabbit-', @run_id, '-doe-a'), @actor, @actor),
    (@house_id, @doe_b_cage, '0', '0', 'LIFECYCLE-DOE-B-EMPTY', '0', NOW() - INTERVAL 220 DAY, 4.00, 0, TRUE, FALSE, CONCAT('lifecycle-rabbit-', @run_id, '-doe-b'), @actor, @actor),
    (@house_id, @buck_cage, '0', '1', 'LIFECYCLE-BUCK', '0', NOW() - INTERVAL 260 DAY, 4.50, 0, TRUE, FALSE, CONCAT('lifecycle-rabbit-', @run_id, '-buck'), @actor, @actor);

SET @mother_a_id = (SELECT id FROM rabbits WHERE request_id = CONCAT('lifecycle-rabbit-', @run_id, '-doe-a'));
SET @mother_b_id = (SELECT id FROM rabbits WHERE request_id = CONCAT('lifecycle-rabbit-', @run_id, '-doe-b'));
SET @father_id = (SELECT id FROM rabbits WHERE request_id = CONCAT('lifecycle-rabbit-', @run_id, '-buck'));

COMMIT;

SELECT
    @run_id AS run_id,
    @house_id AS house_id,
    @mother_a_id AS mother_a_id,
    @mother_b_id AS mother_b_id,
    @father_id AS father_id;

SELECT 'mother-a' AS fixture_role, @mother_a_id AS rabbit_id, 'two successful overlapping cycles' AS scenario
UNION ALL SELECT 'mother-b', @mother_b_id, 'empty pregnancy remains in house'
UNION ALL SELECT 'father', @father_id, 'breeding buck';
