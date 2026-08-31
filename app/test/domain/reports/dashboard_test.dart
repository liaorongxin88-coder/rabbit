import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/reports/dashboard.dart';

void main() {
  test('dashboard summary normalizes month arrays to twelve values', () {
    final summary = DashboardSummary.fromJson({
      'selectedHouseId': 8,
      'selectedBatchId': 11,
      'houseCount': 1,
      'year': 2026,
      'totalRabbits': 12,
      'liveRate': 0.8,
      'monthlyBirths': [1, 2],
      'monthlyWeaned': List<int>.generate(14, (index) => index),
    });

    expect(summary.selectedHouseId, 8);
    expect(summary.selectedBatchId, 11);
    expect(summary.totalRabbits, 12);
    expect(summary.monthlyBirths, hasLength(12));
    expect(summary.monthlyBirths.take(3), [1, 2, 0]);
    expect(summary.monthlyWeaned, hasLength(12));
    expect(summary.monthlyWeaned.last, 11);
  });

  test('empty dashboard summary has no house or batch scope', () {
    final summary = DashboardSummary.empty(year: 2026);

    expect(summary.selectedHouseId, isNull);
    expect(summary.selectedBatchId, isNull);
    expect(summary.year, 2026);
    expect(summary.monthlyBirths, List<int>.filled(12, 0));
    expect(summary.monthlyWeaned, List<int>.filled(12, 0));
  });
}
