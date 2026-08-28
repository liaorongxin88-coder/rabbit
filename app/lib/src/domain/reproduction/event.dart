import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';

class EventItem {
  const EventItem({
    required this.recordId,
    required this.category,
    required this.eventType,
    required this.eventDate,
    required this.batchId,
    required this.rabbitId,
    required this.status,
    this.batchCode = '',
    this.sourceHouseId,
    this.sourceHouseName = '',
    this.content = '',
  });

  final int recordId;
  final String category;
  final String eventType;
  final DateTime? eventDate;
  final int? batchId;

  /// 批次编号，即批次列表和批次详情里用的那个名字。服务端查不到批次时为空。
  final String batchCode;
  final int? rabbitId;
  final String status;
  final int? sourceHouseId;
  final String sourceHouseName;
  final String content;

  bool get isProduction => category == '生产' || category == '生产周期';
  bool get isBreedingCycle => category == '生产周期';
  bool get isReplacement => category == '后备成熟';
  bool get isTreatment => category == '治疗复查';
  bool get isCommodityCare =>
      eventType.startsWith('COMMODITY_') ||
      eventType.contains('幼兔适应') ||
      eventType.contains('生长饲喂') ||
      eventType.contains('育肥饲喂');

  String get operationalTargetLabel {
    final id = rabbitId;
    if (id == null || id <= 0) {
      return '对象待确认';
    }
    if (isProduction &&
        !eventType.contains('出售') &&
        !eventType.contains('后备') &&
        !isCommodityCare) {
      return '母兔 #$id';
    }
    return '兔 #$id';
  }

  /// 提醒卡片上的批次名。没批次就返回 null，调用方据此整个不显示这个字段。
  ///
  /// 只能拿到 batchId 时不再回落成「批次 #12」：那个内部主键在批次列表里从不出现，
  /// 操作者对不上号，反而会把它读成旁边那个周期记录号。
  String? get batchLabel {
    final code = batchCode.trim();
    return code.isEmpty ? null : '批次 $code';
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
    return localDateOnly(date).isBefore(farmToday());
  }

  bool get isDue {
    if (status.toLowerCase() == 'due') {
      return true;
    }
    final date = eventDate;
    if (date == null) {
      return false;
    }
    return localDateOnly(date) == farmToday();
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

  /// 提醒日期。
  ///
  /// 先换算到兔场所在时区再格式化：部分接口回的是 UTC 时刻，直接读 month/day
  /// 会把晚上 8 点以后的到期日提前一天，同一个待办在不同页面就会显示两个日期。
  String get dateLabel {
    final date = eventDate;
    if (date == null) {
      return '日期未设置';
    }
    return DateFormat('MM月dd日').format(farmLocalDateTime(date));
  }

  String get targetLabel {
    final parts = <String>[];
    final code = batchCode.trim();
    if (code.isNotEmpty) {
      parts.add('批次 $code');
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
      batchCode: batchCode,
      rabbitId: rabbitId,
      status: status,
      sourceHouseId: sourceHouseId ?? this.sourceHouseId,
      sourceHouseName: sourceHouseName ?? this.sourceHouseName,
      content: content,
    );
  }

  static EventItem fromJson(Map<String, dynamic> json) {
    return EventItem(
      recordId: _intValue(json['recordId']),
      category: json['category'] as String? ?? '',
      eventType: json['eventType'] as String? ?? '',
      eventDate: _parseDate(json['eventDate']),
      batchId: _nullableInt(json['batchId']),
      batchCode: json['batchCode'] as String? ?? '',
      rabbitId: _nullableInt(json['rabbitId']),
      status: json['status'] as String? ?? '',
      sourceHouseId: _nullableInt(json['sourceHouseId'] ?? json['houseId']),
      sourceHouseName:
          (json['sourceHouseName'] ?? json['houseName']) as String? ?? '',
      content: json['content'] as String? ?? '',
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
