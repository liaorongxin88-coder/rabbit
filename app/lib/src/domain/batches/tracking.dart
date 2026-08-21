class BatchTrackingEvent {
  const BatchTrackingEvent({
    required this.id,
    required this.cycleId,
    required this.motherRabbitId,
    required this.batchId,
    required this.eventType,
    required this.eventLabel,
    required this.fromStageLabel,
    required this.toStageLabel,
    required this.occurredAt,
    required this.operatorName,
  });

  final int id;
  final int? cycleId;
  final int motherRabbitId;
  final int batchId;
  final String eventType;
  final String eventLabel;
  final String? fromStageLabel;
  final String? toStageLabel;
  final DateTime? occurredAt;
  final String operatorName;

  static BatchTrackingEvent fromJson(Map<String, dynamic> json) {
    return BatchTrackingEvent(
      id: _intValue(json['id']),
      cycleId: _nullableInt(json['cycleId']),
      motherRabbitId: _intValue(json['motherRabbitId']),
      batchId: _intValue(json['batchId']),
      eventType: _stringValue(json['eventType']),
      eventLabel: _stringValue(json['eventLabel'], fallback: '生产操作'),
      fromStageLabel: _optionalString(json['fromStageLabel']),
      toStageLabel: _optionalString(json['toStageLabel']),
      occurredAt: _dateValue(json['occurredAt']),
      operatorName: _stringValue(json['operatorName'], fallback: '操作人未记录'),
    );
  }
}

int _intValue(Object? value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value) ?? 0;
  return 0;
}

int? _nullableInt(Object? value) {
  final parsed = _intValue(value);
  return parsed > 0 ? parsed : null;
}

String _stringValue(Object? value, {String fallback = ''}) {
  final text = value?.toString().trim() ?? '';
  return text.isEmpty ? fallback : text;
}

String? _optionalString(Object? value) {
  final text = _stringValue(value);
  return text.isEmpty ? null : text;
}

DateTime? _dateValue(Object? value) {
  if (value is num) {
    return DateTime.fromMillisecondsSinceEpoch(value.toInt());
  }
  if (value is String && value.trim().isNotEmpty) {
    return DateTime.tryParse(value.trim());
  }
  return null;
}
