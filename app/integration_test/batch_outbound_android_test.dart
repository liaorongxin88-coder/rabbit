import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/main.dart' as app;

const _runId = String.fromEnvironment('RABBIT_E2E_RUN_ID');
const _password = String.fromEnvironment(
  'RABBIT_E2E_PASSWORD',
  defaultValue: '123456',
);
const _primaryHouseId = int.fromEnvironment('RABBIT_E2E_PRIMARY_HOUSE_ID');
const _g01RabbitId = int.fromEnvironment('RABBIT_E2E_G01_RABBIT_ID');
const _apiBaseUrl = String.fromEnvironment(
  'RABBIT_API_BASE_URL',
  defaultValue: 'http://10.0.2.2:8080',
);
const _expectedTextScaleValue = String.fromEnvironment(
  'RABBIT_E2E_EXPECTED_TEXT_SCALE',
  defaultValue: '1',
);
const _expectedEffectiveTextScaleValue = String.fromEnvironment(
  'RABBIT_E2E_EXPECTED_EFFECTIVE_TEXT_SCALE',
  defaultValue: '1',
);

String get _controlUser => 'outbound_fixture_${_runId}_control';
String get _viewUser => 'outbound_fixture_${_runId}_view';
String get _concurrentUser => 'outbound_fixture_${_runId}_concurrent';
String get _primaryHouseName => 'H-GOLDEN-$_runId';
double get _expectedTextScale => double.parse(_expectedTextScaleValue);
double get _expectedEffectiveTextScale =>
    double.parse(_expectedEffectiveTextScaleValue);

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Android real-backend batch outbound permission, ergonomics, conflict and submit flow',
    (tester) async {
      _assertFixtureDefines();
      await _clearLocalAppState();
      await app.main();
      await binding.convertFlutterSurfaceToImage();

      await _waitFor(tester, find.byKey(const ValueKey('login-mode-selector')));
      await _takeScreenshot(binding, tester, '01-login');

      await _login(tester, _viewUser);
      await _openPrimaryHouse(tester);
      await tester.tap(find.text('进入笼位'));
      // 笼位区默认是分层地图，格子上不写笼位编号，所以认 key 不认文字。
      await _waitFor(tester, find.byKey(const ValueKey('cage-map')));
      expect(find.byKey(const ValueKey('house-outbound-action')), findsNothing);
      expect(find.byTooltip('整舍批量出库'), findsNothing);
      await _takeScreenshot(binding, tester, '02-view-permission');

      await tester.tap(find.byKey(const ValueKey('nav-profile')));
      await _waitFor(tester, find.text(_viewUser));
      await tester.tap(find.text('退出登录'));
      await _waitFor(tester, find.byKey(const ValueKey('login-mode-selector')));

      await _login(tester, _controlUser);
      await _openPrimaryHouse(tester);
      await tester.tap(find.text('进入笼位'));
      await _waitFor(
          tester, find.byKey(const ValueKey('house-outbound-action')));
      await tester.tap(find.byKey(const ValueKey('house-outbound-action')));

      await _waitFor(tester, find.text('正常可出库'));
      _expectSummaryMetric(
        tester,
        const ValueKey('outbound-summary-normal'),
        label: '正常可出库',
        value: 2,
      );
      _expectSummaryMetric(
        tester,
        const ValueKey('outbound-summary-early-sale'),
        label: '可提前出售',
        value: 1,
      );
      _expectSummaryMetric(
        tester,
        const ValueKey('outbound-summary-blocked'),
        label: '不可批量选择',
        value: 6,
      );
      expect(find.text('下一步 · 2 只'), findsOneWidget);
      _expectReachablePrimaryAction(
        tester,
        find.byKey(const ValueKey('outbound-continue-button')),
      );
      _expectAndroidViewport(tester);
      _expectTextScale(tester);
      await _takeScreenshot(binding, tester, '03-selection');

      await tester.tap(
        find.byKey(const ValueKey('outbound-summary-early-sale')),
      );
      final earlySaleCage = find.text('R1-C2-L1');
      await _waitFor(tester, earlySaleCage);
      await tester.ensureVisible(earlySaleCage);
      await tester.pumpAndSettle();
      await tester.tap(earlySaleCage);
      final rabbitAction = find.byTooltip('兔只操作');
      await _waitFor(tester, rabbitAction);
      await tester.tap(rabbitAction);
      await _waitFor(tester, find.text('提前出售'));
      await tester.tap(find.text('提前出售'));
      await _waitFor(
        tester,
        find.byKey(const ValueKey('outbound-early-sale-reason')),
      );
      await tester.enterText(
        find.byKey(const ValueKey('outbound-early-sale-reason')),
        'Android E2E 客户提前采购',
      );
      await tester.tap(
        find.byKey(const ValueKey('outbound-early-sale-confirm')),
      );
      await _waitFor(tester, find.text('提前出售 · Android E2E 客户提前采购'));
      await tester.tap(find.byTooltip('关闭'));
      await _waitFor(tester, find.text('下一步 · 3 只'));
      await _takeScreenshot(binding, tester, '04-early-sale-selected');

      await tester.tap(find.byKey(const ValueKey('outbound-continue-button')));
      await _waitFor(tester, find.text('冻结清单 3 只'));
      expect(find.text('正常 2 · 提前出售 1 · 3 笼 · 2 排'), findsOneWidget);

      await _enterField(
        tester,
        const ValueKey('outbound-total-weight'),
        '6.4',
      );
      await _enterField(
        tester,
        const ValueKey('outbound-unit-price'),
        '20',
      );
      await _enterField(
        tester,
        const ValueKey('outbound-customer'),
        'Android 联调客户',
      );
      await _enterField(
        tester,
        const ValueKey('outbound-remark'),
        '设备级自动化：冲突后继续提交',
      );
      final submitButton = find.byKey(const ValueKey('outbound-submit-button'));
      _expectReachablePrimaryAction(tester, submitButton);
      FocusManager.instance.primaryFocus?.unfocus();
      await tester.pumpAndSettle(const Duration(milliseconds: 300));
      await _takeScreenshot(binding, tester, '05-confirmation');

      await _quarantineRabbit(
        userName: _concurrentUser,
        rabbitId: _g01RabbitId,
      );
      await tester.ensureVisible(submitButton);
      expect(tester.widget<FilledButton>(submitButton).onPressed, isNotNull);
      final hittableSubmitButton = submitButton.hitTestable();
      expect(hittableSubmitButton, findsOneWidget);
      await tester.tap(hittableSubmitButton);
      await tester.pump();
      final conflictTitle = find.text('1 只兔状态冲突');
      await _waitFor(tester, conflictTitle);
      await tester.pumpAndSettle();
      _expectVisibleInViewport(tester, conflictTitle);
      expect(find.textContaining('#$_g01RabbitId'), findsWidgets);
      expect(find.text('移除冲突兔只 1 只'), findsOneWidget);
      await _takeScreenshot(binding, tester, '06-conflict');

      await tester.tap(find.text('移除冲突兔只 1 只'));
      await _waitFor(tester, find.text('确认出库 2 只'));
      expect(find.text('冻结清单 2 只'), findsOneWidget);
      expect(find.text('Android 联调客户'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('outbound-submit-button')));
      await _waitFor(tester, find.text('出库完成'),
          timeout: const Duration(seconds: 30));
      expect(find.text('兔只数量'), findsOneWidget);
      expect(find.text('空间范围'), findsOneWidget);
      expect(find.text('总重量'), findsOneWidget);
      await _takeScreenshot(binding, tester, '07-success');

      binding.reportData ??= <String, dynamic>{};
      binding.reportData!.addAll({
        'runId': _runId,
        'primaryHouseId': _primaryHouseId,
        'conflictedRabbitId': _g01RabbitId,
        'expectedSoldRabbitCount': 2,
        'systemTextScale': _systemTextScale(tester),
        'effectiveTextScale': _currentTextScale(tester),
        'screenshotTextScale': _currentTextScale(tester),
        'logicalSize': _logicalSize(tester).toString(),
      });
    },
  );
}

