-- V29 散养母兔的分笼记录：放开 weaning_records.batch_id 的非空约束
--
-- 背景：doe-breeding-v2 把 batch 降级为纯标签，并在 V26 把 breeding_cycles.batch_id
-- 改为可空（业务已裁定散养母兔开放）。但 weaning_records.batch_id 仍是 V1 时代的
-- NOT NULL，于是散养母兔一旦分笼，KitPlacementService 写落位记录时直接撞
-- "Column 'batch_id' cannot be null"，整个分笼事务回滚——母兔卡在待分笼出不来。
--
-- 为什么是放开而不是回填一个批次：这条记录描述的是「哪一窝的仔兔进了哪些笼位」，
-- 归属对象是窝与周期，batch_id 只是旧模型留下的冗余标签。散养母兔本就没有批次，
-- 硬造一个假批次会污染批次列表。
--
-- 外键 fk_wr_batch 不受影响：MySQL 外键允许 NULL 值。
-- 查询侧也不受影响：selectByBatch 用 `w.batch_id = #{batchId}` 过滤，NULL 天然不匹配，
-- 散养的分笼记录不会串进任何批次；按兔查询走 selectByRabbit，仍然看得到。

ALTER TABLE weaning_records MODIFY COLUMN batch_id BIGINT NULL COMMENT '批次ID（散养母兔为空）';
