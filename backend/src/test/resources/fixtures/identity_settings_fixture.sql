-- 真机验收种子数据：身份与设置链路（账号设置、应用设置、生产设置、数据面板、掉线回登录）。
-- 目标库 rabbit_app 或 rabbit_app_e2e；每次运行生成独立的用户与兔舍，可重复执行。
--
-- 这条链路验的是「一个人自己的账号和偏好」，不是生产流程，所以兔舍只要小而确定：
--   1 排 4 位 1 层，3 只在栏兔（数据面板「在养兔只」必须正好是 3）
--   1 只已离场兔（用来证明面板数的是在栏，不是历史累计）
--
-- 生产设置刻意不预置 global_settings 行：用户默认配置应当是首次进页面时按后端默认值
-- 渲染、保存时才落库。预置了就验不出「第一次保存」这条真实路径。

SET NAMES utf8mb4;
SET @run_id = DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f');
SET @actor = CONCAT('identity_fixture_', @run_id, '_owner');
-- 与其它 fixture 相同的 bcrypt("123456")
SET @password_hash = '$2a$10$OIR2d8mdeNFv4Ddm.W.S6eKSB.fx2mCJ3G35eVdxxyedn9AyGCIA6';
SET @prefix = CONCAT('identity-fixture:', @run_id);

START TRANSACTION;

-- user_code 是 V30 之后的 NOT NULL 列：兔号由建号方负责给，fixture 也不例外。
-- 这个兔号会在「账号设置」页面上被读出来核对，所以必须是合法的 R+10 位十六进制。
INSERT INTO sys_user (user_name, user_code, password, status)
VALUES (@actor, CONCAT('R', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, 'identity'), 256), 1, 10))), @password_hash, 'ENABLED');
SET @user_owner = (SELECT user_id FROM sys_user WHERE user_name = @actor);

INSERT INTO rabbit_houses (
    name, status, layout_rows, layout_cols, layout_layers,
    request_id, remark, create_by, update_by
)
VALUES (
    CONCAT('H-IDENTITY-', @run_id),
    'ENABLED', 1, 4, 1,
    CONCAT('identity-primary-', @run_id),
    CONCAT(@prefix, ':primary'),
    @actor, @actor
);
SET @house_id = LAST_INSERT_ID();

INSERT INTO house_users (house_id, user_id, role, status, perms, is_admin, create_by, update_by)
VALUES (@house_id, @user_owner, 'OWNER', 'ENABLED', 'control', TRUE, @actor, @actor);

-- 编号统一「排-位-层」，跟后端 CageNumbers 生成的一致
INSERT INTO cages (
    house_id, cage_number, row_code, layer_index, position_index,
    status, rabbit_count, is_fed, is_enabled, remark, create_by, update_by
)
VALUES
    (@house_id, '1-1-1', 'R1', 1, 1, '1', 1, TRUE,  TRUE, CONCAT(@prefix, ':doe'),       @actor, @actor),
    (@house_id, '1-2-1', 'R1', 1, 2, '3', 2, TRUE,  TRUE, CONCAT(@prefix, ':commodity'), @actor, @actor),
    (@house_id, '1-3-1', 'R1', 1, 3, '0', 0, TRUE,  TRUE, CONCAT(@prefix, ':empty'),     @actor, @actor),
    (@house_id, '1-4-1', 'R1', 1, 4, '0', 0, FALSE, TRUE, CONCAT(@prefix, ':gone'),      @actor, @actor);

SET @c1 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-1-1');
SET @c2 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-2-1');
SET @c4 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number = '1-4-1');

-- type: 0 种兔 / 1 后备兔 / 2 商品兔；gender: 0 母 / 1 公
INSERT INTO rabbits (
    house_id, cage_id, type, gender, breed, arrival_method, arrival_date, weight,
    growth_stage, reproductive_stage, current_stage, stage_entered_at,
    state_version, is_active, is_quarantined,
    request_id, create_by, update_by
)
VALUES
    (@house_id, @c1, '0', '0', 'IDENT-DOE',    '0', DATE_SUB(CURDATE(), INTERVAL 300 DAY), 4.10,
     NULL, NULL, 'AWAIT_MATING', DATE_SUB(CURDATE(), INTERVAL 3 DAY),
     0, TRUE,  FALSE, CONCAT(@prefix, ':doe'),    @actor, @actor),
    (@house_id, @c2, '2', '1', 'IDENT-COMM-A', '0', DATE_SUB(CURDATE(), INTERVAL 60 DAY),  2.20,
     '育肥期', NULL, NULL, NULL,
     0, TRUE,  FALSE, CONCAT(@prefix, ':comm-a'), @actor, @actor),
    (@house_id, @c2, '2', '0', 'IDENT-COMM-B', '0', DATE_SUB(CURDATE(), INTERVAL 60 DAY),  2.15,
     '育肥期', NULL, NULL, NULL,
     0, TRUE,  FALSE, CONCAT(@prefix, ':comm-b'), @actor, @actor),
    -- 已离场：面板的「在养兔只」不能把它算进去
    (@house_id, @c4, '2', '1', 'IDENT-GONE',   '0', DATE_SUB(CURDATE(), INTERVAL 90 DAY),  2.40,
     '育肥期', NULL, NULL, NULL,
     0, FALSE, FALSE, CONCAT(@prefix, ':gone'),   @actor, @actor);

COMMIT;

-- 以下输出块由 shell 脚本按位置解析，列的顺序和数量不要随意调整。
SELECT @run_id AS run_id, '123456' AS fixture_password;

SELECT
    'HOUSE' AS kind, h.name AS name, h.id AS id
FROM rabbit_houses h WHERE h.id = @house_id;

SELECT
    'USER' AS kind, u.user_name AS name, u.user_id AS id, u.user_code AS user_code
FROM sys_user u WHERE u.user_id = @user_owner;

SELECT
    'CAGE' AS kind, c.cage_number AS name, c.id AS id, c.status AS status, c.rabbit_count AS cnt
FROM cages c WHERE c.house_id = @house_id
ORDER BY c.position_index;
