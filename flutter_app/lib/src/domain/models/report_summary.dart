class FeedSummary {
  const FeedSummary({
    required this.recordCount,
    required this.totalAmount,
  });

  final int recordCount;
  final double totalAmount;

  static FeedSummary fromJson(Map<String, dynamic> json) {
    return FeedSummary(
      recordCount: _intValue(json['recordCount']),
      totalAmount: _doubleValue(json['totalAmount']),
    );
  }
}

class BreedingSummary {
  const BreedingSummary({
    required this.totalLitters,
    required this.totalKits,
    required this.totalLiveKits,
    required this.totalWeaned,
    required this.successBreedingCount,
    required this.failedBreedingCount,
  });

  final int totalLitters;
  final int totalKits;
  final int totalLiveKits;
  final int totalWeaned;
  final int successBreedingCount;
  final int failedBreedingCount;

  double get liveRate {
    if (totalKits <= 0) {
      return 0;
    }
    return totalLiveKits / totalKits;
  }

  static BreedingSummary fromJson(Map<String, dynamic> json) {
    return BreedingSummary(
      totalLitters: _intValue(json['totalLitters']),
      totalKits: _intValue(json['totalKits']),
      totalLiveKits: _intValue(json['totalLiveKits']),
      totalWeaned: _intValue(json['totalWeaned']),
      successBreedingCount: _intValue(json['successBreedingCount']),
      failedBreedingCount: _intValue(json['failedBreedingCount']),
    );
  }
}

int _intValue(Object? value) {
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

double _doubleValue(Object? value) {
  if (value is num) {
    return value.toDouble();
  }
  if (value is String) {
    return double.tryParse(value) ?? 0;
  }
  return 0;
}
