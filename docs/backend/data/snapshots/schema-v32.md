# Flyway V32 数据表结构与关联快照

状态：历史快照（截至 Flyway V32，不代表后续迁移）
来源：`backend/rabbit-boot/src/main/resources/db/migration/` V1–V32 逐一核对；实体类交叉验证
关联文档：[母兔生产流程 V2 设计](../../../features/reproduction-v2/design.md)（V2 优化设计，含本结构的问题诊断）

> 阅读约定：仅列关键字段（主键/外键/状态/守卫列），审计四列 `create_by/create_time/update_by/update_time` 与 `remark` 一律省略。`house_id` 是全局租户隔离键，除账号/平台域外所有表均携带并作为索引前缀。

---

## 0. 全景：七个域、38 张在用表

| 域 | 表 | 说明 |
| --- | --- | --- |
| 账号与租户 | `sys_user` `rabbit_houses` `house_users` `house_invitations` `phone_one_tap_attempts` `phone_one_tap_rate_buckets` | 全局账号，兔舍=租户单元 |
| 场舍基础设施 | `cages` `cage_nfc_tags` `nfc_tags` `global_setting` | 笼位、NFC、生产周期配置 |
| 兔只 | `rabbits` `rabbit_status_history` `rabbit_departure_records` `rabbit_abnormal_conditions` `replacement_records` `weight_logs` `treatment_records` | 个体全生命周期 |
| 繁育生产（核心） | `batches` `batch_rabbits` `breeding_cycles` `pregnancy_check_records` `prepartum_records` `parturition_records` `weaning_records` `weaning_record_allocations` `breeding_performance` | 生产状态机所在域 |
| 事件提醒 | `event_acks` `event_reminder_logs` `reminder_preferences` | 兼容确认记录、扫描日志、用户+兔舍级应用内提醒偏好 |
| 运营（喂养/库存/销售/出栏） | `feed_logs` `feed_log_rabbits` `inventory_items` `inventory_txs` `sale_orders` `sale_order_items` `outbound_tasks` `outbound_task_items` `outbound_requests` | 日常运营与出栏交易 |
| 平台支撑 | `platform_admins` `audit_logs` `request_dedup` | SaaS 管理台、审计、幂等 |

已废弃（迁移中建又删）：`merchants` `merchant_users` `merchant_house_policies`（V16 移除商户模型）、`sms_verification_codes`（V19 移入缓存）。

---

## 1. 账号与租户域

```mermaid
erDiagram
    sys_user {
        bigint user_id PK
        varchar user_name
        varchar openid UK "微信身份"
        varchar phone_hash "HMAC 手机号"
    }
    rabbit_houses {
        bigint id PK
        varchar name
        int layout_rows_cols_layers "排/列/层"
        boolean is_deleted
    }
    house_users {
        bigint id PK
        bigint house_id FK
        bigint user_id FK
        varchar perms "view/edit"
        boolean is_admin
    }
    house_invitations {
        bigint id PK
        bigint house_id FK
        varchar phone_hash "精确手机号邀请"
        varchar status
    }
    sys_user ||--o{ house_users : "加入"
    rabbit_houses ||--o{ house_users : "成员"
    rabbit_houses ||--o{ house_invitations : "邀请"
```

- **多租户方式**：单库行级隔离。请求头 `X-House-Id` → 校验 `house_users` 成员资格 → 所有 SQL 强制 `WHERE house_id=?`。
- 兔舍即租户单元；没有更上层的组织/企业实体（V2 设计中预留 `tenant_id`）。
- `phone_one_tap_*` 两表仅服务登录风控（尝试记录/限流桶），无业务外键。

## 2. 场舍基础设施域

