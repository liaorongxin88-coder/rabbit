class ReplacementBatchAllocation {
  const ReplacementBatchAllocation({
    required this.batchId,
    required this.rabbitCount,
    required this.totalWeightKg,
  });

  final int? batchId;
  final int rabbitCount;
  final double totalWeightKg;

  String? validate() {
    if (rabbitCount <= 0) return '转换兔只数必须大于 0';
    if (!totalWeightKg.isFinite || totalWeightKg <= 0) {
      return '请填写大于 0 的转换实测总重';
    }
    if (((totalWeightKg * 1000).round() - totalWeightKg * 1000).abs() >=
        0.000001) {
      return '转换实测总重最多保留三位小数';
    }
    return null;
  }

  Map<String, Object?> toJson() => {
        'batchId': batchId,
        'rabbitCount': rabbitCount,
        'totalWeightKg': totalWeightKg,
      };
}

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
