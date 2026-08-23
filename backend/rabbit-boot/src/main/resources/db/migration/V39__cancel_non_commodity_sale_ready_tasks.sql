-- SALE_READY 仅适用于在栏商品兔。V36 已清理离场兔待办；这里清理类型已变更的历史残留。
UPDATE work_tasks wt
INNER JOIN rabbits r
  ON r.house_id = wt.house_id
 AND r.id = wt.rabbit_id
SET wt.status = 'CANCELLED',
    wt.update_by = 'v39',
    wt.update_time = NOW()
WHERE wt.task_type = 'SALE_READY'
  AND wt.status = 'PENDING'
  AND r.is_active = TRUE
  AND r.type <> '2';
