import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/app.dart';
import 'package:rabbit_flutter/src/data/repositories/auth_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/events_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/report_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/settings_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/data/services/local_app_settings_store.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';
import 'package:rabbit_flutter/src/domain/models/batch.dart';
import 'package:rabbit_flutter/src/domain/models/auth_session.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/domain/models/global_setting.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
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
    expect(find.byKey(const ValueKey('hongtu-logo')), findsOneWidget);
    final logo = tester.widget<Image>(
      find.byKey(const ValueKey('hongtu-logo')),
    );
    expect(
      (logo.image as AssetImage).assetName,
      'assets/branding/hongtu_logo.png',
    );
    expect(find.text('手机号一键进入'), findsOneWidget);
    expect(find.text('检测手机号'), findsOneWidget);
    expect(find.text('账号'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('《隐私政策》'),
      160,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('《隐私政策》'), findsOneWidget);
    expect(find.text('《用户协议》'), findsOneWidget);
    expect(find.textContaining('模拟器默认连接'), findsNothing);
  });

  testWidgets('login controls share the same horizontal alignment',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(const ProviderScopeWrapper());
    await tester.pumpAndSettle();

    final alignedKeys = [
      'login-mode-selector',
      'phone-number-input',
      'phone-login-flow',
      'phone-login-button',
      'legal-consent-row',
    ];
    final reference = tester.getRect(find.byKey(ValueKey(alignedKeys.first)));
    for (final key in alignedKeys.skip(1)) {
      final rect = tester.getRect(find.byKey(ValueKey(key)));
      expect(rect.left, closeTo(reference.left, 0.1), reason: key);
      expect(rect.right, closeTo(reference.right, 0.1), reason: key);
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('login flow remains usable with 200 percent text',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(
      tester.platformDispatcher.clearTextScaleFactorTestValue,
    );

    await tester.pumpWidget(const ProviderScopeWrapper());
    await tester.pumpAndSettle();

    final loginContext = tester.element(
      find.byKey(const ValueKey('login-mode-selector')),
    );
    expect(MediaQuery.textScalerOf(loginContext).scale(10), 15);
    expect(find.text('检测手机号'), findsOneWidget);
    expect(find.text('识别已有账号'), findsOneWidget);
    expect(find.text('账号登录'), findsOneWidget);
    expect(find.text('切换后继续'), findsOneWidget);
    await tester
        .ensureVisible(find.byKey(const ValueKey('phone-login-button')));
    expect(
      tester.getSize(find.byKey(const ValueKey('phone-login-button'))).height,
      greaterThanOrEqualTo(48),
    );
    expect(tester.takeException(), isNull);

    await tester.drag(
      find.byKey(const ValueKey('login-mode-content')),
      const Offset(-420, 0),
    );
    await tester.pumpAndSettle();
    expect(
        find.byKey(const ValueKey('account-username-field')), findsOneWidget);
    await tester.ensureVisible(
      find.byKey(const ValueKey('account-login-button')),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('does not load protected home while auth restore is pending',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
    final pendingSession = Completer<SessionSnapshot>();
    final houseRepository = _RecordingHouseRepository();

    await tester.pumpWidget(
      ProviderScopeWrapper(
        overrides: [
          sessionStoreProvider.overrideWithValue(
            _PendingSessionStore(pendingSession.future),
          ),
          houseRepositoryProvider.overrideWithValue(houseRepository),
        ],
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(find.text('今日预警!'), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsWidgets);
    expect(houseRepository.calls, 0);

    pendingSession.complete(
      const SessionSnapshot(
        token: null,
        userId: null,
        userName: null,
        houseId: 0,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('手机号一键进入'), findsOneWidget);
    expect(houseRepository.calls, 0);
  });

  testWidgets('login methods switch by horizontal swipe', (tester) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});

    await tester.pumpWidget(const ProviderScopeWrapper());
    await tester.pumpAndSettle();

    expect(find.text('手机号一键进入'), findsOneWidget);
    expect(find.text('用户名'), findsNothing);

    await tester.drag(
      find.byKey(const ValueKey('login-mode-content')),
      const Offset(-420, 0),
    );
    await tester.pumpAndSettle();

    expect(find.text('用户名'), findsOneWidget);
    expect(find.text('密码'), findsOneWidget);
    expect(find.text('创建新账号'), findsNothing);
    expect(find.text('登录'), findsOneWidget);
  });

  testWidgets('account password can be shown and hidden', (tester) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});

    await tester.pumpWidget(const ProviderScopeWrapper());
    await tester.pumpAndSettle();

    await tester.drag(
      find.byKey(const ValueKey('login-mode-content')),
      const Offset(-420, 0),
    );
    await tester.pumpAndSettle();

    final passwordField = find.byKey(
      const ValueKey('account-password-field'),
    );
    final visibilityToggle = find.byKey(
      const ValueKey('password-visibility-toggle'),
    );
    final passwordEditor = find.descendant(
      of: passwordField,
      matching: find.byType(EditableText),
    );

    expect(tester.widget<EditableText>(passwordEditor).obscureText, isTrue);
    expect(find.byTooltip('显示密码'), findsOneWidget);

    await tester.enterText(passwordField, 'secret123');
    await tester.tap(visibilityToggle);
    await tester.pump();

    expect(tester.widget<EditableText>(passwordEditor).obscureText, isFalse);
    expect(find.byTooltip('隐藏密码'), findsOneWidget);
    expect(
      tester.widget<EditableText>(passwordEditor).controller.text,
      'secret123',
    );

    await tester.tap(visibilityToggle);
    await tester.pump();

    expect(tester.widget<EditableText>(passwordEditor).obscureText, isTrue);
    expect(find.byTooltip('显示密码'), findsOneWidget);
  });

  testWidgets('validates restored session and opens the protected shell',
      (tester) async {
    SharedPreferences.setMockInitialValues({
      'userId': 3,
      'userName': 'test_20260623',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'test-token'});

    final authRepository = _FakeAuthRepository();
    await tester.pumpWidget(
      ProviderScopeWrapper(
        authRepository: authRepository,
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
    expect(authRepository.validationCalls, 1);
  });

  testWidgets('invalid restored session is cleared and returns to login',
      (tester) async {
    SharedPreferences.setMockInitialValues({
      'userId': 3,
      'userName': 'expired_user',
      'houseId.3': 8,
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'expired-token'});
    final houseRepository = _RecordingHouseRepository();
    final authRepository = _FakeAuthRepository(
      validationError: const ApiException('未登录', businessCode: 401),
    );

    await tester.pumpWidget(
      ProviderScopeWrapper(
        authRepository: authRepository,
        overrides: [
          houseRepositoryProvider.overrideWithValue(houseRepository),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('手机号一键进入'), findsOneWidget);
    expect(find.text('今日预警!'), findsNothing);
    expect(houseRepository.calls, 0);
    expect(authRepository.validationCalls, 1);
    expect(
      await const FlutterSecureStorage().read(key: 'token'),
      isNull,
    );
  });

  testWidgets(
      'unauthorized event clears an active session and returns to login',
      (tester) async {
    SharedPreferences.setMockInitialValues({
      'userId': 3,
      'userName': 'active_user',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'active-token'});
    final authRepository = _FakeAuthRepository();

    await tester.pumpWidget(
      ProviderScopeWrapper(
        authRepository: authRepository,
        overrides: [
          housesProvider.overrideWith((_) async => const <RabbitHouse>[]),
          homeEventsProvider.overrideWith((_) async => const <EventItem>[]),
        ],
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('今日预警!'), findsOneWidget);

    authRepository.emitUnauthorized();
    await tester.pumpAndSettle();

    expect(find.text('手机号一键进入'), findsOneWidget);
    expect(find.text('今日预警!'), findsNothing);
    expect(
      await const FlutterSecureStorage().read(key: 'token'),
      isNull,
    );
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
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'control',
              isAdmin: true,
            ),
          ),
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
    await tester.scrollUntilVisible(
      find.text('生产设置'),
      180,
      scrollable: find.byType(Scrollable).first,
    );
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

  testWidgets('blocks account login when legal consent is unchecked',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});

    await tester.pumpWidget(const ProviderScopeWrapper());
    await tester.pumpAndSettle();

    await tester.drag(
      find.byKey(const ValueKey('login-mode-content')),
      const Offset(-420, 0),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextFormField).first, 'demo_user');
    await tester.enterText(find.byType(TextFormField).last, 'password');
    await tester.tap(find.text('登录'));
    await tester.pumpAndSettle();

    expect(find.text('请阅读并同意《隐私政策》与《用户协议》'), findsOneWidget);
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
  const ProviderScopeWrapper({
    super.key,
    this.overrides = const [],
    this.authRepository,
  });

  final List<Override> overrides;
  final AuthRepository? authRepository;

  @override
  Widget build(BuildContext context) {
    return ProviderScope(
      overrides: [
        authRepositoryProvider.overrideWithValue(
          authRepository ?? _FakeAuthRepository(),
        ),
        ...overrides,
      ],
      child: const RabbitManagerApp(),
    );
  }
}

class _FakeAuthRepository extends AuthRepository {
  _FakeAuthRepository({this.validationError})
      : super(ApiClient(SessionStore()));

  final ApiException? validationError;
  final _unauthorizedController = StreamController<void>.broadcast(sync: true);
  int validationCalls = 0;

  @override
  Stream<void> get unauthorizedEvents => _unauthorizedController.stream;

  @override
  Future<AuthSession> validateSession(AuthSession localSession) async {
    validationCalls += 1;
    final error = validationError;
    if (error != null) {
      throw error;
    }
    return localSession;
  }

  void emitUnauthorized() => _unauthorizedController.add(null);
}

class _PendingSessionStore extends SessionStore {
  _PendingSessionStore(this._snapshot);

  final Future<SessionSnapshot> _snapshot;

  @override
  Future<SessionSnapshot> readSession() => _snapshot;
}

class _RecordingHouseRepository extends HouseRepository {
  _RecordingHouseRepository() : super(ApiClient(SessionStore()));

  int calls = 0;

  @override
  Future<List<RabbitHouse>> listHouses() async {
    calls += 1;
    return const <RabbitHouse>[];
  }
}
