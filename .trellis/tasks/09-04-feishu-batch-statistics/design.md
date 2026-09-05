# 飞书批次统计技术设计

## 1. 设计边界

本任务实施前，`GET /api/batches/{batchId}/statistics` 只返回产崽窝数、产崽总数、活崽总数和断奶数量四个整数。本任务保留该端点和兔舍权限边界，扩展为 28 项有序指标，并补齐断奶重量、批次饲料、混批销售、转后备重量和出肉率五类不可变数据。

统计读模型归 `rabbit-production` 的批次域所有。各类写入仍留在饲料、繁殖、出库、兔只转换等现有业务域中，不能把业务校验搬到统计查询里。Flyway 迁移和完整上下文测试继续放在 `rabbit-boot`。Admin 与 Flutter 只消费后端给出的指标目录、状态和展示值，不自行复制公式。

本任务增加单批次 `.xlsx` 导出，不回填缺少原始凭证的历史数据，也不改变批次只能属于一个兔舍的模型。Excel 格式与下载由 `rabbit-reporting` 所有，`rabbit-production` 仍只负责统计计算。

## 2. 数据流

实施后的读取链路如下：

- `BatchController.getBatchStatistics` 位于 `rabbit-production`，要求 `rabbit:batches:query` 并接收 `X-House-Id`。
- `BatchStatisticsService.getStatistics` 先调用 `BatchStatisticsMapper.selectBatch(houseId, batchId)` 校验并读取批次；查询无批次行时抛出“批次不存在”。
- 服务再调用 `selectMatingAggregate`、`selectMatingDates`、`selectAbortionAggregate`、`selectLitterAggregate`、`selectSalesCountAggregate`、`selectSalesValueAggregate`、`selectFeedAggregate`、`selectReplacementAggregate` 和 `selectLatestCarcassYield`，分别读取原始聚合，避免一条多表 SQL 重复求和。
- `rabbit-boot/src/test/java/com/rabbit/app/e2e/BatchStatisticsIT.java` 覆盖 28 项指标、兼容四字段、空批次、兔舍隔离和无权访问。

完整链路如下：

```text
业务操作
  ├─ 配种、孕检、流产、接产、选留
  ├─ 断奶总重快照
  ├─ 投喂批次与阶段分配
  ├─ 出库批次重量与金额分配
  ├─ 转后备来源批次与实测总重
  └─ 出肉率追加版本
          ↓
MySQL 业务表与新增快照
          ↓
BatchStatisticsMapper 原始聚合
          ↓
BatchStatisticsService
  ├─ 固定指标目录
  ├─ 公式与状态传播
  ├─ 原始值与展示值
  └─ 旧四字段兼容投影
          ↓
GET /api/batches/{batchId}/statistics
          ↓
Admin 独立批次详情 + Flutter 现有批次详情

BatchStatisticsService 同一统计结果
          ↓
rabbit-reporting Excel 写出器
          ↓
GET /api/reports/batches/{batchId}/statistics.xlsx
```

`BatchStatisticsMapper` 只返回原始聚合和完整性标志。公式、状态优先级、舍入和展示文本由 `BatchStatisticsService` 统一处理，避免把 28 项业务规则散落在 SQL、Admin 和 Flutter 中。

## 3. 统计响应契约

端点保持不变：

```text
GET /api/batches/{batchId}/statistics
X-House-Id: <houseId>
Requires: rabbit:batches:query
```

HTTP 响应使用 `{code, message, data}` envelope。以下代码块是 `data` 中的 `BatchStatistics`；其中保留四个兼容汇总字段一个兼容周期：

```json
{
  "schemaVersion": 1,
  "batchId": 101,
  "houseName": "1号兔舍",
  "batchCode": "2024-第7胎",
  "calculatedAt": "2026-09-04T03:20:00Z",
  "totalLitters": 1004,
  "totalKits": 10040,
  "totalLiveKits": 9870,
  "totalWeaned": 8604,
  "metrics": [
    {
      "code": "CONCEPTION_RATE",
      "name": "受胎率",
      "stage": "MATING",
      "stageName": "配种",
      "order": 30,
      "excelColumnName": "受胎率",
      "valueType": "NUMBER",
      "unit": "PERCENT",
      "format": "PERCENT_2",
      "formula": "确认怀孕周期数 / 已配种周期数",
      "status": "AVAILABLE",
      "numericValue": 0.8609756097560975,
      "displayValue": "86.10%",
      "dateValue": null,
      "numerator": {"code": "PREGNANT_CYCLES", "label": "确认怀孕周期数", "value": 1059, "unit": "COUNT"},
      "denominator": {"code": "MATED_CYCLES", "label": "已配种周期数", "value": 1230, "unit": "COUNT"},
      "components": [],
      "missingCauses": []
    }
  ]
}
```

