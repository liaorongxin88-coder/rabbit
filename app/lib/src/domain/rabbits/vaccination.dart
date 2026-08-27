/// 一条疫苗接种记录。
///
/// 后端一次接种可覆盖多只兔，但每只兔各自成行，所以这里就是单行模型。
class VaccinationRecord {
  const VaccinationRecord({
    required this.id,
    required this.rabbitId,
    required this.vaccineName,
    required this.vaccinatedAt,
    required this.status,
    this.vaccineBatchNo,
    this.dose,
    this.route,
    this.nextDueDate,
    this.remark,
  });

  final int id;
  final int rabbitId;
  final String vaccineName;
  final DateTime? vaccinatedAt;

  /// `SCHEDULED` 表示还欠下一针，`DONE` 表示本轮已闭合。
  final String status;

  final String? vaccineBatchNo;
  final String? dose;
  final String? route;
  final DateTime? nextDueDate;
  final String? remark;

  bool get awaitsNextDose => status == 'SCHEDULED' && nextDueDate != null;

  String get statusLabel => awaitsNextDose ? '待补种' : '已完成';

  static VaccinationRecord fromJson(Map<String, dynamic> json) {
    return VaccinationRecord(
      id: _intValue(json['id']),
      rabbitId: _intValue(json['rabbitId']),
      vaccineName: _optionalString(json['vaccineName']) ?? '未命名疫苗',
      vaccinatedAt: _dateTimeValue(json['vaccinatedAt']),
      status: _optionalString(json['status']) ?? 'DONE',
      vaccineBatchNo: _optionalString(json['vaccineBatchNo']),
      dose: _optionalString(json['dose']),
      route: _optionalString(json['route']),
      nextDueDate: _dateTimeValue(json['nextDueDate']),
      remark: _optionalString(json['remark']),
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
      return int.tryParse(value.trim()) ?? 0;
    }
    return 0;
  }

  static DateTime? _dateTimeValue(Object? value) {
    if (value is int) {
      return DateTime.fromMillisecondsSinceEpoch(value, isUtc: true);
    }
    if (value is num) {
      return DateTime.fromMillisecondsSinceEpoch(value.toInt(), isUtc: true);
    }
    if (value is String && value.trim().isNotEmpty) {
      return DateTime.tryParse(value);
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
