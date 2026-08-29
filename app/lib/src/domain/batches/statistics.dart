class BatchStatistics {
  const BatchStatistics({
    required this.totalLitters,
    required this.totalKits,
    required this.totalLiveKits,
    required this.totalWeaned,
  });

  const BatchStatistics.empty()
      : totalLitters = 0,
        totalKits = 0,
        totalLiveKits = 0,
        totalWeaned = 0;

  final int totalLitters;
  final int totalKits;
  final int totalLiveKits;
  final int totalWeaned;

  bool get isEmpty =>
      totalLitters == 0 &&
      totalKits == 0 &&
      totalLiveKits == 0 &&
      totalWeaned == 0;

  factory BatchStatistics.fromJson(Map<String, dynamic> json) {
    return BatchStatistics(
      totalLitters: _integer(json, 'totalLitters'),
      totalKits: _integer(json, 'totalKits'),
      totalLiveKits: _integer(json, 'totalLiveKits'),
      totalWeaned: _integer(json, 'totalWeaned'),
    );
  }

  static int _integer(Map<String, dynamic> json, String field) {
    final value = json[field];
    if (value is int) {
      return value;
    }
    throw FormatException('批次统计字段 $field 格式不正确');
  }
}
