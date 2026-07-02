import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/app.dart';
import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/events_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/report_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/settings_repository.dart';
import 'package:rabbit_flutter/src/data/services/local_app_settings_store.dart';
import 'package:rabbit_flutter/src/domain/models/batch.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/domain/models/global_setting.dart';
import 'package:rabbit_flutter/src/domain/models/local_app_settings.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/domain/models/report_summary.dart';

void main() {
  testWidgets('shows login screen before session is restored', (tester) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});

    await tester.pumpWidget(const ProviderScopeWrapper());
    await tester.pumpAndSettle();

    expect(find.text('智能兔管家'), findsOneWidget);
    expect(find.text('手机号一键进入'), findsOneWidget);
    expect(find.text('检测手机号'), findsOneWidget);
    expect(find.text('账号'), findsOneWidget);
    expect(find.textContaining('模拟器默认连接'), findsNothing);
  });

  testWidgets('login methods switch by horizontal swipe', (tester) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});

    await tester.pumpWidget(const ProviderScopeWrapper());
    await tester.pumpAndSettle();

    expect(find.text('手机号一键进入'), findsOneWidget);
    expect(find.text('用户名'), findsNothing);

    await tester.drag(find.byType(PageView), const Offset(-420, 0));
    await tester.pumpAndSettle();

    expect(find.text('用户名'), findsOneWidget);
    expect(find.text('密码'), findsOneWidget);
    expect(find.text('创建新账号'), findsOneWidget);
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

  testWidgets('house list summary avoids context wording', (tester) async {
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
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('共 1 个兔舍，点击兔舍进入管理。'), findsOneWidget);
    expect(find.textContaining('业务上下文'), findsNothing);
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

  testWidgets('dashboard defaults to all houses and filters one house',
      (tester) async {
    SharedPreferences.setMockInitialValues({
      'userId': 3,
      'userName': 'test_20260627',
      'app.startRoute': '/dashboard',
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
              RabbitHouse(
                id: 9,
                name: '测试2',
                remark: '',
                layoutRows: 1,
                layoutCols: 1,
                layoutLayers: 1,
              ),
            ],
          ),
          homeEventsProvider.overrideWith((_) async => const <EventItem>[]),
          houseReportProvider.overrideWith((_, houseId) async {
            return DashboardReport(
              feed: const FeedSummary(recordCount: 0, totalAmount: 0),
              breeding: BreedingSummary(
                totalLitters: houseId == 8 ? 2 : 1,
                totalKits: houseId == 8 ? 10 : 5,
                totalLiveKits: houseId == 8 ? 8 : 5,
                totalWeaned: houseId == 8 ? 4 : 2,
                successBreedingCount: houseId == 8 ? 1 : 0,
                failedBreedingCount: 0,
              ),
            );
          }),
          allActiveHouseRabbitsProvider.overrideWith((_, houseId) async {
            if (houseId == 8) {
              return const [
                Rabbit(
                  id: 1,
                  houseId: 8,
                  cageId: 1,
                  motherId: null,
                  type: '0',
                  gender: '0',
                  breed: '',
                  arrivalMethod: '',
                  arrivalDate: null,
                  weight: null,
                  isActive: true,
                ),
                Rabbit(
                  id: 2,
                  houseId: 8,
                  cageId: 1,
                  motherId: null,
                  type: '2',
                  gender: '1',
                  breed: '',
                  arrivalMethod: '',
                  arrivalDate: null,
                  weight: null,
                  isActive: true,
                ),
              ];
            }
            return const [
              Rabbit(
                id: 3,
                houseId: 9,
                cageId: 1,
                motherId: null,
                type: '1',
                gender: '1',
                breed: '',
                arrivalMethod: '',
                arrivalDate: null,
                weight: null,
                isActive: true,
              ),
            ];
          }),
          houseBatchesProvider.overrideWith((_, houseId) async {
            if (houseId == 8) {
              return const [
                Batch(
                  id: 1,
                  houseId: 8,
                  batchCode: 'B1',
                  status: 'MATING',
                  startDate: null,
                  endDate: null,
                  remark: '',
                ),
              ];
            }
            return const <Batch>[];
          }),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('数据面板'), findsWidgets);
    expect(find.text('全部兔舍'), findsOneWidget);
    expect(find.text('已汇总 2 个兔舍'), findsOneWidget);
    expect(find.text('3'), findsAtLeastNWidgets(1));

    await tester.tap(find.byTooltip('选择兔舍'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('测试1').last);
    await tester.pumpAndSettle();

    expect(find.text('测试1'), findsOneWidget);
    expect(find.text('仅显示当前选择的兔舍'), findsOneWidget);
    expect(find.text('2'), findsAtLeastNWidgets(1));
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