void _assertFixtureDefines() {
  expect(_runId, isNotEmpty, reason: 'RABBIT_E2E_RUN_ID is required');
  expect(_primaryHouseId, greaterThan(0),
      reason: 'RABBIT_E2E_PRIMARY_HOUSE_ID is required');
  expect(_g01RabbitId, greaterThan(0),
      reason: 'RABBIT_E2E_G01_RABBIT_ID is required');
}

Future<void> _clearLocalAppState() async {
  final preferences = await SharedPreferences.getInstance();
  await preferences.clear();
  await const FlutterSecureStorage().deleteAll();
}

Future<void> _login(WidgetTester tester, String userName) async {
  await tester.tap(find.text('账号'));
  await tester.pumpAndSettle();
  await tester.enterText(
    find.byKey(const ValueKey('account-username-field')),
    userName,
  );
  await tester.enterText(
    find.byKey(const ValueKey('account-password-field')),
    _password,
  );
  await tester.tap(find.byKey(const ValueKey('legal-consent-checkbox')));
  await tester.tap(find.byKey(const ValueKey('account-login-button')));
  await _waitFor(tester, find.text('兔舍'));
}

Future<void> _openPrimaryHouse(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('nav-houses')));
  final houseCard = find.byKey(const ValueKey('house-card-$_primaryHouseId'));
  await _waitFor(tester, houseCard);
  expect(find.text(_primaryHouseName), findsOneWidget);
  await tester.tap(houseCard);
  await _waitFor(tester, find.text('笼位管理'));
}

