# 批次统计设计

- 状态：实施中，交付验收未完成
- 核对日期：2026-09-05
- 适用范围：Backend、Admin、Flutter App
- 需求来源：飞书需求记录 `recvsV7QhkxxvD`

## 1. 范围与最终口径

MVP 固定为 28 项。原需求中名为“受胎数量”、定义为配种母兔数量的指标改名为“配种母兔数”。“怀孕数量”固定使用 `PREGNANT_DOE_COUNT`，按确认怀孕周期中的 `mother_rabbit_id` 去重；受胎率和流产率仍按 `cycle_id` 统计，“怀孕数量”不作为受胎率的周期分子。下表顺序就是接口和 Excel 顺序，`order` 从 10 到 280，每项递增 10。

| 阶段 | code | 指标 | 最终计算 |
| --- | --- | --- | --- |
| 配种 | `MATING_DATE` | 配种日期 | 批次配种日期按自然日去重，返回首日、末日、日期数和每日周期数 |
| 配种 | `MATED_DOE_COUNT` | 配种母兔数 | 已配种周期去重母兔数 |
| 配种 | `CONCEPTION_RATE` | 受胎率 | 确认怀孕周期数 / 已配种周期数 |
| 配种 | `DOE_BUCK_RATIO` | 配种母兔/公兔比例 | 去重配种母兔数 / 去重参与配种公兔数 |
| 怀孕 | `PREGNANT_DOE_COUNT` | 怀孕数量 | 确认怀孕周期去重母兔数 |
| 怀孕 | `ABORTION_RATE` | 流产率 | 已怀孕且流产的周期数 / 确认怀孕周期数 |
| 产崽 | `DELIVERED_LITTER_COUNT` | 产崽窝数 | 批次窝数 |
| 产崽 | `TOTAL_KIT_COUNT` | 产崽总数 | 批次各窝产崽数之和 |
| 产崽 | `AVERAGE_KITS_PER_LITTER` | 平均窝产数 | 产崽总数 / 产崽窝数 |
| 产崽 | `LIVE_KIT_COUNT` | 活崽总数 | 批次各窝活崽数之和 |
| 产崽 | `LIVE_BIRTH_RATE` | 平均活崽率 | 活崽总数 / 产崽总数 |
| 选留 | `KEPT_LITTER_COUNT` | 选留窝数 | 选留数大于零的窝数 |
| 选留 | `KEPT_KIT_COUNT` | 选留总数 | 批次各窝选留数之和 |
| 选留 | `KEPT_LIVE_RATE` | 选留活崽率 | 选留总数 / 活崽总数 |
| 选留 | `AVERAGE_KEPT_PER_LITTER` | 窝均选留 | 选留总数 / 选留窝数 |
| 断奶 | `WEANED_KIT_COUNT` | 断奶数量 | 批次各窝断奶数之和 |
| 断奶 | `AVERAGE_WEANING_WEIGHT` | 断奶均重 | 不可变断奶总重之和 / 断奶数量 |
| 断奶 | `WEANING_SURVIVAL_RATE` | 断奶成活率 | 断奶数量 / 选留总数 |
| 出栏 | `SOLD_RABBIT_COUNT` | 出栏数量 | 完成销售中归属该批次的商品兔数 |
| 出栏 | `OUTBOUND_SURVIVAL_RATE` | 出栏成活率 | 出栏数量 / 断奶数量 |
| 出栏 | `SOLD_WEIGHT` | 出栏总重 | 该批次实际销售重量分配之和 |
| 出栏 | `AVERAGE_SOLD_WEIGHT` | 出栏均重 | 出栏总重 / 出栏数量 |
| 销售 | `TOTAL_SALES_AMOUNT` | 总销售金额 | 该批次销售金额快照之和 |
| 销售 | `SALES_PRICE_PER_KG` | 销售单价（重量口径） | 总销售金额 / 出栏总重 |
| 销售 | `SALES_PRICE_PER_RABBIT` | 销售单价（只数口径） | 总销售金额 / 出栏数量 |
| 料肉比 | `FULL_FEED_CONVERSION_RATIO` | 全程料肉比 | 全程饲料 /（商品兔销售重量 + 转后备实测重量） |
| 料肉比 | `FATTENING_FEED_CONVERSION_RATIO` | 育肥期料肉比 | 育肥饲料 /（商品兔销售重量 + 转后备实测重量 - 断奶总重） |
| 料肉比 | `CARCASS_YIELD_RATE` | 出肉率 | 最新一条批次出肉率版本 |

育肥增重等于零时，育肥期料肉比不可计算。育肥增重小于零表示重量账不一致，不能输出负料肉比。

## 2. 数据来源

