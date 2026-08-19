CREATE TABLE reminder_preferences (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  house_id BIGINT NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  advance_days INT NOT NULL DEFAULT 0,
  notify_overdue TINYINT(1) NOT NULL DEFAULT 1,
  task_types VARCHAR(255) NOT NULL DEFAULT 'ALL',
  create_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reminder_preferences_user_house (user_id, house_id),
  KEY idx_reminder_preferences_house_user (house_id, user_id),
  CONSTRAINT fk_reminder_preferences_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id),
  CONSTRAINT fk_reminder_preferences_house FOREIGN KEY (house_id) REFERENCES rabbit_houses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
