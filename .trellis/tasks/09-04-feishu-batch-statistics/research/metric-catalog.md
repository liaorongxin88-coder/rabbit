# 批次统计指标契约

## 1. 通用规则

- 接口始终按 `batchId + houseId` 查询，服务和 SQL 都必须保留兔舍条件。
- 统计只使用记录上的批次归属或操作时保存的批次快照，不使用兔只当前所在兔舍、笼位或当前体重反推历史数据。
- 计数、重量和金额在来源完整时允许显示真实零值。比率和均值的分母为零时返回 `NOT_APPLICABLE`。
- 应由人工录入但尚未录入的数据返回 `NOT_RECORDED`。历史记录缺少可靠快照时返回 `DATA_MISSING`，且缺失优先级高于分母为零。
- 聚合来源不完整时，受影响的指标整项不展示部分合计。其他来源完整的指标继续返回。
- 数值以原始精度计算。计数显示整数；重量、金额、单价、普通比值和料肉比显示两位小数；百分比原始值范围为 0 至 1，显示两位百分比；公母比例显示为 `x.xx:1`。展示值使用 `HALF_UP`。
- 配种日期按业务自然日去重并升序排列。一个日期直接显示日期；多个日期显示起止范围和日期数，并返回每日去重周期数。

状态优先级为：

```text
DATA_MISSING > NOT_RECORDED > NOT_APPLICABLE > AVAILABLE
```

其中 `NOT_RECORDED` 只用于配种日期和外部录入的出肉率。其余计数或合计在来源完整但没有业务记录时以 `AVAILABLE` 返回零。

## 2. 固定指标目录

接口和 Excel 列顺序由 `order` 固定，客户端不得重新命名或重排。`excelColumnName` 是 `.xlsx` 统计结果页的正式列名，导出器必须直接消费本目录和同次统计快照。

