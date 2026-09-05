enum OutboundEligibility { normal, earlySale, needsAction, blocked }

enum OutboundSelectionMode { cage, row, house }

class OutboundBatchAllocation {
  const OutboundBatchAllocation({
    required this.batchId,
    required this.actualWeightKg,
  });

  final int? batchId;
  final double actualWeightKg;

  String get key => batchId?.toString() ?? 'unassigned';

  Map<String, Object?> toJson() => {
        'batchId': batchId,
        'actualWeightKg': actualWeightKg,
      };

  factory OutboundBatchAllocation.fromJson(Map<String, dynamic> json) {
    final rawBatchId = json['batchId'];
    final batchId = rawBatchId == null ? null : _nullableInt(rawBatchId);
    final actualWeightKg = _double(json['actualWeightKg']);
    if ((rawBatchId != null && batchId == null) ||
        actualWeightKg == null ||
        !actualWeightKg.isFinite ||
        actualWeightKg <= 0) {
      throw const FormatException('出库草稿批次重量格式不正确');
    }
    return OutboundBatchAllocation(
      batchId: batchId,
      actualWeightKg: actualWeightKg,
    );
  }
}

class OutboundAllocationGroup {
  const OutboundAllocationGroup({
    required this.batchId,
    required this.rabbitCount,
  });

  final int? batchId;
  final int rabbitCount;

  String get key => batchId?.toString() ?? 'unassigned';
  String get label => batchId == null ? '未归属批次' : '批次 #$batchId';
}

List<OutboundAllocationGroup> buildOutboundAllocationGroups(
  List<OutboundSelectedItem> selectedItems,
  List<OutboundRabbit> rabbits,
) {
  final rabbitById = {for (final rabbit in rabbits) rabbit.rabbitId: rabbit};
  final counts = <int?, int>{};
  for (final item in selectedItems) {
    final rabbit = rabbitById[item.rabbitId];
    if (rabbit != null) {
      counts.update(rabbit.batchId, (value) => value + 1, ifAbsent: () => 1);
    }
  }
  final groups = counts.entries
      .map((entry) => OutboundAllocationGroup(
            batchId: entry.key,
            rabbitCount: entry.value,
          ))
      .toList();
  groups.sort((left, right) {
    if (left.batchId == null) return 1;
    if (right.batchId == null) return -1;
    return left.batchId!.compareTo(right.batchId!);
  });
  return groups;
}

String? validateOutboundAllocations({
  required double? totalWeight,
  required double? unitPricePerKg,
  required List<OutboundBatchAllocation> allocations,
}) {
  if (totalWeight == null || !totalWeight.isFinite || totalWeight <= 0) {
    return '请输入大于 0 的总重量';
  }
  if (totalWeight > 100000 || !_atMostDecimals(totalWeight, 3)) {
    return '总重量不能超过 100000 kg，且最多保留三位小数';
  }
  if (unitPricePerKg == null ||
      !unitPricePerKg.isFinite ||
      unitPricePerKg <= 0) {
    return '请输入大于 0 的统一重量单价';
  }
  if (unitPricePerKg > 99999999.99 || !_atMostDecimals(unitPricePerKg, 2)) {
    return '统一重量单价最多保留两位小数';
  }
  if (allocations.isEmpty ||
      allocations.any(
        (item) => !item.actualWeightKg.isFinite || item.actualWeightKg <= 0,
      )) {
    return '请填写每个批次分组的实际重量';
  }
  if (allocations.any((item) => !_atMostDecimals(item.actualWeightKg, 3))) {
    return '批次分组重量最多保留三位小数';
  }
  final allocated = allocations.fold<int>(
    0,
    (sum, item) => sum + (item.actualWeightKg * 1000).round(),
  );
  return allocated == (totalWeight * 1000).round() ? null : '批次分组重量合计必须等于订单总重量';
}

class OutboundSummary {
  const OutboundSummary({
    required this.normal,
    required this.earlySale,
    required this.needsAction,
    required this.blocked,
  });

  final int normal;
  final int earlySale;
  final int needsAction;
  final int blocked;

  factory OutboundSummary.fromJson(Map<String, dynamic> json) {
    return OutboundSummary(
      normal: _int(json['normal']),
      earlySale: _int(json['earlySale']),
      needsAction: _int(json['needsAction']),
      blocked: _int(json['blocked']),
    );
  }

