class BatchRabbitEntryResult {
  const BatchRabbitEntryResult({
    required this.requestedRabbitCount,
    required this.enteredRabbitCount,
    required this.replayedRabbitCount,
    required this.skippedCages,
  });

  final int requestedRabbitCount;
  final int enteredRabbitCount;
  final int replayedRabbitCount;
  final List<BatchRabbitEntrySkippedCage> skippedCages;

  bool get hasPartialResult =>
      enteredRabbitCount > 0 && skippedCages.isNotEmpty;

  static BatchRabbitEntryResult fromJson(Map<String, dynamic> json) {
    return BatchRabbitEntryResult(
      requestedRabbitCount: _intValue(json['requestedRabbitCount']),
      enteredRabbitCount: _intValue(json['enteredRabbitCount']),
      replayedRabbitCount: _intValue(json['replayedRabbitCount']),
      skippedCages: (json['skippedCages'] as List? ?? const [])
          .whereType<Map>()
          .map(
            (item) => BatchRabbitEntrySkippedCage.fromJson(
              Map<String, dynamic>.from(item),
            ),
          )
          .toList(growable: false),
    );
  }
}

class BatchRabbitEntrySkippedCage {
  const BatchRabbitEntrySkippedCage({
    required this.cageId,
    required this.cageNumber,
    required this.rabbitCount,
    required this.reason,
  });

  final int cageId;
  final String cageNumber;
  final int rabbitCount;
  final String reason;

  String get cageLabel => cageNumber.isEmpty ? '#$cageId' : cageNumber;

  static BatchRabbitEntrySkippedCage fromJson(Map<String, dynamic> json) {
    return BatchRabbitEntrySkippedCage(
      cageId: _intValue(json['cageId']),
      cageNumber: json['cageNumber'] as String? ?? '',
      rabbitCount: _intValue(json['rabbitCount']),
      reason: json['reason'] as String? ?? '未录入',
    );
  }
}

int _intValue(Object? value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value) ?? 0;
  return 0;
}
