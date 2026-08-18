-- 本轮真机验收的种子数据：死亡记录收口、换笼位对调/并笼、笼内逐只管理、录入母兔入轨。
-- 目标库 rabbit_app 或 rabbit_app_e2e；每次运行生成独立的用户与兔舍，可重复执行。
--
-- 笼位布局（1 行 6 列 1 层）刻意造出换笼的三种目标形态：
--   1-1-1 种母兔 DOE      （占用的非商品兔笼 → 对调的一端）
--   1-2-1 后备兔 RESERVE  （占用的非商品兔笼 → 对调的另一端）
--   1-3-1 商品兔 ×2       （未满的商品兔笼 → 并笼目标，同时是「多只兔笼挑一只登记死亡」的现场）
--   1-4-1 商品兔 ×1       （被并走的那只）
--   1-5-1 空笼            （直接入笼目标）
--   1-6-1 空笼            （录入母兔用）

SET NAMES utf8mb4;
SET @run_id = DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f');
SET @actor = CONCAT('cage_ops_fixture_', @run_id, '_control');
-- 与其它 fixture 相同的 bcrypt("123456")
SET @password_hash = '$2a$10$OIR2d8mdeNFv4Ddm.W.S6eKSB.fx2mCJ3G35eVdxxyedn9AyGCIA6';
SET @prefix = CONCAT('cage-ops-fixture:', @run_id);

START TRANSACTION;

-- user_code 是 V30 之后的 NOT NULL 列：兔号由建号方负责给，fixture 也不例外。
INSERT INTO sys_user (user_name, user_code, password, status)
VALUES (@actor, CONCAT('R', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, 'cageops'), 256), 1, 10))), @password_hash, 'ENABLED');
SET @user_control = (SELECT user_id FROM sys_user WHERE user_name = @actor);

INSERT INTO rabbit_houses (
    name, status, layout_rows, layout_cols, layout_layers,
    request_id, remark, create_by, update_by
)
VALUES (
    CONCAT('H-CAGEOPS-', @run_id),
    'ENABLED', 1, 8, 1,
    CONCAT('cage-ops-primary-', @run_id),
    CONCAT(@prefix, ':primary'),
    @actor, @actor
);
SET @house_id = LAST_INSERT_ID();

INSERT INTO house_users (house_id, user_id, role, status, perms, is_admin, create_by, update_by)
VALUES (@house_id, @user_control, 'OWNER', 'ENABLED', 'control', TRUE, @actor, @actor);

-- status: '0' 空笼 / '1' 种兔笼 / '2' 后备兔笼 / '3' 商品兔笼
--
-- is_fed / is_enabled 在这里是有意安排的：分层地图按「关注度」着色，
-- 如果所有有兔的笼都未投喂，整张地图会是一片琥色，验收截图就证明不了颜色真的在分状态。
-- C1/C2 已投喂（已满，中性）、C3/C4 未投喂（琥）、C5/C6 空笼（绿）、
-- C7 停用空笼（灰）、C8 故意记一笔不平的账：标为空闲却写着在栏 2 只（红）。
-- C8 没有对应的 rabbits 行，这正是“异常”要暴露的真实数据形态。
INSERT INTO cages (
    house_id, cage_number, row_code, layer_index, position_index,
    status, rabbit_count, is_fed, is_enabled, remark, create_by, update_by
)
VALUES
    (@house_id, '1-1-1', 'R1', 1, 1, '1', 1, TRUE, TRUE, CONCAT(@prefix, ':doe'), @actor, @actor),
    (@house_id, '1-2-1', 'R1', 1, 2, '2', 1, TRUE, TRUE, CONCAT(@prefix, ':reserve'), @actor, @actor),
    (@house_id, '1-3-1', 'R1', 1, 3, '3', 2, FALSE, TRUE, CONCAT(@prefix, ':commodity-pair'), @actor, @actor),
    (@house_id, '1-4-1', 'R1', 1, 4, '3', 1, FALSE, TRUE, CONCAT(@prefix, ':commodity-single'), @actor, @actor),
    (@house_id, '1-5-1', 'R1', 1, 5, '0', 0, TRUE, TRUE, CONCAT(@prefix, ':empty'), @actor, @actor),
    (@house_id, '1-6-1', 'R1', 1, 6, '0', 0, TRUE, TRUE, CONCAT(@prefix, ':intake'), @actor, @actor),
    (@house_id, '1-7-1', 'R1', 1, 7, '0', 0, TRUE, FALSE, CONCAT(@prefix, ':disabled'), @actor, @actor),
    (@house_id, '1-8-1', 'R1', 1, 8, '0', 2, TRUE, TRUE, CONCAT(@prefix, ':inconsistent'), @actor, @actor);

SET @c1 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-1-1');
SET @c2 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-2-1');
SET @c3 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-3-1');
SET @c4 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-4-1');
SET @c5 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-5-1');
SET @c6 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-6-1');

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

-- 1-5-1 预先贴好并绑定 NFC 标签：真实养殖场里标签是早就贴在笼上的，
-- 换笼时“碰一下目标笼位”碰到的就是已绑定的标签。
-- 两张表都要写：后端 resolve 同时校 nfc_tags 与 cage_nfc_tags，只写一张会报 UID 不一致。
-- payload 带 HMAC 签名，无法在 SQL 里算，用例改从 GET /api/nfc/cages/write-queue 取。
SET @c5_uid = CONCAT('04CA6E0P5', UPPER(SUBSTRING(MD5(@run_id), 1, 6)));

INSERT INTO nfc_tags (
    house_id, tag_uid, target_type, target_id,
    request_id, remark, create_by, update_by
)
VALUES
    (@house_id, @c5_uid, 'CAGE', @c5,
     CONCAT('cage-ops-nfc-', @run_id, '-C5'), CONCAT(@prefix, ':nfc-c5'), @actor, @actor);

INSERT INTO cage_nfc_tags (
    house_id, cage_id, tag_uid, request_id, remark, create_by, update_by
)
VALUES
    (@house_id, @c5, @c5_uid,
     CONCAT('cage-ops-nfc-', @run_id, '-C5'), CONCAT(@prefix, ':nfc-c5'), @actor, @actor);

COMMIT;

SELECT
    @run_id AS run_id,
    @house_id AS house_id,
    '123456' AS fixture_password,
    @actor AS control_user,
    @c5_uid AS c5_tag_uid;

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
