-- 本轮真机验收的种子数据：死亡记录收口、换笼位对调/并笼、笼内逐只管理、录入母兔入轨。
-- 目标库 rabbit_app 或 rabbit_app_e2e；每次运行生成独立的用户与兔舍，可重复执行。
--
-- 笼位布局（1 行 6 列 1 层）刻意造出换笼的三种目标形态：
--   R1-C1 种母兔 DOE      （占用的非商品兔笼 → 对调的一端）
--   R1-C2 后备兔 RESERVE  （占用的非商品兔笼 → 对调的另一端）
--   R1-C3 商品兔 ×2       （未满的商品兔笼 → 并笼目标，同时是「多只兔笼挑一只登记死亡」的现场）
--   R1-C4 商品兔 ×1       （被并走的那只）
--   R1-C5 空笼            （直接入笼目标）
--   R1-C6 空笼            （录入母兔用）

SET NAMES utf8mb4;
SET @run_id = DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f');
SET @actor = CONCAT('cage_ops_fixture_', @run_id, '_control');
-- 与其它 fixture 相同的 bcrypt("123456")
SET @password_hash = '$2a$10$OIR2d8mdeNFv4Ddm.W.S6eKSB.fx2mCJ3G35eVdxxyedn9AyGCIA6';
SET @prefix = CONCAT('cage-ops-fixture:', @run_id);

START TRANSACTION;

INSERT INTO sys_user (user_name, password, status)
VALUES (@actor, @password_hash, 'ENABLED');
SET @user_control = (SELECT user_id FROM sys_user WHERE user_name = @actor);

INSERT INTO rabbit_houses (
    name, status, layout_rows, layout_cols, layout_layers,
    request_id, remark, create_by, update_by
)
VALUES (
    CONCAT('H-CAGEOPS-', @run_id),
    'ENABLED', 1, 6, 1,
    CONCAT('cage-ops-primary-', @run_id),
    CONCAT(@prefix, ':primary'),
    @actor, @actor
);
SET @house_id = LAST_INSERT_ID();

INSERT INTO house_users (house_id, user_id, role, status, perms, is_admin, create_by, update_by)
VALUES (@house_id, @user_control, 'OWNER', 'ENABLED', 'control', TRUE, @actor, @actor);

-- status: '0' 空笼 / '1' 种兔笼 / '2' 后备兔笼 / '3' 商品兔笼
INSERT INTO cages (
    house_id, cage_number, row_code, layer_index, position_index,
    status, rabbit_count, is_fed, is_enabled, remark, create_by, update_by
)
VALUES
    (@house_id, 'R1-C1-L1', 'R1', 1, 1, '1', 1, FALSE, TRUE, CONCAT(@prefix, ':doe'), @actor, @actor),
    (@house_id, 'R1-C2-L1', 'R1', 1, 2, '2', 1, FALSE, TRUE, CONCAT(@prefix, ':reserve'), @actor, @actor),
    (@house_id, 'R1-C3-L1', 'R1', 1, 3, '3', 2, FALSE, TRUE, CONCAT(@prefix, ':commodity-pair'), @actor, @actor),
    (@house_id, 'R1-C4-L1', 'R1', 1, 4, '3', 1, FALSE, TRUE, CONCAT(@prefix, ':commodity-single'), @actor, @actor),
    (@house_id, 'R1-C5-L1', 'R1', 1, 5, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':empty'), @actor, @actor),
    (@house_id, 'R1-C6-L1', 'R1', 1, 6, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':intake'), @actor, @actor);

SET @c1 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = 'R1-C1-L1');
SET @c2 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = 'R1-C2-L1');
SET @c3 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = 'R1-C3-L1');
SET @c4 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = 'R1-C4-L1');
SET @c5 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = 'R1-C5-L1');
SET @c6 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = 'R1-C6-L1');

-- type: '0' 种兔 / '1' 后备兔 / '2' 商品兔；gender: '0' 母 / '1' 公
-- 种母兔的 current_stage 由生产流程投影，这里直接给一个在轨阶段，验的正是列表能读到它。
INSERT INTO rabbits (
    house_id, cage_id, type, gender, breed, arrival_method, arrival_date, weight,
    growth_stage, reproductive_stage, current_stage, stage_entered_at,
    state_version, is_active, is_quarantined,
    request_id, create_by, update_by
)
VALUES
    (@house_id, @c1, '0', '0', 'CAGEOPS-DOE', '0', NOW() - INTERVAL 200 DAY, 4.10,
     'MATURE', NULL, 'AWAIT_MATING', NOW() - INTERVAL 3 DAY,
     0, TRUE, FALSE, CONCAT('cage-ops-rabbit-', @run_id, '-DOE'), @actor, @actor),
    (@house_id, @c2, '1', '0', 'CAGEOPS-RESERVE', '0', NOW() - INTERVAL 90 DAY, 3.20,
     'GROWING', 'RESERVE', NULL, NULL,
     0, TRUE, FALSE, CONCAT('cage-ops-rabbit-', @run_id, '-RESERVE'), @actor, @actor),
    (@house_id, @c3, '2', '0', 'CAGEOPS-COMM-A', '0', NOW() - INTERVAL 50 DAY, 2.80,
     'FATTENING', NULL, NULL, NULL,
     0, TRUE, FALSE, CONCAT('cage-ops-rabbit-', @run_id, '-COMM-A'), @actor, @actor),
    (@house_id, @c3, '2', '1', 'CAGEOPS-COMM-B', '0', NOW() - INTERVAL 50 DAY, 2.90,
     'FATTENING', NULL, NULL, NULL,
     0, TRUE, FALSE, CONCAT('cage-ops-rabbit-', @run_id, '-COMM-B'), @actor, @actor),
    (@house_id, @c4, '2', '0', 'CAGEOPS-COMM-C', '0', NOW() - INTERVAL 45 DAY, 2.70,
     'FATTENING', NULL, NULL, NULL,
     0, TRUE, FALSE, CONCAT('cage-ops-rabbit-', @run_id, '-COMM-C'), @actor, @actor);

COMMIT;

SELECT
    @run_id AS run_id,
    @house_id AS house_id,
    '123456' AS fixture_password,
    @actor AS control_user;

SELECT
    'CAGE' AS kind, c.cage_number AS name, c.id AS id, c.status AS status, c.rabbit_count AS cnt
FROM cages c WHERE c.house_id = @house_id
ORDER BY c.position_index;

SELECT
    'RABBIT' AS kind, r.breed AS name, r.id AS id, c.cage_number AS cage, r.type AS type
FROM rabbits r
INNER JOIN cages c ON c.id = r.cage_id
WHERE r.request_id LIKE CONCAT('cage-ops-rabbit-', @run_id, '-%')
ORDER BY r.breed;
