class PendingWeaningRecord {
  const PendingWeaningRecord({
    required this.id,
    required this.batchId,
    required this.rabbitId,
    required this.weaningCount,
    required this.waitingCount,
    this.breedingCycleId,
    this.weaningDate,
    this.maleCount,
    this.femaleCount,
    this.waitingMaleCount,
    this.waitingFemaleCount,
    this.sireRabbitId,
    this.avgWeight,
    this.remark,
  });

  final int id;
  final int batchId;
  final int rabbitId;
  final int weaningCount;
  final int waitingCount;
  final int? breedingCycleId;
  final DateTime? weaningDate;
  final int? maleCount;
  final int? femaleCount;
  final int? waitingMaleCount;
  final int? waitingFemaleCount;
  final int? sireRabbitId;
  final double? avgWeight;
  final String? remark;

  bool get hasTrustworthyWaitingGenderCounts =>
      waitingMaleCount != null && waitingFemaleCount != null;

  factory PendingWeaningRecord.fromJson(Map<String, dynamic> json) {
    return PendingWeaningRecord(
      id: _intValue(json['id']),
      batchId: _intValue(json['batchId']),
      rabbitId: _intValue(json['rabbitId']),
      weaningCount: _intValue(json['weaningCount']),
      waitingCount: _intValue(json['waitingCount']),
      breedingCycleId: _nullableIntValue(json['breedingCycleId']),
      weaningDate: _dateValue(json['weaningDate']),
      maleCount: _nullableIntValue(json['maleCount']),
      femaleCount: _nullableIntValue(json['femaleCount']),
      waitingMaleCount: _nullableIntValue(json['waitingMaleCount']),
      waitingFemaleCount: _nullableIntValue(json['waitingFemaleCount']),
      sireRabbitId: _nullableIntValue(
        json['sireRabbitId'] ?? json['maleRabbitId'] ?? json['fatherRabbitId'],
      ),
      avgWeight: _doubleValue(json['avgWeight']),
      remark: json['remark'] as String?,
    );
  }
}

class CageAllocation {
  const CageAllocation({
    required this.cageId,
    required this.count,
    this.maleCount,
    this.femaleCount,
  });

  final int cageId;
  final int count;
  final int? maleCount;
  final int? femaleCount;

  bool get hasGenderCounts => maleCount != null && femaleCount != null;

  Map<String, Object> toJson() {
    return <String, Object>{
      'cageId': cageId,
      'count': count,
      if (maleCount != null) 'maleCount': maleCount!,
      if (femaleCount != null) 'femaleCount': femaleCount!,
    };
  }

  String? validate({
    required int waitingCount,
    int? waitingMaleCount,
    int? waitingFemaleCount,
  }) {
    if (cageId <= 0) {
      return '请选择商品兔笼位';
    }
    if (count <= 0 || count > waitingCount) {
      return '分笼数量需在 1 到 $waitingCount 之间';
    }
    if ((maleCount == null) != (femaleCount == null)) {
      return '公兔和母兔数量必须同时填写';
    }
    if (!hasGenderCounts) {
      return null;
    }
    if (maleCount! < 0 || femaleCount! < 0) {
      return '公兔和母兔数量不能小于 0';
    }
    if (maleCount! + femaleCount! != count) {
      return '公兔和母兔数量之和必须等于本次分笼数量';
    }
    if (waitingMaleCount != null && maleCount! > waitingMaleCount) {
      return '公兔数量不能超过剩余 $waitingMaleCount 只';
    }
    if (waitingFemaleCount != null && femaleCount! > waitingFemaleCount) {
      return '母兔数量不能超过剩余 $waitingFemaleCount 只';
    }
    return null;
  }
}

class WeaningSeparationResult {
  const WeaningSeparationResult({
    required this.weaningRecordId,
    required this.separatedCount,
    required this.waitingCount,
    required this.generatedRabbitIds,
    required this.replayed,
  });

  final int weaningRecordId;
  final int separatedCount;
  final int waitingCount;
  final List<int> generatedRabbitIds;
  final bool replayed;

  factory WeaningSeparationResult.fromJson(Map<String, dynamic> json) {
    return WeaningSeparationResult(
      weaningRecordId: _intValue(json['weaningRecordId']),
      separatedCount: _intValue(json['separatedCount']),
      waitingCount: _intValue(json['waitingCount']),
      generatedRabbitIds:
          (json['generatedRabbitIds'] as List<dynamic>? ?? const <dynamic>[])
              .map(_nullableIntValue)
              .whereType<int>()
              .toList(growable: false),
      replayed: _boolValue(json['replayed']),
    );
  }
}

List<PendingWeaningRecord> pendingProductionRecords(
  Iterable<PendingWeaningRecord> records,
) {
  final result = records.where((record) => record.waitingCount > 0).toList();
  result.sort((left, right) => right.id.compareTo(left.id));
  return result;
}

int _intValue(Object? value) => _nullableIntValue(value) ?? 0;

int? _nullableIntValue(Object? value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value);
  return null;
}

double? _doubleValue(Object? value) {
  if (value is num) return value.toDouble();
  if (value is String) return double.tryParse(value);
  return null;
}

DateTime? _dateValue(Object? value) {
  if (value is String) return DateTime.tryParse(value);
  if (value is num) return DateTime.fromMillisecondsSinceEpoch(value.toInt());
  return null;
}

bool _boolValue(Object? value) {
  if (value is bool) return value;
  if (value is num) return value != 0;
  if (value is String) return value == 'true' || value == '1';
  return false;
}
