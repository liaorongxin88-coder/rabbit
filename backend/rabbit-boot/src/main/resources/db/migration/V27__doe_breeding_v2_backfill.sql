-- =====================================================================
-- V27：母兔生产流程 V2 —— 历史数据回填 + 约束收紧
--
-- 执行位置：P3 停写窗口内（开维护开关 → 跑 V27 → 关维护开关）。
-- 施工计划见 docs/doe-breeding-v2-implementation-plan.md §3.3；
-- 停写前的对账预检查清单见 docs/doe-breeding-v2-backfill-runbook.md。
--
-- ---------------------------------------------------------------------
-- 三个贯穿全文件的约定
--
-- 1) 顺序敏感。窝要在周期定级之后才能建，分笼任务要挂在窝上，兔子投影要在
--    周期全部定级之后才准确。请勿重排步骤。
--
-- 2) 全程幂等，可重跑。每步都带存在性判据（stage IS NULL / NOT EXISTS /
--    ON DUPLICATE KEY UPDATE id = id），DDL 走仓库既有的 information_schema
--    守卫写法。这一点是刻意设计的：MySQL 的 DDL 不参与事务，Flyway 无法回滚，
--    所以失败后的正确动作是「修数据 → repair → 重跑」，而不是手工补偿。
--
-- 3) 唯一键即对账闸门。步骤 8 的 uk_bc_pipeline 一旦
--    建不上，MySQL 会直接报出冲突的具体值（形如 Duplicate entry '1-2'），
--    迁移随即中止，此时 DDL 尚未落任何一笔。之所以不用 SIGNAL 自造断言：
--    实测 MySQL 8.0 裸 SIGNAL 无法带条件（它是语句不是表达式，@dup=0 时照样
--    抛错），而 PREPARE + SIGNAL 直接报 ERROR 1295 不支持。与其为此引入
--    存储过程，不如让唯一键本身充当断言——它的报错信息比自造的更精确。
--
-- ---------------------------------------------------------------------
-- 为什么回填是 SQL 而不是调用 openCycleAt
--
-- 设计文档要求回填与线上 API 共用 openCycleAt，以免两套逻辑漂移。这条约束
-- 对<b>活的代码</b>成立，对一次性迁移不成立：Flyway 迁移一旦发布即冻结、
-- 只跑一次，不存在"日后各自演进"的漂移风险。
--
-- 反过来，逐只调用 openCycleAt 要为每只母兔走一遍「加锁 → 写事件 → upsert
-- 任务 → 投影」，在万只规模的场子上远远撑不住计划给出的 30 分钟停写窗口，
-- 而集合式 SQL 是一次表扫描。
--
-- 真正的防漂移保障放在测试里：V27BackfillIT 会把回填结果与状态机跑出来的
-- 结果做对照断言，这比"共用了代码"的口头保证更硬。
-- =====================================================================


