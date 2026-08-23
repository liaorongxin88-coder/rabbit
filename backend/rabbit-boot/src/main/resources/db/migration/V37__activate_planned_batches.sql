ALTER TABLE batches
  MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT '进行中';

UPDATE batches
SET status = '进行中',
    start_date = COALESCE(start_date, create_time)
WHERE status = '计划中';