Future<void> _enterField(
  WidgetTester tester,
  ValueKey<String> key,
  String value,
) async {
  final finder = find.byKey(key);
  await tester.ensureVisible(finder);
  await tester.tap(finder);
  await tester.enterText(finder, value);
  await tester.pump(const Duration(milliseconds: 150));
  expect(find.byKey(const ValueKey('outbound-submit-button')), findsOneWidget);
}

Future<void> _quarantineRabbit({
  required String userName,
  required int rabbitId,
}) async {
  final dio = Dio(BaseOptions(
    baseUrl: _apiBaseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 20),
    headers: const {'Content-Type': 'application/json'},
  ));
  try {
    final login = await dio.post<Map<String, dynamic>>(
      '/api/auth/login',
      data: {'userName': userName, 'password': _password},
    );
    final loginBody = login.data ?? const <String, dynamic>{};
    expect(loginBody['code'], 0);
    final data = Map<String, dynamic>.from(loginBody['data'] as Map);
    final token = data['token'] as String;
    final response = await dio.post<Map<String, dynamic>>(
      '/api/rabbits/events',
      data: {
        'rabbitId': rabbitId,
        'eventType': 'quarantine',
        'actionDate': DateTime.now().toUtc().toIso8601String(),
        'reason': 'Android E2E 并发隔离',
        'remark': '冻结清单后的第二账号状态变更',
        'forceExitBatch': false,
        'requestId': const Uuid().v4(),
      },
      options: Options(headers: {
        'Authorization': 'Bearer $token',
        'X-House-Id': '$_primaryHouseId',
      }),
    );
    expect(response.data?['code'], 0);
  } finally {
    dio.close(force: true);
  }
}

Future<void> _waitFor(
  WidgetTester tester,
  Finder finder, {
  Duration timeout = const Duration(seconds: 20),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(milliseconds: 100));
    if (finder.evaluate().isNotEmpty) return;
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 50)),
    );
  }
  fail('Timed out waiting for $finder');
}

Future<void> _takeScreenshot(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
  String name,
) async {
  await tester.pumpAndSettle();
  _expectTextScale(tester);
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 150)),
  );
  await tester.pump();
  await binding.takeScreenshot(name);
}

void _expectVisibleInViewport(WidgetTester tester, Finder finder) {
  expect(finder, findsOneWidget);
  final rect = tester.getRect(finder);
  final viewport = _logicalSize(tester);
  expect(rect.top, greaterThanOrEqualTo(0));
  expect(rect.bottom, lessThanOrEqualTo(viewport.height));
}

void _expectReachablePrimaryAction(WidgetTester tester, Finder finder) {
  expect(finder, findsOneWidget);
  final rect = tester.getRect(finder);
  final viewport = _logicalSize(tester);
  expect(rect.height, greaterThanOrEqualTo(48));
  expect(rect.left, greaterThanOrEqualTo(0));
  expect(rect.right, lessThanOrEqualTo(viewport.width));
  expect(rect.bottom, lessThanOrEqualTo(viewport.height));
}

void _expectSummaryMetric(
  WidgetTester tester,
  ValueKey<String> key, {
  required String label,
  required int value,
}) {
  final metric = find.byKey(key);
  expect(metric, findsOneWidget);
  expect(
      find.descendant(of: metric, matching: find.text(label)), findsOneWidget);
  expect(
    find.descendant(of: metric, matching: find.text('$value')),
    findsOneWidget,
  );
}

void _expectAndroidViewport(WidgetTester tester) {
  final size = _logicalSize(tester);
  expect(size.width, inInclusiveRange(320, 600));
  expect(size.height, greaterThanOrEqualTo(640));
}

void _expectTextScale(WidgetTester tester) {
  expect(
    _systemTextScale(tester),
    closeTo(_expectedTextScale, 0.15),
  );
  expect(
    _currentTextScale(tester),
    closeTo(_expectedEffectiveTextScale, 0.15),
  );
}

double _systemTextScale(WidgetTester tester) {
  return tester.binding.platformDispatcher.textScaleFactor;
}

double _currentTextScale(WidgetTester tester) {
  final context = tester.element(find.byType(Scaffold).first);
  return MediaQuery.textScalerOf(context).scale(10) / 10;
}

Size _logicalSize(WidgetTester tester) {
  final view = tester.view;
  return view.physicalSize / view.devicePixelRatio;
}
