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
}

class _RecordingSettingsRepository extends SettingsRepository {
  _RecordingSettingsRepository() : super(ApiClient(SessionStore()));

  int? updatedHouseId;
  ReminderPreference? updatedPreference;

  @override
  Future<void> updateReminderPreference({
    required int houseId,
    required ReminderPreference preference,
  }) async {
    updatedHouseId = houseId;
    updatedPreference = preference;
  }
}
