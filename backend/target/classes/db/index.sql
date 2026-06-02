USE rabbit_app;

ALTER TABLE house_users
  ADD INDEX idx_house_users_user_house (user_id, house_id);

ALTER TABLE feed_logs
  ADD INDEX idx_feed_logs_house_time_id (house_id, feed_time, id);

ALTER TABLE replacement_records
  ADD INDEX idx_rr_house_notified_expected_id (house_id, is_mature_notified, expected_mature_date, id);

ALTER TABLE rabbit_abnormal_conditions
  ADD INDEX idx_rac_house_deal_time_id (house_id, is_deal, warning_time, id);

ALTER TABLE rabbit_status_history
  ADD INDEX idx_rsh_rabbit_time_id (rabbit_id, change_time, id);

ALTER TABLE batch_rabbits
  ADD INDEX idx_br_batch_rabbit_active_id (batch_id, rabbit_id, is_active, id);

ALTER TABLE batch_rabbits
  ADD INDEX idx_br_rabbit_active_id (rabbit_id, is_active, id);

ALTER TABLE batch_rabbits
  ADD INDEX idx_br_batch_active_nextdate_id (batch_id, is_active, next_event_date, id);

ALTER TABLE cages
  ADD INDEX idx_cages_is_fed (is_fed);

ALTER TABLE breeding_performance
  ADD INDEX idx_bp_house_updatetime_id (house_id, update_time, id);

ALTER TABLE rabbits
  ADD INDEX idx_rabbits_house_active_type_id (house_id, is_active, type, id);

ALTER TABLE batches
  ADD UNIQUE KEY uk_batches_house_code (house_id, batch_code);

ALTER TABLE rabbit_abnormal_conditions
  MODIFY is_deal BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE replacement_records
  MODIFY is_mature_notified BOOLEAN NOT NULL DEFAULT FALSE;
