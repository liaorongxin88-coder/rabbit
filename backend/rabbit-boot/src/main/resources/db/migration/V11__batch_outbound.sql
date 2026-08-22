ALTER TABLE cages
  ADD COLUMN row_code VARCHAR(40) NULL AFTER cage_number,
  ADD COLUMN layer_index INT NULL AFTER row_code,
  ADD COLUMN position_index INT NULL AFTER layer_index,
  ADD KEY idx_cages_house_row_position (house_id, row_code, position_index, layer_index);

UPDATE cages
SET row_code = 'LEGACY',
    layer_index = 1,
    position_index = id
WHERE row_code IS NULL;

ALTER TABLE rabbits
  ADD COLUMN state_version BIGINT NOT NULL DEFAULT 0 AFTER weight;

ALTER TABLE sale_order_items
  ADD COLUMN cage_id_snapshot BIGINT NULL AFTER rabbit_id,
  ADD COLUMN cage_number_snapshot VARCHAR(50) NULL AFTER cage_id_snapshot,
  ADD COLUMN row_code_snapshot VARCHAR(40) NULL AFTER cage_number_snapshot,
  ADD COLUMN layer_index_snapshot INT NULL AFTER row_code_snapshot,
  ADD COLUMN position_index_snapshot INT NULL AFTER layer_index_snapshot,
  ADD COLUMN rabbit_type_snapshot VARCHAR(1) NULL AFTER position_index_snapshot,
  ADD COLUMN stage_snapshot VARCHAR(30) NULL AFTER rabbit_type_snapshot,
  ADD COLUMN parallel_status_snapshot VARCHAR(120) NULL AFTER stage_snapshot,
  ADD COLUMN state_version_snapshot BIGINT NULL AFTER parallel_status_snapshot,
  ADD COLUMN early_sale BOOLEAN NOT NULL DEFAULT FALSE AFTER state_version_snapshot,
  ADD COLUMN early_sale_reason VARCHAR(300) NULL AFTER early_sale,
  ADD COLUMN batch_id_snapshot BIGINT NULL AFTER early_sale_reason,
  -- Precondition: historical data must not contain duplicate (sale_order_id, rabbit_id) rows.
  -- This DDL intentionally aborts if the documented preflight query finds duplicates.
  ADD UNIQUE KEY uk_sale_order_rabbit (sale_order_id, rabbit_id);

CREATE TABLE outbound_tasks (
  task_id VARCHAR(36) PRIMARY KEY,
  house_id BIGINT NOT NULL,
  operator_id BIGINT NOT NULL,
  entry_type VARCHAR(16) NOT NULL,
  source_rabbit_id BIGINT NULL,
  source_cage_id BIGINT NULL,
  source_row_code VARCHAR(40) NULL,
  status VARCHAR(24) NOT NULL,
  revision BIGINT NOT NULL DEFAULT 0,
  sale_time DATETIME NULL,
  total_weight DOUBLE NULL,
  unit_price DECIMAL(10,2) NULL,
  customer VARCHAR(100) NULL,
  remark TEXT NULL,
  request_id VARCHAR(64) NULL,
  sale_order_id BIGINT NULL,
  snapshot_time DATETIME NULL,
  completed_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_outbound_task_house_operator (house_id, operator_id, status, update_time),
  KEY idx_outbound_task_sale_order (sale_order_id),
  CONSTRAINT fk_outbound_task_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_outbound_task_operator FOREIGN KEY (operator_id) REFERENCES sys_user (user_id),
  CONSTRAINT fk_outbound_task_sale_order FOREIGN KEY (sale_order_id) REFERENCES sale_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE outbound_task_items (
  task_id VARCHAR(36) NOT NULL,
  rabbit_id BIGINT NOT NULL,
  state_version BIGINT NOT NULL,
  selection_type VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  early_sale_reason VARCHAR(300) NULL,
  cage_id_snapshot BIGINT NOT NULL,
  cage_number_snapshot VARCHAR(50) NOT NULL,
  row_code_snapshot VARCHAR(40) NOT NULL,
  layer_index_snapshot INT NULL,
  position_index_snapshot INT NULL,
  stage_snapshot VARCHAR(30) NULL,
  batch_id_snapshot BIGINT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (task_id, rabbit_id),
  KEY idx_outbound_item_rabbit (rabbit_id),
  CONSTRAINT fk_outbound_item_task FOREIGN KEY (task_id) REFERENCES outbound_tasks (task_id),
  CONSTRAINT fk_outbound_item_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE outbound_requests (
  request_id VARCHAR(64) PRIMARY KEY,
  house_id BIGINT NOT NULL,
  task_id VARCHAR(36) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  sale_order_id BIGINT NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(500) NULL,
  conflicts_json TEXT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_outbound_request_task (task_id, update_time),
  KEY idx_outbound_request_sale_order (sale_order_id),
  CONSTRAINT fk_outbound_request_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_outbound_request_task FOREIGN KEY (task_id) REFERENCES outbound_tasks (task_id),
  CONSTRAINT fk_outbound_request_sale_order FOREIGN KEY (sale_order_id) REFERENCES sale_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
