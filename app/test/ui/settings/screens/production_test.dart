import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/settings/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/domain/settings/production.dart';
import 'package:rabbit_flutter/src/domain/reproduction/reminder_preference.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/settings/screens/production.dart';

void main() {
  testWidgets('house production settings saves reminders for that house',
      (tester) async {
    tester.view.physicalSize = const Size(360, 4000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final repository = _RecordingSettingsRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsRepositoryProvider.overrideWithValue(repository),
          houseSettingProvider(8).overrideWith(
            (_) async => HouseSettingState(
              setting: GlobalSetting.defaults(),
              customized: false,
            ),
          ),
          reminderPreferenceProvider(8).overrideWith(
            (_) async => const ReminderPreference(
              id: 31,
              houseId: 8,
              enabled: true,
              advanceDays: 2,
              notifyOverdue: true,
              taskTypes: {'ALL'},
            ),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const ProductionSettingsScreen(
            houseId: 8,
            houseName: '东区兔舍',
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('当前兔舍提醒'), findsOneWidget);
    expect(find.textContaining('东区兔舍的提醒单独配置'), findsOneWidget);
    expect(find.byKey(const ValueKey('reminder-house')), findsNothing);

    await tester.tap(find.byKey(const ValueKey('reminder-enabled')));
    await tester.tap(find.byKey(const ValueKey('reminder-save')));
    await tester.pumpAndSettle();

    expect(repository.updatedHouseId, 8);
    expect(repository.updatedPreference?.enabled, isFalse);
    expect(repository.updatedPreference?.advanceDays, 2);
    expect(find.text('事件提醒设置已保存'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('production fields explain their dates and keep save contract',
      (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final repository = _RecordingSettingsRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          settingsRepositoryProvider.overrideWithValue(repository),
          houseSettingProvider(8).overrideWith(
            (_) async => HouseSettingState(
              setting: GlobalSetting.defaults(),
              customized: true,
            ),
          ),
          reminderPreferenceProvider(8).overrideWith(
            (_) async => ReminderPreference.defaults,
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const ProductionSettingsScreen(
            houseId: 8,
            houseName: '东区兔舍',
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final scrollable = find.byType(Scrollable).first;
    for (final description in const [
      '执行催情后开始计算，到期提醒配种。',
      '完成配种后开始计算，到期提醒摸胎。',
      '按预产期提前设置的天数，到期提醒备产。',
      '完成接产后开始计算，到期提醒断奶分笼。',
      '接产后开始计算休养到期；空怀、流产或分娩失败后也用这一天数安排下一轮催情。',
      '进入后备阶段后开始计算，到期提醒转为种兔。',
      '从进入幼兔适应期起计入成熟日期；适应期内生成对应的日常观察提醒。',
      '从进入生长期起计入剩余成熟天数；生长期内生成对应的日常观察提醒。',
      '从进入育肥期起计算成熟日期，到期生成可出售提醒。',
    ]) {
      await tester.scrollUntilVisible(
        find.text(description),
        260,
        scrollable: scrollable,
      );
      expect(find.text(description), findsOneWidget);
    }

    final save = find.byKey(const ValueKey('production-settings-save'));
    for (var attempt = 0; attempt < 4 && save.evaluate().isEmpty; attempt++) {
      await tester.drag(scrollable, const Offset(0, -320));
      await tester.pumpAndSettle();
    }
    expect(save, findsOneWidget);
    await tester.tap(save);
    await tester.pumpAndSettle();

    expect(repository.updatedSettingHouseId, 8);
    expect(repository.updatedSetting?.aphrodisiacDays, 2);
    expect(repository.updatedSetting?.palpationDays, 12);
    expect(repository.updatedSetting?.prepartumDays, 3);
    expect(repository.updatedSetting?.weaningDays, 30);
    expect(repository.updatedSetting?.postpartumDays, 10);
    expect(repository.updatedSetting?.adaptationDays, 3);
    expect(repository.updatedSetting?.growingDays, 18);
    expect(repository.updatedSetting?.fatteningDays, 12);
    expect(repository.updatedSetting?.replacementDays, 90);
    expect(find.text('兔舍生产设置已保存'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('production stage settings remain reachable at 200 percent text',
      (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(
      tester.platformDispatcher.clearTextScaleFactorTestValue,
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          houseSettingProvider(8).overrideWith(
            (_) async => HouseSettingState(
              setting: GlobalSetting.defaults(),
              customized: true,
            ),
          ),
          reminderPreferenceProvider(8).overrideWith(
            (_) async => ReminderPreference.defaults,
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const ProductionSettingsScreen(
            houseId: 8,
            houseName: '东区兔舍',
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('production-fattening-days')),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();

    expect(find.text('商品兔生长参数'), findsOneWidget);
    expect(find.byKey(const ValueKey('production-fattening-days')),
        findsOneWidget);
    expect(find.text('从进入育肥期起计算成熟日期，到期生成可出售提醒。'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

class _RecordingSettingsRepository extends SettingsRepository {
  _RecordingSettingsRepository() : super(ApiClient(SessionStore()));

  int? updatedHouseId;
  ReminderPreference? updatedPreference;
  int? updatedSettingHouseId;
  GlobalSetting? updatedSetting;

  @override
  Future<void> updateHouseSetting({
    required int houseId,
    required GlobalSetting setting,
  }) async {
    updatedSettingHouseId = houseId;
    updatedSetting = setting;
  }

  @override
  Future<void> updateReminderPreference({
    required int houseId,
    required ReminderPreference preference,
  }) async {
    updatedHouseId = houseId;
    updatedPreference = preference;
  }
}