-- ---------------------------------------------------------------------
-- 步骤 1：旧 status → stage / lifecycle / result / mating_method
--
-- V26 给 lifecycle 加了 DEFAULT 'OPEN'，存量行因此<b>全部</b>被置成 OPEN，
-- 这正是 P1 实测时 batch_member_guard 出现重复值的原因。本步把已结束的周期
-- 改回 CLOSED，重复值随之消失。
--
-- 幂等判据用 stage IS NULL：V26 之后、本步之前，所有存量行的 stage 都是 NULL。
-- ---------------------------------------------------------------------
UPDATE breeding_cycles
SET
  stage = CASE
    WHEN status IN ('计划中', '待催情')      THEN 'AWAIT_ESTRUS'
    -- 催情中：催情动作已执行完，等的是配种。
    WHEN status IN ('催情中', '待配种')      THEN 'AWAIT_MATING'
    WHEN status IN ('已配种', '不确定')      THEN 'AWAIT_PALPATION'
    -- 怀孕确认在旧模型里横跨备产前后（旧的 prepartumFinish 不改 status），
    -- 只能靠下一次提醒事件把两段拆开。
    WHEN status = '怀孕确认' AND next_event_type = '分娩' THEN 'AWAIT_DELIVERY'
    WHEN status = '怀孕确认'                 THEN 'AWAIT_PREPARTUM'
    WHEN status = '哺乳中'                   THEN 'AWAIT_WEANING'
    -- 已结束的周期：stage 记录"在哪一步结束的"，供流产/失败率按阶段归因。
    WHEN status = '已断奶'                   THEN 'AWAIT_WEANING'
    WHEN status = '空怀'                     THEN 'AWAIT_PALPATION'
    WHEN status = '分娩失败'                 THEN 'AWAIT_DELIVERY'
    -- 已终止 / 未知词汇：旧库没记在哪一步终止，按已落库的日期列反推。
    ELSE CASE
      WHEN weaning_date IS NOT NULL OR birth_date IS NOT NULL THEN 'AWAIT_WEANING'
      WHEN expected_birth_date IS NOT NULL                    THEN 'AWAIT_DELIVERY'
      WHEN pregnancy_check_date IS NOT NULL
        OR mating_date IS NOT NULL                            THEN 'AWAIT_PALPATION'
      ELSE 'AWAIT_MATING'
    END
  END,
  lifecycle = CASE
    WHEN closed_at IS NOT NULL
      OR status IN ('已断奶', '空怀', '分娩失败', '已终止') THEN 'CLOSED'
    ELSE 'OPEN'
  END,
  result = CASE
    WHEN status = '已断奶'   THEN 'WEANED'
    WHEN status = '空怀'     THEN 'EMPTY'
    WHEN status = '分娩失败' THEN 'FAILED'
    -- 旧库没有独立的流产状态，终止一律记 REMOVED；真流产在 V2 里靠事件区分。
    WHEN status = '已终止'   THEN 'REMOVED'
    WHEN closed_at IS NOT NULL THEN 'REMOVED'
    ELSE NULL
  END,
  -- 旧库只有体配；人工授精是 V2 才有的概念，不臆造。
  mating_method = CASE
    WHEN mating_date IS NOT NULL AND male_rabbit_id IS NOT NULL THEN 'NATURAL'
    ELSE NULL
  END
WHERE stage IS NULL;


-- ---------------------------------------------------------------------
-- 步骤 2：stage_entered_at 兜底（收紧 NOT NULL 的前置条件）
--
-- 取"进入该阶段的那个动作"的时间；都缺失时退到 update_time。
-- 单独一条 UPDATE 而不并进步骤 1，是因为它依赖上一步刚算出的 stage，
-- 同语句内引用刚赋值的列虽然 MySQL 允许，但可读性差且易错。
-- ---------------------------------------------------------------------
UPDATE breeding_cycles
SET stage_entered_at = COALESCE(
  CASE stage
    WHEN 'AWAIT_ESTRUS'    THEN create_time
    WHEN 'AWAIT_MATING'    THEN create_time
    WHEN 'AWAIT_PALPATION' THEN mating_date
    WHEN 'AWAIT_PREPARTUM' THEN COALESCE(pregnancy_check_date, mating_date)
    WHEN 'AWAIT_DELIVERY'  THEN COALESCE(pregnancy_check_date, mating_date)
    WHEN 'AWAIT_WEANING'   THEN birth_date
  END,
  update_time
)
WHERE stage_entered_at IS NULL;


-- ---------------------------------------------------------------------
-- 步骤 3：为"在栏但没有周期"的活跃成员补建周期（计划 A6）
--
-- 旧模型直到配种才建 breeding_cycles 行，因此待催情/催情中/待配种的母兔
-- 在新模型里是"无周期"状态，不补建就会从待办中心整体消失。
--
-- cycle_no 先物化成临时表再 JOIN：INSERT ... SELECT 里对目标表做相关子查询
-- 在 MySQL 上语义脆弱，物化一次既安全又只扫一遍表。
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS v27_max_cycle_no;
CREATE TEMPORARY TABLE v27_max_cycle_no (
  house_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  mother_rabbit_id BIGINT NOT NULL,
  max_no INT NOT NULL,
  PRIMARY KEY (house_id, batch_id, mother_rabbit_id)
) ENGINE=InnoDB;

INSERT INTO v27_max_cycle_no (house_id, batch_id, mother_rabbit_id, max_no)
SELECT house_id, batch_id, mother_rabbit_id, MAX(cycle_no)
FROM breeding_cycles
WHERE batch_id IS NOT NULL
GROUP BY house_id, batch_id, mother_rabbit_id;

