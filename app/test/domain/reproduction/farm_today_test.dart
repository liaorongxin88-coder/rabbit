import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';

/// 农场“今天”必须按 Asia/Shanghai 算，不能跟着设备时区走。
///
/// 这条线之前断过：代码里 12 处写的是 `localDateOnly(DateTime.now())`，而
/// [farmLocalDateTime] 对非 UTC 值原样返回（那条分支是留给无时区身份的日历选择器的），
/// 于是 `now()` 带着设备时区被当成农场墙上时间，取到的是**设备本地日期**。
///
/// 设备不在 UTC+8 时这会偏移一天：跨零点前后提交的业务日期落到错误的一天，
/// 而且不会报错，只会静默写进单据，事后从报表很难倒查。CI 跑在 UTC，
/// 每天 16:00 UTC 之后必然触发，此前一直是靠运气避开的。
void main() {
  test('农场今天等于此刻的上海日期，与设备时区无关', () {
    final shanghai = DateTime.now().toUtc().add(const Duration(hours: 8));

    expect(
      farmToday(),
      DateTime(shanghai.year, shanghai.month, shanghai.day),
    );
  });

  test('农场今天不带时分秒', () {
    final today = farmToday();

    expect(today.hour, 0);
    expect(today.minute, 0);
    expect(today.second, 0);
  });

  /// 边界就在 UTC 16:00：此刻上海跨入次日，而 UTC 设备还停在当天。
  test('UTC 16:00 是农场日期翻篇的那一刻', () {
    expect(
      localDateOnly(DateTime.utc(2026, 3, 14, 15, 59)),
      DateTime(2026, 3, 14),
    );
    expect(
      localDateOnly(DateTime.utc(2026, 3, 14, 16, 0)),
      DateTime(2026, 3, 15),
    );
  });

  /// 选择器值没有时区身份，它的年月日就是农场日期，不能再换算一次。
  test('日历选择器选中的日期原样保留', () {
    expect(localDateOnly(DateTime(2026, 3, 14)), DateTime(2026, 3, 14));
    expect(localDateOnly(DateTime(2026, 3, 14, 23, 30)), DateTime(2026, 3, 14));
  });
}
