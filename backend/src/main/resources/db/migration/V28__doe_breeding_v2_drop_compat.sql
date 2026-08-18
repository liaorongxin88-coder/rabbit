-- doe-breeding-v2 P4 步骤 4：删除兼容镜像列，并补上 P3 推迟的 NOT NULL 收紧。
--
-- 为什么现在才做，而不是在 V26/V27：
--   V26 只做加法，V27 做回填并加 uk_bc_pipeline。那时旧写路径仍在跑，
--   仍需要这些镜像列；直到旧端点与旧写方法被整体删除、客户端同步升级之后，
--   它们才真正无人读写。本迁移执行前，代码侧已完成：
--     - 八个旧生产写端点与 BatchService 的旧写方法已删除
--     - GET /api/events 改为读 work_tasks（提醒的唯一来源）
--     - 仪表盘「在哺仔兔数」改读 litters
--     - repro 写路径不再回写 status / next_event_*
--
-- 收紧 NOT NULL 现在才安全：唯一会插入 stage 为空的旧写路径已经不存在，
-- 而 V27 已把存量数据补齐。

-- ---------------------------------------------------------------------------
-- 1. 提醒镜像列：提醒已统一由 work_tasks 承载
-- ---------------------------------------------------------------------------
ALTER TABLE breeding_cycles DROP COLUMN next_event_date;
ALTER TABLE breeding_cycles DROP COLUMN next_event_type;
ALTER TABLE breeding_cycles DROP COLUMN is_event_notified;
ALTER TABLE breeding_cycles DROP COLUMN event_notify_date;

-- ---------------------------------------------------------------------------
-- 2. 中文状态镜像列
--
--    一个 stage 可能对应多个旧状态值（待备产与待分娩都写「怀孕确认」），
--    所以它从来不是可靠的判据。权威状态是 stage + lifecycle + result。
-- ---------------------------------------------------------------------------
ALTER TABLE breeding_cycles DROP COLUMN status;

-- ---------------------------------------------------------------------------
-- 3. 血配重叠列：只写不读的死数据
--
--    新模型用「同一母兔并存两条开放周期」表达血配，这几列自始至终没有读者
--    （报表、admin、App 均无引用），保留只会让人误以为它们仍有含义。
-- ---------------------------------------------------------------------------
ALTER TABLE breeding_cycles DROP COLUMN postpartum_remating_days;
ALTER TABLE breeding_cycles DROP COLUMN overlap_litter_cycle_no;
ALTER TABLE breeding_cycles DROP COLUMN overlap_start_date;
ALTER TABLE breeding_cycles DROP COLUMN overlap_end_date;
ALTER TABLE breeding_cycles DROP COLUMN overlap_days;

-- ---------------------------------------------------------------------------
-- 4. batch_member_guard：诊断用的生成列
--
--    V27 最终没有创建 uk_bc_batch_member（它会连哺乳周期一起算，反而挡掉
--    pipeline_guard 特意允许的血配）。这一列因此没有任何约束依赖它。
-- ---------------------------------------------------------------------------
ALTER TABLE breeding_cycles DROP COLUMN batch_member_guard;

-- ---------------------------------------------------------------------------
-- 5. 收紧 stage / stage_entered_at
--
--    防御性兜底放在 MODIFY 之前：真要有残留 NULL，这里补齐比让迁移失败更好，
--    因为失败会把库停在半迁移状态。补齐用的是「无法判断阶段」的最保守取值。
-- ---------------------------------------------------------------------------
UPDATE breeding_cycles SET stage = 'AWAIT_ESTRUS' WHERE stage IS NULL;
UPDATE breeding_cycles
   SET stage_entered_at = COALESCE(mating_date, birth_date, create_time, NOW())
 WHERE stage_entered_at IS NULL;

ALTER TABLE breeding_cycles MODIFY COLUMN stage VARCHAR(20) NOT NULL COMMENT '生产阶段（ReproStage）';
ALTER TABLE breeding_cycles MODIFY COLUMN stage_entered_at DATETIME NOT NULL COMMENT '进入当前阶段的时间';
