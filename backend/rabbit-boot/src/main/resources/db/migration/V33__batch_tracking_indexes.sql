-- 批次标签详情按 batch_id + mother_rabbit_id 汇总生产操作与窝数据。
-- 这两个访问路径覆盖千只母兔批次，避免每次打开批次详情都扫描完整事件/窝表。
ALTER TABLE repro_events
  ADD KEY idx_re_batch_mother_time (
    batch_id, mother_rabbit_id, occurred_at, id
  );

ALTER TABLE litters
  ADD KEY idx_lt_batch_mother (
    batch_id, mother_rabbit_id, id
  );