INSERT INTO breeding_cycles (
  house_id, batch_id, mother_rabbit_id, male_rabbit_id, cycle_no,
  status, stage, stage_entered_at, lifecycle, state_version,
  request_id, create_by, create_time, update_by, update_time
)
SELECT
  b.house_id,
  br.batch_id,
  br.rabbit_id,
  br.male_rabbit_id,
  COALESCE(mx.max_no, 0) + 1,
  -- status 是兼容镜像列：没有 OTA 的老 APK 仍直读它渲染列表。
  CASE WHEN br.current_status = '待催情' THEN '待催情' ELSE '待配种' END,
  CASE WHEN br.current_status = '待催情' THEN 'AWAIT_ESTRUS' ELSE 'AWAIT_MATING' END,
  COALESCE(br.last_event_date, br.join_date),
  'OPEN',
  0,
  CONCAT('v27-br-', br.id),
  'v27', NOW(), 'v27', NOW()
FROM batch_rabbits br
JOIN batches b ON b.id = br.batch_id
LEFT JOIN v27_max_cycle_no mx
  ON mx.house_id = b.house_id
 AND mx.batch_id = br.batch_id
 AND mx.mother_rabbit_id = br.rabbit_id
WHERE br.is_active = TRUE
  AND br.current_status IN ('待催情', '催情中', '待配种')
  AND NOT EXISTS (
    SELECT 1 FROM breeding_cycles c
    WHERE c.house_id = b.house_id
      AND c.mother_rabbit_id = br.rabbit_id
      AND c.lifecycle = 'OPEN'
  );

DROP TEMPORARY TABLE IF EXISTS v27_max_cycle_no;


-- ---------------------------------------------------------------------
-- 步骤 4：哺乳中周期 → litters
--
-- 只建 NURSING 窝。已断奶周期不补窝：窝表服务的是"在哺乳、要分笼"的在途
-- 业务，历史产仔数据仍由周期列和事件流承载，补历史窝只会凭空造出一批
-- 无人读取的行。
-- ---------------------------------------------------------------------
INSERT INTO litters (
  house_id, cycle_id, mother_rabbit_id, sire_rabbit_id, batch_id,
  birth_date, total_kits, live_kits, kept_kits, loss_count, current_nursing,
  status, nursing_cage_id, request_id, create_by, create_time, update_by, update_time
)
SELECT
  c.house_id, c.id, c.mother_rabbit_id, c.male_rabbit_id, c.batch_id,
  COALESCE(c.birth_date, c.stage_entered_at),
  c.total_kits, c.live_kits, c.live_kits,
  GREATEST(c.total_kits - c.live_kits, 0),
  c.current_nursing_kits,
  'NURSING', r.cage_id,
  CONCAT('v27-lt-', c.id),
  'v27', NOW(), 'v27', NOW()
FROM breeding_cycles c
JOIN rabbits r ON r.id = c.mother_rabbit_id
WHERE c.lifecycle = 'OPEN'
  AND c.stage = 'AWAIT_WEANING'
ON DUPLICATE KEY UPDATE litters.id = litters.id;


-- ---------------------------------------------------------------------
-- 步骤 5：4 张记录表 → repro_events（业务裁定：只迁近 6 个月）
--
-- 更早的历史留在 4 张只读旧表里可查，不进事件流——事件流是给操作者看的
-- 近期留痕，把三年前的记录灌进来只会拖慢按兔查询而无人翻阅。
--
-- from_stage / to_stage 一律留空：旧表根本没记录阶段迁移，凭 status 反推
-- 会把猜测伪装成事实。payload 只放旧表真实存在的字段。
-- ---------------------------------------------------------------------

-- 5a 摸胎
INSERT INTO repro_events (
  house_id, cycle_id, mother_rabbit_id, batch_id, event_type,
  occurred_at, payload, operator_name, request_id, create_time
)
SELECT
  COALESCE(p.house_id, b.house_id), p.breeding_cycle_id, p.rabbit_id, p.batch_id,
  CASE
    WHEN p.result IN ('怀孕确认', '怀孕') THEN 'PALPATION_PREGNANT'
    WHEN p.result = '空怀'                THEN 'PALPATION_EMPTY'
    ELSE 'PALPATION_UNSURE'
  END,
  p.check_date,
  JSON_OBJECT('legacyResult', p.result, 'backfilledFrom', 'pregnancy_check_records'),
  COALESCE(p.create_by, 'system'),
  CONCAT('v27-pcr-', p.id),
  NOW()