| order | code | 阶段 | 名称 | 原始计算 | 单位与格式 | excelColumnName |
| ---: | --- | --- | --- | --- | --- | --- |
| 10 | `MATING_DATE` | `MATING` | 配种日期 | `breeding_cycles.mating_date` 按自然日去重 | 日期范围 | 配种日期 |
| 20 | `MATED_DOE_COUNT` | `MATING` | 配种母兔数 | 已配种周期去重 `mother_rabbit_id` | 整数，只 | 配种母兔数 |
| 30 | `CONCEPTION_RATE` | `MATING` | 受胎率 | 确认怀孕周期数 / 已配种周期数，均去重 `cycle_id` | 百分比 | 受胎率 |
| 40 | `DOE_BUCK_RATIO` | `MATING` | 配种母兔/公兔比例 | 去重配种母兔数 / 去重参与配种公兔数 | `x.xx:1` | 配种母兔/公兔比例 |
| 50 | `PREGNANT_DOE_COUNT` | `PREGNANCY` | 怀孕数量 | 确认怀孕周期去重 `mother_rabbit_id` 数量 | 整数，只 | 怀孕数量 |
| 60 | `ABORTION_RATE` | `PREGNANCY` | 流产率 | 已怀孕且发生流产的去重周期数 / 确认怀孕周期数 | 百分比 | 流产率 |
| 70 | `DELIVERED_LITTER_COUNT` | `BIRTH` | 产崽窝数 | 批次内 `litters.id` 数量 | 整数，窝 | 产崽窝数 |
| 80 | `TOTAL_KIT_COUNT` | `BIRTH` | 产崽总数 | `sum(litters.total_kits)` | 整数，只 | 产崽总数 |
| 90 | `AVERAGE_KITS_PER_LITTER` | `BIRTH` | 平均窝产数 | 产崽总数 / 产崽窝数 | 两位小数，只/窝 | 平均窝产数 |
| 100 | `LIVE_KIT_COUNT` | `BIRTH` | 活崽总数 | `sum(litters.live_kits)` | 整数，只 | 活崽总数 |
| 110 | `LIVE_BIRTH_RATE` | `BIRTH` | 平均活崽率 | 活崽总数 / 产崽总数 | 百分比 | 平均活崽率 |
| 120 | `KEPT_LITTER_COUNT` | `SELECTION` | 选留窝数 | `count(litters.id where kept_kits > 0)` | 整数，窝 | 选留窝数 |
| 130 | `KEPT_KIT_COUNT` | `SELECTION` | 选留总数 | `sum(litters.kept_kits)` | 整数，只 | 选留总数 |
| 140 | `KEPT_LIVE_RATE` | `SELECTION` | 选留活崽率 | 选留总数 / 活崽总数 | 百分比 | 选留活崽率 |
| 150 | `AVERAGE_KEPT_PER_LITTER` | `SELECTION` | 窝均选留 | 选留总数 / 选留窝数 | 两位小数，只/窝 | 窝均选留 |
| 160 | `WEANED_KIT_COUNT` | `WEANING` | 断奶数量 | `sum(litters.weaned_count)` | 整数，只 | 断奶数量 |
| 170 | `AVERAGE_WEANING_WEIGHT` | `WEANING` | 断奶均重 | 断奶总重快照之和 / 断奶数量 | 两位小数，kg/只 | 断奶均重 |
| 180 | `WEANING_SURVIVAL_RATE` | `WEANING` | 断奶成活率 | 断奶数量 / 选留总数 | 百分比 | 断奶成活率 |
| 190 | `SOLD_RABBIT_COUNT` | `OUTBOUND` | 出栏数量 | 已完成销售中批次快照匹配的兔只数 | 整数，只 | 出栏数量 |
| 200 | `OUTBOUND_SURVIVAL_RATE` | `OUTBOUND` | 出栏成活率 | 出栏数量 / 断奶数量 | 百分比 | 出栏成活率 |
| 210 | `SOLD_WEIGHT` | `OUTBOUND` | 出栏总重 | 销售订单批次分配中的实际销售重量之和 | 两位小数，kg | 出栏总重 |
| 220 | `AVERAGE_SOLD_WEIGHT` | `OUTBOUND` | 出栏均重 | 出栏总重 / 出栏数量 | 两位小数，kg/只 | 出栏均重 |
| 230 | `TOTAL_SALES_AMOUNT` | `SALES` | 总销售金额 | 销售订单批次分配中的金额快照之和 | 两位小数，元 | 总销售金额 |
| 240 | `SALES_PRICE_PER_KG` | `SALES` | 销售单价（重量口径） | 总销售金额 / 出栏总重 | 两位小数，元/kg | 销售单价（重量口径） |
| 250 | `SALES_PRICE_PER_RABBIT` | `SALES` | 销售单价（只数口径） | 总销售金额 / 出栏数量 | 两位小数，元/只 | 销售单价（只数口径） |
| 260 | `FULL_FEED_CONVERSION_RATIO` | `FEED_CONVERSION` | 全程料肉比 | 批次全程饲料量 /（商品兔实际销售重量 + 转后备兔实测总重） | 两位小数 | 全程料肉比 |
| 270 | `FATTENING_FEED_CONVERSION_RATIO` | `FEED_CONVERSION` | 育肥期料肉比 | 批次育肥饲料量 /（商品兔实际销售重量 + 转后备兔实测总重 - 断奶总重） | 两位小数 | 育肥期料肉比 |
| 280 | `CARCASS_YIELD_RATE` | `FEED_CONVERSION` | 出肉率 | 按创建时间和主键倒序的最新出肉率版本 | 百分比 | 出肉率 |

附件验收映射保留原列名：`MATED_DOE_COUNT` 对应“配种数量”，`PREGNANT_DOE_COUNT` 对应“受胎数量”，`DELIVERED_LITTER_COUNT` 对应“产仔窝数”，`KEPT_KIT_COUNT` 对应“选留数量”，`SALES_PRICE_PER_KG` 对应“销售单价”。这些旧名称不作为产品界面或正式导出表头。

## 3. 权威来源与完整性

