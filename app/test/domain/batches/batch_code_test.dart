import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/batches/batch_code.dart';

void main() {
  final fixedTime = DateTime(2026, 2, 3, 4, 5, 6, 7);

  test('uses the selected house and local time in a new batch code', () {
    expect(
      defaultBatchCode('东一舍', fixedTime),
      '东一舍-批次-20260203040506007',
    );
    expect(
      defaultBatchCode('西二舍', fixedTime),
      '西二舍-批次-20260203040506007',
    );
  });

  test('reserves space for the timestamp when the house name is long', () {
    final code = defaultBatchCode('兔' * 100, fixedTime);

    expect(code.length, 100);
    expect(code, endsWith('-批次-20260203040506007'));
  });
}
