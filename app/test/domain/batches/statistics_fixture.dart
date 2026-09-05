class TestBatchMetricContract {
  const TestBatchMetricContract({
    required this.code,
    required this.order,
    required this.stage,
    required this.stageName,
    required this.name,
    required this.excelColumnName,
    required this.valueType,
    required this.unit,
    required this.format,
  });

  final String code;
  final int order;
  final String stage;
  final String stageName;
  final String name;
  final String excelColumnName;
  final String valueType;
  final String unit;
  final String format;
}

const testBatchMetricContracts = <TestBatchMetricContract>[
  TestBatchMetricContract(
    code: 'MATING_DATE',
    order: 10,
    stage: 'MATING',
    stageName: '配种',
    name: '配种日期',
    excelColumnName: '配种日期',
    valueType: 'DATE_RANGE',
    unit: 'DATE',
    format: 'DATE_RANGE',
  ),
  TestBatchMetricContract(
    code: 'MATED_DOE_COUNT',
    order: 20,
    stage: 'MATING',
    stageName: '配种',
    name: '配种母兔数',
    excelColumnName: '配种母兔数',
    valueType: 'NUMBER',
    unit: 'COUNT',
    format: 'INTEGER',
  ),
  TestBatchMetricContract(
    code: 'CONCEPTION_RATE',
    order: 30,
    stage: 'MATING',
    stageName: '配种',
    name: '受胎率',
    excelColumnName: '受胎率',
    valueType: 'NUMBER',
    unit: 'PERCENT',
    format: 'PERCENT_2',
  ),
  TestBatchMetricContract(
    code: 'DOE_BUCK_RATIO',
    order: 40,
    stage: 'MATING',
    stageName: '配种',
    name: '配种母兔/公兔比例',
    excelColumnName: '配种母兔/公兔比例',
    valueType: 'NUMBER',
    unit: 'RATIO',
    format: 'RATIO_TO_ONE',
  ),
  TestBatchMetricContract(
    code: 'PREGNANT_DOE_COUNT',
    order: 50,
    stage: 'PREGNANCY',
    stageName: '怀孕',
    name: '怀孕数量',
    excelColumnName: '怀孕数量',
    valueType: 'NUMBER',
    unit: 'COUNT',
    format: 'INTEGER',
  ),
  TestBatchMetricContract(
    code: 'ABORTION_RATE',
    order: 60,
    stage: 'PREGNANCY',
    stageName: '怀孕',
    name: '流产率',
    excelColumnName: '流产率',
    valueType: 'NUMBER',
    unit: 'PERCENT',
    format: 'PERCENT_2',
  ),
  TestBatchMetricContract(
    code: 'DELIVERED_LITTER_COUNT',
    order: 70,
    stage: 'BIRTH',
    stageName: '产崽',
    name: '产崽窝数',
    excelColumnName: '产崽窝数',
    valueType: 'NUMBER',
    unit: 'LITTER',
    format: 'INTEGER',
  ),
  TestBatchMetricContract(
    code: 'TOTAL_KIT_COUNT',
    order: 80,
    stage: 'BIRTH',
    stageName: '产崽',
    name: '产崽总数',
    excelColumnName: '产崽总数',
    valueType: 'NUMBER',
    unit: 'COUNT',
    format: 'INTEGER',
  ),
  TestBatchMetricContract(
    code: 'AVERAGE_KITS_PER_LITTER',
    order: 90,
    stage: 'BIRTH',
    stageName: '产崽',
    name: '平均窝产数',
    excelColumnName: '平均窝产数',
    valueType: 'NUMBER',
    unit: 'COUNT_PER_LITTER',
    format: 'DECIMAL_2',
  ),
  TestBatchMetricContract(
    code: 'LIVE_KIT_COUNT',
    order: 100,
    stage: 'BIRTH',
    stageName: '产崽',
    name: '活崽总数',
    excelColumnName: '活崽总数',
    valueType: 'NUMBER',
    unit: 'COUNT',
    format: 'INTEGER',
  ),
  TestBatchMetricContract(
    code: 'LIVE_BIRTH_RATE',
    order: 110,
    stage: 'BIRTH',
    stageName: '产崽',
    name: '平均活崽率',
    excelColumnName: '平均活崽率',
    valueType: 'NUMBER',
    unit: 'PERCENT',
    format: 'PERCENT_2',
  ),
  TestBatchMetricContract(
    code: 'KEPT_LITTER_COUNT',
    order: 120,
    stage: 'SELECTION',
    stageName: '选留',
    name: '选留窝数',
    excelColumnName: '选留窝数',
    valueType: 'NUMBER',
    unit: 'LITTER',
    format: 'INTEGER',
  ),
  TestBatchMetricContract(
    code: 'KEPT_KIT_COUNT',
    order: 130,
    stage: 'SELECTION',
    stageName: '选留',
    name: '选留总数',
    excelColumnName: '选留总数',
    valueType: 'NUMBER',
    unit: 'COUNT',
    format: 'INTEGER',
  ),
  TestBatchMetricContract(
    code: 'KEPT_LIVE_RATE',
    order: 140,
    stage: 'SELECTION',
    stageName: '选留',
    name: '选留活崽率',
    excelColumnName: '选留活崽率',
    valueType: 'NUMBER',
    unit: 'PERCENT',
    format: 'PERCENT_2',
  ),
  TestBatchMetricContract(
    code: 'AVERAGE_KEPT_PER_LITTER',
    order: 150,
    stage: 'SELECTION',
    stageName: '选留',
    name: '窝均选留',
    excelColumnName: '窝均选留',
    valueType: 'NUMBER',
    unit: 'COUNT_PER_LITTER',
    format: 'DECIMAL_2',
  ),
  TestBatchMetricContract(
    code: 'WEANED_KIT_COUNT',
    order: 160,
    stage: 'WEANING',
    stageName: '断奶',
    name: '断奶数量',
    excelColumnName: '断奶数量',
    valueType: 'NUMBER',
    unit: 'COUNT',
    format: 'INTEGER',
  ),
  TestBatchMetricContract(
    code: 'AVERAGE_WEANING_WEIGHT',
    order: 170,
    stage: 'WEANING',
    stageName: '断奶',
    name: '断奶均重',
    excelColumnName: '断奶均重',
    valueType: 'NUMBER',
    unit: 'KG_PER_RABBIT',
    format: 'DECIMAL_2',
  ),
  TestBatchMetricContract(
    code: 'WEANING_SURVIVAL_RATE',
    order: 180,
    stage: 'WEANING',
    stageName: '断奶',
    name: '断奶成活率',
    excelColumnName: '断奶成活率',
    valueType: 'NUMBER',
    unit: 'PERCENT',
    format: 'PERCENT_2',
  ),
  TestBatchMetricContract(
    code: 'SOLD_RABBIT_COUNT',
    order: 190,
    stage: 'OUTBOUND',
    stageName: '出栏',
    name: '出栏数量',
    excelColumnName: '出栏数量',
    valueType: 'NUMBER',
    unit: 'COUNT',
    format: 'INTEGER',
  ),
  TestBatchMetricContract(
    code: 'OUTBOUND_SURVIVAL_RATE',
    order: 200,
    stage: 'OUTBOUND',
    stageName: '出栏',
    name: '出栏成活率',
    excelColumnName: '出栏成活率',
    valueType: 'NUMBER',
    unit: 'PERCENT',
    format: 'PERCENT_2',
  ),
  TestBatchMetricContract(
    code: 'SOLD_WEIGHT',
    order: 210,
    stage: 'OUTBOUND',
    stageName: '出栏',
    name: '出栏总重',
    excelColumnName: '出栏总重',
    valueType: 'NUMBER',
    unit: 'KG',
    format: 'DECIMAL_2',
  ),
  TestBatchMetricContract(
    code: 'AVERAGE_SOLD_WEIGHT',
    order: 220,
    stage: 'OUTBOUND',
    stageName: '出栏',
    name: '出栏均重',
    excelColumnName: '出栏均重',
    valueType: 'NUMBER',
    unit: 'KG_PER_RABBIT',
    format: 'DECIMAL_2',
  ),
  TestBatchMetricContract(
    code: 'TOTAL_SALES_AMOUNT',
    order: 230,
    stage: 'SALES',
    stageName: '销售',
    name: '总销售金额',
    excelColumnName: '总销售金额',
    valueType: 'NUMBER',
    unit: 'CNY',
    format: 'DECIMAL_2',
  ),
  TestBatchMetricContract(
    code: 'SALES_PRICE_PER_KG',
    order: 240,
    stage: 'SALES',
    stageName: '销售',
    name: '销售单价（重量口径）',
    excelColumnName: '销售单价（重量口径）',
    valueType: 'NUMBER',
    unit: 'CNY_PER_KG',
    format: 'DECIMAL_2',
  ),
  TestBatchMetricContract(
    code: 'SALES_PRICE_PER_RABBIT',
    order: 250,
    stage: 'SALES',
    stageName: '销售',
    name: '销售单价（只数口径）',
    excelColumnName: '销售单价（只数口径）',
    valueType: 'NUMBER',
    unit: 'CNY_PER_RABBIT',
    format: 'DECIMAL_2',
  ),
  TestBatchMetricContract(
    code: 'FULL_FEED_CONVERSION_RATIO',
    order: 260,
    stage: 'FEED_CONVERSION',
    stageName: '料肉比',
    name: '全程料肉比',
    excelColumnName: '全程料肉比',
    valueType: 'NUMBER',
    unit: 'RATIO',
    format: 'DECIMAL_2',
  ),
  TestBatchMetricContract(
    code: 'FATTENING_FEED_CONVERSION_RATIO',
    order: 270,
    stage: 'FEED_CONVERSION',
    stageName: '料肉比',
    name: '育肥期料肉比',
    excelColumnName: '育肥期料肉比',
    valueType: 'NUMBER',
    unit: 'RATIO',
    format: 'DECIMAL_2',
  ),
  TestBatchMetricContract(
    code: 'CARCASS_YIELD_RATE',
    order: 280,
    stage: 'FEED_CONVERSION',
    stageName: '料肉比',
    name: '出肉率',
    excelColumnName: '出肉率',
    valueType: 'NUMBER',
    unit: 'PERCENT',
    format: 'PERCENT_2',
  ),
];

