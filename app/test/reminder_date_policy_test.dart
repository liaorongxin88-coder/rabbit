import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/models/global_setting.dart';
import 'package:rabbit_flutter/src/domain/models/reminder_date_policy.dart';
import 'package:rabbit_flutter/src/domain/models/repro_task.dart';

void main() {
  const setting = GlobalSetting(
    id: 1,
    userId: 0,
    houseId: 9,
    aphrodisiacDays: 2,
    palpationDays: 12,
    gestationDays: 30,
    prepartumDays: 3,
    weaningDays: 25,
    postpartumDays: 10,
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
      DateTime(2026, 8, 22),
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
