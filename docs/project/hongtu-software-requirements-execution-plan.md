# 鸿兔项目软件需求修改执行计划

> - 对应审阅稿：[hongtu-software-requirements-review.md](hongtu-software-requirements-review.md)
> - 需求记录：`recvsV7QhkxxvD`（批次统计）、`recvrqsCS3Six3`（页面按钮用途）
> - 代码基线：`3e91558`，发布基线 `v1.0.11`，Flyway 最新版本 `V54`
> - 计划重心：本轮统计、页面、按钮和接口的实际修改

## 1. 本轮交付范围

本轮修改分成两条主线：批次生产统计 MVP，以及生产相关页面的按钮闭环。销售、饲料、退货、冲正和屠宰统计需要先补数据地基，作为后续版本实施。

| 主线 | 本轮修改 | 交付结果 |
| --- | --- | --- |
| `S` 批次统计 MVP | 扩展统计聚合与 API；增加 Admin 批次详情和 Flutter 分组统计 | 进行中和已完成批次可查看繁殖、产崽、选留、断奶及数据质量 |
| `U` 页面按钮 | 修正当前 10 个确认差距；统一权限、禁用原因、反馈、返回和幂等 | 用户能从按钮文案判断用途，前后端状态和权限一致 |

当前 4 个基础字段 `totalLitters`、`totalKits`、`totalLiveKits`、`totalWeaned` 保持兼容，不重复实现。

本轮默认不交付：出栏与销售金额、饲料效率、退货与冲正、出肉率。相关数据模型保留在第 9 节的后续范围。

## 2. 开工前需要确认的修改口径

这些决策直接影响字段、公式和按钮行为，应在接口开发前确认。确认工作是实施前置，不是本计划的主体。

| 决策 | 本计划采用的推荐值 |
| --- | --- |
| `D-STAT-01` | 同时返回去重母兔数和繁殖周期数，比例按周期计算 |
| `D-STAT-03` | 生产事实使用操作时固化的批次或周期归属，不从兔只当前状态反推 |
| `D-STAT-04` | 只统计有效最终事实；更正追加审计，不覆盖历史凭证 |
| `D-STAT-06` | 生产统计沿用批次查看权限；后续财务指标使用独立权限 |
| `D-BTN-01` | 业务动作使用文字或图标加文字；工具图标必须有可访问名称 |
| `D-BTN-02` | 无权知道的动作隐藏；状态不允许时禁用并说明原因 |
| `D-BTN-03` | 写操作防连点，失败保留输入和原 `requestId` |
| `D-BTN-04` | 返回目标固定；无历史时回模块列表；未保存时确认 |
| `D-BTN-05` | 笼位页为异常主入口，兔只详情保留快捷入口 |

`D-STAT-02` 料肉比和 `D-STAT-05` 饲料分摊不阻塞本轮 MVP，但必须在后续销售与饲料版本开工前确认。

还需确认批次创建语义。推荐把“创建批次并加入母兔”和“开始生产周期”拆成两个明确动作，维持当前后端“创建批次只建立关系”的行为，不让一个新增按钮隐式推进生产状态。

## 3. S1：后端批次统计聚合

### 3.1 本轮指标

| 分组 | 新增或扩展指标 |
| --- | --- |
| 配种 | 配种日期范围、配种母兔数、配种母兔次、公兔数、受胎数量、受胎率、母兔公兔比 |
| 妊娠 | 怀孕数量、流产数量、流产率 |
| 接产 | 产崽窝数、产崽总数、窝均产崽、活崽总数、活崽率 |
| 选留 | 选留窝数、选留总数、选留活崽率、窝均选留 |
| 断奶 | 断奶数量、断奶总重、断奶均重、断奶成活率 |
| 元数据 | 统计时间、最后变化时间、统计版本、数据质量问题 |

权威数据来自 `breeding_cycles`、`litters`、`weaning_records` 及对应操作事件。断奶总重按 `SUM(avg_weight * weaning_count)` 计算；任何参与记录缺重量时，重量和依赖指标返回 `null`，不做静默估算。

### 3.2 聚合实现

修改 [BatchStatisticsMapper.xml](../../backend/rabbit-production/src/main/resources/mapper/modules/batch/BatchStatisticsMapper.xml) 和 [BatchStatisticsService.java](../../backend/rabbit-production/src/main/java/com/rabbit/app/modules/batch/service/BatchStatisticsService.java)：