FROM pregnancy_check_records p
JOIN batches b ON b.id = p.batch_id
WHERE p.check_date >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
ON DUPLICATE KEY UPDATE repro_events.id = repro_events.id;

-- 5b 备产
INSERT INTO repro_events (
  house_id, cycle_id, mother_rabbit_id, batch_id, event_type,
  occurred_at, payload, operator_name, request_id, create_time
)
SELECT
  COALESCE(p.house_id, b.house_id), p.breeding_cycle_id, p.rabbit_id, p.batch_id,
  'PREPARTUM_DONE',
  p.action_date,
  JSON_OBJECT('backfilledFrom', 'prepartum_records'),
  COALESCE(p.create_by, 'system'),
  CONCAT('v27-ppr-', p.id),
  NOW()
FROM prepartum_records p
JOIN batches b ON b.id = p.batch_id
WHERE p.action_date >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
ON DUPLICATE KEY UPDATE repro_events.id = repro_events.id;

-- 5c 分娩（活仔为 0 记为分娩失败，与 V2 的 DELIVERY_FAILED 对齐）
INSERT INTO repro_events (
  house_id, cycle_id, mother_rabbit_id, batch_id, event_type,
  occurred_at, payload, operator_name, request_id, create_time
)
SELECT
  COALESCE(p.house_id, b.house_id), p.breeding_cycle_id, p.rabbit_id, p.batch_id,
  CASE WHEN p.live_kits > 0 THEN 'DELIVERY_DONE' ELSE 'DELIVERY_FAILED' END,
  p.birth_date,
  JSON_OBJECT('totalKits', p.total_kits, 'liveKits', p.live_kits,
              'backfilledFrom', 'parturition_records'),
  COALESCE(p.create_by, 'system'),
  CONCAT('v27-par-', p.id),
  NOW()
FROM parturition_records p
JOIN batches b ON b.id = p.batch_id
WHERE p.birth_date >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
ON DUPLICATE KEY UPDATE repro_events.id = repro_events.id;

-- 5d 分笼（weaning_date 可空，退到记录创建时间）
INSERT INTO repro_events (
  house_id, cycle_id, mother_rabbit_id, batch_id, event_type,
  occurred_at, payload, operator_name, request_id, create_time
)
SELECT
  COALESCE(w.house_id, b.house_id), w.breeding_cycle_id, w.rabbit_id, w.batch_id,
  'WEANING_DONE',
  COALESCE(w.weaning_date, w.create_time),
  JSON_OBJECT('weaningCount', w.weaning_count, 'avgWeight', w.avg_weight,
              'backfilledFrom', 'weaning_records'),
  COALESCE(w.create_by, 'system'),
  CONCAT('v27-wr-', w.id),
  NOW()
FROM weaning_records w
JOIN batches b ON b.id = w.batch_id
WHERE COALESCE(w.weaning_date, w.create_time) >= DATE_SUB(NOW(), INTERVAL 6 MONTH)
ON DUPLICATE KEY UPDATE repro_events.id = repro_events.id;


-- ---------------------------------------------------------------------
-- 步骤 6：生成 work_tasks
--
-- 待办中心要一次性接管三处分散的提醒：breeding_cycles.next_event_*、
-- batch_rabbits.next_event_*、replacement_records.expected_mature_date。
-- 少接管任何一处，对应的提醒在 P4 切换后就会静默消失。
--
-- 到期日优先沿用旧的 next_event_date（操作者已经看惯了那个日期）；缺失时
-- 按房级配置重算，配置也缺失则用与 SettingService 一致的内置默认值。
-- 已逾期的一律拉到今天，与 DueDateCalculator 的"过去日期上提到今日"同规则。
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS v27_settings;
CREATE TEMPORARY TABLE v27_settings (
  house_id BIGINT NOT NULL PRIMARY KEY,
  estrus_days INT NOT NULL,
  palpation_days INT NOT NULL,
  gestation_days INT NOT NULL,
  prepartum_days INT NOT NULL,
  weaning_days INT NOT NULL,
  sale_days INT NOT NULL
) ENGINE=InnoDB;

