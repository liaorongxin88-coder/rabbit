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

class DashboardReport {
  const DashboardReport({
    required this.feed,
    required this.breeding,
  });

  final FeedSummary feed;
  final BreedingSummary breeding;

  factory DashboardReport.empty() {
    return const DashboardReport(
      feed: FeedSummary(recordCount: 0, totalAmount: 0),
      breeding: BreedingSummary(
        totalLitters: 0,
        totalKits: 0,
        totalLiveKits: 0,
        totalWeaned: 0,
        successBreedingCount: 0,
        failedBreedingCount: 0,
      ),
    );
  }

  factory DashboardReport.sum(Iterable<DashboardReport> reports) {
    var feedRecordCount = 0;
    var feedTotalAmount = 0.0;
    var totalLitters = 0;
    var totalKits = 0;
    var totalLiveKits = 0;
    var totalWeaned = 0;
    var successBreedingCount = 0;
    var failedBreedingCount = 0;

    for (final report in reports) {
      feedRecordCount += report.feed.recordCount;
      feedTotalAmount += report.feed.totalAmount;
      totalLitters += report.breeding.totalLitters;
      totalKits += report.breeding.totalKits;
      totalLiveKits += report.breeding.totalLiveKits;
      totalWeaned += report.breeding.totalWeaned;
      successBreedingCount += report.breeding.successBreedingCount;
      failedBreedingCount += report.breeding.failedBreedingCount;
    }

    return DashboardReport(
      feed: FeedSummary(
        recordCount: feedRecordCount,
        totalAmount: feedTotalAmount,
      ),
      breeding: BreedingSummary(
        totalLitters: totalLitters,
        totalKits: totalKits,
        totalLiveKits: totalLiveKits,
        totalWeaned: totalWeaned,
        successBreedingCount: successBreedingCount,
        failedBreedingCount: failedBreedingCount,
      ),
    );
  }
}

class DashboardSummary {
  const DashboardSummary({
    required this.selectedHouseId,
    this.selectedBatchId,
    required this.houseCount,
    required this.year,
    required this.totalRabbits,
    required this.seedRabbits,
    required this.maleRabbits,
    required this.femaleRabbits,
    required this.bredRabbits,
    required this.readyForBreeding,
    required this.litters,
    required this.nursingKits,
    required this.commodityRabbits,
    required this.replacementRabbits,
    required this.liveRate,
    required this.monthlyBirths,
    required this.monthlyWeaned,
  });

  final int? selectedHouseId;
  final int? selectedBatchId;
  final int houseCount;
  final int year;
  final int totalRabbits;
  final int seedRabbits;
  final int maleRabbits;
  final int femaleRabbits;
  final int bredRabbits;
  final int readyForBreeding;
  final int litters;
  final int nursingKits;
  final int commodityRabbits;
  final int replacementRabbits;
  final double liveRate;
  final List<int> monthlyBirths;
  final List<int> monthlyWeaned;

  factory DashboardSummary.empty({required int year}) {
    return DashboardSummary(
      selectedHouseId: null,
      selectedBatchId: null,
      houseCount: 0,
      year: year,
      totalRabbits: 0,
      seedRabbits: 0,
      maleRabbits: 0,
      femaleRabbits: 0,
      bredRabbits: 0,
      readyForBreeding: 0,
      litters: 0,
      nursingKits: 0,
      commodityRabbits: 0,
      replacementRabbits: 0,
      liveRate: 0,
      monthlyBirths: List<int>.filled(12, 0),
      monthlyWeaned: List<int>.filled(12, 0),
    );
  }

  static DashboardSummary fromJson(Map<String, dynamic> json) {
    return DashboardSummary(
      selectedHouseId: _nullableIntValue(json['selectedHouseId']),
      selectedBatchId: _nullableIntValue(json['selectedBatchId']),
      houseCount: _intValue(json['houseCount']),
      year: _intValue(json['year']),
      totalRabbits: _intValue(json['totalRabbits']),
      seedRabbits: _intValue(json['seedRabbits']),
      maleRabbits: _intValue(json['maleRabbits']),
      femaleRabbits: _intValue(json['femaleRabbits']),
      bredRabbits: _intValue(json['bredRabbits']),
      readyForBreeding: _intValue(json['readyForBreeding']),
      litters: _intValue(json['litters']),
      nursingKits: _intValue(json['nursingKits']),
      commodityRabbits: _intValue(json['commodityRabbits']),
      replacementRabbits: _intValue(json['replacementRabbits']),
      liveRate: _doubleValue(json['liveRate']),
      monthlyBirths: _monthValues(json['monthlyBirths']),
      monthlyWeaned: _monthValues(json['monthlyWeaned']),
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

int? _nullableIntValue(Object? value) {
  if (value == null) {
    return null;
  }
  return _intValue(value);
}

List<int> _monthValues(Object? value) {
  final parsed =
      value is List ? value.map(_intValue).take(12).toList() : const <int>[];
  return [
    ...parsed,
    ...List<int>.filled(12 - parsed.length, 0),
  ];
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
