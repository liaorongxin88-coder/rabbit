import 'package:rabbit_flutter/src/domain/reproduction/task.dart';

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
    this.currentStage,
    this.currentCycleId,
    this.batchCycleCount = 0,
    this.batchOperationCount = 0,
    this.batchLitterCount = 0,
    this.batchTotalKits = 0,
    this.batchLiveKits = 0,
    this.batchWeanedKits = 0,
    this.batchLastOperationAt,
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

  /// 该批次标签下当前最先要处理的开放周期阶段。
  final String? currentStage;

  /// 与 [currentStage] 对应、且属于当前批次标签的开放周期 id。
  final int? currentCycleId;

  final int batchCycleCount;
  final int batchOperationCount;
  final int batchLitterCount;
  final int batchTotalKits;
  final int batchLiveKits;
  final int batchWeanedKits;
  final DateTime? batchLastOperationAt;

  /// 界面上该显示的状态文字：优先用实时阶段，没有才回退到旧快照。
  String get displayStatus {
    final stage = ReproStage.tryParse(currentStage);
    if (stage != null) {
      return stage.label;
    }
    if (batchRole == 'breeding' && batchCycleCount > 0) {
      return '周期已结束';
    }
    return currentStatus.isEmpty ? '未入轨' : currentStatus;
  }

  bool get isNursing => currentNursingKits > 0;

  /// 繁殖标签的活动性由该批次是否仍有开放周期决定；商品兔沿用在栏标签状态。
  bool get isActivityActive => batchRole == 'breeding'
      ? isActive && (currentStage?.trim().isNotEmpty ?? false)
      : isActive;

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
      currentStage: json['currentStage'] as String?,
      currentCycleId: _nullableInt(json['currentCycleId']),
      batchCycleCount: _intValue(json['batchCycleCount']),
      batchOperationCount: _intValue(json['batchOperationCount']),
      batchLitterCount: _intValue(json['batchLitterCount']),
      batchTotalKits: _intValue(json['batchTotalKits']),
      batchLiveKits: _intValue(json['batchLiveKits']),
      batchWeanedKits: _intValue(json['batchWeanedKits']),
      batchLastOperationAt: _parseDate(json['batchLastOperationAt']),
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