### 3.1 类型规则

- `schemaVersion` 固定为 `1`。不兼容的字段或枚举变更必须提升版本，不能在同一版本内改变含义。
- `data.houseName` 和 `data.batchCode` 是非空展示元数据。它们分别来自本次 `selectBatch(houseId, batchId)` 查询命中的 `rabbit_houses.name` 和 `batches.batch_code`；授权与批次身份仍使用请求中的 `houseId`、`batchId` 和响应中的 `batchId`，不能使用名称或编号代替 ID 校验。
- `metrics` 始终包含固定的 28 项，按 `order` 升序返回。缺数据不删除指标。
- `valueType` 只有 `NUMBER` 和 `DATE_RANGE`。数值指标使用 `numericValue`，配种日期使用 `dateValue`，未使用的值字段必须为 `null`。
- `dateValue` 的固定结构是 `firstDate`、`lastDate`、`dateCount`、`dailyCycleCounts`；每日项固定为 `{date, cycleCount}`，按日期升序。
- `status` 只有 `AVAILABLE`、`NOT_APPLICABLE`、`NOT_RECORDED` 和 `DATA_MISSING`。非 `AVAILABLE` 指标的 `numericValue`、`dateValue` 和 `displayValue` 均为 `null`。
- `numerator`、`denominator` 和 `components` 使用同一结构 `{code, label, value, unit}`。不适用时为 `null` 或空数组，不省略字段。
- `missingCauses` 是有序数组，每项固定为 `{code, message}`。同一指标存在多个缺口时全部返回，按指标目录规定的依赖顺序排列；客户端主值显示状态文本，展开“查看口径”后显示全部原因，不能根据中文 `message` 判断逻辑。
- `displayValue` 仅在 `AVAILABLE` 时有值。客户端直接展示该字段，原始值用于自动化断言和后续机器处理。
- `calculatedAt` 使用 UTC ISO 8601。客户端刷新失败时保留上次成功响应及该时间。
- 同一 `schemaVersion` 下出现未知指标 code 时，客户端可以忽略该项以保持向前兼容；缺少固定 28 项、出现未知 `valueType` 或未知 `status` 时，将统计区作为契约错误处理并提示升级，不能让整页崩溃。

完整指标目录、公式、单位、Excel 列名和状态传播规则见 `research/metric-catalog.md`。

### 3.2 Excel 导出契约

新增端点：

```text
GET /api/reports/batches/{batchId}/statistics.xlsx
X-House-Id: <houseId>
Requires: rabbit:reports:export
```

`rabbit-reporting` 依赖现有 `rabbit-production`。`BatchStatisticsExportController` 调用 `BatchStatisticsService` 一次，再由 `BatchStatisticsWorkbookWriter.prepare/write` 校验快照并生成工作簿。写出器不查询业务表、不复制公式，也不读取飞书附件。文件中的兔舍名称、批次编号、28 项值、状态、原因和 `calculatedAt` 都来自同一统计快照。

响应类型固定为 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，使用 RFC 5987 `filename*` 返回包含 `batchCode` 的 UTF-8 文件名，并保留包含该编号 ASCII 部分的 `filename` 回退。文件名格式为 `批次-{batchCode}-统计-{UTC yyyyMMddHHmmss}.xlsx` 和 `batch-{batchCode}-statistics-{UTC yyyyMMddHHmmss}.xlsx`。文件名单独清理批次编号中的路径分隔符、控制字符和平台保留字符；工作表中的批次编号仍使用快照原值。

实现使用成熟的 OOXML 库并通过 try-with-resources 关闭工作簿。导出失败发生在响应提交前时走现有业务错误响应；写流中断只记录 traceId，不能再写 JSON 到已提交的文件流。

