-- Android 真后端验收种子：商品兔日常提醒、兔舍前缀批次名、种兔/后备兔出售、断奶后延迟分笼。
-- 每次运行创建独立用户和兔舍，不修改已有业务记录。

SET NAMES utf8mb4;
SET @run_id = DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f');
SET @actor = CONCAT('client_additions_fixture_', @run_id, '_control');
SET @password_hash = '$2a$10$OIR2d8mdeNFv4Ddm.W.S6eKSB.fx2mCJ3G35eVdxxyedn9AyGCIA6';
SET @prefix = CONCAT('client-additions-fixture:', @run_id);

START TRANSACTION;

INSERT INTO sys_user (user_name, user_code, password, status)
VALUES (
    @actor,
    CONCAT('R', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, 'client-additions'), 256), 1, 10))),
    @password_hash,
    'ENABLED'
);
SET @user_id = LAST_INSERT_ID();

INSERT INTO rabbit_houses (
    name, status, layout_rows, layout_cols, layout_layers,
    request_id, remark, create_by, update_by
)
VALUES (
    CONCAT('H-ADDITIONS-', @run_id),
    'ENABLED', 1, 5, 1,
    CONCAT('client-additions-house-', @run_id),
    @prefix,
    @actor, @actor
);
SET @house_id = LAST_INSERT_ID();

INSERT INTO house_users (
    house_id, user_id, role, status, perms, is_admin, create_by, update_by
)
VALUES (@house_id, @user_id, 'OWNER', 'ENABLED', 'control', TRUE, @actor, @actor);

-- 1-1-1 待出售种母兔；1-2-1 后备兔；1-3-1 商品兔；1-4-1 分笼目标；1-5-1 断奶母兔。
INSERT INTO cages (
    house_id, cage_number, row_code, layer_index, position_index,
    status, rabbit_count, is_fed, is_enabled, remark, create_by, update_by
)
VALUES
    (@house_id, '1-1-1', 'R1', 1, 1, '1', 1, TRUE, TRUE, CONCAT(@prefix, ':breeder'), @actor, @actor),
    (@house_id, '1-2-1', 'R1', 1, 2, '2', 1, TRUE, TRUE, CONCAT(@prefix, ':replacement'), @actor, @actor),
    (@house_id, '1-3-1', 'R1', 1, 3, '3', 1, FALSE, TRUE, CONCAT(@prefix, ':commodity'), @actor, @actor),
    (@house_id, '1-4-1', 'R1', 1, 4, '0', 0, TRUE, TRUE, CONCAT(@prefix, ':separation-target'), @actor, @actor),
    (@house_id, '1-5-1', 'R1', 1, 5, '1', 1, TRUE, TRUE, CONCAT(@prefix, ':weaning-mother'), @actor, @actor);

SET @breeder_cage_id = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-1-1');
SET @replacement_cage_id = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-2-1');
SET @commodity_cage_id = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-3-1');
SET @target_cage_id = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-4-1');
SET @mother_cage_id = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-5-1');

INSERT INTO rabbits (
    house_id, cage_id, type, gender, breed, arrival_method, arrival_date, weight,
    growth_stage, reproductive_stage, current_stage, stage_entered_at,
    state_version, is_active, is_quarantined, request_id, create_by, update_by
)
VALUES
    (@house_id, @breeder_cage_id, '0', '0', 'ADDITIONS-BREEDER', '0', NOW() - INTERVAL 220 DAY, 4.20,
     'MATURE', NULL, NULL, NOW() - INTERVAL 120 DAY,
     0, TRUE, FALSE, CONCAT('client-additions-rabbit-', @run_id, '-BREEDER'), @actor, @actor),
    (@house_id, @replacement_cage_id, '1', '0', 'ADDITIONS-REPLACEMENT', '0', NOW() - INTERVAL 110 DAY, 3.40,
     'MATURE', 'RESERVE', NULL, NOW() - INTERVAL 30 DAY,
     0, TRUE, FALSE, CONCAT('client-additions-rabbit-', @run_id, '-REPLACEMENT'), @actor, @actor),
    (@house_id, @commodity_cage_id, '2', '1', 'ADDITIONS-COMMODITY', '1', NOW() - INTERVAL 1 DAY, 1.10,
     'JUVENILE', NULL, NULL, NOW() - INTERVAL 1 DAY,
     0, TRUE, FALSE, CONCAT('client-additions-rabbit-', @run_id, '-COMMODITY'), @actor, @actor),
    (@house_id, @mother_cage_id, '0', '0', 'ADDITIONS-WEANING-MOTHER', '0', NOW() - INTERVAL 240 DAY, 4.40,
     'MATURE', NULL, NULL, NOW() - INTERVAL 180 DAY,
     0, TRUE, FALSE, CONCAT('client-additions-rabbit-', @run_id, '-MOTHER'), @actor, @actor);

SET @breeder_id = (SELECT id FROM rabbits WHERE request_id = CONCAT('client-additions-rabbit-', @run_id, '-BREEDER'));
SET @replacement_id = (SELECT id FROM rabbits WHERE request_id = CONCAT('client-additions-rabbit-', @run_id, '-REPLACEMENT'));
SET @commodity_id = (SELECT id FROM rabbits WHERE request_id = CONCAT('client-additions-rabbit-', @run_id, '-COMMODITY'));
SET @mother_id = (SELECT id FROM rabbits WHERE request_id = CONCAT('client-additions-rabbit-', @run_id, '-MOTHER'));

INSERT INTO batches (
    house_id, batch_code, status, start_date, request_id, remark, create_by, update_by
)
VALUES (
    @house_id,
    CONCAT('H-ADDITIONS-', @run_id, '-历史批次'),
    '进行中', NOW() - INTERVAL 5 DAY,
    CONCAT('client-additions-batch-', @run_id),
    CONCAT(@prefix, ':deferred-separation'),
    @actor, @actor
);
SET @batch_id = LAST_INSERT_ID();

INSERT INTO weaning_records (
    house_id, batch_id, rabbit_id, breeding_cycle_id,
    weaning_date, weaning_count, waiting_count, male_count, female_count,
    avg_weight, remark, create_by, update_by
)
VALUES (
    @house_id, @batch_id, @mother_id, NULL,
    NOW() - INTERVAL 1 DAY, 4, 4, 2, 2,
    1.05, CONCAT(@prefix, ':pending-separation'), @actor, @actor
);
SET @weaning_record_id = LAST_INSERT_ID();

INSERT INTO work_tasks (
    house_id, task_type, subject_type, subject_id, rabbit_id, cage_id,
    due_date, due_time, status, dedup_key, remark, create_by, update_by
)
VALUES (
    @house_id,
    'COMMODITY_ADAPTATION_CARE', 'RABBIT', @commodity_id, @commodity_id, @commodity_cage_id,
    CURDATE(), NOW(), 'PENDING',
    CONCAT('client-additions-daily:', @commodity_id, ':', DATE_FORMAT(CURDATE(), '%Y%m%d')),
    '观察适应情况，按生长和体况分群。',
    @actor, @actor
);
SET @daily_task_id = LAST_INSERT_ID();

COMMIT;

SELECT
    @run_id AS run_id,
    @house_id AS house_id,
    @breeder_id AS breeder_id,
    @replacement_id AS replacement_id,
    @commodity_id AS commodity_id,
    @mother_id AS mother_id,
    @batch_id AS batch_id,
    @weaning_record_id AS weaning_record_id,
    @target_cage_id AS target_cage_id,
    @daily_task_id AS daily_task_id,
    @actor AS control_user,
    '123456' AS fixture_password;
