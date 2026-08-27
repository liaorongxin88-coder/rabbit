-- 休养、待催情和待配种属于母兔自身的下一轮管线，不归属任何生产批次。
-- 生产批次在执行配种、进入待摸胎时绑定；空怀、流产、失败产或分笼结束时释放。

-- V42 曾要求所有 OPEN 周期必须带批次。新流程允许前三个阶段无批次运行。
ALTER TABLE breeding_cycles
  DROP CHECK ck_bc_open_batch;

ALTER TABLE breeding_cycles
  ADD COLUMN planned_batch_id BIGINT NULL AFTER batch_id;

-- READY 现在是有持续时间、有待办的真实管线阶段，必须参与一母兔一管线的唯一守卫。
ALTER TABLE breeding_cycles
  DROP INDEX uk_bc_pipeline;

ALTER TABLE breeding_cycles
  DROP COLUMN pipeline_guard;

ALTER TABLE breeding_cycles
  ADD COLUMN pipeline_guard BIGINT GENERATED ALWAYS AS (
    CASE WHEN lifecycle = 'OPEN'
          AND stage IN ('READY', 'AWAIT_ESTRUS', 'AWAIT_MATING', 'AWAIT_PALPATION',
                        'AWAIT_PREPARTUM', 'AWAIT_DELIVERY')
         THEN mother_rabbit_id END
  ) STORED;

ALTER TABLE breeding_cycles
  ADD UNIQUE KEY uk_bc_pipeline (house_id, pipeline_guard);

-- 存量早期周期解除正式绑定，原批次转为可移除的计划批次。
-- 事件保留当时的批次用于历史审计，未完成待办继续按计划批次展示。
DROP TEMPORARY TABLE IF EXISTS v47_early_cycles;
CREATE TEMPORARY TABLE v47_early_cycles AS
SELECT id AS cycle_id, house_id, batch_id, mother_rabbit_id
FROM breeding_cycles
WHERE lifecycle = 'OPEN'
  AND batch_id IS NOT NULL
  AND stage IN ('READY', 'AWAIT_ESTRUS', 'AWAIT_MATING');

ALTER TABLE v47_early_cycles ADD PRIMARY KEY (cycle_id);

UPDATE breeding_cycles cycle
JOIN v47_early_cycles early ON early.cycle_id = cycle.id
SET cycle.planned_batch_id = early.batch_id,
    cycle.batch_id = NULL,
    cycle.update_by = 'v47',
    cycle.update_time = NOW();

DROP TEMPORARY TABLE IF EXISTS v47_early_cycles;

-- 购入母兔需要保留供应方；自留来源继续使用 rabbits.mother_id 关联母兔。
ALTER TABLE rabbits
  ADD COLUMN source_seller VARCHAR(120) NULL AFTER arrival_method;
