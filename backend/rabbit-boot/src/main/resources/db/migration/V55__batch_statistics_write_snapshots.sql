-- Immutable write snapshots required by batch statistics.
ALTER TABLE litters
  ADD COLUMN weaning_total_weight_kg DECIMAL(12,3) NULL AFTER avg_weaning_weight,
  ADD CONSTRAINT chk_litter_weaning_total_weight
    CHECK (
      weaning_total_weight_kg IS NULL
      OR (weaned_count > 0 AND weaning_total_weight_kg > 0)
    );

ALTER TABLE sale_order_items
  ADD KEY idx_soi_batch_snapshot (batch_id_snapshot, sale_order_id, rabbit_id);

ALTER TABLE repro_events
  ADD KEY idx_re_event_time (event_type, occurred_at, id),
  ADD KEY idx_re_batch_event (house_id, batch_id, event_type, id);

ALTER TABLE feed_logs
  ADD UNIQUE KEY uk_feed_logs_id_house (id, house_id);

ALTER TABLE sale_orders
  ADD UNIQUE KEY uk_sale_orders_id_house (id, house_id);

ALTER TABLE batches
  ADD UNIQUE KEY uk_batches_id_house (id, house_id);

CREATE TABLE feed_log_batch_allocations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  feed_log_id BIGINT NOT NULL,
  house_id BIGINT NOT NULL,
  batch_id BIGINT NULL,
  batch_scope_id BIGINT GENERATED ALWAYS AS (IFNULL(batch_id, -1)) STORED,
  phase VARCHAR(16) NOT NULL,
  amount_kg DECIMAL(10,2) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_feed_batch_allocation (feed_log_id, batch_scope_id, phase),
  KEY idx_feed_batch_allocation_batch (house_id, batch_id, phase, created_at),
  KEY idx_feed_batch_allocation_log_fk (feed_log_id, house_id),
  KEY idx_feed_batch_allocation_batch_fk (batch_id, house_id),
  CONSTRAINT fk_feed_batch_allocation_log
    FOREIGN KEY (feed_log_id, house_id) REFERENCES feed_logs (id, house_id),
  CONSTRAINT fk_feed_batch_allocation_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_feed_batch_allocation_batch
    FOREIGN KEY (batch_id, house_id) REFERENCES batches (id, house_id),
  CONSTRAINT chk_feed_batch_allocation_amount CHECK (amount_kg > 0),
  CONSTRAINT chk_feed_batch_allocation_scope CHECK (
    (batch_id IS NULL AND phase = 'UNASSIGNED')
    OR (batch_id IS NOT NULL AND phase IN ('BREEDING', 'FATTENING'))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sale_order_batch_allocations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sale_order_id BIGINT NOT NULL,
  house_id BIGINT NOT NULL,
  batch_id BIGINT NULL,
  batch_scope_id BIGINT GENERATED ALWAYS AS (IFNULL(batch_id, -1)) STORED,
  rabbit_count INT NOT NULL,
  actual_weight_kg DECIMAL(12,3) NOT NULL,
  unit_price_per_kg DECIMAL(10,2) NULL,
  amount DECIMAL(12,2) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sale_batch_allocation (sale_order_id, batch_scope_id),
  KEY idx_sale_batch_allocation_batch (house_id, batch_id, created_at),
  KEY idx_sale_batch_allocation_order_fk (sale_order_id, house_id),
  KEY idx_sale_batch_allocation_batch_fk (batch_id, house_id),
  CONSTRAINT fk_sale_batch_allocation_order
    FOREIGN KEY (sale_order_id, house_id) REFERENCES sale_orders (id, house_id),
  CONSTRAINT fk_sale_batch_allocation_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_sale_batch_allocation_batch
    FOREIGN KEY (batch_id, house_id) REFERENCES batches (id, house_id),
  CONSTRAINT chk_sale_batch_allocation_count CHECK (rabbit_count > 0),
  CONSTRAINT chk_sale_batch_allocation_weight CHECK (actual_weight_kg > 0),
  CONSTRAINT chk_sale_batch_allocation_price CHECK (
    unit_price_per_kg IS NULL OR unit_price_per_kg > 0
  ),
  CONSTRAINT chk_sale_batch_allocation_amount CHECK (amount IS NULL OR amount >= 0),
  CONSTRAINT chk_sale_batch_allocation_money_pair CHECK (
    (unit_price_per_kg IS NULL AND amount IS NULL)
    OR (unit_price_per_kg IS NOT NULL AND amount IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE replacement_batch_allocations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  source_batch_id BIGINT NULL,
  source_batch_scope_id BIGINT GENERATED ALWAYS AS (IFNULL(source_batch_id, -1)) STORED,
  rabbit_count INT NOT NULL,
  total_weight_kg DECIMAL(12,3) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_replacement_batch_allocation (house_id, request_id, source_batch_scope_id),
  KEY idx_replacement_batch_allocation_batch (house_id, source_batch_id, created_at),
  KEY idx_replacement_batch_allocation_batch_fk (source_batch_id, house_id),
  CONSTRAINT fk_replacement_batch_allocation_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_replacement_batch_allocation_batch
    FOREIGN KEY (source_batch_id, house_id) REFERENCES batches (id, house_id),
  CONSTRAINT chk_replacement_batch_allocation_count CHECK (rabbit_count > 0),
  CONSTRAINT chk_replacement_batch_allocation_weight CHECK (total_weight_kg > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE batch_carcass_yield_versions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  yield_rate DECIMAL(7,6) NOT NULL,
  source_unit VARCHAR(100) NOT NULL,
  measured_date DATE NOT NULL,
  report_number VARCHAR(100) NULL,
  evidence_file VARCHAR(64) NULL,
  remark TEXT NULL,
  change_reason VARCHAR(300) NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_carcass_yield_request (house_id, request_id),
  KEY idx_carcass_yield_latest (house_id, batch_id, created_at, id),
  KEY idx_carcass_yield_batch_fk (batch_id, house_id),
  CONSTRAINT fk_carcass_yield_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_carcass_yield_batch
    FOREIGN KEY (batch_id, house_id) REFERENCES batches (id, house_id),
  CONSTRAINT chk_carcass_yield_rate CHECK (yield_rate > 0 AND yield_rate <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