工作簿固定包含两个页签：

1. `批次统计`。首行表头依次为兔舍、批次编号、统计时间和指标目录中的 28 个 `excelColumnName`，第二行是当前批次结果。`AVAILABLE` 数值写为数值单元格并应用对应 Excel 数字格式；日期范围写为 ISO 日期或范围文本；其他状态写入“暂无可计算数据”“未录入”或“历史数据缺失”。冻结首行并启用筛选，不合并单元格。
2. `口径与状态`。每个指标一行，列顺序固定为顺序、指标编码、阶段、指标名称、单位、原始值、展示值、状态、公式、分子、分母、组成项、缺失原因。非可用指标的原始值和展示值留空，由状态列和缺失原因列说明结果。单位使用“日期、只、窝、只/窝、%、比值、kg、kg/只、元、元/kg、元/只”等展示文本；配种日期的每日明细在组成项中写为 `{date}: {cycleCount} 个周期`。分子、分母和组成项使用可读文本，缺失原因包含全部机器码和中文说明，保持接口顺序。

两个页签都使用同一 `BatchStatistics` 对象。工作簿不包含附件独有指标、隐藏公式、宏、外部链接或运行时附件内容。

现有 `E2eApiClient.download`、`ReportController` 流式 CSV 和 `FieldInventoryNfcExportIT` 提供下载、权限与响应头测试模式。Excel 测试还必须重新打开生成文件并断言页签、28 项表头、单元格类型、状态和样例值，不能只检查字节非空。

## 4. 数据持久化与写入契约

追加迁移 `V55__batch_statistics_write_snapshots.sql` 已新增 `litters.weaning_total_weight_kg`、`feed_log_batch_allocations`、`sale_order_batch_allocations`、`replacement_batch_allocations` 和 `batch_carcass_yield_versions`；`V56__outbound_draft_batch_allocations.sql` 已新增 `outbound_task_batch_allocations`，用于恢复服务端权威的出库确认草稿。可空批次分组使用 `batch_scope_id` 或 `source_batch_scope_id` 建立稳定唯一键；出肉率版本保存 `evidence_file` 和 `payload_hash`。每条租户数据都带 `house_id`，关联查询和写入同时校验 `houseId`。子表通过 `(parent_id, house_id)` 或 `(batch_id, house_id)` 复合外键绑定同一兔舍，而不是仅依赖彼此独立的父记录、批次和兔舍外键。新快照和服务计算全部使用 `BigDecimal`，不能沿用实体中的 `Double` 做合计或相等比较。固定精度如下：投喂量沿用 `feed_logs.amount` 的两位小数；断奶、销售和转后备重量使用三位小数；重量单价使用两位小数；金额使用两位小数；出肉率按 0 至 1 保存六位小数。

当前写链路的所有权保持不变：

- `FeedService.addFeedLog` 负责投喂、兔只关联、库存流水和任务完成，并已使用 `@Transactional`、`@TrackedOperation(code = "feed:add")` 和 `RequestDedupService`。
- 繁殖域的断奶动作更新 `litters`；`BatchWeaningSeparationService` 负责后续分笼。旧 `weaning_records` 不作为 V26 以后批次统计的权威来源。
- `OutboundSubmitService` 是批量出库与销售快照的主路径；`SaleService` 是仍需兼容的旧销售路径。
- 兔只域的转后备操作写入每只兔的 `replacement_records`。
- 新增子表必须在父服务现有事务中保存，操作追踪和幂等继续由父服务负责。

### 4.1 投喂分配

新增 `feed_log_batch_allocations` 明细，逻辑字段如下：

```text
feed_log_id
house_id
batch_id nullable
phase: BREEDING | FATTENING | UNASSIGNED
amount_kg DECIMAL(10,2)
created_at
```

`batch_id` 非空时 `phase` 只能是 `BREEDING` 或 `FATTENING`；`batch_id` 为空时 `phase` 必须是 `UNASSIGNED`。同一投喂单中的 `(batch_id, phase)` 不得重复。

客户端先用选中的兔只和 `feedTime` 请求分组预览。服务端按投喂时点解析可归属组：

