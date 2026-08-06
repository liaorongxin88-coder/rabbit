USE rabbit_app;

SET @demo_user_id = IFNULL((SELECT user_id FROM sys_user WHERE user_name = 'demo' ORDER BY user_id DESC LIMIT 1), 1);
SET @demo_merchant_id = (SELECT merchant_id FROM sys_user WHERE user_id = @demo_user_id);

SET @old_house_id = (SELECT id FROM rabbit_houses WHERE name = '演示兔场A' ORDER BY id DESC LIMIT 1);

DELETE FROM rabbit_abnormal_conditions WHERE house_id = @old_house_id;
DELETE FROM rabbit_status_history WHERE rabbit_id IN (SELECT id FROM rabbits WHERE house_id = @old_house_id);
DELETE FROM feed_logs WHERE house_id = @old_house_id;
DELETE FROM weaning_records WHERE batch_id IN (SELECT id FROM batches WHERE house_id = @old_house_id);
DELETE FROM parturition_records WHERE batch_id IN (SELECT id FROM batches WHERE house_id = @old_house_id);
DELETE FROM pregnancy_check_records WHERE batch_id IN (SELECT id FROM batches WHERE house_id = @old_house_id);
DELETE FROM batch_rabbits WHERE batch_id IN (SELECT id FROM batches WHERE house_id = @old_house_id);
DELETE FROM batches WHERE house_id = @old_house_id;
DELETE FROM replacement_records WHERE house_id = @old_house_id;
DELETE FROM breeding_performance WHERE house_id = @old_house_id;
DELETE FROM rabbits WHERE house_id = @old_house_id;
DELETE FROM cages WHERE house_id = @old_house_id;
DELETE FROM global_setting WHERE house_id = @old_house_id OR user_id = @demo_user_id;
DELETE FROM house_users WHERE house_id = @old_house_id;
DELETE FROM rabbit_houses WHERE id = @old_house_id;

INSERT INTO rabbit_houses(merchant_id, owner_user_id, name, layout_rows, layout_cols, layout_layers, remark, create_by, update_by)
VALUES (@demo_merchant_id, @demo_user_id, '演示兔场A', 4, 5, 1, 'demo 数据', 'system', 'system');

SET @house_id = LAST_INSERT_ID();

INSERT INTO house_users(house_id, user_id, role, perms, is_admin)
VALUES (@house_id, @demo_user_id, 'OWNER', 'control', TRUE);

INSERT INTO global_setting(house_id, user_id, aphrodisiac_days, palpation_days, prepartum_days, weaning_days, postpartum_days, sale_days, replacement_days, remark, create_by, update_by)
VALUES (NULL, @demo_user_id, 7, 10, 3, 25, 10, 90, 120, 'demo 设置', 'system', 'system');

INSERT INTO cages(house_id, cage_number, status, rabbit_count, is_fed, remark, create_by, update_by)
VALUES
  (@house_id, 'A-01', '0', 0, FALSE, NULL, 'system', 'system'),
  (@house_id, 'A-02', '0', 0, FALSE, NULL, 'system', 'system'),
  (@house_id, 'A-03', '0', 0, FALSE, NULL, 'system', 'system'),
  (@house_id, 'A-04', '3', 0, FALSE, NULL, 'system', 'system');

SET @cage1 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number='A-01' LIMIT 1);
SET @cage2 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number='A-02' LIMIT 1);
SET @cage3 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number='A-03' LIMIT 1);
SET @cage4 = (SELECT id FROM cages WHERE house_id = @house_id AND cage_number='A-04' LIMIT 1);

INSERT INTO rabbits(house_id, cage_id, mother_id, type, gender, breed, arrival_method, arrival_date, weight, is_active, remark, create_by, update_by)
VALUES
  (@house_id, @cage1, NULL, '0', '1', '新西兰', '1', NOW() - INTERVAL 200 DAY, 4.2, TRUE, NULL, 'system', 'system'),
  (@house_id, @cage2, NULL, '0', '0', '新西兰', '1', NOW() - INTERVAL 180 DAY, 3.9, TRUE, NULL, 'system', 'system'),
  (@house_id, @cage3, NULL, '2', '0', '加利福尼亚', '1', NOW() - INTERVAL 60 DAY, 2.8, TRUE, NULL, 'system', 'system');

SET @rabbit_male = (SELECT id FROM rabbits WHERE house_id=@house_id AND gender='1' ORDER BY id ASC LIMIT 1);
SET @rabbit_female = (SELECT id FROM rabbits WHERE house_id=@house_id AND gender='0' AND type='0' ORDER BY id ASC LIMIT 1);
SET @rabbit_commodity = (SELECT id FROM rabbits WHERE house_id=@house_id AND type='2' ORDER BY id ASC LIMIT 1);

UPDATE cages
SET rabbit_count = (SELECT COUNT(1) FROM rabbits r WHERE r.cage_id = cages.id AND r.is_active = TRUE)
WHERE house_id = @house_id;

INSERT INTO batches(house_id, batch_code, status, start_date, end_date, remark, create_by, update_by)
VALUES (@house_id, 'BATCH-DEMO-001', '进行中', NOW() - INTERVAL 12 DAY, NULL, 'demo 批次', 'system', 'system');

SET @batch_id = LAST_INSERT_ID();

INSERT INTO batch_rabbits(batch_id, rabbit_id, male_rabbit_id, join_reason, batch_role, current_status,
                         last_event_date, next_event_date, next_event_type, is_active, join_date, exit_date, remark,
                         create_by, update_by)
VALUES
  (@batch_id, @rabbit_female, @rabbit_male, '配种', '母兔', '已配种',
   NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 1 DAY, '摸胎', TRUE, NOW() - INTERVAL 12 DAY, NULL, NULL,
   'system', 'system');

INSERT INTO feed_logs(house_id, feeding_rabbits, feed_time, feed_type, amount, remark, create_by, update_by)
VALUES
  (@house_id, CONCAT(@rabbit_male, ',', @rabbit_female), NOW() - INTERVAL 2 DAY, '颗粒料', 5.00, NULL, 'system', 'system'),
  (@house_id, CONCAT(@rabbit_male, ',', @rabbit_female), NOW() - INTERVAL 1 DAY, '苜蓿草', 3.50, NULL, 'system', 'system');

INSERT INTO rabbit_abnormal_conditions(rabbit_id, house_id, warning_status, warning_time, img_url, remark, is_deal, create_by, update_by)
VALUES
  (@rabbit_female, @house_id, '食欲下降', NOW() - INTERVAL 3 HOUR, NULL, 'demo 异常', FALSE, 'system', 'system');

INSERT INTO replacement_records(house_id, rabbit_id, original_type, replacement_date, expected_mature_date, is_mature_notified, mature_notify_date, remark, create_by, update_by)
VALUES
  (@house_id, @rabbit_commodity, '2', NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 1 DAY, FALSE, NULL, 'demo 后备到期', 'system', 'system');

INSERT INTO breeding_performance(house_id, rabbit_id, total_litters, total_kits, total_live_kits, total_weaned,
                                success_breeding_count, failed_breeding_count, avg_litter_size, avg_weaning_size,
                                last_litter_date, performance_score, remark, update_time)
VALUES
  (@house_id, @rabbit_female, 1, 8, 7, 6, 1, 0, 8.00, 6.00, NOW() - INTERVAL 40 DAY, 90, 'demo 性能', NOW());
