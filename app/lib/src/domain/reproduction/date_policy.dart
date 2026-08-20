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
  final base = localDateOnly(from ?? DateTime.now());
  final configuredDays = switch (stage) {
    ReproStage.ready => setting.postpartumDays,
    ReproStage.awaitEstrus => setting.postpartumDays,
    ReproStage.awaitMating => setting.aphrodisiacDays,
    ReproStage.awaitPalpation => setting.palpationDays,
    ReproStage.awaitPrepartum => setting.prepartumDays,
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
  final today = localDateOnly(now ?? DateTime.now());
  final candidate = localDateOnly(selected ?? suggested);
  if (candidate.isBefore(today)) {
    return today;
  }
  final latestDate = latest == null ? null : localDateOnly(latest);
  return latestDate != null && candidate.isAfter(latestDate)
      ? latestDate
      : candidate;
}

DateTime localDateOnly(DateTime value) =>
    DateTime(value.year, value.month, value.day);
