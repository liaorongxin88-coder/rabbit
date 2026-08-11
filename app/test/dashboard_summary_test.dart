import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/report_summary.dart';

void main() {
  test('dashboard summary normalizes month arrays to twelve values', () {
    final summary = DashboardSummary.fromJson({
      'selectedHouseId': 8,
      'houseCount': 1,
      'year': 2026,
      'totalRabbits': 12,
      'liveRate': 0.8,
      'monthlyBirths': [1, 2],
      'monthlyWeaned': List<int>.generate(14, (index) => index),
    });

    expect(summary.selectedHouseId, 8);
    expect(summary.totalRabbits, 12);
    expect(summary.monthlyBirths, hasLength(12));
    expect(summary.monthlyBirths.take(3), [1, 2, 0]);
    expect(summary.monthlyWeaned, hasLength(12));
    expect(summary.monthlyWeaned.last, 11);
  });
}
