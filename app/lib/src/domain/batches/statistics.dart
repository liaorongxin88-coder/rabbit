class BatchMetricContract {
  const BatchMetricContract(
    this.code,
    this.order,
    this.stage,
    this.stageName,
    this.name,
    this.excelColumnName,
    this.valueType,
    this.unit,
    this.format,
  );

  final String code;
  final int order;
  final String stage;
  final String stageName;
  final String name;
  final String excelColumnName;
  final BatchMetricValueType valueType;
  final String unit;
  final String format;
}

const batchMetricContracts = <BatchMetricContract>[
  BatchMetricContract('MATING_DATE', 10, 'MATING', '配种', '配种日期', '配种日期',
      BatchMetricValueType.dateRange, 'DATE', 'DATE_RANGE'),
  BatchMetricContract('MATED_DOE_COUNT', 20, 'MATING', '配种', '配种母兔数', '配种母兔数',
      BatchMetricValueType.number, 'COUNT', 'INTEGER'),
  BatchMetricContract('CONCEPTION_RATE', 30, 'MATING', '配种', '受胎率', '受胎率',
      BatchMetricValueType.number, 'PERCENT', 'PERCENT_2'),
  BatchMetricContract('DOE_BUCK_RATIO', 40, 'MATING', '配种', '配种母兔/公兔比例',
      '配种母兔/公兔比例', BatchMetricValueType.number, 'RATIO', 'RATIO_TO_ONE'),
  BatchMetricContract('PREGNANT_DOE_COUNT', 50, 'PREGNANCY', '怀孕', '怀孕数量',
      '怀孕数量', BatchMetricValueType.number, 'COUNT', 'INTEGER'),
  BatchMetricContract('ABORTION_RATE', 60, 'PREGNANCY', '怀孕', '流产率', '流产率',
      BatchMetricValueType.number, 'PERCENT', 'PERCENT_2'),
  BatchMetricContract('DELIVERED_LITTER_COUNT', 70, 'BIRTH', '产崽', '产崽窝数',
      '产崽窝数', BatchMetricValueType.number, 'LITTER', 'INTEGER'),
  BatchMetricContract('TOTAL_KIT_COUNT', 80, 'BIRTH', '产崽', '产崽总数', '产崽总数',
      BatchMetricValueType.number, 'COUNT', 'INTEGER'),
  BatchMetricContract('AVERAGE_KITS_PER_LITTER', 90, 'BIRTH', '产崽', '平均窝产数',
      '平均窝产数', BatchMetricValueType.number, 'COUNT_PER_LITTER', 'DECIMAL_2'),
  BatchMetricContract('LIVE_KIT_COUNT', 100, 'BIRTH', '产崽', '活崽总数', '活崽总数',
      BatchMetricValueType.number, 'COUNT', 'INTEGER'),
  BatchMetricContract('LIVE_BIRTH_RATE', 110, 'BIRTH', '产崽', '平均活崽率', '平均活崽率',
      BatchMetricValueType.number, 'PERCENT', 'PERCENT_2'),
  BatchMetricContract('KEPT_LITTER_COUNT', 120, 'SELECTION', '选留', '选留窝数',
      '选留窝数', BatchMetricValueType.number, 'LITTER', 'INTEGER'),
  BatchMetricContract('KEPT_KIT_COUNT', 130, 'SELECTION', '选留', '选留总数', '选留总数',
      BatchMetricValueType.number, 'COUNT', 'INTEGER'),
  BatchMetricContract('KEPT_LIVE_RATE', 140, 'SELECTION', '选留', '选留活崽率',
      '选留活崽率', BatchMetricValueType.number, 'PERCENT', 'PERCENT_2'),
  BatchMetricContract('AVERAGE_KEPT_PER_LITTER', 150, 'SELECTION', '选留', '窝均选留',
      '窝均选留', BatchMetricValueType.number, 'COUNT_PER_LITTER', 'DECIMAL_2'),
  BatchMetricContract('WEANED_KIT_COUNT', 160, 'WEANING', '断奶', '断奶数量', '断奶数量',
      BatchMetricValueType.number, 'COUNT', 'INTEGER'),
  BatchMetricContract('AVERAGE_WEANING_WEIGHT', 170, 'WEANING', '断奶', '断奶均重',
      '断奶均重', BatchMetricValueType.number, 'KG_PER_RABBIT', 'DECIMAL_2'),
  BatchMetricContract('WEANING_SURVIVAL_RATE', 180, 'WEANING', '断奶', '断奶成活率',
      '断奶成活率', BatchMetricValueType.number, 'PERCENT', 'PERCENT_2'),
  BatchMetricContract('SOLD_RABBIT_COUNT', 190, 'OUTBOUND', '出栏', '出栏数量',
      '出栏数量', BatchMetricValueType.number, 'COUNT', 'INTEGER'),
  BatchMetricContract('OUTBOUND_SURVIVAL_RATE', 200, 'OUTBOUND', '出栏', '出栏成活率',
      '出栏成活率', BatchMetricValueType.number, 'PERCENT', 'PERCENT_2'),
  BatchMetricContract('SOLD_WEIGHT', 210, 'OUTBOUND', '出栏', '出栏总重', '出栏总重',
      BatchMetricValueType.number, 'KG', 'DECIMAL_2'),
  BatchMetricContract('AVERAGE_SOLD_WEIGHT', 220, 'OUTBOUND', '出栏', '出栏均重',
      '出栏均重', BatchMetricValueType.number, 'KG_PER_RABBIT', 'DECIMAL_2'),
  BatchMetricContract('TOTAL_SALES_AMOUNT', 230, 'SALES', '销售', '总销售金额',
      '总销售金额', BatchMetricValueType.number, 'CNY', 'DECIMAL_2'),
  BatchMetricContract('SALES_PRICE_PER_KG', 240, 'SALES', '销售', '销售单价（重量口径）',
      '销售单价（重量口径）', BatchMetricValueType.number, 'CNY_PER_KG', 'DECIMAL_2'),
  BatchMetricContract(
      'SALES_PRICE_PER_RABBIT',
      250,
      'SALES',
      '销售',
      '销售单价（只数口径）',
      '销售单价（只数口径）',
      BatchMetricValueType.number,
      'CNY_PER_RABBIT',
      'DECIMAL_2'),
  BatchMetricContract(
      'FULL_FEED_CONVERSION_RATIO',
      260,
      'FEED_CONVERSION',
      '料肉比',
      '全程料肉比',
      '全程料肉比',
      BatchMetricValueType.number,
      'RATIO',
      'DECIMAL_2'),
  BatchMetricContract(
      'FATTENING_FEED_CONVERSION_RATIO',
      270,
      'FEED_CONVERSION',
      '料肉比',
      '育肥期料肉比',
      '育肥期料肉比',
      BatchMetricValueType.number,
      'RATIO',
      'DECIMAL_2'),
  BatchMetricContract('CARCASS_YIELD_RATE', 280, 'FEED_CONVERSION', '料肉比',
      '出肉率', '出肉率', BatchMetricValueType.number, 'PERCENT', 'PERCENT_2'),
];

