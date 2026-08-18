-- 新场开张的种子数据：这一条链路的重点是「什么都还没有」。
-- 目标库 rabbit_app 或 rabbit_app_e2e；每次运行生成独立账号，可重复执行。
--
-- 所以这里**故意不建兔舍、不建笼位、不建兔只**——建兔舍、批量建笼、录入兔只
-- 全部由用例在界面上一步步做出来，那才是客户拿到 App 的第一天真正走的路。
-- fixture 只负责准备两个账号：
--   founder  场主，名下一个兔舍都没有（首屏应该是空态，而不是兔舍列表）
--   mate     被邀请的人，同样没有兔舍；它的兔号是这条链路的关键道具
--
-- mate 的兔号必须能被 shell 取到再传给用例，所以这里显式指定而不是交给后端生成。
SET NAMES utf8mb4;
SET @run_id = DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f');
SET @founder = CONCAT('farm_setup_', @run_id, '_founder');
SET @mate = CONCAT('farm_setup_', @run_id, '_mate');
-- 与其它 fixture 相同的 bcrypt("123456")
SET @password_hash = '$2a$10$OIR2d8mdeNFv4Ddm.W.S6eKSB.fx2mCJ3G35eVdxxyedn9AyGCIA6';
-- 兔号形如 R + 10 位十六进制，和后端 UserCodes 的形态一致
SET @founder_code = CONCAT('R', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, 'founder'), 256), 1, 10)));
SET @mate_code = CONCAT('R', UPPER(SUBSTRING(SHA2(CONCAT(@run_id, 'mate'), 256), 1, 10)));

START TRANSACTION;

INSERT INTO sys_user (user_name, user_code, password, status)
VALUES
    (@founder, @founder_code, @password_hash, 'ENABLED'),
    (@mate, @mate_code, @password_hash, 'ENABLED');

SET @user_founder = (SELECT user_id FROM sys_user WHERE user_name = @founder);
SET @user_mate = (SELECT user_id FROM sys_user WHERE user_name = @mate);

COMMIT;

-- 第一块的列顺序和其它 fixture 保持一致：shell 是按位置取的（NR==2）。
SELECT
    @run_id AS run_id,
    '123456' AS fixture_password;

SELECT
    'USER' AS kind,
    'founder' AS name,
    @user_founder AS id,
    @founder AS user_name,
    @founder_code AS user_code
UNION ALL
SELECT
    'USER',
    'mate',
    @user_mate,
    @mate,
    @mate_code;