1. 按配种、妊娠、接产、选留和断奶拆分独立聚合查询。
2. 在同一个只读事务和一致性快照内组装结果。
3. 避免把窝、成员、销售和投喂连接为一条大 SQL，防止行数相乘。
4. 比率在 Java 中使用 `BigDecimal` 计算，API 保留原始精度，展示层保留两位。
5. 以 `house_id + batch_id` 强制租户隔离。
6. 只统计已确认、未撤销、未冲正的最终事实。
7. 同一 `requestId` 的重复事件只进入一次统计。
8. 统计版本取相关操作事件的最大事件 ID，最后变化时间取权威事实的最大更新时间。

只有 `EXPLAIN` 证明现有索引不足时，才新增统计索引迁移 `M-STAT-01`。迁移版本在合并前从主分支顺序分配，不能预占固定的 V55。

### 3.3 API 修改

继续扩展 `GET /api/batches/{batchId}/statistics`：

- 保留当前 4 个顶层字段。
- 新增 `breeding`、`delivery`、`weaning` 和 `dataQuality`。
- 返回 `calculatedAt`、`lastChangedAt`、`statisticsVersion`。
- `dataQuality.issues[]` 包含问题代码、严重度、受影响指标、记录数和不可用原因。
- 不可计算时返回 `null`，原因区分零分母、缺记录、归属不完整和数据源未接入。
- 本轮不返回虚假的 `outbound`、`sales`、`feed` 或 `slaughter` 数值；客户端为后续分组保留兼容解析。

生产统计继续使用 `rabbit:batches:query`。所有跨兔舍或无权限请求由服务端拒绝，客户端显隐不能替代权限校验。

## 4. S2：Admin 批次详情与统计

### 4.1 路由和页面

- 新增 `/workspace/production/:batchId`。
- 批次列表同时显示进行中和已完成批次，并进入同一详情页。
- 详情页提供“概览、统计、成员”页签。
- “统计”页签只消费服务端结果，不在浏览器重复计算公式。
- “成员”页签补齐添加和移除成员，使批次维护在 Admin 闭环。
- 工作概览增加兔舍和批次选择器；未选择单一批次时不显示批次指标。

主要修改 [workspace-production-page.tsx](../../admin/src/pages/workspace-production-page.tsx)、业务 API、类型、路由和必要的批次详情组件。先完成页面职责拆分，再添加统计和成员行为，避免继续扩大单个页面文件。

### 4.2 展示状态

每个指标必须区分：

- 有效数值，包括真实的 0。
- 尚无业务记录。
- 数据不完整，显示具体问题。
- 无权限查看。
- 请求失败。

页面显示批次、兔舍、状态、统计时间、最后变化时间和统计版本。已完成批次默认只读；更正入口不在本轮范围。

## 5. S3：Flutter 批次统计

修改 [detail.dart](../../app/lib/src/ui/batches/screens/detail.dart)、批次 repository、领域模型和 provider：

- 保留现有批次详情路由，将统计区扩展为分组页签。
- 数据面板只有选择单一兔舍和批次后才请求统计。
- 兼容旧后端只有 4 个字段的响应，显示“当前服务版本仅提供基础统计”。
- 缺少新分组字段不能解析成 0。
- `null` 指标显示服务端返回的不可用原因。
- 切换兔舍或批次时取消或忽略旧请求，避免响应串台。
- 进行中和已完成批次都可查看，已完成批次保持只读。

Flutter 不计算业务比例，只负责格式化服务端值和原因。

## 6. U1：后端按钮能力与权限

按钮问题不只改文案。本轮先补服务端需要提供的状态和权限信息：

1. 批次结束前增加能力预检，返回待分笼数、开放周期数、活跃成员数和不可结束原因。
2. 创建批次接口继续要求 `rabbit:batches:add`，Admin 改为使用同一权限。
3. 出库入口和接口统一使用 `rabbit:outbound:edit`。
4. 生产动作可用阶段来自服务端阶段字典；客户端不维护另一套状态机。
5. 批次创建与开始生产周期按确认后的两个动作执行。
6. 所有写接口继续校验 `X-House-Id`、权限、状态和 `requestId`。

