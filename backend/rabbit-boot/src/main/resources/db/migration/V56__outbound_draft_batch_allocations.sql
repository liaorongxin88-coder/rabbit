ALTER TABLE outbound_tasks
  ADD UNIQUE KEY uk_outbound_task_id_house (task_id, house_id);

CREATE TABLE outbound_task_batch_allocations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id VARCHAR(36) NOT NULL,
  house_id BIGINT NOT NULL,
  batch_id BIGINT NULL,
  batch_scope_id BIGINT GENERATED ALWAYS AS (IFNULL(batch_id, -1)) STORED,
  actual_weight_kg DECIMAL(12,3) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_outbound_task_batch_allocation (task_id, house_id, batch_scope_id),
  KEY idx_outbound_task_allocation_house (house_id, task_id),
  KEY idx_outbound_task_allocation_batch (house_id, batch_id),
  KEY idx_outbound_task_allocation_batch_fk (batch_id, house_id),
  CONSTRAINT fk_outbound_task_allocation_task
    FOREIGN KEY (task_id, house_id) REFERENCES outbound_tasks (task_id, house_id)
    ON DELETE CASCADE,
  CONSTRAINT fk_outbound_task_allocation_house
    FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_outbound_task_allocation_batch
    FOREIGN KEY (batch_id, house_id) REFERENCES batches (id, house_id),
  CONSTRAINT chk_outbound_task_allocation_weight
    CHECK (actual_weight_kg > 0 AND actual_weight_kg <= 100000.000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
