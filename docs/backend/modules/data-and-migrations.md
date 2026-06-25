# 数据库和迁移

## Flyway

迁移脚本目录：

```text
backend/src/main/resources/db/migration/
```

规则：

- 新增、修改表结构或索引必须新增 Flyway 迁移。
- 迁移必须能在全新库和已有库上执行。
- 迁移不能写入密码、token、生产连接串或其它敏感信息。
- `schema.sql` 只作结构参考，不是常规初始化入口。

## 参考文件

- `backend/src/main/resources/db/schema.sql`：当前全量结构参考。
- `backend/src/main/resources/db/seed_demo.sql`：演示数据参考。
- `tools/demo_flow.ps1`、`tools/demo_flow_full.ps1`：更推荐的演示数据生成方式，因为它们走真实 API。

## 兔舍隔离

核心生产和状态主记录直接带 `house_id`，包括：

- `pregnancy_check_records`
- `parturition_records`
- `prepartum_records`
- `weaning_records`
- `rabbit_status_history`

明细表仍通过父表关联归属兔舍，例如：

- `weaning_record_allocations` 通过 `weaning_records`。
- `sale_order_items` 通过 `sale_orders`。

涉及查询或写入时，不要只看当前表是否有 `house_id`；需要确认最终数据路径是否被兔舍上下文限制。
