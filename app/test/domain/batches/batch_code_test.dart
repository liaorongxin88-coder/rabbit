import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/batches/batch_code.dart';

void main() {
  final fixedTime = DateTime(2026, 2, 3, 4, 5, 6, 7);

  test('uses the local date and minute in a new batch code', () {
    expect(defaultBatchCode(fixedTime), '批次-20260203-0405');
  });

  test('converts a UTC instant to farm-local time before formatting', () {
    expect(
      defaultBatchCode(DateTime.utc(2026, 2, 3, 4, 5)),
      defaultBatchCode(DateTime.utc(2026, 2, 3, 4, 5).toLocal()),
    );
  });

  /// 编号要显示在提醒卡片上，和周期号、日期挤一行，所以生成值必须短。
  /// 旧格式带兔舍名加 17 位毫秒戳，兔舍名一长就被省略号截掉。
  test('stays short enough for the reminder chip', () {
    final code = defaultBatchCode(fixedTime);

    expect(code.length, 16);
    expect(code, startsWith('批次-'));
  });
}