能力预检返回机器可读原因码和中文展示文案，Admin 与 Flutter 复用同一结果，避免各写一套禁用规则。

## 7. U2：Admin 和 Flutter 按钮修改

### 7.1 当前 10 个差距

| 编号 | 修改内容 | 主要端 |
| --- | --- | --- |
| `U-01` | 删除“自动匹配当前业务周期”；只有一个合法周期时自动选中，否则要求明确选择 | Admin |
| `U-02` | 所有生产动作按服务端阶段字典过滤 | Admin、Flutter |
| `U-03` | 强制退出或结束批次前显示服务端能力预检和具体阻塞原因 | Admin、Flutter |
| `U-04` | 新建批次按钮改用 `rabbit:batches:add` | Admin |
| `U-05` | Dashboard“前往出库”先检查 `rabbit:outbound:edit` | Admin |
| `U-06` | 增加 Admin 批次详情、添加成员和移除成员 | Admin |
| `U-07` | 对齐“创建批次”和“开始生产周期”的按钮、请求和结果 | Admin、Flutter |
| `U-08` | 无权知道的动作隐藏；状态不允许时禁用并显示原因 | Admin、Flutter |
| `U-09` | 批次列表区分加载失败、无权限和真实空数据 | Admin |
| `U-10` | 区分“失败分娩”和“记录流产”的文案、阶段和结果 | Admin、Flutter |

### 7.2 统一交互修改

- 业务动作使用文字或图标加文字。返回、刷新、关闭等工具按钮可保留图标，但必须有可访问名称和 tooltip。
- 点击写操作后进入加载态并禁用；连续点击三次只产生一份事实。
- 失败保留表单、附件和原 `requestId`；用户改变字段后才生成新 ID。
- 删除、出售、离场、强制完成和批量操作在确认前显示对象和数量。
- 成功后刷新受影响区域，并说明成功、跳过和失败数量。
- 返回按钮有固定上级页面；无历史记录时返回模块列表；未保存输入时二次确认。
- 笼位页作为异常主入口，兔只详情保留快捷入口，两个入口调用同一契约。

按钮矩阵是验收产物，不是独立的全站重构。只记录本轮涉及页面以及 Base 主记录关联的页面。

## 8. 修改文件范围与提交拆分

### 8.1 文件范围

| 模块 | 主要文件或目录 | 修改内容 |
| --- | --- | --- |
| 统计聚合 | `BatchStatisticsMapper.xml`、`BatchStatisticsService.java`、统计 DTO | 分域查询、公式、质量问题、版本和兼容字段 |
| 批次接口 | batch controller/service | 统计响应、结束能力预检、权限和状态校验 |
| Admin | `workspace-production-page.tsx`、路由、API、types、批次详情组件 | 详情页、统计、成员、按钮权限和错误态 |
| Flutter | `ui/batches/`、批次 repository/domain/provider | 分组统计、旧响应兼容、按钮和页面状态 |
| 权限 | 后端权限字典与双端权限映射 | `rabbit:batches:add`、`rabbit:outbound:edit` 等语义对齐 |
| 测试 | `BatchStatisticsIT`、Admin 测试与浏览器脚本、Flutter tests | 公式、租户、权限、交互和响应兼容 |
| 数据库 | `rabbit-boot/.../db/migration/` | 仅在 `EXPLAIN` 证明需要时增加索引 |

### 8.2 提交单元

| 顺序 | 提交单元 | 交付内容 |
| --- | --- | --- |
| 1 | `feat(backend): expand batch production statistics` | 聚合、DTO、API、权限、质量问题和 IT |
| 2 | `feat(backend): expose batch action availability` | 结束预检、动作阶段和原因码 |
| 3 | `feat(admin): add batch detail statistics` | 路由、统计、成员和页面状态 |
| 4 | `fix(admin): align production actions and permissions` | U-01 至 U-10 中的 Admin 修改 |
| 5 | `feat(flutter): show batch production statistics` | 分组统计、旧响应兼容和请求状态 |
| 6 | `fix(flutter): clarify production actions` | U-02、U-03、U-07、U-08、U-10 及统一交互 |

后端统计和能力预检可并行设计，但同一批次 service 和 DTO 由一名负责人合并。Admin 的统计与按钮修改都经过 `workspace-production-page.tsx`，按上表串行。Flutter 的详情统计和批次按钮也串行，避免同文件冲突。

