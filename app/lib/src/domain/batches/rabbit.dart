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

  /// 当前生产阶段（服务端枚举名）。权威现状，来自 rabbits 投影列；
  /// [currentStatus] 是旧写路径的中文快照，已不再更新，仅作降级显示。
  final String? currentStage;

  /// 当前进行中的周期 id，提交生产动作时需要。
  final int? currentCycleId;

  /// 界面上该显示的状态文字：优先用实时阶段，没有才回退到旧快照。
  String get displayStatus {
    final stage = ReproStage.tryParse(currentStage);
    if (stage != null) {
      return stage.label;
    }
    return currentStatus.isEmpty ? '未入轨' : currentStatus;
  }

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
      currentStage: json['currentStage'] as String?,
      currentCycleId: _nullableInt(json['currentCycleId']),
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
