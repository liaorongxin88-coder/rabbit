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
  final double? avgWeight;
  final String? remark;

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
      avgWeight: _doubleValue(json['avgWeight']),
      remark: json['remark'] as String?,
    );
  }
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