| 数据 | 权威来源与归属规则 |
| --- | --- |
| 配种与怀孕 | `breeding_cycles.batch_id`。怀孕判定读取 `pregnancy_result`；`PREGNANT_DOE_COUNT` 按确认怀孕周期的 `mother_rabbit_id` 去重，受胎率按确认怀孕与已配种的去重 `cycle_id` 计算。人工授精周期可以没有 `male_rabbit_id`，自然配种缺公兔时公母比例为 `DATA_MISSING`。 |
| 流产 | `repro_events.cycle_id` 关联到已确认怀孕周期，再按周期所属批次统计。找不到周期或怀孕依据时，流产率为 `DATA_MISSING`。 |
| 产崽、选留和断奶数量 | `litters.batch_id`。`batch_id` 为空的散养窝不进入批次统计。 |
| 断奶重量 | 断奶操作保存的 `weaning_total_weight_kg` 不可变快照。不得用旧平均体重或兔只当前体重回填。 |
| 出栏数量 | 已完成销售明细中的兔只和 `batch_id_snapshot`。不能按当前批次成员关系反推。 |
| 出栏重量与金额 | 销售订单的批次实际重量、统一单价和金额分配快照。未归批次组保留在订单总账，但不进入任何批次统计。 |
| 饲料 | 投喂记录的批次与阶段分配。新分配只接受 kg，按 `BREEDING`、`FATTENING` 或 `UNASSIGNED` 保存，不按兔只数量或当前体重自动均摊。 |
| 转后备重量 | 转换操作保存的来源批次、服务端派生只数和实测总重快照。不得使用固定重量折算。 |
| 出肉率 | 批次出肉率追加版本，按 `created_at`、`id` 倒序读取最新版本。 |

统计只使用记录上的批次归属或操作时保存的批次快照，不使用兔只当前所在兔舍、笼位、批次成员关系或当前体重反推历史数据。任何依赖来源不完整时，只将受影响的指标标为 `DATA_MISSING`，其他来源完整的指标继续返回。

## 3. 统计契约

`GET /api/batches/{batchId}/statistics` 返回 `{code, message, data}`。其中 `data` 是 `BatchStatistics`，包含固定为 `1` 的 `schemaVersion`、`batchId`、非空 `houseName`、非空 `batchCode`、UTC `calculatedAt` 和固定 28 项 `metrics`。`houseName` 与 `batchCode` 分别来自同一次兔舍范围内批次查询命中的 `rabbit_houses.name` 和 `batches.batch_code`；授权和批次身份继续使用 `houseId` 与 `batchId`，不能使用名称或编号代替 ID 校验。每项包含：

```text
code, name, stage, stageName, order, excelColumnName,
valueType, unit, format, numericValue, dateValue, displayValue,
formula, numerator, denominator, components,
status, missingCauses
```

`dateValue` 固定为 `firstDate`、`lastDate`、`dateCount` 和按日期升序的 `dailyCycleCounts`，每日项是 `{date, cycleCount}`。`numerator`、`denominator` 和 `components` 共用 `{code, label, value, unit}` 结构；不适用时返回 `null` 或空数组，不省略字段。每个缺失原因固定为 `{code, message}`。

兼容周期内继续在 `data` 中返回旧客户端使用的 `totalLitters`、`totalKits`、`totalLiveKits` 和 `totalWeaned` 四个字段。`stage` 固定使用 `MATING`、`PREGNANCY`、`BIRTH`、`SELECTION`、`WEANING`、`OUTBOUND`、`SALES` 和 `FEED_CONVERSION`。出肉率属于 `FEED_CONVERSION`，不增加第九组。客户端不得重新计算公式、重排或重新命名固定指标。未知指标 code 可以忽略以保持向前兼容；未知状态、未知值类型或缺少固定指标属于契约错误。

四种状态如下：

| 状态 | 含义 | 默认显示 |
| --- | --- | --- |
| `AVAILABLE` | 来源完整且有确定结果，包括真实零值 | 服务端 `displayValue` |
| `NOT_APPLICABLE` | 来源完整但分母为零 | 暂无可计算数据 |
| `NOT_RECORDED` | 配种日期没有业务记录，或出肉率尚未录入 | 未录入 |
| `DATA_MISSING` | 历史快照缺失或来源互相矛盾 | 历史数据缺失 |

状态优先级固定为 `DATA_MISSING > NOT_RECORDED > NOT_APPLICABLE > AVAILABLE`。非 `AVAILABLE` 指标的 `numericValue`、`dateValue` 和 `displayValue` 都为 `null`；已经确定的分子、分母和组成项仍可保留用于诊断。同一指标可以返回多个固定 `missingCauses`，后端按指标依赖顺序返回全部原因；客户端主值显示状态文本，展开“查看口径”后显示全部原因，不能根据中文原因判断逻辑。来源不完整时不得返回部分合计。

## 4. 精度与时间

- 计数显示整数。
- 重量使用 kg；重量、金额、单价、普通比值和料肉比显示两位小数。
- 百分比原始值范围为 0 至 1，显示两位百分比。
- 公母比例显示为 `x.xx:1`。
- 后端使用原始精度计算，只在生成 `displayValue` 时使用 `HALF_UP`。
- `calculatedAt` 使用 UTC ISO 8601。现有模型没有兔舍时区，业务日期和饲料时间边界按数据库保存的业务本地时间直接比较，不在本期做时区换算。
- 饲料统计从 `DATE(最早配种时间)` 开始并包含起点；已结束批次以 `DATE_ADD(end_date, INTERVAL 1 DAY)` 为排他上界，未结束批次包含查询时刻。

