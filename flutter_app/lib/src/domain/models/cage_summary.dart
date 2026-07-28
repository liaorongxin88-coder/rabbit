class CageSummary {
  const CageSummary({
    required this.cageId,
    required this.cageNumber,
    required this.rabbitCount,
    required this.isFed,
    required this.lastFeedTime,
    required this.lastFeedType,
    required this.lastFeedAmount,
    required this.lastFeedUnit,
    required this.abnormalUndealCount,
    required this.lastAbnormalTime,
    required this.lastAbnormalStatus,
  });

  final int cageId;
  final String cageNumber;
  final int rabbitCount;
  final bool isFed;
  final DateTime? lastFeedTime;
  final String lastFeedType;
  final double? lastFeedAmount;
  final String lastFeedUnit;
  final int abnormalUndealCount;
  final DateTime? lastAbnormalTime;
  final String lastAbnormalStatus;

  static CageSummary fromJson(Map<String, dynamic> json) {
    return CageSummary(
      cageId: _intValue(json['cageId']),
      cageNumber: json['cageNumber'] as String? ?? '',
      rabbitCount: _intValue(json['rabbitCount']),
      isFed: _boolValue(json['isFed']),
      lastFeedTime: _dateValue(json['lastFeedTime']),
      lastFeedType: json['lastFeedType'] as String? ?? '',
      lastFeedAmount: _doubleValue(json['lastFeedAmount']),
      lastFeedUnit: json['lastFeedUnit'] as String? ?? '',
      abnormalUndealCount: _intValue(json['abnormalUndealCount']),
      lastAbnormalTime: _dateValue(json['lastAbnormalTime']),
      lastAbnormalStatus: json['lastAbnormalStatus'] as String? ?? '',
    );
  }

  static int _intValue(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    if (value is String) return int.tryParse(value) ?? 0;
    return 0;
  }

  static double? _doubleValue(Object? value) {
    if (value is num) return value.toDouble();
    if (value is String) return double.tryParse(value);
    return null;
  }

  static bool _boolValue(Object? value) {
    if (value is bool) return value;
    if (value is num) return value != 0;
    return value?.toString().toLowerCase() == 'true';
  }

  static DateTime? _dateValue(Object? value) {
    if (value is int) return DateTime.fromMillisecondsSinceEpoch(value);
    if (value is num) {
      return DateTime.fromMillisecondsSinceEpoch(value.toInt());
    }
    if (value is String) return DateTime.tryParse(value);
    return null;
  }
}
