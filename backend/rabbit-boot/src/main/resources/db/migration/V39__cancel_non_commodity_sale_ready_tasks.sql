-- SALE_READY 只适用于在栏商品兔。即使 V36 已清理离场兔待办，仍在这里独立
-- 取消空引用、孤儿、离场和非商品兔任务。未成熟商品兔可继续等待成熟，不在此取消。
UPDATE work_tasks wt
LEFT JOIN rabbits r
  ON r.house_id = wt.house_id
 AND r.id = wt.rabbit_id
SET wt.status = 'CANCELLED',
    wt.update_by = 'v39',
    wt.update_time = NOW()
WHERE wt.task_type = 'SALE_READY'
  AND wt.status = 'PENDING'
  AND (
    wt.rabbit_id IS NULL
    OR r.id IS NULL
    OR r.is_active <> TRUE
    OR r.type <> '2'
  );