- 在 `batch_rabbits` 的母兔成员有效期内，且对应批次已有不晚于 `feedTime` 的配种周期时，归为该批次 `BREEDING`。同一母兔存在重叠批次时返回全部候选组，要求人工分配。
- 商品兔按操作时的 `birth_batch_id` 或商品兔批次成员快照归为 `FATTENING`，且 `feedTime` 不得早于该批次的断奶分笼时间。
- 公兔、配种前母兔、无批次商品兔及其他兔只进入 `UNASSIGNED`。

保存时服务端重新解析分组，防止预览后成员状态变化。客户端提交的组必须与最新候选集合一致，每行用量大于零，按两位小数归一后的分配总和必须精确等于 `feed_logs.amount`。只有一个候选组且没有未归属对象时自动归属；其他情况必须分别提交。

当前 Flutter 固定提交 `unit = kg`，但后端没有校验。新版分配请求只接受大小写归一后的 `kg`，其他单位返回 400，不在本期引入袋、克或库存单位换算。历史日志单位不是 kg 时，相关料肉比返回 `MISSING_FEED_UNIT`。

旧投喂记录没有分配明细时不做均摊。完整性检测通过 `feed_log_rabbits`、投喂时点成员有效期和上述分组规则重建候选集合；缺少分配的旧记录会把所有相关候选批次、阶段标为 `MISSING_FEED_ALLOCATION`。重叠批次无法证明唯一归属时保守地标记全部候选批次。

### 4.2 断奶总重

在 `litters` 上新增可空的 `weaning_total_weight_kg`。新客户端在 `weanedCount > 0` 时必须提交大于零的总重；后端由总重除以数量派生并保留现有 `avgWeaningWeight` 兼容字段。旧 `weaning_records.avg_weight` 只服务旧流程，不参与新批次统计。

历史行保持为空，不用兔只当前体重或旧平均值自动回填。断奶数量仍可统计，断奶均重和依赖总重的育肥期料肉比返回 `DATA_MISSING`。

新 Admin 和 Flutter 均只提交总重。兼容开关开启时，未升级 Flutter 只带旧平均体重的请求保留原行为，并记录 `LEGACY_WEANING_WEIGHT_GAP` 事件；开关关闭后在事务开始前返回升级提示。任何阶段都不得把旧平均值伪装成实测总重。

### 4.3 出库批次分配

新增销售订单批次分配明细，逻辑字段如下：

```text
sale_order_id
house_id
batch_id nullable
rabbit_count
actual_weight_kg
unit_price_per_kg nullable only during compatibility
amount nullable only during compatibility
created_at
```

客户端仍提交订单级总重量和选中兔只，同时增加每个批次及未归批次组的实际重量。服务端根据选中兔只和操作时批次快照派生组及只数，不信任客户端提交的只数。销售操作要求统一的正数重量单价，并校验：

```text
各组实际重量之和 = 订单总重量，统一为三位小数后精确比较
订单总金额 = HALF_UP(订单总重量 × 统一重量单价, 2)
各组金额之和 = 订单总金额
```

只有一个组时由服务端自动分配订单总重量。新版混批请求缺少分配时返回明确业务错误，不能按只数或当前体重均摊。兼容开关开启时，旧混批请求保留订单并记录 `LEGACY_SALE_ALLOCATION_GAP` 事件；开关关闭后在事务开始前返回升级提示。`batch_id` 为空的组进入订单总账，但不进入批次统计。

现有 `sale_order_items.batch_id_snapshot` 继续作为出栏只数和分组校验来源。旧订单缺少批次重量或金额分配时，能独立确认的数量可继续使用；重量、金额、单价和两个料肉比返回 `DATA_MISSING`。

`POST /api/sales` 仍由 Admin 和 Flutter 的单兔销售使用。服务在调用 `rabbitEvent` 退出批次前冻结每只兔的批次归属。所有兔只属于同一批次时，订单总重量自动写入该批次分配；全部未归批次时写入 `batch_id = null` 的分配且不影响批次统计。存在多个批次却没有分组重量，或没有统一单价时，兼容开关开启可完成旧操作，并按受影响批次分别记录 `LEGACY_SALE_ALLOCATION_GAP` 或 `LEGACY_SALE_PRICE_GAP`；前者不写无法证明的分配，后者可保存重量但单价和金额保持空值。缺单价只让总销售金额和两项销售单价返回 `MISSING_SALE_UNIT_PRICE`，不影响已有实际重量、均重或料肉比。关闭开关后，这两类不完整请求在事务开始前拒绝。新版请求以及可自动完整归属的单兔请求不受兼容开关影响。