final batchMetricCodes = List<String>.unmodifiable(
  batchMetricContracts.map((contract) => contract.code),
);

final _batchMetricContractByCode = <String, BatchMetricContract>{
  for (final contract in batchMetricContracts) contract.code: contract,
};

const _batchMetricFormulaByCode = <String, String>{
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

const _missingCauseCodes = <String>[
  'MISSING_BATCH_ATTRIBUTION',
  'MISSING_NATURAL_MALE',
  'MISSING_PREGNANCY_EVIDENCE',
  'MISSING_WEANING_WEIGHT',
  'MISSING_BATCH_SALE_ALLOCATION',
  'MISSING_SALE_UNIT_PRICE',
  'MISSING_FEED_ALLOCATION',
  'MISSING_FEED_UNIT',
  'MISSING_REPLACEMENT_WEIGHT',
  'INVALID_FATTENING_GAIN',
  'MATING_NOT_RECORDED',
  'CARCASS_YIELD_NOT_RECORDED',
  'ZERO_DENOMINATOR',
];

const batchMetricLayout = <BatchMetricGroup>[
  BatchMetricGroup('MATING', '配种', [
    BatchMetricRow([
      ['MATING_DATE']
    ]),
    BatchMetricRow([
      ['MATED_DOE_COUNT'],
      ['CONCEPTION_RATE'],
    ]),
    BatchMetricRow([
      ['DOE_BUCK_RATIO']
    ]),
  ]),
  BatchMetricGroup('PREGNANCY', '怀孕', [
    BatchMetricRow([
      ['PREGNANT_DOE_COUNT'],
      ['ABORTION_RATE'],
    ]),
  ]),
  BatchMetricGroup('BIRTH', '产崽', [
    BatchMetricRow([
      ['DELIVERED_LITTER_COUNT'],
      ['TOTAL_KIT_COUNT'],
    ]),
    BatchMetricRow([
      ['AVERAGE_KITS_PER_LITTER']
    ]),
    BatchMetricRow([
      ['LIVE_KIT_COUNT'],
      ['LIVE_BIRTH_RATE'],
    ]),
  ]),
  BatchMetricGroup('SELECTION', '选留', [
    BatchMetricRow([
      ['KEPT_LITTER_COUNT'],
      ['KEPT_KIT_COUNT'],
    ]),
    BatchMetricRow([
      ['KEPT_LIVE_RATE'],
      ['AVERAGE_KEPT_PER_LITTER'],
    ]),
  ]),
  BatchMetricGroup('WEANING', '断奶', [
    BatchMetricRow([
      ['WEANED_KIT_COUNT']
    ]),
    BatchMetricRow([
      ['AVERAGE_WEANING_WEIGHT'],
      ['WEANING_SURVIVAL_RATE'],
    ]),
  ]),
  BatchMetricGroup('OUTBOUND', '出栏', [
    BatchMetricRow([
      ['SOLD_RABBIT_COUNT'],
      ['OUTBOUND_SURVIVAL_RATE'],
    ]),
    BatchMetricRow([
      ['SOLD_WEIGHT'],
      ['AVERAGE_SOLD_WEIGHT'],
    ]),
  ]),
  BatchMetricGroup('SALES', '销售', [
    BatchMetricRow([
      ['TOTAL_SALES_AMOUNT'],
      ['SALES_PRICE_PER_KG'],
      ['SALES_PRICE_PER_RABBIT'],
    ]),
  ]),
  BatchMetricGroup('FEED_CONVERSION', '料肉比', [
    BatchMetricRow([
      ['FULL_FEED_CONVERSION_RATIO'],
      ['FATTENING_FEED_CONVERSION_RATIO'],
    ]),
    BatchMetricRow([
      ['CARCASS_YIELD_RATE']
    ]),
  ]),
];

class BatchMetricGroup {
  const BatchMetricGroup(this.stage, this.name, this.rows);

  final String stage;
  final String name;
  final List<BatchMetricRow> rows;
}

class BatchMetricRow {
  const BatchMetricRow(this.slots);

  final List<List<String>> slots;
}

enum BatchMetricStatus {
  available,
  notApplicable,
  notRecorded,
  dataMissing;

  String get label => switch (this) {
        BatchMetricStatus.available => '数据可用',
        BatchMetricStatus.notApplicable => '暂无可计算数据',
        BatchMetricStatus.notRecorded => '未录入',
        BatchMetricStatus.dataMissing => '历史数据缺失',
      };

  static BatchMetricStatus parse(Object? value) => switch (value) {
        'AVAILABLE' => BatchMetricStatus.available,
        'NOT_APPLICABLE' => BatchMetricStatus.notApplicable,
        'NOT_RECORDED' => BatchMetricStatus.notRecorded,
        'DATA_MISSING' => BatchMetricStatus.dataMissing,
        _ => throw FormatException('批次统计包含无法识别的数据状态：$value'),
      };
}

enum BatchMetricValueType {
  number,
  dateRange;

  static BatchMetricValueType parse(Object? value) => switch (value) {
        'NUMBER' => BatchMetricValueType.number,
        'DATE_RANGE' => BatchMetricValueType.dateRange,
        _ => throw FormatException('批次统计包含无法识别的指标类型：$value'),
      };
}

class BatchStatistics {
  const BatchStatistics({
    required this.schemaVersion,
    required this.batchId,
    required this.houseName,
    required this.batchCode,
    required this.calculatedAt,
    required this.totalLitters,
    required this.totalKits,
    required this.totalLiveKits,
    required this.totalWeaned,
    required this.metrics,
  });

  final int schemaVersion;
  final int batchId;
  final String houseName;
  final String batchCode;
  final DateTime calculatedAt;
  final int totalLitters;
  final int totalKits;
  final int totalLiveKits;
  final int totalWeaned;
  final List<BatchStatisticMetric> metrics;

  Map<String, BatchStatisticMetric> get metricsByCode => {
        for (final metric in metrics) metric.code: metric,
      };

  factory BatchStatistics.fromJson(Map<String, dynamic> json) {
    final schemaVersion = _requiredInt(json['schemaVersion'], 'schemaVersion');
    if (schemaVersion != 1) {
      throw FormatException('暂不支持批次统计版本 $schemaVersion，请升级应用');
    }
    final rawMetrics = json['metrics'];
    if (rawMetrics is! List) {
      throw const FormatException('批次统计 metrics 格式不正确');
    }
    final metrics = <BatchStatisticMetric>[];
    final seen = <String>{};
    var previousFixedOrder = 0;
    for (final rawMetric in rawMetrics) {
      if (rawMetric is! Map) {
        throw const FormatException('批次统计指标格式不正确');
      }
      final metricJson = Map<String, dynamic>.from(rawMetric);
      final code = _requiredText(metricJson['code'], 'metric.code');
      final contract = _batchMetricContractByCode[code];
      if (contract == null) continue;
      final metric = BatchStatisticMetric.fromJson(metricJson);
      if (!seen.add(metric.code)) {
        throw FormatException('批次统计包含重复指标 ${metric.code}');
      }
      _validateMetricContract(metric, contract);
      if (metric.order <= previousFixedOrder) {
        throw const FormatException('批次统计固定指标未按版本 1 顺序返回');
      }
      previousFixedOrder = metric.order;
      metrics.add(metric);
    }
    final missing = batchMetricCodes.where((code) => !seen.contains(code));
    if (missing.isNotEmpty) {
      throw FormatException('批次统计缺少固定指标：${missing.join(', ')}');
    }
    return BatchStatistics(
      schemaVersion: schemaVersion,
      batchId: _positiveInt(json['batchId'], 'batchId'),
      houseName: _requiredText(json['houseName'], 'houseName'),
      batchCode: _requiredText(json['batchCode'], 'batchCode'),
      calculatedAt: _requiredDateTime(json['calculatedAt'], 'calculatedAt'),
      totalLitters: _nonNegativeInt(json['totalLitters'], 'totalLitters'),
      totalKits: _nonNegativeInt(json['totalKits'], 'totalKits'),
      totalLiveKits: _nonNegativeInt(json['totalLiveKits'], 'totalLiveKits'),
      totalWeaned: _nonNegativeInt(json['totalWeaned'], 'totalWeaned'),
      metrics: List.unmodifiable(metrics),
    );
  }
}

class BatchStatisticMetric {
  const BatchStatisticMetric({
    required this.code,
    required this.name,
    required this.stage,
    required this.stageName,
    required this.order,
    required this.excelColumnName,
    required this.valueType,
    required this.unit,
    required this.format,
    required this.formula,
    required this.status,
    required this.numericValue,
    required this.displayValue,
    required this.dateValue,
    required this.numerator,
    required this.denominator,
    required this.components,
    required this.missingCauses,
  });

  final String code;
  final String name;
  final String stage;
  final String stageName;
  final int order;
  final String excelColumnName;
  final BatchMetricValueType valueType;
  final String unit;
  final String format;
  final String formula;
  final BatchMetricStatus status;
  final double? numericValue;
  final String? displayValue;
  final BatchMetricDateValue? dateValue;
  final BatchMetricOperand? numerator;
  final BatchMetricOperand? denominator;
  final List<BatchMetricOperand> components;
  final List<BatchMetricMissingCause> missingCauses;

  String get visibleValue => status == BatchMetricStatus.available
      ? displayValue ?? '-'
      : status.label;

  factory BatchStatisticMetric.fromJson(Map<String, dynamic> json) {
    _requireKeys(
      json,
      const [
        'numericValue',
        'dateValue',
        'displayValue',
        'numerator',
        'denominator',
        'components',
        'missingCauses',
      ],
      'metric',
    );
    final formulaValue = json['formula'];
    if (formulaValue is! String) {
      throw const FormatException('批次统计 formula 格式不正确');
    }
    final valueType = BatchMetricValueType.parse(json['valueType']);
    final status = BatchMetricStatus.parse(json['status']);
    final components = _objectList(json['components'], 'components')
        .map(BatchMetricOperand.fromJson)
        .toList(growable: false);
    final missingCauses = _objectList(json['missingCauses'], 'missingCauses')
        .map(BatchMetricMissingCause.fromJson)
        .toList(growable: false);
    _validateMissingCauses(
      _requiredText(json['code'], 'metric.code'),
      status,
      missingCauses,
    );
    final rawDateValue = json['dateValue'];
    final dateValue = rawDateValue == null
        ? null
        : rawDateValue is Map
            ? BatchMetricDateValue.fromJson(
                Map<String, dynamic>.from(rawDateValue),
              )
            : throw const FormatException('批次统计 dateValue 格式不正确');
    final numericValue = _nullableDouble(json['numericValue'], 'numericValue');
    final displayValue = _optionalText(json['displayValue']);
    if (valueType == BatchMetricValueType.number && dateValue != null) {
      throw FormatException('${json['code']} 的数值指标包含日期范围');
    }
    if (valueType == BatchMetricValueType.dateRange && numericValue != null) {
      throw FormatException('${json['code']} 的日期指标包含数值');
    }
    if (status != BatchMetricStatus.available &&
        (numericValue != null || dateValue != null || displayValue != null)) {
      throw FormatException('${json['code']} 的非可用状态包含展示值');
    }
    if (status == BatchMetricStatus.available) {
      if (displayValue == null) {
        throw FormatException('${json['code']} 缺少服务端展示值');
      }
      if (valueType == BatchMetricValueType.number && numericValue == null) {
        throw FormatException('${json['code']} 缺少数值');
      }
      if (valueType == BatchMetricValueType.dateRange && dateValue == null) {
        throw FormatException('${json['code']} 缺少日期范围');
      }
    }
    return BatchStatisticMetric(
      code: _requiredText(json['code'], 'metric.code'),
      name: _requiredText(json['name'], 'metric.name'),
      stage: _requiredText(json['stage'], 'metric.stage'),
      stageName: _requiredText(json['stageName'], 'metric.stageName'),
      order: _requiredInt(json['order'], 'metric.order'),
      excelColumnName:
          _requiredText(json['excelColumnName'], 'metric.excelColumnName'),
      valueType: valueType,
      unit: _requiredText(json['unit'], 'metric.unit'),
      format: _requiredText(json['format'], 'metric.format'),
      formula: formulaValue,
      status: status,
      numericValue: numericValue,
      displayValue: displayValue,
      dateValue: dateValue,
      numerator: _nullableOperand(json['numerator'], 'numerator'),
      denominator: _nullableOperand(json['denominator'], 'denominator'),
      components: List.unmodifiable(components),
      missingCauses: List.unmodifiable(missingCauses),
    );
  }
}

class BatchMetricDateValue {
  const BatchMetricDateValue({
    required this.firstDate,
    required this.lastDate,
    required this.dateCount,
    required this.dailyCycleCounts,
  });

  final String firstDate;
  final String lastDate;
  final int dateCount;
  final List<BatchMetricDateCount> dailyCycleCounts;

  factory BatchMetricDateValue.fromJson(Map<String, dynamic> json) {
    _requireKeys(
      json,
      const ['firstDate', 'lastDate', 'dateCount', 'dailyCycleCounts'],
      'dateValue',
    );
    final firstDate = _requiredDate(json['firstDate'], 'firstDate');
    final lastDate = _requiredDate(json['lastDate'], 'lastDate');
    final dateCount = _positiveInt(json['dateCount'], 'dateCount');
    final dailyCycleCounts = _objectList(
      json['dailyCycleCounts'],
      'dailyCycleCounts',
    ).map(BatchMetricDateCount.fromJson).toList(growable: false);
    if (dailyCycleCounts.length != dateCount) {
      throw const FormatException('批次统计配种日期数量不一致');
    }
    for (var index = 1; index < dailyCycleCounts.length; index++) {
      if (dailyCycleCounts[index - 1]
              .date
              .compareTo(dailyCycleCounts[index].date) >=
          0) {
        throw const FormatException('批次统计每日配种日期必须升序且不重复');
      }
    }
    if (dailyCycleCounts.first.date != firstDate ||
        dailyCycleCounts.last.date != lastDate ||
        firstDate.compareTo(lastDate) > 0) {
      throw const FormatException('批次统计配种日期首尾不一致');
    }
    return BatchMetricDateValue(
      firstDate: firstDate,
      lastDate: lastDate,
      dateCount: dateCount,
      dailyCycleCounts: List.unmodifiable(dailyCycleCounts),
    );
  }
}

class BatchMetricDateCount {
  const BatchMetricDateCount({required this.date, required this.cycleCount});

  final String date;
  final int cycleCount;

  factory BatchMetricDateCount.fromJson(Map<String, dynamic> json) {
    _requireKeys(json, const ['date', 'cycleCount'], 'dailyCycleCount');
    return BatchMetricDateCount(
      date: _requiredDate(json['date'], 'date'),
      cycleCount: _positiveInt(json['cycleCount'], 'cycleCount'),
    );
  }
}

class BatchMetricOperand {
  const BatchMetricOperand({
    required this.code,
    required this.label,
    required this.value,
    required this.unit,
  });

  final String code;
  final String label;
  final double? value;
  final String unit;

  factory BatchMetricOperand.fromJson(Map<String, dynamic> json) {
    _requireKeys(json, const ['code', 'label', 'value', 'unit'], 'operand');
    return BatchMetricOperand(
      code: _requiredText(json['code'], 'operand.code'),
      label: _requiredText(json['label'], 'operand.label'),
      value: _nullableDouble(json['value'], 'operand.value'),
      unit: _requiredText(json['unit'], 'operand.unit'),
    );
  }
}

class BatchMetricMissingCause {
  const BatchMetricMissingCause({required this.code, required this.message});

  final String code;
  final String message;

  factory BatchMetricMissingCause.fromJson(Map<String, dynamic> json) {
    _requireKeys(json, const ['code', 'message'], 'missingCause');
    return BatchMetricMissingCause(
      code: _requiredText(json['code'], 'missingCause.code'),
      message: _requiredText(json['message'], 'missingCause.message'),
    );
  }
}

BatchMetricOperand? _nullableOperand(Object? value, String field) {
  if (value == null) return null;
  if (value is! Map) {
    throw FormatException('批次统计 $field 格式不正确');
  }
  return BatchMetricOperand.fromJson(Map<String, dynamic>.from(value));
}

List<Map<String, dynamic>> _objectList(Object? value, String field) {
  if (value is! List) {
    throw FormatException('批次统计 $field 格式不正确');
  }
  return value.map((item) {
    if (item is! Map) {
      throw FormatException('批次统计 $field 明细格式不正确');
    }
    return Map<String, dynamic>.from(item);
  }).toList(growable: false);
}

int _requiredInt(Object? value, String field) {
  if (value is int) return value;
  if (value is num && value.isFinite && value == value.roundToDouble()) {
    return value.toInt();
  }
  throw FormatException('批次统计字段 $field 格式不正确');
}

int _positiveInt(Object? value, String field) {
  final parsed = _requiredInt(value, field);
  if (parsed <= 0) throw FormatException('批次统计字段 $field 格式不正确');
  return parsed;
}

int _nonNegativeInt(Object? value, String field) {
  final parsed = _requiredInt(value, field);
  if (parsed < 0) throw FormatException('批次统计字段 $field 格式不正确');
  return parsed;
}

double? _nullableDouble(Object? value, String field) {
  if (value == null) return null;
  if (value is num && value.isFinite) return value.toDouble();
  throw FormatException('批次统计字段 $field 格式不正确');
}

String _requiredText(Object? value, String field) {
  if (value is String && value.trim().isNotEmpty) return value.trim();
  throw FormatException('批次统计字段 $field 格式不正确');
}

String? _optionalText(Object? value) {
  if (value == null) return null;
  if (value is! String) {
    throw const FormatException('批次统计展示值格式不正确');
  }
  final trimmed = value.trim();
  return trimmed.isEmpty ? null : trimmed;
}

DateTime _requiredDateTime(Object? value, String field) {
  final text = _requiredText(value, field);
  final isQualifiedUtc = RegExp(
    r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]00:00)$',
  ).hasMatch(text);
  final parsed = DateTime.tryParse(text);
  if (!isQualifiedUtc || parsed == null || !parsed.isUtc) {
    throw FormatException('批次统计字段 $field 格式不正确');
  }
  return parsed;
}