```mermaid
erDiagram
    rabbit_houses ||--o{ cages : ""
    rabbit_houses ||--|| global_setting : "house 级配置"
    sys_user ||--o| global_setting : "user 级配置(V8)"
    cages ||--o| cage_nfc_tags : "一笼一标签"
    rabbit_houses ||--o{ nfc_tags : "通用标签"
    cages {
        bigint id PK
        bigint house_id FK
        varchar cage_number UK "house内唯一"
        varchar status "0空闲/占用"
        int rabbit_count "计数器"
        boolean is_enabled
    }
    cage_nfc_tags {
        bigint id PK
        bigint cage_id FK,UK
        varchar tag_uid UK
    }
    nfc_tags {
        bigint id PK
        varchar tag_uid UK
        varchar target_type "CAGE/RABBIT/..."
        bigint target_id
    }
    global_setting {
        bigint id PK
        bigint house_id FK "可空-互斥"
        bigint user_id FK "可空-互斥"
        int aphrodisiac_days "催情2"
        int palpation_days "摸胎12"
        int prepartum_days "备产3(语义混用)"
        int weaning_days "断奶25"
        int postpartum_days "产后恢复10"
        int sale_days "出售30"
        int replacement_days "后备成熟45"
    }
```

- `global_setting` 分为用户级创建模板和兔场级独立快照。创建兔场时会把用户模板复制成 `house_id` 配置；之后修改用户模板不影响已创建兔场，状态转换和事件日期只读取当前兔场快照。存量缺失行会在首次读取时固化一次。`gestation_days` 已在 V26 配置化，不再由 `BatchService` 硬编码。

## 3. 兔只域

```mermaid
erDiagram
    rabbits {
        bigint id PK
        bigint house_id FK
        bigint cage_id FK
        bigint mother_id FK "自关联"
        bigint father_id FK "自关联(V21)"
        bigint birth_batch_id FK "出生批次(V21)"
        bigint birth_cycle_id FK "出生周期(V21)"
        varchar type "0种母?1后备?2商品"
        varchar gender "0母/1公"
        varchar growth_stage "V25 生长阶段"
        varchar reproductive_stage "V25 繁育阶段⚠写点1"
        bigint state_version "乐观锁"
        boolean is_active
        bigint active_breeding_cage_id "生成列:种兔单笼守卫(V25)"
        datetime departure_date
    }
    cages ||--o{ rabbits : "在笼"
    rabbits ||--o{ rabbits : "母/父系谱"
    rabbits ||--o{ rabbit_status_history : "状态流水"
    rabbits ||--o{ rabbit_departure_records : "离场"
    rabbits ||--o{ rabbit_abnormal_conditions : "异常"
    rabbits ||--o{ replacement_records : "转后备"
    rabbits ||--o{ weight_logs : "称重"
    rabbits ||--o{ treatment_records : "用药"
    rabbit_status_history {
        bigint id PK
        bigint house_id
        bigint rabbit_id FK
        bigint batch_id FK "可空"
        varchar from_status
        varchar to_status
        varchar related_record_table "关联业务记录"
        bigint related_record_id
    }
    replacement_records {
        bigint id PK
        bigint rabbit_id FK
        datetime expected_mature_date "⚠提醒源3"
        boolean is_mature_notified
    }
```

- `uk_rabbits_house_active_breeding_cage (house_id, active_breeding_cage_id)`：DB 层保证一笼最多一只活跃种兔/后备兔（V25，生成列模式）。
- 商品兔多只同笼，`cages.rabbit_count` 计数器维护。

## 4. 繁育生产域（核心状态机）