每组先按 `HALF_UP(组重量 × 统一单价, 2)` 计算金额。若各组舍入后之和与订单总金额有一分钱级尾差，把尾差调整到实际重量最大的组；同重时按非空 `batch_id` 升序，未归批次组最后。服务端保存调整后的金额快照，客户端不得自行分配尾差。

`PUT /api/outbound/tasks/{taskId}` 允许 `WAITING_CONFIRMATION` 保存空或部分分配，便于确认页逐步编辑和跨设备恢复。服务端将冻结兔只、销售日期、总重、`unitPricePerKg`、客户、备注和批次分配一起保存。`POST /api/outbound/tasks/{taskId}/submit` 在事务内锁定并加载草稿，逐项比较提交载荷与服务端快照，并只使用服务端值创建订单和出库记录；兔只批次归属自冻结后发生变化时返回冲突。客户端最终提交前必须强制保存最新草稿，不能让防抖保存与提交竞态。

### 4.4 转后备重量

当前 `replacement_records` 按兔只保存转换记录，适合保留兔只明细，但不适合重复保存一次称重得到的批次总重。新增 `replacement_batch_allocations`，逻辑字段如下：

```text
house_id
request_id
source_batch_id nullable
rabbit_count
total_weight_kg DECIMAL(12,3)
created_by
created_at
```

转后备操作按来源批次提交实测总重。服务端从待转换兔只的 `birth_batch_id` 和操作时批次快照派生分组与只数，校验客户端分组完整且每组总重大于零。每只兔继续写现有 `replacement_records`，分组总重只写一次分配表；两者使用同一 `requestId` 和事务。

`source_batch_id` 为空的组保留操作记录，但不进入任何批次统计。统计只读取操作时快照，不使用转换后的当前分类或当前体重。历史转换只有数量没有重量时，两个料肉比返回 `DATA_MISSING`。兼容开关开启时，未升级客户端的转换请求保留原操作并记录 `LEGACY_REPLACEMENT_WEIGHT_GAP` 事件；开关关闭后在事务开始前返回升级提示。

### 4.5 出肉率版本

新增批次出肉率版本表，记录：

```text
house_id
batch_id
yield_rate
source_unit
measured_date
report_number nullable
evidence_file nullable
remark nullable
change_reason
request_id
created_by
created_at
```

写接口采用追加语义，不覆盖旧行：

```text
POST /api/batches/{batchId}/carcass-yields
Requires: rabbit:batches:edit
```

首次录入允许简短说明，修改已有值时 `changeReason` 必填。`yieldRate` 必须大于 0 且不超过 1。MVP 中每条版本都有效，不提供删除、作废或恢复；录错时只能追加更正版本。统计端点按 `created_at`、`id` 倒序读取最新版本，没有记录时返回 `NOT_RECORDED`。

`requestId` 在兔舍内唯一。相同 `requestId` 和相同规范化请求体重放时返回原版本；相同 `requestId` 携带不同内容时返回幂等冲突，不能新增版本。

完整历史使用独立读接口并支持分页：

```text
GET /api/batches/{batchId}/carcass-yields
Requires: rabbit:audit:list
```

通用操作事件可以记录“录入或修改了出肉率”，但不能替代保存全部业务字段的版本表。

### 4.6 事务、幂等与操作追踪

现有 `RequestDedupService` 以 `(house_id, user_id, api_code, request_id)` 判重，`begin(..., payloadHash)` 会校验请求载荷并记录 `PROCESSING`、`DONE`、`FAILED`。投喂、断奶、出库和转后备继续使用各自现有的 API code；新增分配行不创建第二套 requestId，也不在事务外单独提交。已经使用 payload hash 的分笼和出库路径继续复用；仍使用旧 `shouldSkipAsDone` 或 `markProcessing` 的投喂、销售和转后备路径，在本任务触及时迁移到 `begin(..., payloadHash)`。