  Map<String, dynamic> toJson() => {
        'normal': normal,
        'earlySale': earlySale,
        'needsAction': needsAction,
        'blocked': blocked,
      };
}

class OutboundRabbit {
  const OutboundRabbit({
    required this.rabbitId,
    required this.cageId,
    required this.cageNumber,
    required this.rowCode,
    required this.layerIndex,
    required this.positionIndex,
    required this.rabbitType,
    required this.gender,
    required this.weight,
    required this.stage,
    required this.batchId,
    required this.stateVersion,
    required this.eligibility,
    required this.reasonCode,
    required this.message,
    required this.recommendedAction,
    required this.defaultSelected,
  });

  final int rabbitId;
  final int cageId;
  final String cageNumber;
  final String rowCode;
  final int? layerIndex;
  final int? positionIndex;
  final String rabbitType;
  final String gender;
  final double? weight;
  final String stage;
  final int? batchId;
  final int stateVersion;
  final OutboundEligibility eligibility;
  final String reasonCode;
  final String message;
  final String recommendedAction;
  final bool defaultSelected;

  bool get isNormal => eligibility == OutboundEligibility.normal;
  bool get canEarlySell => eligibility == OutboundEligibility.earlySale;

  factory OutboundRabbit.fromJson(Map<String, dynamic> json) {
    return OutboundRabbit(
      rabbitId: _int(json['rabbitId']),
      cageId: _int(json['cageId']),
      cageNumber: json['cageNumber'] as String? ?? '',
      rowCode: json['rowCode'] as String? ?? 'LEGACY',
      layerIndex: _nullableInt(json['layerIndex']),
      positionIndex: _nullableInt(json['positionIndex']),
      rabbitType: json['rabbitType'] as String? ?? '',
      gender: json['gender'] as String? ?? '',
      weight: _double(json['weight']),
      stage: json['stage'] as String? ?? '',
      batchId: _nullableInt(json['batchId']),
      stateVersion: _int(json['stateVersion']),
      eligibility: _eligibility(json['eligibility'] as String?),
      reasonCode: json['reasonCode'] as String? ?? '',
      message: json['message'] as String? ?? '',
      recommendedAction: json['recommendedAction'] as String? ?? '',
      defaultSelected: _bool(json['defaultSelected']),
    );
  }

  Map<String, dynamic> toJson() => {
        'rabbitId': rabbitId,
        'cageId': cageId,
        'cageNumber': cageNumber,
        'rowCode': rowCode,
        'layerIndex': layerIndex,
        'positionIndex': positionIndex,
        'rabbitType': rabbitType,
        'gender': gender,
        'weight': weight,
        'stage': stage,
        'batchId': batchId,
        'stateVersion': stateVersion,
        'eligibility': eligibility.name,
        'reasonCode': reasonCode,
        'message': message,
        'recommendedAction': recommendedAction,
        'defaultSelected': defaultSelected,
      };
}

class OutboundSelectedItem {
  const OutboundSelectedItem({
    required this.rabbitId,
    required this.stateVersion,
    required this.selectionType,
    this.earlySaleReason,
  });

  final int rabbitId;
  final int stateVersion;
  final String selectionType;
  final String? earlySaleReason;

  bool get isEarlySale => selectionType == 'EARLY_SALE';

