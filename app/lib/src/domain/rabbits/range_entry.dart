class RangeRabbitEntrySkippedCage {
  const RangeRabbitEntrySkippedCage({
    required this.cageId,
    required this.cageNumber,
    required this.reason,
  });

  final int cageId;
  final String cageNumber;
  final String reason;

  factory RangeRabbitEntrySkippedCage.fromJson(Map<String, dynamic> json) {
    return RangeRabbitEntrySkippedCage(
      cageId: _intValue(json['cageId']),
      cageNumber: json['cageNumber'] as String? ?? '',
      reason: json['reason'] as String? ?? '当前笼位不可入栏',
    );
  }
}

class RangeRabbitEntryResult {
  const RangeRabbitEntryResult({
    required this.requestedSlotCount,
    required this.missingCageCount,
    required this.unplacedCageCount,
    required this.enteredCageCount,
    required this.enteredRabbitCount,
    required this.replayedCageCount,
    required this.skippedCages,
  });

  final int requestedSlotCount;
  final int missingCageCount;
  final int unplacedCageCount;
  final int enteredCageCount;
  final int enteredRabbitCount;
  final int replayedCageCount;
  final List<RangeRabbitEntrySkippedCage> skippedCages;

  factory RangeRabbitEntryResult.fromJson(Map<String, dynamic> json) {
    final skipped = json['skippedCages'];
    return RangeRabbitEntryResult(
      requestedSlotCount: _intValue(json['requestedSlotCount']),
      missingCageCount: _intValue(json['missingCageCount']),
      unplacedCageCount: _intValue(json['unplacedCageCount']),
      enteredCageCount: _intValue(json['enteredCageCount']),
      enteredRabbitCount: _intValue(json['enteredRabbitCount']),
      replayedCageCount: _intValue(json['replayedCageCount']),
      skippedCages: skipped is List
          ? skipped
              .whereType<Map<String, dynamic>>()
              .map(RangeRabbitEntrySkippedCage.fromJson)
              .toList()
          : const [],
    );
  }
}

int _intValue(Object? value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value) ?? 0;
  return 0;
}
