-- ---------------------------------------------------------------------------
-- vaccination_records —— 疫苗接种记录（飞书 recvt7fpa64K76）
-- ---------------------------------------------------------------------------
-- 与 treatment_records 分表而不是合表，理由有三条：
--
-- 1. 基数不同。治疗是一兔一诊断，疫苗是一针打一笼/一批，同一支疫苗、同一批号、
--    同一天覆盖成百上千只兔。合表后 diagnosis/drug/dose 对批量接种全部为空，
--    而 vaccine_batch_no 对治疗全部为空，两边互相塞 NULL。
-- 2. 生命周期不同。治疗有 OPEN -> DONE 的复查闭环，并且 TreatmentService 会写
--    rabbit_status_history 把兔只状态从「在栏」改成「治疗」。接种是瞬时事件，
--    打完针的兔子仍然「在栏」，绝不能因为记一针就改状态。
-- 3. 查询会互相污染。treatment_records 的 idx_tr_house_review 服务「待复查」，
--    合表后每次复查扫描都要跨过数量级更大的接种行。
--
-- 下次接种日期不进 work_tasks，沿用 treatment_records.next_review_date 的做法：
-- BatchController 的注释已经写明「治疗复查暂时仍走治疗记录」，接种属于同一层级。
-- status 表达的是「这条记录是否还欠一针」：
--   SCHEDULED —— 填了 next_due_date，下一针尚未补种，进「待接种」列表
--   DONE      —— 无需下一针，或已被同一疫苗的新记录接替
CREATE TABLE IF NOT EXISTS vaccination_records (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id         BIGINT NOT NULL,
  rabbit_id        BIGINT NOT NULL,
  vaccine_name     VARCHAR(100) NOT NULL,
  vaccine_batch_no VARCHAR(64),
  dose             VARCHAR(50),
  route            VARCHAR(20),
  vaccinated_at    DATETIME NOT NULL,
  next_due_date    DATETIME,
  status           VARCHAR(20) NOT NULL DEFAULT 'DONE',
  remark           TEXT,
  request_id       VARCHAR(64),
  create_by        VARCHAR(64),
  create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by        VARCHAR(64),
  update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- 与 uk_tr_req 同形。批量接种时一个 requestId 覆盖 N 只兔，rabbit_id 不同因此
  -- 各自成行；整批重放则整批命中唯一键，幂等语义天然成立。
  UNIQUE KEY uk_vr_req (house_id, rabbit_id, request_id),
  KEY idx_vr_house_due (house_id, status, next_due_date),
  KEY idx_vr_rabbit (rabbit_id, vaccinated_at),
  KEY idx_vr_house_vaccine (house_id, rabbit_id, vaccine_name, status),
  CONSTRAINT fk_vr_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_vr_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
