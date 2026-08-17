-- 母兔生产流程 V2 —— P1 纯加法迁移（docs/doe-breeding-v2-implementation-plan.md §2）
--
-- 本迁移只做加法：新建 4 张表 + 为 4 张既有表加列。刻意不做的事：
--   * 不加 uk_bc_pipeline / uk_bc_batch_member 唯一键 —— 存量数据尚未回填 stage/lifecycle，
--     可能违反新不变式；唯一键在 V27 回填并对账通过后才收紧。
--   * 不收紧 stage / stage_entered_at 为 NOT NULL —— 同上，V27 回填后再收紧。
--   * 不动 batches.status、不退役 batch_rabbits —— 属 V27/V28 范畴。
-- 因此本迁移执行后生产行为零变化：新列对旧代码不可见，新表无写入方。

-- ---------------------------------------------------------------------------
-- 1. repro_events —— 操作事件流（append-only，权威留痕 + 幂等兜底 + 可重放）
-- ---------------------------------------------------------------------------
-- 无外键：事件流须在业务行归档/删除后依然可查，且回填期允许指向尚未建立的 litter。
CREATE TABLE repro_events (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id        BIGINT,
  house_id         BIGINT NOT NULL,
  cycle_id         BIGINT,
  litter_id        BIGINT,
  mother_rabbit_id BIGINT NOT NULL,
  batch_id         BIGINT,
  event_type       VARCHAR(32) NOT NULL,
  from_stage       VARCHAR(20),
  to_stage         VARCHAR(20),
  occurred_at      DATETIME NOT NULL,
  payload          JSON,
  operator_id      BIGINT,
  operator_name    VARCHAR(64) NOT NULL,
  request_id       VARCHAR(64) NOT NULL,
  create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_re_request (house_id, request_id),
  KEY idx_re_cycle (house_id, cycle_id, id),
  KEY idx_re_mother_time (house_id, mother_rabbit_id, occurred_at),
  KEY idx_re_house_time (house_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- 2. litters —— 窝（分娩产物；哺乳段与管线段解耦，支撑血配）
-- ---------------------------------------------------------------------------
CREATE TABLE litters (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id          BIGINT,
  house_id           BIGINT NOT NULL,
  cycle_id           BIGINT NOT NULL,
  mother_rabbit_id   BIGINT NOT NULL,
  sire_rabbit_id     BIGINT,
  batch_id           BIGINT,
  birth_date         DATETIME NOT NULL,
  total_kits         INT NOT NULL,
  live_kits          INT NOT NULL,
  kept_kits          INT NOT NULL,
  foster_in          INT NOT NULL DEFAULT 0,
  foster_out         INT NOT NULL DEFAULT 0,
  loss_count         INT NOT NULL DEFAULT 0,
  current_nursing    INT NOT NULL DEFAULT 0,
  status             VARCHAR(10) NOT NULL,
  weaning_date       DATETIME,
  weaned_count       INT,
  avg_weaning_weight DOUBLE,
  nursing_cage_id    BIGINT,
  request_id         VARCHAR(64),
  remark             TEXT,
  create_by          VARCHAR(64),
  create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by          VARCHAR(64),
  update_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_lt_cycle (house_id, cycle_id),
  KEY idx_lt_status (house_id, status, birth_date),
  KEY idx_lt_mother (house_id, mother_rabbit_id, birth_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- 3. work_tasks —— 统一任务/提醒中心（取代夜间扫表 EventReminderScanJob）
-- ---------------------------------------------------------------------------
-- idx_wt_due 是首页今日待办的唯一访问路径；idx_wt_cage 服务 NFC 碰笼直查。
CREATE TABLE work_tasks (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id          BIGINT,
  house_id           BIGINT NOT NULL,
  task_type          VARCHAR(32) NOT NULL,
  subject_type       VARCHAR(16) NOT NULL,
  subject_id         BIGINT NOT NULL,
  cycle_id           BIGINT,
  rabbit_id          BIGINT,
  batch_id           BIGINT,
  cage_id            BIGINT,
  due_date           DATE NOT NULL,
  due_time           DATETIME NOT NULL,
  status             VARCHAR(12) NOT NULL DEFAULT 'PENDING',
  snooze_count       INT NOT NULL DEFAULT 0,
  completed_event_id BIGINT,
  dedup_key          VARCHAR(96) NOT NULL,
  remark             TEXT,
  create_by          VARCHAR(64),
  create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by          VARCHAR(64),
  update_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wt_dedup (house_id, dedup_key),
  KEY idx_wt_due (house_id, status, due_date, task_type),
  KEY idx_wt_subject (house_id, subject_type, subject_id, status),
  KEY idx_wt_cage (house_id, cage_id, status),
  KEY idx_wt_batch (house_id, batch_id, status, task_type),
  KEY idx_wt_rabbit (house_id, rabbit_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- 4. biz_attachments —— 通用业务附件（流产照片等；事件 payload 只存 file_id 引用）
-- ---------------------------------------------------------------------------
CREATE TABLE biz_attachments (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id    BIGINT,
  house_id     BIGINT NOT NULL,
  biz_type     VARCHAR(32) NOT NULL,
  biz_id       BIGINT NOT NULL,
  file_id      VARCHAR(128) NOT NULL,
  file_name    VARCHAR(255),
  content_type VARCHAR(100),
  file_size    BIGINT,
  sort_no      INT NOT NULL DEFAULT 0,
  request_id   VARCHAR(64),
  create_by    VARCHAR(64),
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ba_biz_file (house_id, biz_type, biz_id, file_id),
  KEY idx_ba_biz (house_id, biz_type, biz_id, sort_no, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------------
-- 5. breeding_cycles —— 周期主状态加列
-- ---------------------------------------------------------------------------
-- stage/stage_entered_at 先可空：存量行无从推断，V27 回填后收紧 NOT NULL。
-- lifecycle 默认 OPEN 会把存量已结束周期也标成 OPEN，这是刻意的：V27 步骤 1 按旧
-- status 一次性改写；在那之前无人读该列，且本迁移不加依赖它的唯一键。
ALTER TABLE breeding_cycles
  ADD COLUMN tenant_id BIGINT NULL AFTER id,
  ADD COLUMN stage VARCHAR(20) NULL AFTER status,
  ADD COLUMN stage_entered_at DATETIME NULL AFTER stage,
  ADD COLUMN lifecycle VARCHAR(10) NOT NULL DEFAULT 'OPEN' AFTER stage_entered_at,
  ADD COLUMN result VARCHAR(10) NULL AFTER lifecycle,
  ADD COLUMN mating_method VARCHAR(10) NULL AFTER result,
  ADD COLUMN state_version BIGINT NOT NULL DEFAULT 0 AFTER mating_method;

-- 生成列单独一条 ALTER：MySQL 要求被引用列在生成列之前已经存在于表定义中。
-- 两个 guard 现在都不带唯一键，纯粹为 V27 备好列，避免回填后再做一次表重建。
ALTER TABLE breeding_cycles
  ADD COLUMN pipeline_guard BIGINT GENERATED ALWAYS AS (
    CASE WHEN lifecycle = 'OPEN'
          AND stage IN ('AWAIT_ESTRUS', 'AWAIT_MATING', 'AWAIT_PALPATION',
                        'AWAIT_PREPARTUM', 'AWAIT_DELIVERY')
         THEN mother_rabbit_id END) STORED,
  ADD COLUMN batch_member_guard VARCHAR(64) GENERATED ALWAYS AS (
    CASE WHEN lifecycle = 'OPEN' AND batch_id IS NOT NULL
         THEN CONCAT(batch_id, ':', mother_rabbit_id) END) STORED;

ALTER TABLE breeding_cycles
  ADD KEY idx_bc_stage (house_id, lifecycle, stage, id),
  ADD KEY idx_bc_mother_lifecycle (house_id, mother_rabbit_id, lifecycle, id);

-- batch_id 放宽为可空（业务裁定 2026-08-16：散养母兔不归属任何批次）。
-- 这是放宽而非收紧：旧代码总是传值，行为零变化，因此属于纯加法范畴。
-- 它不能等到 V27：P2 的新写路径若建不了无批次周期，散养场景就无法验证。
-- 注：uk_bc_batch_mother_cycle 在 batch_id 为 NULL 时不去重（MySQL 语义），
-- 散养周期的周期号唯一性由应用层的 selectMaxCycleNo 保证。
ALTER TABLE breeding_cycles
  MODIFY COLUMN batch_id BIGINT NULL;

-- ---------------------------------------------------------------------------
-- 6. rabbits —— 读模型投影列（状态机服务是唯一写者）
-- ---------------------------------------------------------------------------
ALTER TABLE rabbits
  ADD COLUMN current_stage VARCHAR(20) NULL AFTER reproductive_stage,
  ADD COLUMN current_cycle_id BIGINT NULL AFTER current_stage,
  ADD COLUMN stage_entered_at DATETIME NULL AFTER current_cycle_id,
  ADD COLUMN last_mating_date DATETIME NULL AFTER stage_entered_at,
  ADD KEY idx_rabbits_house_current_stage (house_id, current_stage, id);

-- ---------------------------------------------------------------------------
-- 7. global_setting —— 妊娠天数配置化（消除 BatchService 里硬编码的 30 天）
-- ---------------------------------------------------------------------------
-- 物理表刻意不改名、旧列刻意不改名（施工计划 A8）：代码层先用新语义常量映射旧列，
-- 列改名推迟到 V29+，避免本期大面积改动 mapper XML。
ALTER TABLE global_setting
  ADD COLUMN gestation_days INT NOT NULL DEFAULT 30 AFTER palpation_days;

-- ---------------------------------------------------------------------------
-- 8. batches —— 标签化第一步：可归档标记
-- ---------------------------------------------------------------------------
-- status/start_date/end_date 本期保留不动（老 APK 仍在读），V28 才删除。
ALTER TABLE batches
  ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE AFTER end_date;
