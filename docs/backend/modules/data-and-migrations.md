# 数据库和迁移

## Flyway

迁移脚本目录：

```text
backend/rabbit-boot/src/main/resources/db/migration/
```

规则：

- 新增、修改表结构或索引必须新增 Flyway 迁移。
- 迁移必须能在全新库和已有库上执行。
- 迁移不能写入密码、token、生产连接串或其它敏感信息。
- `schema.sql` 只作结构参考，不是常规初始化入口。

当前身份与归属模型的连续迁移：

- V15 建立用户直接关联兔场和精确手机号邀请。
- V16 删除运行时商户模型。
- V17 新增匿名一键登录幂等/防重放记录，只保存 token HMAC 摘要、请求状态和限流信息，
  不保存运营商原 token 或手机号。
- V18 为一键登录处理记录增加可接管租约，并新增按 IP 和时间桶唯一的原子限流计数；终态记录
  和过期限流桶由后端按配置保留期清理。
- V34 增加商品兔三阶段参数、成长起始时间和后备转种状态；从在栏兔只幂等回填
  `SALE_READY` / `REPLACEMENT_MATURE` 任务，并让新写路径在业务事务内同步维护任务。
- V35 增加按兔场隔离的 `business_files` 图片内容表，并给 `replacement_records` 增加
  `request_id`，让商品兔留后备接口可以幂等返回生成的后备记录 ID。
- V36 将离场兔遗留的 `PENDING` 待办改为 `CANCELLED`。离场事务会同步取消兔只名下待办，
  待办和治疗复查查询也只返回仍在场的兔只。
- V41 增加 `app_releases`，保存 Flutter 各渠道 APK 的版本元数据和磁盘存储键，供软件内升级。

## 参考文件

- `backend/rabbit-boot/src/main/resources/db/schema.sql`：当前全量结构参考。
- `backend/rabbit-boot/src/main/resources/db/seed_demo.sql`：演示数据参考。
- [后端数据结构资料](../data/README.md)：按业务域整理的结构说明和关系图。

生产和持续集成以 Flyway 回放为准，演示数据不能替代迁移验证。

## 兔场隔离

核心生产和状态主记录直接带 `house_id`，包括：

- `pregnancy_check_records`
- `parturition_records`
- `prepartum_records`
- `weaning_records`
- `rabbit_status_history`
- `business_files`

明细表仍通过父表关联归属兔场，例如：

- `weaning_record_allocations` 通过 `weaning_records`。
- `sale_order_items` 通过 `sale_orders`。

涉及查询或写入时，不要只看当前表是否有 `house_id`；需要确认最终数据路径是否被兔场上下文限制。
