enum OutboundEligibility { normal, earlySale, needsAction, blocked }

enum OutboundSelectionMode { cage, row, house }

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
      unitPrice: _double(json['unitPrice']),
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
