// 模拟器验收：NFC 碰一下的「启动/回前台」路径。
//
// 与 cages/rabbit_operations_android_test.dart 的分工要说清楚，否则很容易把
// 这个用例当成「NFC 全都测了」：
//
//   * 那个用例跑在真机上，从 Dart 侧的 defaultBinaryMessenger 直接注入
//     nfcIntent，覆盖的是**采集窗口**（换笼表单开着时碰标签选中目标笼）。
//     采集窗口在订阅之前会先问 NfcHardwareService.isAvailable()，
//     模拟器没有 android.hardware.nfc，这一问必然是 false，窗口根本开不起来。
//   * 本用例跑在模拟器上，注入点在**native**：脚本用 adb am start 发一条
//     debug-only 的 DEBUG_NFC_TAG intent，MainActivity 收下后照常调
//     channel.invokeMethod('nfcIntent', ...)。走的是 onNewIntent 这一支，
//     和真卡贴上来时完全同一行代码，只少了射频与 NDEF 解析。
//
// 所以本用例只断言一件事，但断得很实：碰一张**后端真实签名**的标签，
// App 要跳进这张标签绑定的那个笼位详情。payload 从
// GET /api/nfc/cages/write-queue 取（HMAC 客户端伪造不出来），
// 跳转过程中的解析、校签、兔舍归属判断一律照走。

import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/main.dart' as app;

const _runId = String.fromEnvironment('RABBIT_E2E_RUN_ID');
const _password = String.fromEnvironment(
  'RABBIT_E2E_PASSWORD',
  defaultValue: '123456',
);
const _houseId = int.fromEnvironment('RABBIT_E2E_HOUSE_ID');
const _firstCageId = int.fromEnvironment('RABBIT_E2E_FIRST_CAGE_ID');

/// 被碰的那个笼位号；fixture 把标签绑在 1-5-1 上。
const _targetCageNumber = String.fromEnvironment(
  'RABBIT_E2E_NFC_CAGE_NUMBER',
  defaultValue: '1-5-1',
);

/// 等注入的时长。脚本是每隔几秒补发一次 intent，不是只发一发，
/// 所以这里宽一点也不会把用例拖满——第一发到了就往下走。
const _injectionTimeoutSeconds = int.fromEnvironment(
  'RABBIT_E2E_NFC_INJECT_TIMEOUT_SECONDS',
  defaultValue: 150,
);

const _devicePhysicalWidth = int.fromEnvironment(
  'RABBIT_E2E_DEVICE_PHYSICAL_WIDTH',
);
const _devicePhysicalHeight = int.fromEnvironment(
  'RABBIT_E2E_DEVICE_PHYSICAL_HEIGHT',
);
const _devicePixelRatioValue = String.fromEnvironment(
  'RABBIT_E2E_DEVICE_PIXEL_RATIO',
);
double get _devicePixelRatio => double.tryParse(_devicePixelRatioValue) ?? 0;

String get _controlUser => 'cage_ops_fixture_${_runId}_control';
String get _houseName => 'H-CAGEOPS-$_runId';

/// fixture 的六个笼位是一条 INSERT 连号插入的；标签绑在第五个（1-5-1）上。
int get _targetCageId => _firstCageId + 4;

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Emulator NFC tap routes to the cage bound to the scanned tag',
    (tester) async {
      _assertFixtureDefines();
      _ensurePhysicalTestView(tester);
      debugPrint('[nfc-e2e] clear local state');
      await _clearLocalAppState();
      debugPrint('[nfc-e2e] start app');
      await app.main();
      await binding.convertFlutterSurfaceToImage();

      await _waitFor(tester, find.byKey(const ValueKey('login-mode-selector')));
      await _login(tester, _controlUser);
      debugPrint('[nfc-e2e] login complete');
      await _openHouseDetail(tester);
      await _enterCages(tester);
      await _takeScreenshot(binding, tester, '00-cage-grid');

      // 脚本在 logcat 里等这一行，等到了才开始发 intent。
      // 先到位再注入，省得 intent 落在登录页上被丢掉。
      debugPrint('[nfc-e2e] ready-for-injection');

      // 注入由脚本从设备外部发起（adb am start），这里只负责等结果。
      // 断言落在「跳进了标签绑定的那个笼位」，不是「收到了一条消息」。
      // 两步都要：先确认真的换到了笼位详情页（cage-detail-scroll 只在详情页上），
      // 再确认是**这一个**笼位——只看到详情页说明不了标签解析对没对。
      await _waitFor(
        tester,
        find.byKey(const ValueKey('cage-detail-scroll')),
        timeout: const Duration(seconds: _injectionTimeoutSeconds),
      );
      await _waitFor(tester, find.text(_targetCageNumber));
      expect(find.text(_targetCageNumber), findsWidgets,
          reason: '碰标签应该落在该标签绑定的笼位详情页');
      debugPrint('[nfc-e2e] injected tap landed on $_targetCageNumber');
      await _takeScreenshot(binding, tester, '01-nfc-jump-to-cage');

      binding.reportData ??= <String, dynamic>{};
      binding.reportData!.addAll({
        'runId': _runId,
        'houseId': _houseId,
        'nfcTargetCageId': _targetCageId,
        'nfcTargetCageNumber': _targetCageNumber,
        'injectionChannel': 'native:DEBUG_NFC_TAG',
      });
    },
    timeout: const Timeout(Duration(seconds: _injectionTimeoutSeconds + 240)),
  );
}

