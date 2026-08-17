import 'package:intl/intl.dart';

class EventItem {
  const EventItem({
    required this.recordId,
    required this.category,
    required this.eventType,
    required this.eventDate,
    required this.batchId,
    required this.rabbitId,
    required this.status,
    this.sourceHouseId,
    this.sourceHouseName = '',
  });

  final int recordId;
  final String category;
  final String eventType;
  final DateTime? eventDate;
  final int? batchId;
  final int? rabbitId;
  final String status;
  final int? sourceHouseId;
  final String sourceHouseName;

  bool get isProduction => category == '生产' || category == '生产周期';
  bool get isBreedingCycle => category == '生产周期';
  bool get isReplacement => category == '后备成熟';
  bool get isTreatment => category == '治疗复查';

  String get operationalTargetLabel {
    final id = rabbitId;
    if (id == null || id <= 0) {
      return '对象待确认';
    }
    if (isProduction &&
        !eventType.contains('出售') &&
        !eventType.contains('后备')) {
      return '母兔 #$id';
    }
    return '兔 #$id';
  }

  String? get batchLabel {
    final id = batchId;
    return id == null || id <= 0 ? null : '批次 #$id';
  }

  String? get cycleRecordLabel => isBreedingCycle ? '周期记录 #$recordId' : null;

  bool get isOverdue {
    if (status.toLowerCase() == 'overdue') {
      return true;
    }
    final date = eventDate;
    if (date == null) {
      return false;
    }
    final today = DateTime.now();
    return DateTime(date.year, date.month, date.day).isBefore(
      DateTime(today.year, today.month, today.day),
    );
  }

  bool get isDue {
    if (status.toLowerCase() == 'due') {
      return true;
    }
    final date = eventDate;
    if (date == null) {
      return false;
    }
    final today = DateTime.now();
    return date.year == today.year &&
        date.month == today.month &&
        date.day == today.day;
  }

  String get statusLabel {
    switch (status.toLowerCase()) {
      case 'overdue':
        return '逾期';
      case 'due':
        return '到期';
      case 'upcoming':
        return '未到期';
      default:
        if (isOverdue) {
          return '逾期';
        }
        if (isDue) {
          return '到期';
        }
        return status.isEmpty ? '待处理' : status;
    }
  }

  String get dateLabel {
    final date = eventDate;
    if (date == null) {
      return '日期未设置';
    }
    return DateFormat('MM月dd日').format(date);
  }

  String get targetLabel {
    final parts = <String>[];
    if (batchId != null && batchId! > 0) {
      parts.add('批次#$batchId');
    }
    if (rabbitId != null && rabbitId! > 0) {
      parts.add('兔#$rabbitId');
    }
    return parts.isEmpty ? '对象待确认' : parts.join(' · ');
  }

  String get houseLabel {
    final name = sourceHouseName.trim();
    if (name.isNotEmpty) {
      return name;
    }
    if (sourceHouseId != null && sourceHouseId! > 0) {
      return '兔舍#$sourceHouseId';
    }
    return '全部兔舍';
  }

  EventItem copyWith({
    int? sourceHouseId,
    String? sourceHouseName,
  }) {
    return EventItem(
      recordId: recordId,
      category: category,
      eventType: eventType,
      eventDate: eventDate,
      batchId: batchId,
      rabbitId: rabbitId,
      status: status,
      sourceHouseId: sourceHouseId ?? this.sourceHouseId,
      sourceHouseName: sourceHouseName ?? this.sourceHouseName,
    );
  }

  static EventItem fromJson(Map<String, dynamic> json) {
    return EventItem(
      recordId: _intValue(json['recordId']),
      category: json['category'] as String? ?? '',
      eventType: json['eventType'] as String? ?? '',
      eventDate: _parseDate(json['eventDate']),
      batchId: _nullableInt(json['batchId']),
      rabbitId: _nullableInt(json['rabbitId']),
      status: json['status'] as String? ?? '',
      sourceHouseId: _nullableInt(json['sourceHouseId'] ?? json['houseId']),
      sourceHouseName:
          (json['sourceHouseName'] ?? json['houseName']) as String? ?? '',
    );
  }

  static DateTime? _parseDate(Object? value) {
    if (value is! String || value.isEmpty) {
      return null;
    }
    return DateTime.tryParse(value);
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