父请求重放时：

- 状态为 `done` 时返回既有结果，不重复插入父记录或分配行。
- 状态为 `failed` 时允许使用同一 requestId 重试完整事务。
- 同一 requestId 对应不同规范化请求体时返回幂等冲突。

出肉率是新操作，增加独立 API code 和 `@TrackedOperation`。所有写接口继续由原有业务权限保护；统计读取沿用 `rabbit:batches:query`，不新增 `rabbit:statistics:*` 权限。

## 5. 统计计算与完整性

### 5.1 归属

- 配种和怀孕按 `breeding_cycles.batch_id` 归属。受胎率按确认怀孕和已配种的去重 `cycle_id` 计算，怀孕数量按确认怀孕周期中的 `mother_rabbit_id` 去重。`mating_method = AI` 时 `male_rabbit_id` 为空是合法状态，不算数据缺失；自然配种周期缺少公兔才返回 `MISSING_NATURAL_MALE`。公母比例的分母只统计实际参与的去重公兔。
- 流产事件按 `repro_events.cycle_id` 关联到已确认怀孕周期，不能只按当前母兔所在批次归属。流产事件找不到周期或对应周期没有确认怀孕依据时，流产率返回 `DATA_MISSING`。
- 产崽、选留和断奶按 `litters.batch_id` 统计，`batch_id` 为空的散养记录排除。
- 出栏数量按销售明细的批次快照统计，重量和金额按订单批次分配统计。
- 转后备按转换操作的来源批次快照统计。
- 饲料只按显式的批次和阶段分配统计。

### 5.2 时间边界

配种、怀孕、产崽、断奶、销售和转换以记录上的批次归属为准。饲料从 `DATE(最早 breeding_cycles.mating_date)` 开始并包含起点；已结束批次以 `DATE_ADD(batches.end_date, INTERVAL 1 DAY)` 为排他上界，未结束批次包含查询时刻。现有模型没有兔舍时区，本期按数据库保存的业务本地时间比较，不做时区换算。不得用当前成员关系补写历史归属。

飞书原表把出肉率归在“屠宰”，产品页面已经确认只使用八组，因此接口和两端 UI 将其放在 `FEED_CONVERSION` 组的末尾，组标题显示“料肉比”。不新增第九组。

### 5.3 状态规则

`DATA_MISSING` 的优先级最高。例如历史出库重量缺失时，即使可见重量合计为零，也不能返回 `NOT_APPLICABLE`。来源完整且分母为零时才返回 `NOT_APPLICABLE`。真实分子为零且分母有效时返回 `AVAILABLE` 和零值。

育肥增重等于零时，育肥期料肉比返回 `NOT_APPLICABLE` 和 `ZERO_DENOMINATOR`。育肥增重小于零说明出栏、转后备或断奶重量账互相矛盾，返回 `DATA_MISSING` 和 `INVALID_FATTENING_GAIN`，客户端及 Excel 显示“历史数据缺失”，明细中显示核对重量账的原因。

某一数据源缺失只影响依赖它的指标。断奶总重缺失不影响断奶数量；销售重量缺失不应影响已能由兔只快照确认的出栏数量。

## 6. Admin 设计

新增独立路由 `/workspace/production/batches/:batchId` 和批次详情页。生产批次列表的桌面行与移动条目都提供详情入口，已结束批次仍可进入。现有兔场数据页保留四项概览，并在已选批次旁增加“查看完整批次统计”入口。

详情页复用兔只详情页的路由参数校验、全页加载、返回入口和主数据错误处理。统计区按配种、怀孕、产崽、选留、断奶、出栏、销售、料肉比八组展示。宽屏保留飞书原文的固定 16 行关系，包括第 14 行三个独立销售指标；窄屏和 200% 字号时按原顺序逐项降为单列。组是无外层卡片的页面分节，指标可使用紧凑重复项，不做卡片套卡片。

每个指标显示后端 `displayValue`、状态和口径入口。展开后显示公式、分子、分母、组成项和缺失原因。统计请求失败只影响统计区；已有成功数据时保留旧值、标注取数时间并提供局部重试。