String _requiredDate(Object? value, String field) {
  final text = _requiredText(value, field);
  final match = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$').firstMatch(text);
  if (match == null) {
    throw FormatException('批次统计字段 $field 格式不正确');
  }
  final year = int.parse(match.group(1)!);
  final month = int.parse(match.group(2)!);
  final day = int.parse(match.group(3)!);
  final parsed = DateTime.utc(year, month, day);
  if (parsed.year != year || parsed.month != month || parsed.day != day) {
    throw FormatException('批次统计字段 $field 格式不正确');
  }
  return text;
}

void _requireKeys(
  Map<String, dynamic> json,
  List<String> keys,
  String objectName,
) {
  for (final key in keys) {
    if (!json.containsKey(key)) {
      throw FormatException('批次统计 $objectName 缺少字段 $key');
    }
  }
}

void _validateMissingCauses(
  String metricCode,
  BatchMetricStatus status,
  List<BatchMetricMissingCause> causes,
) {
  var previousOrder = -1;
  final seen = <String>{};
  for (final cause in causes) {
    final order = _missingCauseCodes.indexOf(cause.code);
    if (order < 0 || order <= previousOrder || !seen.add(cause.code)) {
      throw FormatException('批次统计指标 $metricCode 的缺失原因顺序不正确');
    }
    previousOrder = order;
  }
  switch (status) {
    case BatchMetricStatus.available:
      if (causes.isNotEmpty) {
        throw FormatException('批次统计指标 $metricCode 的可用状态包含缺失原因');
      }
    case BatchMetricStatus.notApplicable:
      if (causes.length != 1 || causes.single.code != 'ZERO_DENOMINATOR') {
        throw FormatException('批次统计指标 $metricCode 的不可计算原因不正确');
      }
    case BatchMetricStatus.notRecorded:
      final expected = metricCode == 'MATING_DATE'
          ? 'MATING_NOT_RECORDED'
          : metricCode == 'CARCASS_YIELD_RATE'
              ? 'CARCASS_YIELD_NOT_RECORDED'
              : null;
      if (expected == null ||
          causes.length != 1 ||
          causes.single.code != expected) {
        throw FormatException('批次统计指标 $metricCode 的未录入原因不正确');
      }
    case BatchMetricStatus.dataMissing:
      if (causes.isEmpty ||
          causes.any((cause) => _missingCauseCodes.indexOf(cause.code) > 9)) {
        throw FormatException('批次统计指标 $metricCode 的数据缺失原因不正确');
      }
  }
}

void _validateMetricContract(
  BatchStatisticMetric metric,
  BatchMetricContract contract,
) {
  final matches = metric.order == contract.order &&
      metric.stage == contract.stage &&
      metric.stageName == contract.stageName &&
      metric.name == contract.name &&
      metric.excelColumnName == contract.excelColumnName &&
      metric.valueType == contract.valueType &&
      metric.unit == contract.unit &&
      metric.format == contract.format &&
      metric.formula == _batchMetricFormulaByCode[metric.code];
  if (!matches) {
    throw FormatException('批次统计指标 ${metric.code} 的版本 1 元数据不一致');
  }
  final value = metric.numericValue;
  if (value == null) return;
  if (value < 0) {
    throw FormatException('批次统计指标 ${metric.code} 的数值不能小于 0');
  }
  if (contract.format == 'INTEGER' && value != value.truncateToDouble()) {
    throw FormatException('批次统计指标 ${metric.code} 的整数值格式不正确');
  }
  if (contract.format == 'PERCENT_2' && (value < 0 || value > 1)) {
    throw FormatException('批次统计指标 ${metric.code} 的百分比值格式不正确');
  }
}
