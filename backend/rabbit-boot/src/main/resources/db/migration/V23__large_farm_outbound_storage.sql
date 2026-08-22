ALTER TABLE outbound_requests
  MODIFY COLUMN conflicts_json MEDIUMTEXT NULL;

ALTER TABLE rabbits
  ADD KEY idx_rabbits_house_birth_batch_id (house_id, birth_batch_id, id);

ALTER TABLE rabbit_abnormal_conditions
  ADD KEY idx_rac_house_rabbit_deal (house_id, rabbit_id, is_deal);
