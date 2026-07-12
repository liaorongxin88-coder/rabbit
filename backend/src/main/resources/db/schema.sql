CREATE DATABASE IF NOT EXISTS rabbit_app DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE rabbit_app;

CREATE TABLE IF NOT EXISTS merchants (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  contact_name VARCHAR(64),
  contact_phone VARCHAR(32),
  status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_merchants_status_id (status, id),
  KEY idx_merchants_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO merchants (name, contact_name, contact_phone, status, remark, create_by, update_by)
SELECT '默认商户', '系统初始化', '', 'ENABLED', '结构初始化创建', 'schema', 'schema'
WHERE NOT EXISTS (SELECT 1 FROM merchants);

CREATE TABLE IF NOT EXISTS sys_user (
  user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  user_name VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  openid VARCHAR(128),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_user_openid (openid),
  KEY idx_sys_user_merchant (merchant_id, user_id),
  CONSTRAINT fk_sys_user_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS platform_admins (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_name VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL DEFAULT 'SUPER_ADMIN',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  last_login_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_platform_admin_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rabbit_houses (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id BIGINT,
  name VARCHAR(100) NOT NULL,
  layout_rows INT NOT NULL DEFAULT 0,
  layout_cols INT NOT NULL DEFAULT 0,
  layout_layers INT NOT NULL DEFAULT 0,
  request_id VARCHAR(64),
  remark TEXT,
  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_house_creator_req (create_by, request_id),
  KEY idx_rabbit_houses_merchant (merchant_id, is_deleted, id),
  CONSTRAINT fk_rabbit_houses_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS house_users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  perms VARCHAR(10) NOT NULL DEFAULT 'view',
  is_admin BOOLEAN NOT NULL DEFAULT FALSE,
  join_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_house_user (house_id, user_id),
  KEY idx_user_id (user_id),
  CONSTRAINT fk_house_users_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_house_users_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  cage_number VARCHAR(50) NOT NULL,
  status VARCHAR(1) NOT NULL DEFAULT '0',
  rabbit_count INT NOT NULL DEFAULT 0,
  is_fed BOOLEAN NOT NULL DEFAULT FALSE,
  is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_house_cage_number (house_id, cage_number),
  KEY idx_house_id (house_id),
  CONSTRAINT fk_cages_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cage_nfc_tags (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  cage_id BIGINT NOT NULL,
  tag_uid VARCHAR(40) NOT NULL,
  request_id VARCHAR(64),
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cage_nfc_uid (house_id, tag_uid),
  UNIQUE KEY uk_cage_nfc_cage (house_id, cage_id),
  KEY idx_cage_nfc_house (house_id),
  KEY idx_cage_nfc_cage (cage_id),
  CONSTRAINT fk_cage_nfc_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_cage_nfc_cage FOREIGN KEY (cage_id) REFERENCES cages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS nfc_tags (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  tag_uid VARCHAR(40) NOT NULL,
  target_type VARCHAR(20) NOT NULL,
  target_id BIGINT,
  rabbit_id BIGINT,
  record_id BIGINT,
  request_id VARCHAR(64),
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_nfc_uid (house_id, tag_uid),
  UNIQUE KEY uk_nfc_target (house_id, target_type, target_id),
  KEY idx_nfc_house (house_id),
  KEY idx_nfc_target (target_type, target_id),
  CONSTRAINT fk_nfc_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS global_setting (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT,
  user_id BIGINT,
  aphrodisiac_days INT NOT NULL,
  palpation_days INT NOT NULL,
  prepartum_days INT NOT NULL,
  weaning_days INT NOT NULL,
  postpartum_days INT NOT NULL,
  sale_days INT NOT NULL,
  replacement_days INT NOT NULL,
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_setting_house (house_id),
  UNIQUE KEY uk_setting_user (user_id),
  CONSTRAINT fk_setting_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_setting_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rabbits (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  cage_id BIGINT NOT NULL,
  mother_id BIGINT,
  type VARCHAR(1) NOT NULL,
  gender VARCHAR(1) NOT NULL,
  breed VARCHAR(100),
  arrival_method VARCHAR(1),
  arrival_date DATETIME,
  weight DOUBLE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  is_quarantined BOOLEAN NOT NULL DEFAULT FALSE,
  quarantine_time DATETIME,
  quarantine_reason VARCHAR(200),
  request_id VARCHAR(64),
  departure_date DATETIME,
  departure_reason VARCHAR(20),
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_rabbits_house_id (house_id),
  KEY idx_rabbits_cage_id (cage_id),
  KEY idx_rabbits_active (is_active),
  KEY idx_rabbits_quarantined (is_quarantined),
  UNIQUE KEY uk_rabbit_req (house_id, request_id),
  CONSTRAINT fk_rabbits_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_rabbits_cage FOREIGN KEY (cage_id) REFERENCES cages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rabbit_departure_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  departure_type VARCHAR(20) NOT NULL,
  departure_date DATETIME NOT NULL,
  reason VARCHAR(200),
  remark TEXT,
  request_id VARCHAR(64),
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_departure_req (house_id, rabbit_id, request_id),
  KEY idx_departure_house_time (house_id, departure_date),
  KEY idx_departure_rabbit (rabbit_id),
  CONSTRAINT fk_departure_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_departure_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS batches (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  batch_code VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT '计划中',
  start_date DATETIME,
  end_date DATETIME,
  request_id VARCHAR(64),
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_batches_house_id (house_id),
  KEY idx_batches_status (status),
  UNIQUE KEY uk_batch_req (house_id, request_id),
  CONSTRAINT fk_batches_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS batch_rabbits (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  male_rabbit_id BIGINT,
  join_reason VARCHAR(20) NOT NULL,
  batch_role VARCHAR(20) NOT NULL,
  current_status VARCHAR(30) NOT NULL,
  last_event_date DATETIME,
  next_event_date DATETIME,
  next_event_type VARCHAR(30),
  is_event_notified BOOLEAN NOT NULL DEFAULT FALSE,
  event_notify_date DATETIME,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  join_date DATETIME NOT NULL,
  exit_date DATETIME,
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_batch_rabbits_batch (batch_id),
  KEY idx_batch_rabbits_rabbit (rabbit_id),
  KEY idx_batch_rabbits_next_event (next_event_date, next_event_type),
  KEY idx_batch_rabbits_active (is_active),
  CONSTRAINT fk_batch_rabbits_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
  CONSTRAINT fk_batch_rabbits_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pregnancy_check_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT,
  batch_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  check_date DATETIME NOT NULL,
  result VARCHAR(10) NOT NULL,
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_pcr_house_batch (house_id, batch_id, id),
  KEY idx_pcr_house_rabbit (house_id, rabbit_id, id),
  KEY idx_pcr_batch (batch_id),
  KEY idx_pcr_rabbit (rabbit_id),
  CONSTRAINT fk_pcr_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
  CONSTRAINT fk_pcr_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS parturition_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT,
  batch_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  birth_date DATETIME NOT NULL,
  total_kits INT NOT NULL DEFAULT 0,
  live_kits INT NOT NULL DEFAULT 0,
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_pr_house_batch (house_id, batch_id, id),
  KEY idx_pr_house_rabbit (house_id, rabbit_id, id),
  KEY idx_pr_batch (batch_id),
  KEY idx_pr_rabbit (rabbit_id),
  CONSTRAINT fk_pr_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
  CONSTRAINT fk_pr_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS prepartum_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT,
  batch_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  action_date DATETIME NOT NULL,
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_ppr_house_batch (house_id, batch_id, id),
  KEY idx_ppr_house_rabbit (house_id, rabbit_id, id),
  KEY idx_ppr_batch (batch_id),
  KEY idx_ppr_rabbit (rabbit_id),
  CONSTRAINT fk_ppr_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
  CONSTRAINT fk_ppr_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS weaning_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT,
  batch_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  target_cage_id BIGINT,
  in_cage_id BIGINT,
  weaning_date DATETIME,
  weaning_count INT NOT NULL,
  waiting_count INT NOT NULL DEFAULT 0,
  avg_weight DOUBLE,
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_wr_house_batch (house_id, batch_id, id),
  KEY idx_wr_house_rabbit (house_id, rabbit_id, id),
  KEY idx_wr_batch (batch_id),
  KEY idx_wr_rabbit (rabbit_id),
  KEY idx_wr_target_cage (target_cage_id),
  KEY idx_wr_in_cage (in_cage_id),
  CONSTRAINT fk_wr_batch FOREIGN KEY (batch_id) REFERENCES batches (id),
  CONSTRAINT fk_wr_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS weaning_record_allocations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  weaning_record_id BIGINT NOT NULL,
  cage_id BIGINT NOT NULL,
  alloc_count INT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wra_record_cage (weaning_record_id, cage_id),
  KEY idx_wra_record (weaning_record_id),
  KEY idx_wra_cage (cage_id),
  CONSTRAINT fk_wra_wr FOREIGN KEY (weaning_record_id) REFERENCES weaning_records (id),
  CONSTRAINT fk_wra_cage FOREIGN KEY (cage_id) REFERENCES cages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS feed_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  item_id BIGINT,
  feeding_rabbits VARCHAR(300),
  feed_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  feed_type VARCHAR(100),
  unit VARCHAR(20),
  request_id VARCHAR(64),
  amount DECIMAL(10,2) NOT NULL,
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_feed_house (house_id),
  KEY idx_feed_time (feed_time),
  KEY idx_feed_item (item_id),
  CONSTRAINT fk_feed_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS feed_log_rabbits (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  feed_log_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  cage_id BIGINT,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_flr (feed_log_id, rabbit_id),
  KEY idx_flr_house_cage (house_id, cage_id, feed_log_id),
  KEY idx_flr_rabbit (rabbit_id),
  CONSTRAINT fk_flr_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_flr_feed FOREIGN KEY (feed_log_id) REFERENCES feed_logs (id),
  CONSTRAINT fk_flr_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  unit VARCHAR(20) NOT NULL,
  current_qty DECIMAL(12,3) NOT NULL DEFAULT 0.000,
  low_stock_qty DECIMAL(12,3),
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_inv_item (house_id, name),
  KEY idx_inv_house (house_id),
  CONSTRAINT fk_inv_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_txs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  tx_type VARCHAR(20) NOT NULL,
  qty_delta DECIMAL(12,3) NOT NULL,
  tx_time DATETIME NOT NULL,
  ref_table VARCHAR(100),
  ref_id BIGINT,
  remark TEXT,
  request_id VARCHAR(64),
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_inv_tx_house_time (house_id, tx_time),
  KEY idx_inv_tx_item (item_id, tx_time),
  UNIQUE KEY uk_inv_tx_req (house_id, item_id, request_id),
  CONSTRAINT fk_inv_tx_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_inv_tx_item FOREIGN KEY (item_id) REFERENCES inventory_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rabbit_status_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT,
  rabbit_id BIGINT NOT NULL,
  batch_id BIGINT,
  from_status VARCHAR(50),
  to_status VARCHAR(50) NOT NULL,
  change_time DATETIME NOT NULL,
  reason VARCHAR(255),
  related_record_id BIGINT,
  related_record_table VARCHAR(100),
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_rsh_house_rabbit_time (house_id, rabbit_id, change_time, id),
  KEY idx_rsh_house_batch_time (house_id, batch_id, change_time, id),
  KEY idx_rsh_rabbit (rabbit_id),
  KEY idx_rsh_batch (batch_id),
  KEY idx_rsh_time (change_time),
  CONSTRAINT fk_rsh_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rabbit_abnormal_conditions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rabbit_id BIGINT NOT NULL,
  house_id BIGINT NOT NULL,
  warning_status VARCHAR(50),
  warning_time DATETIME,
  img_url VARCHAR(255),
  remark VARCHAR(255),
  is_deal BOOLEAN DEFAULT FALSE,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_rac_house (house_id),
  KEY idx_rac_rabbit (rabbit_id),
  CONSTRAINT fk_rac_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_rac_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS treatment_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  start_date DATETIME NOT NULL,
  diagnosis VARCHAR(200),
  drug VARCHAR(200),
  dose VARCHAR(100),
  days INT,
  next_review_date DATETIME,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  remark TEXT,
  request_id VARCHAR(64),
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tr_req (house_id, rabbit_id, request_id),
  KEY idx_tr_house_review (house_id, next_review_date, status),
  KEY idx_tr_rabbit (rabbit_id),
  CONSTRAINT fk_tr_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_tr_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS weight_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  weigh_time DATETIME NOT NULL,
  weight_kg DOUBLE NOT NULL,
  remark TEXT,
  request_id VARCHAR(64),
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wl_req (house_id, rabbit_id, request_id),
  KEY idx_wl_house_time (house_id, weigh_time),
  KEY idx_wl_rabbit (rabbit_id),
  CONSTRAINT fk_wl_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_wl_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sale_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  sale_time DATETIME NOT NULL,
  customer VARCHAR(100),
  total_weight DOUBLE NOT NULL,
  unit_price DECIMAL(10,2),
  total_amount DECIMAL(12,2),
  remark TEXT,
  request_id VARCHAR(64),
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sale_req (house_id, request_id),
  KEY idx_sale_house_time (house_id, sale_time),
  CONSTRAINT fk_sale_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sale_order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sale_order_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  weight DOUBLE,
  price DECIMAL(10,2),
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_soi_order (sale_order_id),
  KEY idx_soi_rabbit (rabbit_id),
  CONSTRAINT fk_soi_order FOREIGN KEY (sale_order_id) REFERENCES sale_orders (id),
  CONSTRAINT fk_soi_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS breeding_performance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  total_litters INT NOT NULL DEFAULT 0,
  total_kits INT NOT NULL DEFAULT 0,
  total_live_kits INT NOT NULL DEFAULT 0,
  total_weaned INT NOT NULL DEFAULT 0,
  success_breeding_count INT NOT NULL DEFAULT 0,
  failed_breeding_count INT NOT NULL DEFAULT 0,
  avg_litter_size DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  avg_weaning_size DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  last_litter_date DATETIME,
  performance_score INT,
  remark TEXT,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_bp_house_rabbit (house_id, rabbit_id),
  KEY idx_bp_house (house_id),
  CONSTRAINT fk_bp_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_bp_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS replacement_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  original_type VARCHAR(1) NOT NULL DEFAULT '2',
  replacement_date DATETIME NOT NULL,
  expected_mature_date DATETIME NOT NULL,
  is_mature_notified BOOLEAN DEFAULT FALSE,
  mature_notify_date DATETIME,
  remark TEXT,
  create_by VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_rr_house (house_id),
  KEY idx_rr_expected (expected_mature_date),
  CONSTRAINT fk_rr_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_rr_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_acks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  category VARCHAR(32) NOT NULL,
  record_id BIGINT NOT NULL,
  action VARCHAR(16) NOT NULL,
  snooze_until DATETIME,
  ack_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  remark VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_event_ack_user_cat_record (user_id, category, record_id),
  KEY idx_event_ack_house (house_id),
  KEY idx_event_ack_snooze (snooze_until),
  CONSTRAINT fk_event_ack_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_event_ack_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS request_dedup (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  api VARCHAR(64) NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  error_message VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_request_dedup (house_id, user_id, api, request_id),
  KEY idx_request_dedup_house (house_id),
  KEY idx_request_dedup_user (user_id),
  KEY idx_request_dedup_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trace_id VARCHAR(64),
  user_id BIGINT,
  house_id BIGINT,
  method VARCHAR(10),
  path VARCHAR(255),
  query_string VARCHAR(1000),
  status INT,
  api_code INT,
  api_message VARCHAR(255),
  cost_ms BIGINT,
  error_message VARCHAR(500),
  ip VARCHAR(64),
  user_agent VARCHAR(255),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_house_time (house_id, create_time),
  KEY idx_audit_user_time (user_id, create_time),
  KEY idx_audit_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_reminder_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  category VARCHAR(32) NOT NULL,
  record_id BIGINT NOT NULL,
  event_date DATETIME,
  notify_date DATE NOT NULL,
  notify_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_erl_house_cat_record_date (house_id, category, record_id, notify_date),
  KEY idx_erl_house_date_id (house_id, notify_date, id),
  CONSTRAINT fk_erl_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
