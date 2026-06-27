import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/app.dart';
import 'package:rabbit_flutter/src/data/repositories/events_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/settings_repository.dart';
import 'package:rabbit_flutter/src/data/services/local_app_settings_store.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/domain/models/global_setting.dart';
import 'package:rabbit_flutter/src/domain/models/local_app_settings.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';

void main() {
  testWidgets('shows login screen before session is restored', (tester) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});

    await tester.pumpWidget(const ProviderScopeWrapper());
    await tester.pumpAndSettle();

    expect(find.text('智能兔管家'), findsOneWidget);
    expect(find.text('登录'), findsOneWidget);
  });

  testWidgets('restores session and opens shell without duplicate keys',
      (tester) async {
    SharedPreferences.setMockInitialValues({
      'userId': 3,
      'userName': 'test_20260623',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'test-token'});

    await tester.pumpWidget(
      ProviderScopeWrapper(
        overrides: [
          housesProvider.overrideWith(
            (_) async => const [
              RabbitHouse(
                id: 1,
                name: '测试兔舍',
                remark: '',
                layoutRows: 1,
                layoutCols: 3,
                layoutLayers: 1,
              ),
            ],
          ),
          homeEventsProvider.overrideWith(
            (_) async => const <EventItem>[],
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('今日预警!'), findsOneWidget);
  });

  testWidgets('opens profile with settings entries after session restore',
      (tester) async {
    SharedPreferences.setMockInitialValues({
      'userId': 3,
      'userName': 'test_20260627',
      'app.startRoute': '/profile',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'test-token'});

    await tester.pumpWidget(
      ProviderScopeWrapper(
        overrides: [
          housesProvider.overrideWith((_) async {
            throw AssertionError('profile should not load houses');
          }),
          homeEventsProvider.overrideWith((_) async => const <EventItem>[]),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('我的'), findsWidgets);
    expect(find.text('账号设置'), findsOneWidget);
    expect(find.text('应用设置'), findsOneWidget);
    expect(find.text('兔舍生产设置'), findsOneWidget);
    expect(find.text('所有兔舍共用的周期配置'), findsOneWidget);
    expect(find.textContaining('当前兔舍'), findsNothing);
    expect(find.text('后端地址'), findsNothing);
  });

  testWidgets('production settings opens without selected house',
      (tester) async {
    SharedPreferences.setMockInitialValues({
      'userId': 3,
      'userName': 'test_20260627',
      'app.startRoute': '/profile',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'test-token'});

    await tester.pumpWidget(
      ProviderScopeWrapper(
        overrides: [
          housesProvider.overrideWith((_) async => const <RabbitHouse>[]),
          homeEventsProvider.overrideWith((_) async => const <EventItem>[]),
          userSettingProvider
              .overrideWith((_) async => GlobalSetting.defaults()),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('兔舍生产设置'));
    await tester.pumpAndSettle();

    expect(find.text('所有兔舍默认配置'), findsOneWidget);
    expect(find.text('摸胎天数'), findsOneWidget);
    expect(find.text('请选择兔舍'), findsNothing);
  });

  testWidgets('house production settings inherits default before save',
      (tester) async {
    SharedPreferences.setMockInitialValues({
      'userId': 3,
      'userName': 'test_20260627',
      'app.startRoute': '/houses',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'test-token'});

    await tester.pumpWidget(
      ProviderScopeWrapper(
        overrides: [
          housesProvider.overrideWith(
            (_) async => const [
              RabbitHouse(
                id: 8,
                name: '测试1',
                remark: '',
                layoutRows: 1,
                layoutCols: 1,
                layoutLayers: 1,
              ),
            ],
          ),
          homeEventsProvider.overrideWith((_) async => const <EventItem>[]),
          houseSettingProvider.overrideWith(
            (_, __) async => HouseSettingState(
              setting: GlobalSetting.defaults(),
              customized: false,
            ),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('测试1'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('生产设置'));
    await tester.pumpAndSettle();

    expect(find.text('兔舍生产设置'), findsWidgets);
    expect(find.text('继承默认配置'), findsOneWidget);
    expect(find.textContaining('测试1 的生产周期'), findsOneWidget);
    expect(find.text('摸胎天数'), findsOneWidget);
  });

  test('persists and clears local app settings', () async {
    SharedPreferences.setMockInitialValues({});
    final store = LocalAppSettingsStore();

    await store.saveThemeMode(ThemeMode.dark);
    await store.saveStartRoute('/dashboard');

    final saved = await store.read();
    expect(saved.themeMode, ThemeMode.dark);
    expect(saved.startRoute, '/dashboard');

    await store.clearLocalPreferences();

    final cleared = await store.read();
    expect(cleared.themeMode, LocalAppSettings.defaultSettings.themeMode);
    expect(cleared.startRoute, LocalAppSettings.defaultSettings.startRoute);
  });
}

class ProviderScopeWrapper extends StatelessWidget {
  const ProviderScopeWrapper({super.key, this.overrides = const []});

  final List<Override> overrides;

  @override
  Widget build(BuildContext context) {
    return ProviderScope(
      overrides: overrides,
      child: const RabbitManagerApp(),
    );
  }
}
