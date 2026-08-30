import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/settings/production.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';

void main() {
  const setting = GlobalSetting(
    id: 1,
    userId: 0,
    houseId: 9,
    aphrodisiacDays: 2,
    palpationDays: 12,
    prepartumDays: 3,
    weaningDays: 25,
    postpartumDays: 10,
    adaptationDays: 3,
    growingDays: 18,
    fatteningDays: 12,
    saleDays: 30,
    replacementDays: 45,
    remark: '',
  );
  final base = DateTime(2026, 8, 19, 16, 30);

  test('提醒日期文案明确指出下一生产阶段', () {
    expect(reminderDateLabelForStage(ReproStage.ready), '催情提醒日期');
    expect(reminderDateLabelForStage(ReproStage.awaitEstrus), '催情提醒日期');
    expect(reminderDateLabelForStage(ReproStage.awaitMating), '配种提醒日期');
    expect(reminderDateLabelForStage(ReproStage.awaitPalpation), '摸胎提醒日期');
    expect(reminderDateLabelForStage(ReproStage.awaitPrepartum), '备产提醒日期');
    expect(reminderDateLabelForStage(ReproStage.awaitDelivery), '分娩提醒日期');
    expect(reminderDateLabelForStage(ReproStage.awaitWeaning), '断奶提醒日期');
  });

  test('业务阶段按兔场生产设置推导本地下次提醒日期', () {
    expect(
      suggestedReminderDate(
        stage: ReproStage.ready,
        setting: setting,
        from: base,
      ),
      DateTime(2026, 8, 29),
    );
    expect(
      suggestedReminderDate(
        stage: ReproStage.awaitMating,
        setting: setting,
        from: base,
      ),
      DateTime(2026, 8, 21),
    );
    expect(
      suggestedReminderDate(
        stage: ReproStage.awaitPalpation,
        setting: setting,
        from: base,
      ),
      DateTime(2026, 8, 31),
    );
    expect(
      suggestedReminderDate(
        stage: ReproStage.awaitPrepartum,
        setting: setting,
        from: base,
      ),
      DateTime(2026, 9, 3),
    );
    expect(
      suggestedReminderDate(
        stage: ReproStage.awaitDelivery,
        setting: setting,
        from: base,
      ),
      DateTime(2026, 8, 19),
    );
    expect(
      suggestedReminderDate(
        stage: ReproStage.awaitWeaning,
        setting: setting,
        from: base,
      ),
      DateTime(2026, 9, 13),
    );
  });

  test('UTC instant uses the Asia Shanghai calendar day', () {
    final instant = DateTime.utc(2026, 8, 19, 16, 45);

    expect(farmLocalDateTime(instant), DateTime(2026, 8, 20, 0, 45));
    expect(localDateOnly(instant), DateTime(2026, 8, 20));
    expect(
      localDateOnly(DateTime(2026, 8, 19, 23, 30)),
      DateTime(2026, 8, 19),
      reason: 'date-only picker values keep their entered components',
    );
  });

  test('picker wall-clock values serialize in the farm timezone', () {
    final selected = DateTime(2026, 8, 20, 9, 30);

    expect(farmDateTimeToUtc(selected), DateTime.utc(2026, 8, 20, 1, 30));
    expect(
      farmDateTimeToEpochMilliseconds(selected),
      DateTime.utc(2026, 8, 20, 1, 30).millisecondsSinceEpoch,
    );
    expect(farmDateTimeToIso(selected), '2026-08-20T01:30:00.000Z');
    expect(
      farmDateTimeToUtc(DateTime.utc(2026, 8, 20, 1, 30)),
      DateTime.utc(2026, 8, 20, 1, 30),
      reason: 'true API instants must not be shifted twice',
    );
  });

  test('日历初始日期不会落到今天以前', () {
    expect(
      reminderInitialDate(
        suggested: DateTime(2026, 8, 10),
        now: base,
      ),
      DateTime(2026, 8, 19),
    );
    expect(
      reminderInitialDate(
        suggested: DateTime(2026, 8, 21),
        selected: DateTime(2026, 8, 18),
        now: base,
      ),
      DateTime(2026, 8, 19),
    );
    expect(
      reminderInitialDate(
        suggested: DateTime(2036, 8, 19),
        now: base,
        latest: DateTime(2036, 8, 18),
      ),
      DateTime(2036, 8, 18),
    );
  });
}