INSERT INTO v27_settings
SELECT
  h.id,
  COALESCE(g.aphrodisiac_days, 2),
  COALESCE(g.palpation_days, 12),
  COALESCE(g.gestation_days, 30),
  COALESCE(g.prepartum_days, 3),
  COALESCE(g.weaning_days, 25),
  COALESCE(g.sale_days, 30)
FROM rabbit_houses h
LEFT JOIN global_setting g ON g.house_id = h.id;

-- 6a 管线任务（挂周期）
INSERT INTO work_tasks (
  house_id, task_type, subject_type, subject_id,
  cycle_id, rabbit_id, batch_id, cage_id,
  due_date, due_time, status, dedup_key,
  create_by, create_time, update_by, update_time
)
SELECT
  c.house_id,
  t.task_type,
  'CYCLE',
  c.id,
  c.id, c.mother_rabbit_id, c.batch_id, r.cage_id,
  GREATEST(DATE(t.due_at), CURDATE()),
  GREATEST(t.due_at, NOW()),
  'PENDING',
  CONCAT('cycle:', c.id, ':', t.task_type),
  'v27', NOW(), 'v27', NOW()
FROM breeding_cycles c
JOIN rabbits r ON r.id = c.mother_rabbit_id
JOIN v27_settings s ON s.house_id = c.house_id
JOIN LATERAL (
  SELECT
    CASE c.stage
      WHEN 'AWAIT_ESTRUS'    THEN 'ESTRUS'
      WHEN 'AWAIT_MATING'    THEN 'MATING'
      WHEN 'AWAIT_PALPATION' THEN 'PALPATION'
      WHEN 'AWAIT_PREPARTUM' THEN 'PREPARTUM'
      WHEN 'AWAIT_DELIVERY'  THEN 'DELIVERY'
    END AS task_type,
    COALESCE(
      c.next_event_date,
      CASE c.stage
        WHEN 'AWAIT_ESTRUS'    THEN c.stage_entered_at
        WHEN 'AWAIT_MATING'    THEN DATE_ADD(c.stage_entered_at, INTERVAL s.estrus_days DAY)
        WHEN 'AWAIT_PALPATION' THEN DATE_ADD(COALESCE(c.mating_date, c.stage_entered_at),
                                             INTERVAL s.palpation_days DAY)
        WHEN 'AWAIT_PREPARTUM' THEN DATE_SUB(
                                      COALESCE(c.expected_birth_date,
                                               DATE_ADD(c.mating_date, INTERVAL s.gestation_days DAY)),
                                      INTERVAL s.prepartum_days DAY)
        WHEN 'AWAIT_DELIVERY'  THEN COALESCE(c.expected_birth_date,
                                             DATE_ADD(c.mating_date, INTERVAL s.gestation_days DAY))
      END,
      NOW()
    ) AS due_at
) t ON TRUE
WHERE c.lifecycle = 'OPEN'
  AND c.stage IN ('AWAIT_ESTRUS', 'AWAIT_MATING', 'AWAIT_PALPATION',
                  'AWAIT_PREPARTUM', 'AWAIT_DELIVERY')
ON DUPLICATE KEY UPDATE work_tasks.id = work_tasks.id;

-- 6b 分笼任务（挂窝，而非挂周期：血配时同一母兔要能同时持有两条待办）
INSERT INTO work_tasks (
  house_id, task_type, subject_type, subject_id,
  cycle_id, rabbit_id, batch_id, cage_id,
  due_date, due_time, status, dedup_key,
  create_by, create_time, update_by, update_time
)
SELECT
  l.house_id, 'WEANING', 'LITTER', l.id,
  l.cycle_id, l.mother_rabbit_id, l.batch_id, r.cage_id,
  GREATEST(DATE(COALESCE(c.next_event_date,
                         DATE_ADD(l.birth_date, INTERVAL s.weaning_days DAY))), CURDATE()),
  GREATEST(COALESCE(c.next_event_date,
                    DATE_ADD(l.birth_date, INTERVAL s.weaning_days DAY)), NOW()),
  'PENDING',
  CONCAT('litter:', l.id, ':WEANING'),
  'v27', NOW(), 'v27', NOW()
FROM litters l
JOIN breeding_cycles c ON c.id = l.cycle_id
JOIN rabbits r ON r.id = l.mother_rabbit_id
JOIN v27_settings s ON s.house_id = l.house_id
WHERE l.status = 'NURSING'
ON DUPLICATE KEY UPDATE work_tasks.id = work_tasks.id;

