-- 兔号：用户自己看得见、可以报给别人的唯一标识。
--
-- 为什么不直接用 user_name 顶这个位置：user_name 是账号密码登录的账号本身
-- （POST /api/auth/login 就收它），让人到处报自己的登录名等于把凭证公开一半，
-- 撞库和钓鱼的门槛都掉下来。而且手机号注册的用户 user_name 是
-- mobile_xxxxxxxx 这种自动生成的串，报出去也没法念。
--
-- 兔号形如 R3F9A0C21B7：R 前缀 + 10 位十六进制。十六进制里没有 O/I/L，
-- 所以「O 还是 0」「I 还是 1」这类口头传达的歧义在解析时可以直接归一化掉。
ALTER TABLE sys_user
  ADD COLUMN user_code VARCHAR(16) NULL AFTER user_name;

UPDATE sys_user
SET user_code = CONCAT('R', UPPER(SUBSTRING(SHA2(CONCAT(user_id, '|', UUID()), 256), 1, 10)))
WHERE user_code IS NULL;

ALTER TABLE sys_user
  MODIFY COLUMN user_code VARCHAR(16) NOT NULL,
  ADD UNIQUE KEY uk_sys_user_code (user_code);

-- 按兔号邀请时对方一定已经是注册用户，没有手机号可留；
-- 手机号邀请则相反，对方可能还没注册。两条通道共用一张表，
-- 所以手机列放开为空，另记 invited_user_id 与通道来源。
ALTER TABLE house_invitations
  MODIFY COLUMN phone_hash CHAR(64) NULL,
  MODIFY COLUMN phone_masked VARCHAR(32) NULL,
  ADD COLUMN invite_channel VARCHAR(16) NOT NULL DEFAULT 'PHONE' AFTER role,
  ADD COLUMN invited_user_id BIGINT NULL AFTER phone_masked,
  ADD KEY idx_house_invitations_invited_user (invited_user_id, status),
  ADD CONSTRAINT fk_house_invitations_invited_user
    FOREIGN KEY (invited_user_id) REFERENCES sys_user (user_id);