出肉率录入按钮只对 `rabbit:batches:edit` 可见，完整版本历史只对 `rabbit:audit:list` 可见。具备 `rabbit:reports:export` 时，页面头部显示带下载图标的“导出 Excel”操作；无权限时不显示入口，后端仍独立拒绝直接请求。请求层增加携带业务 token 和 `X-House-Id` 的 Blob 下载方法，从 `Content-Disposition` 读取服务端文件名并及时释放对象 URL。下载中禁用重复点击，失败保留页面统计。

桌面按 `1440x900`，窄屏按 `390x844` 验证，不得出现水平溢出、文字遮挡或不可达操作。

主要触点：

- `admin/src/App.tsx`
- `admin/src/pages/workspace-production-page.tsx`
- 新增 `admin/src/pages/workspace-batch-detail-page.tsx`
- `admin/src/pages/workspace-livestock-page.tsx`
- `admin/src/components/batch-statistics.tsx`
- `admin/src/api/workspace.ts`
- `admin/src/types/`
- `admin/src/components/workspace-outbound-dialog.tsx`
- `admin/src/components/rabbit-operation-dialogs.tsx`

## 7. Flutter 设计

完整统计继续放在现有 `/houses/:houseId/batches/:batchId` 详情页，不新增路由。替换原四项统计组件；宽屏保留飞书原文的固定 16 行关系，包括第 14 行三个独立销售指标；窄屏和 200% 字号时按原顺序逐项降为单列。统计区保留独立加载、失败和重试，不让统计失败阻断批次成员操作。数据看板在选定兔舍和批次后增加“查看完整批次统计”入口。

领域模型解析 28 项结构化指标和四种状态。Repository 继续发送 `X-House-Id`，Riverpod provider 继续以 `houseId + batchId` 为 family key并保留取消能力。成功后刷新失败时不清空旧数据。

投喂、断奶、出库和转后备表单增加相应分配或总重输入。出库离线草稿必须同步保存批次重量分配和统一单价，恢复草稿后继续执行同一套合计校验。出肉率录入和历史权限与 Admin 一致。

`ApiClient.downloadProtected` 携带认证 token、`X-House-Id` 和 `X-App-Build`，保留 `Content-Type` 与 `Content-Disposition`，并把 JSON 错误响应还原为业务异常；它不复用不带业务请求头的 OTA 下载路径。具备 `rabbit:reports:export` 时，批次统计区显示导出操作；无权限时不显示入口，后端仍独立拒绝直接请求。文件写入应用临时目录，再通过 Android 系统分享或保存入口交给用户；`path_provider` 和分享库是直接依赖，不申请广泛存储权限。下载中禁用重复触发，下载或分享失败不清空页面统计，分享结束后尽力删除临时文件。

在 `360x800` 和 `412x915` 下验证，200% 字号时指标降为单列，长缺失原因可以换行，控件和操作按钮不能重叠。

主要触点：

- `app/lib/src/domain/batches/statistics.dart`
- `app/lib/src/data/repositories/batches/repository.dart`
- `app/lib/src/ui/batches/view_models/providers.dart`
- `app/lib/src/ui/batches/screens/detail.dart`
- `app/lib/src/ui/dashboard/screens/overview.dart`
- `app/lib/src/ui/feed/screens/entry.dart`
- `app/lib/src/ui/reproduction/sheets/weaning.dart`
- `app/lib/src/domain/outbound/workflow.dart`
- `app/lib/src/ui/outbound/view_models/controller.dart`
- `app/lib/src/data/services/storage/outbound.dart`
- `app/lib/src/ui/outbound/screens/flow.dart`
- `app/lib/src/ui/rabbits/sheets/replacement.dart`

## 8. 兼容与发布顺序

统计读接口采用加法兼容：后端先增加 `metrics`，同时保留旧四字段一个兼容周期。旧客户端继续读取四项摘要，新客户端改读 28 项契约。

写接口采用可观测的兼容过渡。增加服务端配置 `app.batch-statistics.legacy-write-enabled`，第一阶段默认为 `true`。旧投喂、断奶、混批出库、单兔销售和转后备载荷继续完成能够原子完成的原操作，但不得生成不存在的测量快照；受影响指标按依赖返回 `DATA_MISSING`。