-- 6c 后备兔成熟（原由 EventReminderScanJob 夜扫 replacement_records）
INSERT INTO work_tasks (
  house_id, task_type, subject_type, subject_id,
  rabbit_id, cage_id, due_date, due_time, status, dedup_key,
  create_by, create_time, update_by, update_time
)
SELECT
  rr.house_id, 'REPLACEMENT_MATURE', 'RABBIT', rr.rabbit_id,
  rr.rabbit_id, r.cage_id,
  GREATEST(DATE(rr.expected_mature_date), CURDATE()),
  GREATEST(rr.expected_mature_date, NOW()),
  'PENDING',
  CONCAT('rabbit:', rr.rabbit_id, ':REPLACEMENT_MATURE'),
  'v27', NOW(), 'v27', NOW()
FROM replacement_records rr
JOIN rabbits r ON r.id = rr.rabbit_id
WHERE r.is_active = TRUE
  AND r.type = '1'
ON DUPLICATE KEY UPDATE work_tasks.id = work_tasks.id;

-- 6d 商品兔可售（入栏日 + sale_days）
INSERT INTO work_tasks (
  house_id, task_type, subject_type, subject_id,
  rabbit_id, cage_id, due_date, due_time, status, dedup_key,
  create_by, create_time, update_by, update_time
)
SELECT
  r.house_id, 'SALE_READY', 'RABBIT', r.id,
  r.id, r.cage_id,
  GREATEST(DATE(DATE_ADD(COALESCE(r.arrival_date, r.create_time), INTERVAL s.sale_days DAY)), CURDATE()),
  GREATEST(DATE_ADD(COALESCE(r.arrival_date, r.create_time), INTERVAL s.sale_days DAY), NOW()),
  'PENDING',
  CONCAT('rabbit:', r.id, ':SALE_READY'),
  'v27', NOW(), 'v27', NOW()
FROM rabbits r
JOIN v27_settings s ON s.house_id = r.house_id
WHERE r.is_active = TRUE
  AND r.type = '2'
ON DUPLICATE KEY UPDATE work_tasks.id = work_tasks.id;

DROP TEMPORARY TABLE IF EXISTS v27_settings;


-- ---------------------------------------------------------------------
-- 步骤 7：rabbits 读模型投影
--
-- 投影口径必须与 ReproStateMachineService.projectMother 完全一致，否则
-- P4 切换后第一次写入就会把投影"纠正"成另一个值，看起来像数据跳变：
--   管线周期优先 → 否则仅哺乳（AWAIT_WEANING）→ 否则 READY。
-- 只动有周期的母兔；其余兔子的投影列留空，避免给商品兔/后备兔硬套繁殖阶段。
-- ---------------------------------------------------------------------
UPDATE rabbits r
LEFT JOIN breeding_cycles pipeline
  ON pipeline.house_id = r.house_id
 AND pipeline.mother_rabbit_id = r.id
 AND pipeline.lifecycle = 'OPEN'
 AND pipeline.stage IN ('AWAIT_ESTRUS', 'AWAIT_MATING', 'AWAIT_PALPATION',
                        'AWAIT_PREPARTUM', 'AWAIT_DELIVERY')
LEFT JOIN breeding_cycles nursing
  ON nursing.house_id = r.house_id
 AND nursing.mother_rabbit_id = r.id
 AND nursing.lifecycle = 'OPEN'
 AND nursing.stage = 'AWAIT_WEANING'
SET
  r.current_stage = COALESCE(pipeline.stage, nursing.stage, 'READY'),
  r.current_cycle_id = COALESCE(pipeline.id, nursing.id),
  r.stage_entered_at = COALESCE(pipeline.stage_entered_at, nursing.stage_entered_at)
WHERE EXISTS (
  SELECT 1 FROM breeding_cycles c
  WHERE c.house_id = r.house_id AND c.mother_rabbit_id = r.id
);

-- 最近一次配种日：公母双方都要，公兔那侧供"这只公兔多久没用了"的排查。
UPDATE rabbits r
JOIN (
  SELECT house_id, mother_rabbit_id AS rabbit_id, MAX(mating_date) AS d
  FROM breeding_cycles WHERE mating_date IS NOT NULL
  GROUP BY house_id, mother_rabbit_id
  UNION ALL
  SELECT house_id, male_rabbit_id, MAX(mating_date)
  FROM breeding_cycles WHERE mating_date IS NOT NULL AND male_rabbit_id IS NOT NULL
  GROUP BY house_id, male_rabbit_id
) m ON m.house_id = r.house_id AND m.rabbit_id = r.id
SET r.last_mating_date = GREATEST(COALESCE(r.last_mating_date, m.d), m.d);


