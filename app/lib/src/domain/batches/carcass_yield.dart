class BatchCarcassYieldDraft {
  const BatchCarcassYieldDraft({
    required this.yieldRate,
    required this.sourceUnit,
    required this.measuredDate,
    required this.changeReason,
    required this.requestId,
    this.reportNumber,
    this.evidenceFileId,
    this.remark,
  });

  final double yieldRate;
  final String sourceUnit;
  final DateTime measuredDate;
  final String changeReason;
  final String requestId;
  final String? reportNumber;
  final String? evidenceFileId;
  final String? remark;

  String? validate() {
    if (!yieldRate.isFinite || yieldRate <= 0 || yieldRate > 1) {
      return '出肉率必须大于 0% 且不超过 100%';
    }
    if (sourceUnit.trim().isEmpty) return '请填写来源单位';
    if (sourceUnit.trim().length > 100) return '来源单位不能超过 100 个字符';
    if (changeReason.trim().isEmpty) return '请填写修改说明';
    if (changeReason.trim().length > 300) return '修改说明不能超过 300 个字符';
    if ((reportNumber?.trim().length ?? 0) > 100) {
      return '报告编号不能超过 100 个字符';
    }
    if ((remark?.trim().length ?? 0) > 2000) return '备注不能超过 2000 个字符';
    return null;
  }

  Map<String, Object> toJson() => {
        'yieldRate': yieldRate,
        'sourceUnit': sourceUnit.trim(),
        'measuredDate': _date(measuredDate),
        if (reportNumber?.trim().isNotEmpty == true)
          'reportNumber': reportNumber!.trim(),
        if (evidenceFileId?.trim().isNotEmpty == true)
          'evidenceFileId': evidenceFileId!.trim(),
        if (remark?.trim().isNotEmpty == true) 'remark': remark!.trim(),
        'changeReason': changeReason.trim(),
        'requestId': requestId,
      };
}

class BatchCarcassYieldRecord {
  const BatchCarcassYieldRecord({
    required this.id,
    required this.houseId,
    required this.batchId,
    required this.yieldRate,
    required this.sourceUnit,
    required this.measuredDate,
    required this.changeReason,
    required this.requestId,
    required this.createdBy,
    required this.createdAt,
    this.reportNumber,
    this.evidenceFileId,
    this.remark,
    this.createdByName,
  });

  final int id;
  final int houseId;
  final int batchId;
  final double yieldRate;
  final String sourceUnit;
  final DateTime measuredDate;
  final String changeReason;
  final String requestId;
  final int createdBy;
  final DateTime createdAt;
  final String? reportNumber;
  final String? evidenceFileId;
  final String? remark;
  final String? createdByName;

  factory BatchCarcassYieldRecord.fromJson(Map<String, dynamic> json) {
    return BatchCarcassYieldRecord(
      id: _int(json['id'], 'id'),
      houseId: _int(json['houseId'], 'houseId'),
      batchId: _int(json['batchId'], 'batchId'),
      yieldRate: _number(json['yieldRate'], 'yieldRate'),
      sourceUnit: _text(json['sourceUnit'], 'sourceUnit'),
      measuredDate: _dateTime(json['measuredDate'], 'measuredDate'),
      changeReason: _text(json['changeReason'], 'changeReason'),
      requestId: _text(json['requestId'], 'requestId'),
      createdBy: _int(json['createdBy'], 'createdBy'),
      createdAt: _dateTime(json['createdAt'], 'createdAt'),
      reportNumber: _optional(json['reportNumber']),
      evidenceFileId: _optional(json['evidenceFileId']),
      remark: _optional(json['remark']),
      createdByName: _optional(json['createdByName']),
    );
  }
}

class BatchCarcassYieldPage {
  const BatchCarcassYieldPage({
    required this.items,
    required this.total,
    required this.page,
    required this.pageSize,
  });

  final List<BatchCarcassYieldRecord> items;
  final int total;
  final int page;
  final int pageSize;

  factory BatchCarcassYieldPage.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'];
    if (rawItems is! List) throw const FormatException('出肉率历史格式不正确');
    return BatchCarcassYieldPage(
      items: rawItems.map((item) {
        if (item is! Map) throw const FormatException('出肉率历史格式不正确');
        return BatchCarcassYieldRecord.fromJson(
            Map<String, dynamic>.from(item));
      }).toList(growable: false),
      total: _int(json['total'], 'total'),
      page: _int(json['page'], 'page'),
      pageSize: _int(json['pageSize'], 'pageSize'),
    );
  }
}

String _date(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}';

int _int(Object? value, String field) {
  if (value is int) return value;
  if (value is num && value == value.roundToDouble()) return value.toInt();
  throw FormatException('出肉率字段 $field 格式不正确');
}

double _number(Object? value, String field) {
  if (value is num) return value.toDouble();
  throw FormatException('出肉率字段 $field 格式不正确');
}

String _text(Object? value, String field) {
  if (value is String && value.trim().isNotEmpty) return value.trim();
  throw FormatException('出肉率字段 $field 格式不正确');
}

String? _optional(Object? value) {
  if (value == null) return null;
  if (value is! String) throw const FormatException('出肉率文本字段格式不正确');
  final trimmed = value.trim();
  return trimmed.isEmpty ? null : trimmed;
}

DateTime _dateTime(Object? value, String field) {
  final text = _text(value, field);
  final parsed = DateTime.tryParse(text);
  if (parsed == null) throw FormatException('出肉率字段 $field 格式不正确');
  return parsed;
}