void _assertFixtureDefines() {
  expect(_runId, isNotEmpty, reason: 'RABBIT_E2E_RUN_ID is required');
  expect(_houseId, greaterThan(0), reason: 'RABBIT_E2E_HOUSE_ID is required');
  expect(_firstCageId, greaterThan(0),
      reason: 'RABBIT_E2E_FIRST_CAGE_ID is required');
}

Future<void> _clearLocalAppState() async {
  SharedPreferences.setMockInitialValues({});
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
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  final consent = find.byKey(const ValueKey('legal-consent-checkbox'));
  for (var attempt = 0; attempt < 8 && consent.evaluate().isEmpty; attempt++) {
    final scrollable = find.byType(Scrollable);
    if (scrollable.evaluate().isEmpty) break;
    await tester.drag(scrollable.first, const Offset(0, -120));
    await tester.pumpAndSettle();
  }
  await tester.ensureVisible(consent);
  await tester.tap(consent);
  await tester.pumpAndSettle();
  final loginButton = find.byKey(const ValueKey('account-login-button'));
  await tester.ensureVisible(loginButton);
  await tester.tap(loginButton);
  await _waitFor(tester, find.text('兔舍'));
}

Future<void> _openHouseDetail(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('nav-houses')));
  final houseCard = find.byKey(const ValueKey('house-card-$_houseId'));
  await _waitFor(tester, houseCard);
  expect(find.text(_houseName), findsOneWidget);
  await tester.tap(houseCard);
  await _waitFor(tester, find.text('兔舍详情'));
}

Future<void> _enterCages(WidgetTester tester) async {
  final entry = find.text('笼位管理');
  await _scrollUntilPresent(tester, entry);
  await tester.ensureVisible(entry);
  await tester.pumpAndSettle();
  await tester.tap(find.text('进入笼位'));
  await _waitFor(tester, find.byKey(const ValueKey('cage-map')));
}

/// 拖动目标取 [Scrollable] 的 last 而不是 first。
///
/// 兄弟脚本 farm_setup_android_test.dart 已经踩过这个坑：兔舍详情页上沿存在
/// 横向滚动的区域，`.first` 会选中它，纵向拖动对它没有任何效果，
/// 于是拖满次数也永远看不到「笼位管理」。
Future<void> _scrollUntilPresent(
  WidgetTester tester,
  Finder finder, {
  Finder? scrollable,
  int maxDrags = 24,
}) async {
  for (var attempt = 0; attempt < maxDrags; attempt++) {
    if (finder.evaluate().isNotEmpty) return;
    final candidates = scrollable ?? find.byType(Scrollable);
    // 详情页数据未到时一个 Scrollable 也没有，而 `.last` 遇空会直接抛。
    // 这不是「找不到目标」，只是还没渲染完，等下一帧再看。
    if (candidates.evaluate().isEmpty) {
      await tester.pump(const Duration(milliseconds: 200));
      continue;
    }
    await tester.drag(candidates.last, const Offset(0, -160));
    await tester.pump(const Duration(milliseconds: 120));
  }
  await _waitFor(tester, finder, timeout: const Duration(seconds: 10));
}

Future<void> _waitFor(
  WidgetTester tester,
  Finder finder, {
  Duration timeout = const Duration(seconds: 25),
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

Future<void> _pumpUntilSettled(WidgetTester tester) async {
  for (var attempt = 0; attempt < 60; attempt++) {
    await tester.pump(const Duration(milliseconds: 100));
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 20)),
    );
  }
}

Future<void> _takeScreenshot(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
  String name,
) async {
  await _pumpUntilSettled(tester);
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 150)),
  );
  await tester.pump();
  await binding.takeScreenshot(name);
}

void _ensurePhysicalTestView(WidgetTester tester) {
  final view = tester.view;
  if (view.physicalSize.width > 0 && view.physicalSize.height > 0) {
    return;
  }
  expect(_devicePhysicalWidth, greaterThan(0),
      reason: 'RABBIT_E2E_DEVICE_PHYSICAL_WIDTH is required');
  expect(_devicePhysicalHeight, greaterThan(0),
      reason: 'RABBIT_E2E_DEVICE_PHYSICAL_HEIGHT is required');
  expect(_devicePixelRatio, greaterThan(0),
      reason: 'RABBIT_E2E_DEVICE_PIXEL_RATIO is required');
  addTearDown(view.resetPhysicalSize);
  addTearDown(view.resetDevicePixelRatio);
  view.devicePixelRatio = _devicePixelRatio;
  view.physicalSize = Size(
    _devicePhysicalWidth.toDouble(),
    _devicePhysicalHeight.toDouble(),
  );
}