| 数据域 | 权威来源 | 完整性规则 |
| --- | --- | --- |
| 配种与怀孕 | `breeding_cycles`、`repro_events` | 只统计当前批次的周期。`pregnancy_result` 是怀孕判定来源；怀孕数量按确认怀孕周期中的 `mother_rabbit_id` 去重，受胎率和流产率按 `cycle_id` 去重。流产事件必须关联到已怀孕周期。人工授精周期的 `male_rabbit_id` 为空是合法状态；只有自然配种周期缺少公兔时，公母比例才为 `DATA_MISSING`。 |
| 产崽、选留、断奶数量 | `litters` | `batch_id` 为空的散养记录不进入任何批次。基础数量字段缺失时，仅依赖该字段的指标为 `DATA_MISSING`。 |
| 断奶重量 | 断奶操作保存的总重快照 | 任何断奶数量大于零的窝缺少总重快照时，断奶均重为 `DATA_MISSING`；断奶数量和断奶成活率仍可独立计算。 |
| 出栏数量 | 销售明细的兔只与批次快照 | 旧销售记录无法可靠确定批次时，出栏数量及其派生指标为 `DATA_MISSING`。 |
| 出栏重量与金额 | 销售订单批次分配快照 | 缺少批次实际重量时，出栏重量、金额及依赖重量的指标为 `DATA_MISSING`。实际重量完整但统一单价或金额缺失时，只把总销售金额和两项销售单价标为 `DATA_MISSING`；出栏重量、均重和料肉比继续按完整重量计算。不得用兔只当前体重或订单总金额补算批次值。 |
| 转后备重量 | 转后备操作的来源批次与实测总重快照 | 数量可归属但重量快照缺失时，全程和育肥期料肉比为 `DATA_MISSING`。不得使用固定重量折算。 |
| 饲料 | 投喂记录的批次与阶段分配明细 | 新分配只接受 kg。批次相关投喂存在未分配历史记录或非 kg 单位时，受影响的料肉比为 `DATA_MISSING`。未归批次的分配行不进入任何批次统计。 |
| 出肉率 | 批次出肉率追加版本 | 没有版本时为 `NOT_RECORDED`。MVP 不提供作废或删除，统计读取最新版本，完整历史需要 `rabbit:audit:list`。 |

## 4. 时间边界

- 配种、怀孕、产崽、选留和断奶按记录自身的 `batch_id` 归属，不再叠加当前兔舍成员过滤。
- 饲料只统计显式分配给该批次的行，时间从 `DATE(最早配种时间)` 开始并包含起点；已结束批次以 `DATE_ADD(batches.end_date, INTERVAL 1 DAY)` 为排他上界，未结束批次包含查询时刻。现有模型没有兔舍时区，本期按数据库保存的业务本地时间比较。
- 销售只统计已完成且未作废的出库或销售记录。当前系统没有退单或冲正，本任务不新增净额口径。
- 转后备按操作时保存的来源批次快照统计，不根据兔只转换后的当前分类反推。
- 统计结果是查询时快照，响应返回 `calculatedAt`。客户端刷新失败时保留上一份成功结果，并标注其取数时间。

## 5. 状态传播

- 分子为零且分母大于零时返回 `AVAILABLE` 和真实零值。
- 分母为零且所有来源完整时返回 `NOT_APPLICABLE`，`numericValue` 或 `dateValue` 为空。育肥增重小于零表示重量账不一致，返回 `DATA_MISSING` 和 `INVALID_FATTENING_GAIN`。
- 依赖多本账的指标只要任一来源不完整，就返回 `DATA_MISSING`。例如育肥期料肉比依赖育肥饲料、销售重量、转后备重量和断奶总重。
- 出栏数量可以在重量分配缺失时保持 `AVAILABLE`，但出栏总重、销售金额和两个料肉比必须分别按依赖关系返回 `DATA_MISSING`。
- 原因使用固定机器码：`MISSING_BATCH_ATTRIBUTION`、`MISSING_NATURAL_MALE`、`MISSING_PREGNANCY_EVIDENCE`、`MISSING_WEANING_WEIGHT`、`MISSING_BATCH_SALE_ALLOCATION`、`MISSING_SALE_UNIT_PRICE`、`MISSING_FEED_ALLOCATION`、`MISSING_FEED_UNIT`、`MISSING_REPLACEMENT_WEIGHT`、`INVALID_FATTENING_GAIN`、`MATING_NOT_RECORDED`、`CARCASS_YIELD_NOT_RECORDED`、`ZERO_DENOMINATOR`。
- 同一指标存在多个原因时全部返回，并严格按上一条的顺序排列。状态由优先级最高的原因决定；客户端主值显示状态文本，展开“查看口径”后显示全部原因。

飞书原表把出肉率归为“屠宰”。产品页面保持已确认的八组结构，因此接口阶段和两端 UI 把出肉率放在 `FEED_CONVERSION` 组末尾，组标题仍显示“料肉比”。