const testBatchMetricFormulas = <String, String>{
  'MATING_DATE': '配种日期按业务自然日去重',
  'MATED_DOE_COUNT': '已配种周期中的去重母兔数',
  'CONCEPTION_RATE': '确认怀孕周期数 / 已配种周期数',
  'DOE_BUCK_RATIO': '去重配种母兔数 / 去重参与配种公兔数',
  'PREGNANT_DOE_COUNT': '确认怀孕周期中的去重母兔数',
  'ABORTION_RATE': '已怀孕流产周期数 / 确认怀孕周期数',
  'DELIVERED_LITTER_COUNT': '批次内产崽窝数',
  'TOTAL_KIT_COUNT': '批次内产崽数之和',
  'AVERAGE_KITS_PER_LITTER': '产崽总数 / 产崽窝数',
  'LIVE_KIT_COUNT': '批次内活崽数之和',
  'LIVE_BIRTH_RATE': '活崽总数 / 产崽总数',
  'KEPT_LITTER_COUNT': '留崽数大于零的窝数',
  'KEPT_KIT_COUNT': '批次内选留数之和',
  'KEPT_LIVE_RATE': '选留总数 / 活崽总数',
  'AVERAGE_KEPT_PER_LITTER': '选留总数 / 选留窝数',
  'WEANED_KIT_COUNT': '批次内断奶数之和',
  'AVERAGE_WEANING_WEIGHT': '断奶总重快照之和 / 断奶数量',
  'WEANING_SURVIVAL_RATE': '断奶数量 / 选留总数',
  'SOLD_RABBIT_COUNT': '批次快照匹配的已销售兔只数',
  'OUTBOUND_SURVIVAL_RATE': '出栏数量 / 断奶数量',
  'SOLD_WEIGHT': '批次销售实际重量之和',
  'AVERAGE_SOLD_WEIGHT': '出栏总重 / 出栏数量',
  'TOTAL_SALES_AMOUNT': '批次销售金额快照之和',
  'SALES_PRICE_PER_KG': '总销售金额 / 出栏总重',
  'SALES_PRICE_PER_RABBIT': '总销售金额 / 出栏数量',
  'FULL_FEED_CONVERSION_RATIO': '批次全程饲料量 /（商品兔实际销售重量 + 转后备兔实测总重）',
  'FATTENING_FEED_CONVERSION_RATIO': '批次育肥饲料量 /（商品兔实际销售重量 + 转后备兔实测总重 - 断奶总重）',
  'CARCASS_YIELD_RATE': '最新出肉率版本',
};

