import 'package:rabbit_flutter/src/domain/settings/production.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';

String reminderTitleForStage(ReproStage stage) => switch (stage) {
      ReproStage.ready || ReproStage.awaitEstrus => '催情提醒',
      ReproStage.awaitMating => '配种提醒',
      ReproStage.awaitPalpation => '摸胎提醒',
      ReproStage.awaitPrepartum => '备产提醒',
      ReproStage.awaitDelivery => '分娩提醒',
      ReproStage.awaitWeaning => '断奶提醒',
      ReproStage.suspended => '恢复提醒',
      ReproStage.retired => '离场提醒',
    };

String reminderDateLabelForStage(ReproStage stage) =>
    '${reminderTitleForStage(stage)}日期';

/// 客户端日期建议只负责日历预填；服务端仍是提醒日期的权威计算方。
DateTime suggestedReminderDate({
  required ReproStage stage,
  required GlobalSetting setting,
  DateTime? from,
}) {
  final base = from == null ? farmToday() : localDateOnly(from);
  final configuredDays = switch (stage) {
    ReproStage.ready => setting.postpartumDays,
    ReproStage.awaitEstrus => setting.postpartumDays,
    ReproStage.awaitMating => setting.aphrodisiacDays,
    ReproStage.awaitPalpation => setting.palpationDays,
    ReproStage.awaitPrepartum =>
      (30 - setting.palpationDays - setting.prepartumDays).clamp(0, 30).toInt(),
    ReproStage.awaitDelivery => 0,
    ReproStage.awaitWeaning => setting.weaningDays,
    ReproStage.suspended => 0,
    ReproStage.retired => 0,
  };
  final days = configuredDays < 0 ? 0 : configuredDays;
  return base.add(Duration(days: days));
}

DateTime reminderInitialDate({
  required DateTime suggested,
  DateTime? selected,
  DateTime? now,
  DateTime? latest,
}) {
  final today = now == null ? farmToday() : localDateOnly(now);
  final candidate = localDateOnly(selected ?? suggested);
  if (candidate.isBefore(today)) {
    return today;
  }
  final latestDate = latest == null ? null : localDateOnly(latest);
  return latestDate != null && candidate.isAfter(latestDate)
      ? latestDate
      : candidate;
}

const _farmUtcOffset = Duration(hours: 8);

/// Converts API instants to the farm's Asia/Shanghai wall clock.
///
/// Local values come from date-only pickers, so their calendar components must
/// be preserved instead of being interpreted through the device time zone.
DateTime farmLocalDateTime(DateTime value) {
  if (!value.isUtc) {
    return value;
  }
  final farmValue = value.add(_farmUtcOffset);
  return DateTime(
    farmValue.year,
    farmValue.month,
    farmValue.day,
    farmValue.hour,
    farmValue.minute,
    farmValue.second,
    farmValue.millisecond,
    farmValue.microsecond,
  );
}

DateTime localDateOnly(DateTime value) {
  final farmValue = farmLocalDateTime(value);
  return DateTime(farmValue.year, farmValue.month, farmValue.day);
}

/// 农场当前日期（Asia/Shanghai 墙上时间的年月日）。
///
/// 必须先 `toUtc()`。`DateTime.now()` 带设备时区身份，而 [farmLocalDateTime] 对
/// 非 UTC 值原样返回——那条分支是留给日历选择器的，选择器值没有时区身份，它的
/// 年月日就是农场日期。把 `now()` 直接传进去会得到**设备本地日期**：设备不在
/// UTC+8 时就会偏移一天，跨零点前后提交的业务日期会落到错误的一天，而这种错误
/// 不会报错，只会静默写进单据。
///
/// 想取“今天”一律用这个函数，不要写 `localDateOnly(DateTime.now())`。
DateTime farmToday() {
  final now = farmNow();
  return DateTime(now.year, now.month, now.day);
}

/// 农场此刻的墙上时间（Asia/Shanghai，无时区身份）。
///
/// 写入路径上的日期字段都是“农场墙上时间”语义：提交前由 [farmDateTimeToUtc]
/// 按 UTC+8 重新解释。所以预填“现在”时必须用本函数，不能直接用
/// `DateTime.now()`——后者带的是设备墙上时间，会被当成农场时间原样提交，
/// 设备不在 UTC+8 时整个时刻就错位了。
DateTime farmNow() => farmLocalDateTime(DateTime.now().toUtc());

/// Interprets a picker value as an Asia/Shanghai wall-clock time.
///
/// Picker values have no time-zone identity. Converting them with [DateTime.toUtc]
/// would use the device zone and can move the selected farm date to another day.
DateTime farmDateTimeToUtc(DateTime value) {
  if (value.isUtc) {
    return value;
  }
  return DateTime.utc(
    value.year,
    value.month,
    value.day,
    value.hour,
    value.minute,
    value.second,
    value.millisecond,
    value.microsecond,
  ).subtract(_farmUtcOffset);
}

int farmDateTimeToEpochMilliseconds(DateTime value) =>
    farmDateTimeToUtc(value).millisecondsSinceEpoch;

String farmDateTimeToIso(DateTime value) =>
    farmDateTimeToUtc(value).toIso8601String();
