class BatchRabbitItem {
  const BatchRabbitItem({
    required this.id,
    required this.batchId,
    required this.rabbitId,
    required this.currentStatus,
    required this.nextEventType,
    this.rabbitType = '',
    this.rabbitGender = '',
    this.cageId,
  });

  final int id;
  final int batchId;
  final int rabbitId;
  final String currentStatus;
  final String nextEventType;
  final String rabbitType;
  final String rabbitGender;
  final int? cageId;

  bool get isNursing => currentStatus == '哺乳中';

  static BatchRabbitItem fromJson(Map<String, dynamic> json) {
    return BatchRabbitItem(
      id: _intValue(json['id']),
      batchId: _intValue(json['batchId']),
      rabbitId: _intValue(json['rabbitId']),
      currentStatus: json['currentStatus'] as String? ?? '',
      nextEventType: json['nextEventType'] as String? ?? '',
      rabbitType: json['rabbitType'] as String? ?? '',
      rabbitGender: json['rabbitGender'] as String? ?? '',
      cageId: _nullableInt(json['cageId']),
    );
  }

  static int? _nullableInt(Object? value) {
    final parsed = _intValue(value);
    return parsed <= 0 ? null : parsed;
  }

  static int _intValue(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    if (value is String) {
      return int.tryParse(value) ?? 0;
    }
    return 0;
  }
}