缺口事件复用持久化操作事件表 `repro_events`，`target_type = BATCH`，`target_id` 和 `batch_id` 均为受影响批次。事件类型固定为 `LEGACY_FEED_ALLOCATION_GAP`、`LEGACY_WEANING_WEIGHT_GAP`、`LEGACY_SALE_ALLOCATION_GAP`、`LEGACY_SALE_PRICE_GAP`、`LEGACY_REPLACEMENT_WEIGHT_GAP`。顶层字段保存 `house_id`、`request_id` 和 `occurred_at`，payload 只保存 `clientBuild`，旧客户端未发送 `X-App-Build` 时写 `UNKNOWN`，不保存请求正文。事件与原业务写入处于同一事务，父操作回滚时缺口事件也回滚。

操作事件当前没有清理任务，本期沿用其持久化保留。发布负责人按数据库业务本地时间执行以下查询，并把窗口、执行人和结果写入发布检查记录：

```sql
SELECT DATE(occurred_at) AS business_date,
       event_type,
       COUNT(*) AS gap_count
FROM repro_events
WHERE event_type IN (
  'LEGACY_FEED_ALLOCATION_GAP',
  'LEGACY_WEANING_WEIGHT_GAP',
  'LEGACY_SALE_ALLOCATION_GAP',
  'LEGACY_SALE_PRICE_GAP',
  'LEGACY_REPLACEMENT_WEIGHT_GAP'
)
  AND occurred_at >= :window_start
  AND occurred_at < :window_end
GROUP BY DATE(occurred_at), event_type
ORDER BY business_date, event_type;
```

`:window_start` 是真机验证通过后下一个自然日的 00:00:00，`:window_end` 是第八个自然日的 00:00:00。结果必须为空；任何一行都重新开始 7 天观察。查询不带 `house_id`，因此覆盖全部兔舍。

发布顺序固定为：

1. 部署追加迁移和兼容模式后端，验证旧四字段、新 28 项、Excel 和旧写入均可用。
2. 发布新版 Admin 和 Flutter。Flutter 发布记录设置 `force_update = true`，确保旧 Android 客户端升级后才能继续使用。
3. 在生产 Android 设备完成投喂、断奶、混批出库、转后备和 Excel 分享冒烟验证。
4. 从真机验证通过后的下一自然日开始观察。连续 7 个完整自然日没有旧载荷缺口事件后，将 `legacy-write-enabled` 设为 `false` 并重新部署后端。
5. 关闭兼容后，缺少新快照字段的请求在事务开始前返回业务冲突和“当前版本过低，请升级应用后重试”，不得写入父记录或任何子记录。

回退不逆向删除数据库结构。新版客户端或强制更新异常时，立即重新开启 `legacy-write-enabled`，下架问题 Flutter 发布记录，恢复旧写入并继续将缺口标记为 `DATA_MISSING`。统计或 Excel 新读路径异常时，可以回滚应用代码并保留旧四字段和所有新增表列。

历史数据不回填。后续如需补录，另建数据治理任务并要求过磅单、销售单或饲料台账凭证。

## 9. 风险与控制

- 统计服务涉及多张业务表。使用多个小型原始聚合查询和服务层组合，避免一条巨大 SQL 产生重复行和错误求和。
- 同一母兔可能有多个周期。所有受胎和流产分子、分母按 `cycle_id` 去重；配种母兔、怀孕数量和公兔按兔只去重。
- 混批和未归批次组容易造成合计漂移。服务端以订单总量为最终约束，并在同一事务内保存订单和分配。
- 小数舍入可能让分组金额合计产生尾差。服务端按 §4.3 的固定规则计算订单金额并分配尾差，数据库保存两位小数金额快照；展示值继续使用 `HALF_UP`。
- Excel 依赖会增加后端包体和解析攻击面。只由服务端创建工作簿，不接收或解析用户上传的 Excel；固定单批次 28 项规模，并在依赖检查中覆盖已知漏洞。
- `docs/features/batch-statistics/` 已与稳定实现同步；后续修改接口、页面落点、状态或兼容策略时，必须同时更新专题文档和本任务的验证记录。

## 10. 验收数据

附件真实样例、补充输入、28 项原始值与展示值、混批、散养、同兔多周期、分母为零、历史缺字段、权限和刷新失败场景见 `research/acceptance-fixture.md`。
