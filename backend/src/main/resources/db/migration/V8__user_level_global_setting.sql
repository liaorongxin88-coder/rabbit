SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'global_setting'
  AND column_name = 'user_id';
SET @sql = IF(@cnt = 0, 'alter table global_setting add column user_id bigint null after house_id', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'global_setting'
  AND column_name = 'house_id'
  AND is_nullable = 'NO';
SET @sql = IF(@cnt > 0, 'alter table global_setting modify column house_id bigint null', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO global_setting (
  house_id, user_id, aphrodisiac_days, palpation_days, prepartum_days, weaning_days,
  postpartum_days, sale_days, replacement_days, remark, create_by, update_by
)
SELECT NULL, picked.user_id, picked.aphrodisiac_days, picked.palpation_days, picked.prepartum_days, picked.weaning_days,
       picked.postpartum_days, picked.sale_days, picked.replacement_days, picked.remark, 'migration', 'migration'
FROM (
  SELECT hu.user_id, gs.aphrodisiac_days, gs.palpation_days, gs.prepartum_days, gs.weaning_days,
         gs.postpartum_days, gs.sale_days, gs.replacement_days, gs.remark
  FROM house_users hu
  JOIN rabbit_houses h ON h.id = hu.house_id AND h.is_deleted = FALSE
  JOIN global_setting gs ON gs.house_id = h.id
  JOIN (
    SELECT hu2.user_id, MIN(h2.id) AS house_id
    FROM house_users hu2
    JOIN rabbit_houses h2 ON h2.id = hu2.house_id AND h2.is_deleted = FALSE
    JOIN global_setting gs2 ON gs2.house_id = h2.id
    GROUP BY hu2.user_id
  ) first_house ON first_house.user_id = hu.user_id AND first_house.house_id = h.id
) picked
WHERE NOT EXISTS (
  SELECT 1
  FROM global_setting existing
  WHERE existing.user_id = picked.user_id
);

SELECT COUNT(1) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'global_setting'
  AND index_name = 'uk_setting_user';
SET @sql = IF(@cnt = 0, 'alter table global_setting add unique key uk_setting_user (user_id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @cnt
FROM information_schema.table_constraints
WHERE table_schema = DATABASE()
  AND table_name = 'global_setting'
  AND constraint_name = 'fk_setting_user';
SET @sql = IF(@cnt = 0, 'alter table global_setting add constraint fk_setting_user foreign key (user_id) references sys_user (user_id)', 'select 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