-- ---------------------------------------------------------------------
-- 步骤 8：约束收紧（对账闸门）
--
-- 走仓库自 V20/V25 起的 information_schema 守卫写法，保证可重跑。
-- 若 uk_bc_pipeline 因重复值失败，说明存量数据违反了「一只母兔同时只能有
-- 一个在途管线周期」，需人工裁决后重跑，排查 SQL 见
-- docs/doe-breeding-v2-backfill-runbook.md。
--
-- 注意 uk_bc_pipeline 对旧写路径是<b>行为中性</b>的：旧代码插入的周期 stage 为
-- NULL，pipeline_guard 随之为 NULL，而 MySQL 的唯一键不约束 NULL。这正是想要的
-- 性质：计划明确 P4 才是唯一行为变更点，V27 不得让任何原本能写入的旧请求失败。
-- 代价是 P3→P4 期间新增的旧周期不受该键保护，P4 切换前需补一次定级
-- （见 runbook §6）。
-- ---------------------------------------------------------------------

-- 兜底：确保存量行全部定级（步骤 1/2 的 CASE 已覆盖全集，这里只防未知词汇）。
UPDATE breeding_cycles SET stage = 'AWAIT_MATING' WHERE stage IS NULL;
UPDATE breeding_cycles SET stage_entered_at = update_time WHERE stage_entered_at IS NULL;

SELECT COUNT(*) INTO @cnt FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'breeding_cycles'
  AND index_name = 'uk_bc_pipeline';
SET @sql = IF(@cnt = 0,
  'ALTER TABLE breeding_cycles ADD UNIQUE KEY uk_bc_pipeline (house_id, pipeline_guard)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------
-- 关于 uk_bc_batch_member：计划要求建，这里故意不建。
--
-- V26 里 batch_member_guard = 「所有 OPEN 周期的 batch_id:mother_id」，包括
-- 哺乳中的周期。而 pipeline_guard 是刻意把 AWAIT_WEANING 排除在外的，
-- 就是为了放行血配：母兔一边哺乳上一窝、一边已经重新配上种。
--
-- 两者直接矛盾：若建 uk_bc_batch_member，同一批次内的血配就会被唯一键
-- 挡住，等于把 pipeline_guard 好不容易留出的口子又堵回去。本条是被
-- V27BackfillIT.pipelineCycleWinsOverNursingInTheRabbitProjection 实测出来的，
-- 不是推演：它报 Duplicate entry '1-1:1'。
--
-- 退一步说，即使给 batch_member_guard 同样排掉 AWAIT_WEANING，它也是冗余的：
-- pipeline_guard 已经保证「一只母兔全厅只能有一个在途管线周期」，范围
-- 严于「同批次内唯一」。再加一个冗余唯一键只会白白拖慢写入。
--
-- 生成列 batch_member_guard 保留（不删）：它仍是定位「同批次同母兔多个
-- OPEN 周期」的现成排查手柄，只是用于诊断，不用于禁止。
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 关于 stage / stage_entered_at 的 NOT NULL 收紧：计划放在这里，实际必须推迟到 V28。
--
-- P3 结束到 P4 之间，旧写路径仍然在线（开关要到 P4 才翻）。而批次模块的
-- BreedingCycleMapper.xml 插入周期时根本不写 stage，一旦收紧，线上每一次配种
-- 都会当场报 "Field 'stage' doesn't have a default value"。
--
-- 这不是推演：本步最初按计划写了收紧，全量 e2e 直接红了 16 个用例，全部是
-- 旧配种/分娩/分笼链路。
--
-- 也不能用 DEFAULT 绕过：给 stage 定一个默认值等于替旧代码猜阶段，猜错就是
-- 静默的状态损坏，比报错更难查。
--
-- 因此正确的顺序是：旧写路径在 P4 变成适配器、所有写入都经过状态机之后，
-- 再由 V28 收紧。已同步回写施工计划 §3.4。
-- ---------------------------------------------------------------------

