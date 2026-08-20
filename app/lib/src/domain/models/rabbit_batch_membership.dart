class RabbitBatchMembership {
  const RabbitBatchMembership({
    required this.batchId,
    required this.rabbitId,
    required this.isActive,
    required this.batchRole,
    this.joinDate,
    this.exitDate,
    this.currentStage,
    this.currentCycleId,
    this.nextEventDate,
    this.nextEventType,
  });

  final int batchId;
  final int rabbitId;
  final bool isActive;
  final DateTime? joinDate;
  final DateTime? exitDate;
  final String? currentStage;
  final int? currentCycleId;
  final String batchRole;
  final DateTime? nextEventDate;
  final String? nextEventType;

  static RabbitBatchMembership fromJson(Map<String, dynamic> json) {
    return RabbitBatchMembership(
      batchId: _intValue(json['batchId']),
      rabbitId: _intValue(json['rabbitId']),
      isActive: _boolValue(json['isActive'], fallback: true),
      joinDate: _dateValue(json['joinDate']),
      exitDate: _dateValue(json['exitDate']),
      currentStage: _optionalString(json['currentStage']),
      currentCycleId: _nullableIntValue(json['currentCycleId']),
      batchRole: _optionalString(json['batchRole']) ?? '',
      nextEventDate: _dateValue(json['nextEventDate']),
      nextEventType: _optionalString(json['nextEventType']),
    );
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

  static int? _nullableIntValue(Object? value) {
    if (value == null) {
      return null;
    }
    final parsed = _intValue(value);
    return parsed > 0 ? parsed : null;
  }

  static bool _boolValue(Object? value, {required bool fallback}) {
    if (value is bool) {
      return value;
    }
    if (value is num) {
      return value != 0;
    }
    if (value is String) {
      switch (value.trim().toLowerCase()) {
        case 'true':
        case '1':
          return true;
        case 'false':
        case '0':
          return false;
      }
    }
    return fallback;
  }

  static DateTime? _dateValue(Object? value) {
    if (value is num) {
      return DateTime.fromMillisecondsSinceEpoch(value.toInt());
    }
    if (value is String && value.trim().isNotEmpty) {
      return DateTime.tryParse(value.trim());
    }
    return null;
  }

  static String? _optionalString(Object? value) {
    if (value is! String) {
      return null;
    }
    final normalized = value.trim();
    return normalized.isEmpty ? null : normalized;
  }
}
