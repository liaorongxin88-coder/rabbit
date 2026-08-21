class ReplacementConversion {
  const ReplacementConversion({
    required this.rabbitId,
    required this.replacementRecordId,
    required this.targetCageId,
  });

  final int rabbitId;
  final int replacementRecordId;
  final int targetCageId;

  factory ReplacementConversion.fromJson(Map<String, dynamic> json) =>
      ReplacementConversion(
        rabbitId: _int(json['rabbitId']),
        replacementRecordId: _int(json['replacementRecordId']),
        targetCageId: _int(json['targetCageId']),
      );
}

int _int(Object? value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  return int.tryParse(value?.toString() ?? '') ?? 0;
}