## 5. 写入快照

以下数据必须在原业务事务中保存，使用父操作的稳定 `requestId`：

- 投喂的批次、阶段和 kg 数量分配。
- 每窝断奶总重，由后端派生兼容平均体重。
- 销售订单中每个批次和未归批次组的实际重量、统一单价及金额。服务端根据兔只快照派生各组只数，并校验分组重量和金额合计。
- 转后备操作中每个来源批次的实测总重。服务端根据待转换兔只和操作时快照派生分组及只数。
- 出肉率来源单位、检测或屠宰日期、录入人、录入时间、修改说明和追加版本；报告编号、凭证和备注可选。

历史记录没有可靠快照时不自动回填。可独立确认的指标继续返回，其余指标使用 `DATA_MISSING`。

## 6. Excel

`GET /api/reports/batches/{batchId}/statistics.xlsx` 需要 `rabbit:reports:export`。报表模块只调用一次批次统计服务，两个页签共用该对象及其 `houseName`、`batchCode` 和 `calculatedAt`：

1. `批次统计`：首行依次是兔舍、批次编号、统计时间和固定顺序的 28 个 `excelColumnName`，第二行使用快照中的 `houseName` 与 `batchCode`，不能写数据库 ID。冻结首行、启用筛选，不合并单元格。
2. `口径与状态`：按指标顺序逐行导出顺序、指标编码、阶段、指标名称、单位、原始值、展示值、状态、公式、分子、分母、组成项和全部缺失原因。

`批次统计` 页的 `AVAILABLE` 数值写为数值单元格并使用对应的整数、小数或百分比格式，日期写为 ISO 日期或日期范围文本；非可用指标写对应的状态文本，不能写成零。`口径与状态` 页的原始值和展示值留空，由状态列和缺失原因列说明结果；单位列使用“日期、只、窝、只/窝、%、比值、kg、kg/只、元、元/kg、元/只”等展示单位，配种日期的每日明细在组成项列写为 `{date}: {cycleCount} 个周期`。工作簿不含第三个隐藏页签、宏、外部链接、隐藏公式、附件内容或附件独有指标。响应使用 OOXML MIME 类型，UTF-8 文件名为 `批次-{清理后的batchCode}-统计-{UTC yyyyMMddHHmmss}.xlsx`，ASCII 回退为 `batch-{ASCII清理结果或batch-code}-statistics-{UTC yyyyMMddHHmmss}.xlsx`。文件名清理批次编号中的路径分隔符、控制字符和平台保留字符，工作表单元格保留原批次编号。

## 7. 权限与隔离

- 查看统计：`rabbit:batches:query`。
- 录入出肉率：`rabbit:batches:edit`。
- 查看完整出肉率历史：`rabbit:audit:list`。
- 导出 Excel：`rabbit:reports:export`。

前端隐藏无权限操作，后端仍需独立校验用户、兔舍、批次归属和权限。

## 8. 兼容过渡

`app.batch-statistics.legacy-write-enabled` 控制旧投喂、断奶、批量出库、单兔销售和转后备载荷，初始阶段开启。兼容开启时，旧操作可以在原业务事务中完成，但系统不伪造缺失快照。每个受影响批次在同一事务的 `repro_events` 中记录对应事件：`LEGACY_FEED_ALLOCATION_GAP`、`LEGACY_WEANING_WEIGHT_GAP`、`LEGACY_SALE_ALLOCATION_GAP`、`LEGACY_SALE_PRICE_GAP` 或 `LEGACY_REPLACEMENT_WEIGHT_GAP`。事件的 `target_type` 固定为 `BATCH`，`target_id` 和 `batch_id` 都指向受影响批次；事件类型标识来源接口和缺失快照类型。顶层字段保存 `house_id`、接口对应的 `request_id` 和 `occurred_at`，payload 只保存 `clientBuild`；缺少 `X-App-Build` 时写 `UNKNOWN`，不保存请求正文。父操作回滚时，缺口事件也必须回滚。

后端先以兼容开启状态部署，再发布 Admin 和 Flutter。新版 Flutter 通过现有 `force_update` 能力强制升级；生产 Android 真机完成投喂、断奶、混批出库、转后备和 Excel 分享验证后，从下一个数据库业务本地自然日 00:00:00 开始观察。关闭开关要求全部兔舍连续 7 个完整自然日没有上述缺口事件，查询窗口到第八个自然日 00:00:00 为止；任何事件都重新开始观察。关闭后，不完整旧载荷在事务开始前返回“当前版本过低，请升级应用后重试”，不得写入父记录、子记录或缺口事件。

新版客户端或强制更新异常时，重新开启开关并下架问题 Flutter 版本，旧写入恢复且缺失指标继续标为 `DATA_MISSING`。统计或 Excel 读路径异常时可以回滚应用代码。两种回退都保留追加数据库结构和已保存快照，不做逆向迁移。