  factory OutboundSelectedItem.fromJson(Map<String, dynamic> json) {
    return OutboundSelectedItem(
      rabbitId: _int(json['rabbitId']),
      stateVersion: _int(json['stateVersion']),
      selectionType: json['selectionType'] as String? ?? 'NORMAL',
      earlySaleReason: json['earlySaleReason'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'rabbitId': rabbitId,
        'stateVersion': stateVersion,
        'selectionType': selectionType,
        if (earlySaleReason != null) 'earlySaleReason': earlySaleReason,
      };
}

class OutboundTask {
  const OutboundTask({
    required this.taskId,
    required this.houseId,
    required this.entryType,
    this.sourceRabbitId,
    this.sourceCageId,
    this.sourceRowCode,
    required this.status,
    required this.revision,
    this.saleTime,
    this.totalWeight,
    this.unitPrice,
    this.customer,
    this.remark,
    this.saleOrderId,
    required this.resumed,
    required this.summary,
    required this.rabbits,
    required this.selectedItems,
    this.batchAllocations = const [],
  });

  final String taskId;
  final int houseId;
  final String entryType;
  final int? sourceRabbitId;
  final int? sourceCageId;
  final String? sourceRowCode;
  final String status;
  final int revision;
  final DateTime? saleTime;
  final double? totalWeight;
  final double? unitPrice;
  final String? customer;
  final String? remark;
  final int? saleOrderId;
  final bool resumed;
  final OutboundSummary summary;
  final List<OutboundRabbit> rabbits;
  final List<OutboundSelectedItem> selectedItems;
  final List<OutboundBatchAllocation> batchAllocations;

  OutboundTask copyWith({
    String? status,
    int? revision,
    DateTime? saleTime,
    double? totalWeight,
    double? unitPrice,
    String? customer,
    String? remark,
    bool? resumed,
    OutboundSummary? summary,
    List<OutboundRabbit>? rabbits,
    List<OutboundSelectedItem>? selectedItems,
    List<OutboundBatchAllocation>? batchAllocations,
  }) {
    return OutboundTask(
      taskId: taskId,
      houseId: houseId,
      entryType: entryType,
      sourceRabbitId: sourceRabbitId,
      sourceCageId: sourceCageId,
      sourceRowCode: sourceRowCode,
      status: status ?? this.status,
      revision: revision ?? this.revision,
      saleTime: saleTime ?? this.saleTime,
      totalWeight: totalWeight ?? this.totalWeight,
      unitPrice: unitPrice ?? this.unitPrice,
      customer: customer ?? this.customer,
      remark: remark ?? this.remark,
      saleOrderId: saleOrderId,
      resumed: resumed ?? this.resumed,
      summary: summary ?? this.summary,
      rabbits: rabbits ?? this.rabbits,
      selectedItems: selectedItems ?? this.selectedItems,
      batchAllocations: batchAllocations ?? this.batchAllocations,
    );
  }

  factory OutboundTask.fromJson(Map<String, dynamic> json) {
    return OutboundTask(
      taskId: json['taskId'] as String? ?? '',
      houseId: _int(json['houseId']),
      entryType: json['entryType'] as String? ?? 'HOUSE',
      sourceRabbitId: _nullableInt(json['sourceRabbitId']),
      sourceCageId: _nullableInt(json['sourceCageId']),
      sourceRowCode: json['sourceRowCode'] as String?,
      status: json['status'] as String? ?? 'SELECTING',
      revision: _int(json['revision']),
      saleTime: _date(json['saleTime']),
      totalWeight: _double(json['totalWeight']),
      unitPrice: _double(json['unitPricePerKg'] ?? json['unitPrice']),
      customer: json['customer'] as String?,
      remark: json['remark'] as String?,
      saleOrderId: _nullableInt(json['saleOrderId']),
      resumed: _bool(json['resumed']),
      summary: OutboundSummary.fromJson(
        Map<String, dynamic>.from(json['summary'] as Map? ?? const {}),
      ),
      rabbits: _mapList(json['rabbits'], OutboundRabbit.fromJson),
      selectedItems:
          _mapList(json['selectedItems'], OutboundSelectedItem.fromJson),
      batchAllocations: _mapList(
        json['batchAllocations'],
        OutboundBatchAllocation.fromJson,
      ),
    );
  }

  Map<String, dynamic> toJson() => {
        'taskId': taskId,
        'houseId': houseId,
        'entryType': entryType,
        'sourceRabbitId': sourceRabbitId,
        'sourceCageId': sourceCageId,
        'sourceRowCode': sourceRowCode,
        'status': status,
        'revision': revision,
        'saleTime': saleTime?.toIso8601String(),
        'totalWeight': totalWeight,
        'unitPrice': unitPrice,
        'customer': customer,
        'remark': remark,
        'saleOrderId': saleOrderId,
        'resumed': resumed,
        'summary': summary.toJson(),
        'rabbits': rabbits.map((item) => item.toJson()).toList(),
        'selectedItems': selectedItems.map((item) => item.toJson()).toList(),
        'batchAllocations':
            batchAllocations.map((item) => item.toJson()).toList(),
      };
}

class OutboundConflict {
  const OutboundConflict({
    required this.rabbitId,
    required this.errorCode,
    required this.currentState,
    required this.message,
    required this.recommendedAction,
  });

  final int rabbitId;
  final String errorCode;
  final String currentState;
  final String message;
  final String recommendedAction;

  factory OutboundConflict.fromJson(Map<String, dynamic> json) {
    return OutboundConflict(
      rabbitId: _int(json['rabbitId']),
      errorCode: json['errorCode'] as String? ?? '',
      currentState: json['currentState'] as String? ?? '',
      message: json['message'] as String? ?? '',
      recommendedAction: json['recommendedAction'] as String? ?? '',
    );
  }
}

class OutboundSubmitResult {
  const OutboundSubmitResult({
    required this.status,
    required this.requestId,
    required this.taskId,
    this.saleOrderId,
    this.saleOrderNumber,
    this.saleTime,
    required this.rabbitCount,
    required this.cageCount,
    required this.rowCount,
    this.totalWeight,
    this.totalAmount,
    this.errorCode,
    required this.message,
    required this.conflicts,
  });

  final String status;
  final String requestId;
  final String taskId;
  final int? saleOrderId;
  final String? saleOrderNumber;
  final DateTime? saleTime;
  final int rabbitCount;
  final int cageCount;
  final int rowCount;
  final double? totalWeight;
  final double? totalAmount;
  final String? errorCode;
  final String message;
  final List<OutboundConflict> conflicts;

  bool get isCompleted => status == 'COMPLETED';
  bool get isConflict => status == 'CONFLICT';
  bool get isFailed => status == 'FAILED';

  factory OutboundSubmitResult.fromJson(Map<String, dynamic> json) {
    return OutboundSubmitResult(
      status: json['status'] as String? ?? 'PROCESSING',
      requestId: json['requestId'] as String? ?? '',
      taskId: json['taskId'] as String? ?? '',
      saleOrderId: _nullableInt(json['saleOrderId']),
      saleOrderNumber: json['saleOrderNumber'] as String?,
      saleTime: _date(json['saleTime']),
      rabbitCount: _int(json['rabbitCount']),
      cageCount: _int(json['cageCount']),
      rowCount: _int(json['rowCount']),
      totalWeight: _double(json['totalWeight']),
      totalAmount: _double(json['totalAmount']),
      errorCode: json['errorCode'] as String?,
      message: json['message'] as String? ?? '',
      conflicts: _mapList(json['conflicts'], OutboundConflict.fromJson),
    );
  }
}

List<T> _mapList<T>(Object? value, T Function(Map<String, dynamic>) decode) {
  if (value is! List) return const [];
  return value
      .whereType<Map>()
      .map((item) => decode(Map<String, dynamic>.from(item)))
      .toList();
}

OutboundEligibility _eligibility(String? value) {
  switch (value) {
    case 'NORMAL':
    case 'normal':
      return OutboundEligibility.normal;
    case 'EARLY_SALE':
    case 'earlySale':
      return OutboundEligibility.earlySale;
    case 'NEEDS_ACTION':
    case 'needsAction':
      return OutboundEligibility.needsAction;
    default:
      return OutboundEligibility.blocked;
  }
}

int _int(Object? value) => value is num
    ? value.toInt()
    : value is String
        ? int.tryParse(value) ?? 0
        : 0;

int? _nullableInt(Object? value) {
  if (value == null) return null;
  final parsed = _int(value);
  return parsed <= 0 ? null : parsed;
}

double? _double(Object? value) => value is num
    ? value.toDouble()
    : value is String
        ? double.tryParse(value)
        : null;

bool _bool(Object? value) => value is bool
    ? value
    : value is num
        ? value != 0
        : value?.toString().toLowerCase() == 'true';

DateTime? _date(Object? value) {
  if (value is num) return DateTime.fromMillisecondsSinceEpoch(value.toInt());
  if (value is String && value.isNotEmpty) return DateTime.tryParse(value);
  return null;
}

bool _atMostDecimals(double value, int places) {
  final scale = places == 3 ? 1000 : 100;
  return ((value * scale).round() - value * scale).abs() < 0.000001;
}
