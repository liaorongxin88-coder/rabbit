import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/batches/statistics.dart';

void main() {
  test('parses the four batch statistics fields', () {
    final statistics = BatchStatistics.fromJson({
      'totalLitters': 3,
      'totalKits': 28,
      'totalLiveKits': 26,
      'totalWeaned': 22,
    });

    expect(statistics.totalLitters, 3);
    expect(statistics.totalKits, 28);
    expect(statistics.totalLiveKits, 26);
    expect(statistics.totalWeaned, 22);
    expect(statistics.isEmpty, isFalse);
  });

  test('retains a valid all-zero batch statistics response', () {
    final statistics = BatchStatistics.fromJson({
      'totalLitters': 0,
      'totalKits': 0,
      'totalLiveKits': 0,
      'totalWeaned': 0,
    });

    expect(statistics, isA<BatchStatistics>());
    expect(statistics.isEmpty, isTrue);
  });
}