## 9. 后续版本：销售、饲料和屠宰

以下不计入本轮修改，但完整需求最终需要：

| 逻辑迁移 | 后续修改 |
| --- | --- |
| `M-STAT-02` | `sale_order_batch_allocations`：按批次保存兔只数、重量、金额和来源请求 |
| `M-STAT-03` | `feed_log_batch_allocations`：按批次和 `BREEDING/FATTENING` 阶段保存标准化 kg 数量 |
| `M-STAT-04` | 销售退回、销售冲正、饲料冲正和 `batch_slaughter_results` |

后续版本再交付出栏数量与成活率、销售金额与单价、全程和育肥期料肉比、出肉率。跨批次订单和混批投喂无法可靠回填时，只记录数据质量问题，不制造分摊值。

`D-STAT-02`、`D-STAT-05` 和财务权限必须在后续版本开工前批准。参考工作量为 48 至 64 人时，单独排期。

## 10. 实施顺序和并发安排

| 阶段 | 修改内容 | 可并发工作 | 估算 |
| --- | --- | --- | ---: |
| `R0` | 冻结本轮指标、API、批次创建和按钮行为 | 准备固定验收数据 | 8 至 12h |
| `R1` | 后端统计聚合和能力预检 | Admin/Flutter 建立类型和页面骨架 | 24 至 32h |
| `R2` | Admin 详情、成员、统计和按钮 | Flutter 统计和按钮 | 28 至 36h |
| `R3` | E2E、浏览器、真机、迁移和数据对账 | 文档与飞书收尾 | 12 至 16h |

工作包有重叠，表中不能机械相加。去重后的本轮参考值为 **72 至 88 人时**。4 人并行时，工程墙钟约 4 至 6 个工作日，不含决策和业务验收排队。

跨计划依赖：

- BUG B1 先冻结断奶日期和成长阶段语义，再完成断奶统计相关字段。
- BUG B3 的兔舍周期配置和事件快照与繁殖统计共用一套定义。
- BUG B4 与首页筛选、按钮反馈共享 Flutter 首页事件模型，安排在同一发布波次。
- Flyway 全局串行；创建任何迁移前从主分支读取最新版本。
- 每个并行后端泳道使用独立 `E2E_SCHEMA_SUFFIX`，全量 E2E 和设备验收错峰执行。

## 11. 测试与验收

### 后端

```bash
mvn --file backend/pom.xml -pl rabbit-production -am test
E2E_CLEAN=1 E2E_SCHEMA_SUFFIX=_requirements_change bash scripts/e2e-local.sh
```

重点覆盖公式、零分母、缺重量、重复事件、跨兔舍、权限裁剪、已完成批次和旧 4 字段兼容。固定验收数据的页面结果必须与事实表重新聚合结果一致。

### Admin

```bash
pnpm --dir admin lint
pnpm --dir admin test
pnpm --dir admin build
pnpm --dir admin e2e:browser:batch-statistics
```

覆盖 OWNER、STAFF、VIEWER、加载失败、无权限、真实空数据、禁用原因、历史批次、失败保留输入和连续点击。

### Flutter 与真机

```bash
cd app && ./rabbit check
```

真机覆盖 360x800、412x915、200% 字号、TalkBack、快速切换、断网恢复和旧后端响应。所有交互遵守[交互验收规范](interaction-acceptance-spec.md)。

## 12. 发布和完成标准

发布顺序：兼容后端、Admin、非强制 Flutter OTA。统计按灰度开关先向内部账号开放；固定验收批次对账一致后再扩大范围。

本轮完成条件：

- 生产统计 MVP 已在后端、Admin 和 Flutter 交付，旧 4 字段继续可用。
- 进行中和已完成批次均可查看统计，并正确区分 0、无记录、数据不完整、无权限和加载失败。
- 10 个按钮差距全部关闭，权限、禁用原因、幂等、返回和失败保留输入通过验收。
- 不属于本轮的销售、饲料和屠宰指标没有伪造值，并已形成独立后续范围。
- 迁移、跨兔舍隔离、浏览器、真机和回滚检查通过。

飞书只在交付收尾时更新状态、负责人、版本、验收人和证据链接，不作为工程实施主线。