```mermaid
erDiagram
    batches {
        bigint id PK
        bigint house_id FK
        varchar batch_code
        varchar status "计划中/进行中/已完成"
        datetime start_date
        datetime end_date
    }
    batch_rabbits {
        bigint id PK
        bigint batch_id FK
        bigint rabbit_id FK
        bigint male_rabbit_id FK
        bigint latest_cycle_id FK "最新周期快照"
        varchar batch_role "breeding/fattening"
        varchar current_status "⚠写点2:状态快照"
        int current_nursing_kits "哺乳计数快照"
        int nursing_litter_count
        datetime next_event_date "⚠提醒源1"
        varchar next_event_type
        boolean is_event_notified
        boolean is_active
        datetime join_date
        datetime exit_date
    }
    breeding_cycles {
        bigint id PK
        bigint house_id FK
        bigint batch_id FK
        bigint mother_rabbit_id FK
        bigint male_rabbit_id FK
        int cycle_no "UK(house,batch,mother,cycle_no) V22"
        varchar status "⚠写点3:权威状态"
        datetime mating_date
        varchar pregnancy_result
        datetime expected_birth_date "=配种+30硬编码"
        datetime birth_date
        int total_live_nursing_weaned_kits "产/活/哺/断计数组"
        int overlap_litter_cycle_no "血配重叠"
        datetime overlap_start_end
        datetime next_event_date "⚠提醒源2"
        varchar next_event_type
        datetime closed_at
        varchar close_reason
    }
    pregnancy_check_records {
        bigint id PK
        bigint breeding_cycle_id FK
        datetime check_date
        varchar result "怀孕/空怀/不确定"
    }
    parturition_records {
        bigint id PK
        bigint breeding_cycle_id FK
        datetime birth_date
        int total_kits
        int live_kits
    }
    prepartum_records {
        bigint id PK
        bigint breeding_cycle_id FK
        datetime action_date
    }
    weaning_records {
        bigint id PK
        bigint breeding_cycle_id FK
        bigint target_cage_id FK
        int weaning_count
        double avg_weight
    }
    weaning_record_allocations {
        bigint id PK
        bigint weaning_record_id FK
        bigint cage_id FK
        int alloc_count "分笼到笼位"
    }
    breeding_performance {
        bigint id PK
        bigint rabbit_id FK,UK
        int total_litters_kits_weaned "累计绩效"
        decimal avg_litter_size
    }
    batches ||--o{ batch_rabbits : "成员"
    rabbits ||--o{ batch_rabbits : "参与"
    batches ||--o{ breeding_cycles : "包含"
    rabbits ||--o{ breeding_cycles : "母兔的周期"
    batch_rabbits |o--o| breeding_cycles : "latest_cycle_id 快照"
    breeding_cycles ||--o{ pregnancy_check_records : ""
    breeding_cycles ||--o{ prepartum_records : ""
    breeding_cycles ||--o{ parturition_records : ""
    breeding_cycles ||--o{ weaning_records : ""
    weaning_records ||--o{ weaning_record_allocations : "按笼分配"
    rabbits ||--o| breeding_performance : "绩效物化"
```

**状态流转链路（现状）**：

```
操作入口(BatchService) ──写──> breeding_cycles.status        (权威，8 个中文枚举)
        │                         │ syncBreedingSummary() 手工同步
        ├──写──> batch_rabbits.current_status + next_event_*  (快照)
        └──(不写)── rabbits.reproductive_stage                (录入/编辑另行维护 → 漂移)
```

四张记录表（摸胎/备产/分娩/断奶）= 各操作的明细留痕，V21 后通过 `breeding_cycle_id` 挂到周期；催情与"推迟"操作**无记录表**。

## 5. 事件提醒域（扫描式）

```mermaid
erDiagram
    event_reminder_logs {
        bigint id PK
        bigint house_id FK
        varchar category "batch/cycle/replacement"
        bigint record_id "源表行id"
        date notify_date UK "house+cat+record+date"
    }
    event_acks {
        bigint id PK
        bigint house_id FK
        bigint user_id FK
        varchar category
        bigint record_id
        varchar action "ack/ignore/snooze"
        datetime snooze_until
    }
    rabbit_houses ||--o{ event_reminder_logs : "每日扫描落日志"
    sys_user ||--o{ event_acks : "确认/忽略/延后"
```

