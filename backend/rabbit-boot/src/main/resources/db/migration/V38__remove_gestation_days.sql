-- 预产期固定为配种日后 30 天，不再作为生产周期配置保存。
ALTER TABLE global_setting
  DROP COLUMN gestation_days;
