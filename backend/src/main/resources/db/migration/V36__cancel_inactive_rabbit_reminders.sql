-- 离场兔只不再保留可执行待办。查询端同时过滤离场兔，迁移负责清理历史残留。
UPDATE work_tasks wt
INNER JOIN rabbits r
  ON r.house_id = wt.house_id
 AND r.id = wt.rabbit_id
SET wt.status = 'CANCELLED',
    wt.update_by = 'v36',
    wt.update_time = NOW()
WHERE r.is_active = FALSE
  AND wt.status = 'PENDING';
