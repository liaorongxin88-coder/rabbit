import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';

class OperationEvent {
  const OperationEvent({
    required this.id,
    required this.occurredAt,
    required this.operationCode,
    required this.eventType,
    required this.eventLabel,
    required this.targetType,
    required this.targetId,
    required this.cageId,
    required this.batchId,
    required this.rabbitId,
    required this.cycleId,
    required this.litterId,
    required this.fromStage,
    required this.toStage,
    required this.operatorId,
    required this.operatorName,
  });

  final int id;
  final DateTime? occurredAt;
  final String operationCode;
  final String eventType;
  final String eventLabel;
  final String? targetType;
  final int? targetId;
  final int? cageId;
  final int? batchId;
  final int? rabbitId;
  final int? cycleId;
  final int? litterId;
  final String? fromStage;
  final String? toStage;
  final int? operatorId;
  final String? operatorName;

  String get occurredAtLabel {
    final value = occurredAt;
    if (value == null) {
      return '操作时间未记录';
    }
    return DateFormat('yyyy-MM-dd HH:mm').format(farmLocalDateTime(value));
  }

  String get title {
    final label = eventLabel.trim();
    return label.isEmpty ? operationCode : label;
  }

  String get targetLabel {
    final parts = <String>[];
    if (targetType != null && targetType!.trim().isNotEmpty) {
      final id = targetId;
      parts.add(id == null ? targetType! : '$targetType #$id');
    }
    if (cageId != null) {
      parts.add('笼位 #$cageId');
    }
    if (batchId != null) {
      parts.add('批次 #$batchId');
    }
    if (rabbitId != null) {
      parts.add('兔 #$rabbitId');
    }
    if (cycleId != null) {
      parts.add('周期 #$cycleId');
    }
    if (litterId != null) {
      parts.add('窝次 #$litterId');
    }
    return parts.isEmpty ? '未关联业务对象' : parts.join(' · ');
  }

  String get operatorLabel {
    final name = operatorName?.trim() ?? '';
    if (name.isNotEmpty) {
      return name;
    }
    final id = operatorId;
    return id == null ? '操作人未记录' : '操作人 #$id';
  }

  factory OperationEvent.fromJson(Map<String, dynamic> json) {
    return OperationEvent(
      id: _requiredInt(json['id'], field: 'id'),
      occurredAt: _dateTime(json['occurredAt']),
      operationCode: _string(json['operationCode']),
      eventType: _string(json['eventType']),
      eventLabel: _string(json['eventLabel']),
      targetType: _nullableString(json['targetType']),
      targetId: _nullableInt(json['targetId']),
      cageId: _nullableInt(json['cageId']),
      batchId: _nullableInt(json['batchId']),
      rabbitId: _nullableInt(json['rabbitId']),
      cycleId: _nullableInt(json['cycleId']),
      litterId: _nullableInt(json['litterId']),
      fromStage: _nullableString(json['fromStage']),
      toStage: _nullableString(json['toStage']),
      operatorId: _nullableInt(json['operatorId']),
      operatorName: _nullableString(json['operatorName']),
    );
  }

  static DateTime? _dateTime(Object? value) {
    if (value is num) {
      return DateTime.fromMillisecondsSinceEpoch(value.toInt(), isUtc: true);
    }
    if (value is! String || value.trim().isEmpty) {
      return null;
    }
    return DateTime.tryParse(value);
  }

  static int _requiredInt(Object? value, {required String field}) {
    final result = _nullableInt(value);
    if (result == null) {
      throw FormatException('操作事件字段 $field 格式不正确');
    }
    return result;
  }

  static int? _nullableInt(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    if (value is String) {
      return int.tryParse(value);
    }
    return null;
  }

  static String _string(Object? value) => value is String ? value : '';

  static String? _nullableString(Object? value) {
    if (value is! String || value.trim().isEmpty) {
      return null;
    }
    return value;
  }
}

class OperationEventsPage {
  const OperationEventsPage({
    required this.items,
    required this.nextCursor,
    required this.hasMore,
  });

  final List<OperationEvent> items;
  final String? nextCursor;
  final bool hasMore;

  factory OperationEventsPage.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'];
    if (rawItems is! List) {
      throw const FormatException('操作事件列表格式不正确');
    }
    final items = <OperationEvent>[];
    for (final item in rawItems) {
      if (item is! Map) {
        throw const FormatException('操作事件列表格式不正确');
      }
      items.add(OperationEvent.fromJson(Map<String, dynamic>.from(item)));
    }
    final rawCursor = json['nextCursor'];
    if (rawCursor != null && rawCursor is! String) {
      throw const FormatException('操作事件游标格式不正确');
    }
    final rawHasMore = json['hasMore'];
    if (rawHasMore is! bool) {
      throw const FormatException('操作事件分页状态格式不正确');
    }
    return OperationEventsPage(
      items: List.unmodifiable(items),
      nextCursor: rawCursor,
      hasMore: rawHasMore,
    );
  }
}

class OperationEventsQuery {
  const OperationEventsQuery({
    this.targetType,
    this.targetId,
    this.operationCode,
    this.cageId,
    this.batchId,
    this.occurredFrom,
    this.occurredTo,
    this.cursor,
    this.limit = 50,
  });

  final String? targetType;
  final int? targetId;
  final String? operationCode;
  final int? cageId;
  final int? batchId;
  final DateTime? occurredFrom;
  final DateTime? occurredTo;
  final String? cursor;
  final int limit;

  void validate() {
    if (targetId != null && _text(targetType) == null) {
      throw ArgumentError('筛选目标 ID 时必须同时指定目标类型');
    }
    if (limit < 1 || limit > 200) {
      throw ArgumentError.value(limit, 'limit', '必须在 1 到 200 之间');
    }
  }

  Map<String, dynamic> toQueryParameters() {
    validate();
    return {
      if (_text(targetType) case final value?) 'targetType': value,
      if (targetId != null) 'targetId': targetId,
      if (_text(operationCode) case final value?) 'operationCode': value,
      if (cageId != null) 'cageId': cageId,
      if (batchId != null) 'batchId': batchId,
      if (occurredFrom != null)
        'occurredFrom': occurredFrom!.millisecondsSinceEpoch,
      if (occurredTo != null) 'occurredTo': occurredTo!.millisecondsSinceEpoch,
      if (_text(cursor) case final value?) 'cursor': value,
      'limit': limit,
    };
  }

  static String? _text(String? value) {
    final trimmed = value?.trim() ?? '';
    return trimmed.isEmpty ? null : trimmed;
  }
}
