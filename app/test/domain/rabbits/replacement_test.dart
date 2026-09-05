import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/rabbits/replacement.dart';

void main() {
  test('serializes measured source batch totals', () {
    const allocation = ReplacementBatchAllocation(
      batchId: 101,
      rabbitCount: 3,
      totalWeightKg: 6.75,
    );

    expect(allocation.validate(), isNull);
    expect(allocation.toJson(), {
      'batchId': 101,
      'rabbitCount': 3,
      'totalWeightKg': 6.75,
    });
  });

  test('requires positive counts and three-decimal positive total weight', () {
    expect(
      const ReplacementBatchAllocation(
        batchId: null,
        rabbitCount: 0,
        totalWeightKg: 1,
      ).validate(),
      contains('只数'),
    );
    expect(
      const ReplacementBatchAllocation(
        batchId: 101,
        rabbitCount: 1,
        totalWeightKg: 1.2345,
      ).validate(),
      contains('三位小数'),
    );
  });
}
