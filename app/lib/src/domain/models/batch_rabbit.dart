class BatchRabbitItem {
  const BatchRabbitItem({
    required this.id,
    required this.batchId,
    required this.rabbitId,
    required this.currentStatus,
    required this.nextEventType,
    this.maleRabbitId,
    this.latestCycleId,
    this.currentNursingKits = 0,
    this.nursingLitterCount = 0,
    this.joinReason = '',
    this.batchRole = '',
    this.lastEventDate,
    this.nextEventDate,
    this.isActive = true,
    this.joinDate,
    this.exitDate,
    this.rabbitType = '',
    this.rabbitGender = '',
    this.cageId,
  });

  final int id;
  final int batchId;
  final int rabbitId;
  final String currentStatus;
  final String nextEventType;
  final int? maleRabbitId;
  final int? latestCycleId;
  final int currentNursingKits;
  final int nursingLitterCount;
  final String joinReason;
  final String batchRole;
  final DateTime? lastEventDate;
  final DateTime? nextEventDate;
  final bool isActive;
  final DateTime? joinDate;
  final DateTime? exitDate;
  final String rabbitType;
  final String rabbitGender;
  final int? cageId;

  bool get isNursing => currentNursingKits > 0;

  static BatchRabbitItem fromJson(Map<String, dynamic> json) {
    return BatchRabbitItem(
      id: _intValue(json['id']),
      batchId: _intValue(json['batchId']),
      rabbitId: _intValue(json['rabbitId']),
      currentStatus: json['currentStatus'] as String? ?? '',
      nextEventType: json['nextEventType'] as String? ?? '',
      maleRabbitId: _nullableInt(json['maleRabbitId']),
      latestCycleId: _nullableInt(json['latestCycleId']),
      currentNursingKits: _intValue(json['currentNursingKits']),
      nursingLitterCount: _intValue(json['nursingLitterCount']),
      joinReason: json['joinReason'] as String? ?? '',
      batchRole: json['batchRole'] as String? ?? '',
      lastEventDate: _parseDate(json['lastEventDate']),
      nextEventDate: _parseDate(json['nextEventDate']),
      isActive: _boolValue(json['isActive'], fallback: true),
      joinDate: _parseDate(json['joinDate']),
      exitDate: _parseDate(json['exitDate']),
      rabbitType: json['rabbitType'] as String? ?? '',
      rabbitGender: json['rabbitGender'] as String? ?? '',
      cageId: _nullableInt(json['cageId']),
    );
  }

  static int? _nullableInt(Object? value) {
    final parsed = _intValue(value);
    return parsed <= 0 ? null : parsed;
  }

  static DateTime? _parseDate(Object? value) {
    if (value is String && value.isNotEmpty) {
      return DateTime.tryParse(value);
    }
    if (value is num) {
      return DateTime.fromMillisecondsSinceEpoch(value.toInt());
    }
    return null;
  }

  static bool _boolValue(Object? value, {required bool fallback}) {
    if (value is bool) {
      return value;
    }
    if (value is num) {
      return value != 0;
    }
    if (value is String) {
      if (value.toLowerCase() == 'true' || value == '1') {
        return true;
      }
      if (value.toLowerCase() == 'false' || value == '0') {
        return false;
      }
    }
    return fallback;
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