- **提醒无独立任务实体**，由 EventReminderScanJob（每日 00:05）扫三处源：`batch_rabbits.next_event_*`、`breeding_cycles.next_event_*`、`replacement_records.expected_mature_date`，命中即写日志并置 `is_event_notified=true`；查询时再用 `event_acks` 过滤。商品兔"可出售"按 `sale_days` 即时计算，不入此链路。

## 6. 运营域（喂养/库存/销售/出栏）

```mermaid
erDiagram
    feed_logs ||--o{ feed_log_rabbits : "投喂覆盖兔只"
    cages ||--o{ feed_logs : "按笼投喂"
    inventory_items ||--o{ inventory_txs : "出入库流水"
    sale_orders ||--o{ sale_order_items : "明细"
    outbound_tasks ||--o{ outbound_task_items : "圈选快照"
    outbound_tasks ||--o{ outbound_requests : "提交幂等"
    outbound_tasks |o--o| sale_orders : "完成生成销售单"
    outbound_tasks {
        varchar task_id PK "UUID"
        bigint house_id FK
        varchar entry_type "整舍/单笼/按排"
        varchar status "草稿/锁定/完成"
        bigint revision "乐观锁"
        bigint sale_order_id FK
    }
    outbound_task_items {
        varchar task_id PK,FK
        bigint rabbit_id PK,FK
        bigint state_version "圈选时兔状态版本"
        varchar cage_row_layer_snapshot "位置快照列组"
    }
    sale_orders {
        bigint id PK
        bigint house_id FK
        varchar order_no
        decimal total_amount
    }
```

- 出栏域（V11/V23）是最新一代设计：任务制 + 快照列 + `revision`/`state_version` 双乐观锁 + 独立幂等表，规模化验证过（大场整舍出栏）。V2 繁育设计中的短事务/快照思路与此对齐。

## 7. 平台支撑域

```mermaid
erDiagram
    platform_admins {
        bigint id PK
        varchar username UK
        varchar role "V7 SaaS管理台"
    }
    audit_logs {
        bigint id PK
        varchar trace_id
        bigint user_id
        bigint house_id
        varchar method_path
        int status_api_code
        bigint cost_ms
    }
    request_dedup {
        bigint id PK
        bigint house_id
        bigint user_id
        varchar api
        varchar request_id UK "house+user+api+reqId"
        varchar status "PROCESSING/DONE"
        varchar payload_hash "V24 防重放不同参数"
    }
```

---

## 8. 跨域关联与约束速查

| 关联 | 基数 | 实现 | 备注 |
| --- | --- | --- | --- |
| house → 一切业务表 | 1:N | `house_id` 列 + FK + 索引前缀 | 租户隔离主线 |
| rabbit → cage | N:1 | `rabbits.cage_id` | 种兔另有生成列唯一键单笼守卫 |
| rabbit ↔ batch | M:N | `batch_rabbits` | 携带状态/提醒快照（过载） |
| mother → cycle | 1:N | `breeding_cycles.mother_rabbit_id`，`uk(house,batch,mother,cycle_no)` | 同批次可多周期（与新口径冲突） |
| cycle → 4 记录表 | 1:N | `breeding_cycle_id`（V21 回填） | 催情/推迟无记录 |
| 仔兔 → 出生周期 | N:1 | `rabbits.birth_cycle_id/birth_batch_id/mother_id/father_id` | 系谱链 |
| 提醒源 | 3 处 | `batch_rabbits` / `breeding_cycles` / `replacement_records` 的 next/expected 列 | 夜间扫描汇聚 |
| 幂等 | 每写接口 | `request_id` 列唯一键 + `request_dedup` 表 | 双机制并存 |

**已知结构性痛点**（详见 V2 设计 §1）：母兔状态三写点、提醒三源扫描、`batch_rabbits` 职责过载、妊娠天数硬编码、催情/推迟操作无留痕。
