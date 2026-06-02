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

