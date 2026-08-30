import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/batches/batch_code.dart';

void main() {
  final fixedTime = DateTime.utc(2026, 2, 3, 4, 5, 6, 7);

  test('uses the house name and farm-local minute in a new batch code', () {
    expect(defaultBatchCode('东一舍', fixedTime), '东一舍-20260203-1205');
  });

  test('the same instant produces the same farm timestamp', () {
    expect(
      defaultBatchCode('东一舍', fixedTime),
      defaultBatchCode('东一舍', fixedTime.toLocal()),
    );
  });

  test('normalizes separators and falls back for a blank house name', () {
    expect(
      defaultBatchCode('  东一 / 舍--A  ', fixedTime),
      '东一-舍-A-20260203-1205',
    );
    expect(defaultBatchCode(' /_- ', fixedTime), '兔舍-20260203-1205');
  });

  test('truncates long names to the backend batch-code limit', () {
    final code = defaultBatchCode('超长兔舍' * 30, fixedTime);

    expect(code.runes.length, maxBatchCodeLength);
    expect(code, endsWith('-20260203-1205'));
  });
}
