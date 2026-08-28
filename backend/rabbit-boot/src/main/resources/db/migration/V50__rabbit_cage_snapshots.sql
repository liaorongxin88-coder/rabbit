CREATE TABLE rabbit_cage_transfer_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  house_id BIGINT NOT NULL,
  rabbit_id BIGINT NOT NULL,
  from_cage_id BIGINT NOT NULL,
  to_cage_id BIGINT NOT NULL,
  transfer_type VARCHAR(32) NOT NULL,
  request_id VARCHAR(64),
  create_by VARCHAR(64),
  operator_name VARCHAR(64),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rctr_request (house_id, rabbit_id, request_id),
  KEY idx_rctr_house_time (house_id, create_time),
  KEY idx_rctr_rabbit_time (rabbit_id, create_time),
  KEY idx_rctr_from_cage (house_id, from_cage_id, create_time),
  KEY idx_rctr_to_cage (house_id, to_cage_id, create_time),
  CONSTRAINT fk_rctr_house FOREIGN KEY (house_id) REFERENCES rabbit_houses (id),
  CONSTRAINT fk_rctr_rabbit FOREIGN KEY (rabbit_id) REFERENCES rabbits (id),
  CONSTRAINT fk_rctr_from_cage FOREIGN KEY (from_cage_id) REFERENCES cages (id),
  CONSTRAINT fk_rctr_to_cage FOREIGN KEY (to_cage_id) REFERENCES cages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE weight_logs ADD COLUMN cage_id BIGINT NULL AFTER rabbit_id;
ALTER TABLE treatment_records ADD COLUMN cage_id BIGINT NULL AFTER rabbit_id;
ALTER TABLE vaccination_records ADD COLUMN cage_id BIGINT NULL AFTER rabbit_id;
ALTER TABLE rabbit_departure_records ADD COLUMN cage_id BIGINT NULL AFTER rabbit_id;
ALTER TABLE rabbit_status_history ADD COLUMN cage_id BIGINT NULL AFTER rabbit_id;
ALTER TABLE rabbit_abnormal_conditions ADD COLUMN cage_id BIGINT NULL AFTER rabbit_id;

ALTER TABLE weight_logs ADD KEY idx_weight_cage_time (house_id, cage_id, weigh_time);
ALTER TABLE treatment_records ADD KEY idx_treatment_cage_time (house_id, cage_id, start_date);
ALTER TABLE vaccination_records ADD KEY idx_vaccination_cage_time (house_id, cage_id, vaccinated_at);
ALTER TABLE rabbit_departure_records ADD KEY idx_departure_cage_time (house_id, cage_id, departure_date);
ALTER TABLE rabbit_status_history ADD KEY idx_status_history_cage_time (house_id, cage_id, change_time);
ALTER TABLE rabbit_abnormal_conditions ADD KEY idx_abnormal_cage_time (house_id, cage_id, warning_time);

-- Historical rows did not retain a cage. This is the best available snapshot for existing data;
-- every new write stores the then-current cage before any later transfer can change rabbits.cage_id.
UPDATE weight_logs t JOIN rabbits r ON r.id = t.rabbit_id AND r.house_id = t.house_id SET t.cage_id = r.cage_id WHERE t.cage_id IS NULL;
UPDATE treatment_records t JOIN rabbits r ON r.id = t.rabbit_id AND r.house_id = t.house_id SET t.cage_id = r.cage_id WHERE t.cage_id IS NULL;
UPDATE vaccination_records t JOIN rabbits r ON r.id = t.rabbit_id AND r.house_id = t.house_id SET t.cage_id = r.cage_id WHERE t.cage_id IS NULL;
UPDATE rabbit_departure_records t JOIN rabbits r ON r.id = t.rabbit_id AND r.house_id = t.house_id SET t.cage_id = r.cage_id WHERE t.cage_id IS NULL;
UPDATE rabbit_status_history t JOIN rabbits r ON r.id = t.rabbit_id AND r.house_id = t.house_id SET t.cage_id = r.cage_id WHERE t.cage_id IS NULL;
UPDATE rabbit_abnormal_conditions t JOIN rabbits r ON r.id = t.rabbit_id AND r.house_id = t.house_id SET t.cage_id = r.cage_id WHERE t.cage_id IS NULL;