List<String> get testBatchMetricCodes =>
    testBatchMetricContracts.map((contract) => contract.code).toList();

Map<String, Object?> testStatisticsPayload({
  bool missingSoldWeight = false,
}) {
  return {
    'schemaVersion': 1,
    'batchId': 11,
    'houseName': '一号兔舍',
    'batchCode': 'STATS-11',
    'calculatedAt': '2026-09-05T08:30:00Z',
    'totalLitters': 3,
    'totalKits': 28,
    'totalLiveKits': 26,
    'totalWeaned': 22,
    'metrics': [
      for (final contract in testBatchMetricContracts)
        testMetricPayload(
          contract,
          missing: missingSoldWeight && contract.code == 'SOLD_WEIGHT',
        ),
    ],
  };
}

Map<String, Object?> testMetricPayload(
  TestBatchMetricContract contract, {
  bool missing = false,
}) {
  final isDate = contract.valueType == 'DATE_RANGE';
  final isConception = contract.code == 'CONCEPTION_RATE';
  return {
    'code': contract.code,
    'name': contract.name,
    'stage': contract.stage,
    'stageName': contract.stageName,
    'order': contract.order,
    'excelColumnName': contract.excelColumnName,
    'valueType': contract.valueType,
    'unit': contract.unit,
    'format': contract.format,
    'formula': testBatchMetricFormulas[contract.code],
    'status': missing ? 'DATA_MISSING' : 'AVAILABLE',
    'numericValue': isDate || missing
        ? null
        : isConception
            ? 0.8609756097560975
            : contract.format == 'PERCENT_2'
                ? 0.5
                : contract.order,
    'displayValue': missing
        ? null
        : isDate
            ? '2024-04-22 至 2024-04-23（2个配种日）'
            : isConception
                ? '86.10%'
                : '${contract.order}',
    'dateValue': isDate
        ? {
            'firstDate': '2024-04-22',
            'lastDate': '2024-04-23',
            'dateCount': 2,
            'dailyCycleCounts': [
              {'date': '2024-04-22', 'cycleCount': 2},
              {'date': '2024-04-23', 'cycleCount': 3},
            ],
          }
        : null,
    'numerator': isConception
        ? {
            'code': 'PREGNANT_CYCLES',
            'label': '确认怀孕周期数',
            'value': 1059,
            'unit': 'COUNT',
          }
        : null,
    'denominator': isConception
        ? {
            'code': 'MATED_CYCLES',
            'label': '已配种周期数',
            'value': 1230,
            'unit': 'COUNT',
          }
        : null,
    'components': isConception
        ? [
            {
              'code': 'PREGNANT_CYCLES',
              'label': '确认怀孕周期数',
              'value': 1059,
              'unit': 'COUNT',
            },
          ]
        : <Map<String, Object?>>[],
    'missingCauses': missing
        ? [
            {
              'code': 'MISSING_BATCH_SALE_ALLOCATION',
              'message': '历史销售缺少批次重量快照',
            },
          ]
        : <Map<String, Object?>>[],
  };
}
