// 模拟器验收：NFC 碰一下的「采集窗口」路径（换笼时碰目标笼位选中它）。
//
// 和 emulator_tap_android_test.dart 的分工：
//
//   * 那个用例覆盖**启动/回前台**路径——没有采集窗口时碰标签，App 跳进该笼位详情。
//   * 本用例覆盖**采集窗口**路径——换笼弹层开着、按下「碰一下目标笼位」之后碰标签，
//     事件必须被 NfcCagePicker 自己消费，把目标笼选中，而不是把弹层顶掉。
//
// 采集窗口在模拟器上一直跑不起来，卡点只有一处：`_NfcCaptureState.startCapture`
// 订阅之前先问 `NfcHardwareService.isAvailable()`，而 AVD 没有 android.hardware.nfc，
// 这一问必然 false，窗口开都开不起来，注入进去也没人听。
//
// 绕开它不需要动 lib/。`main.dart` 的 ProviderScope 是 const 且没有 overrides，
// 所以 `app.main()` 定制不了；但集成测试本来就不必调 `app.main()`——它自己把那三行
// 抄一遍，带上 `overrides:` 即可。这里只覆盖 `nfcHardwareServiceProvider` 一个 provider，
// 让它报告「有 NFC」。标签事件本身来自 MethodChannel，脚本用 adb 从 native 侧注入，
// 和真卡贴上来时是同一行代码。换句话说：被替换掉的只有那一次能力查询，
// 解析、校签、兔舍归属、笼位匹配、选中全是真的。
//
// 注入的 payload 从 GET /api/nfc/cages/write-queue 取回，带**后端真实签名**，
// HMAC 客户端伪造不出来。
//
// 反证开关：把 --dart-define=RABBIT_E2E_NFC_FORCE_HARDWARE=false 传进来，
// 就退回没有 override 的原样，用例应当卡在「设备不支持NFC」而不是绿掉。
// 这条是这个用例存在的前提——它必须能红。

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/app.dart';
import 'package:rabbit_flutter/src/config/app.dart';
import 'package:rabbit_flutter/src/data/services/nfc/hardware.dart';

const _runId = String.fromEnvironment('RABBIT_E2E_RUN_ID');
const _password = String.fromEnvironment(
  'RABBIT_E2E_PASSWORD',
  defaultValue: '123456',
);
const _houseId = int.fromEnvironment('RABBIT_E2E_HOUSE_ID');
const _firstCageId = int.fromEnvironment('RABBIT_E2E_FIRST_CAGE_ID');

/// 被搬的兔：fixture 的后备兔，初始在 1-2-1。
/// 选后备兔是因为它进空笼、进已占用的非商品兔笼都放行，目标笼怎么排都能选中。
const _reserveRabbitId = int.fromEnvironment('RABBIT_E2E_RESERVE_RABBIT_ID');

/// 被碰的那个笼位号；fixture 把标签绑在 1-5-1 上，且该笼是空笼。
const _targetCageNumber = String.fromEnvironment(
  'RABBIT_E2E_NFC_CAGE_NUMBER',
  defaultValue: '1-5-1',
);

/// 是否覆盖硬件可用性。默认 true；置 false 用来验证这个用例真的会红。
const _forceHardware = bool.fromEnvironment(
  'RABBIT_E2E_NFC_FORCE_HARDWARE',
  defaultValue: true,
);

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

/// 后备兔所在的笼（1-2-1）：fixture 六个笼位是一条 INSERT 连号插入的。
int get _sourceCageId => _firstCageId + 1;

/// 采集窗口按下之后的等待文案，`NfcCagePicker.waitingHint` 的默认值。
/// 看到它，就说明 isAvailable() 返回了 true 且已经订阅上了。
const _waitingHint = '请将手机靠近目标笼位的 NFC 标签';

/// isAvailable() 为 false 时的文案，`_MoveCageSheet` 用的是默认 unavailableLabel。
const _unavailableHint = '设备不支持NFC或NFC未开启，请改用下方地图或列表选择';

/// 只把「有没有 NFC」这一问答改掉，别的什么都不动。
///
/// 写卡路径（writePayload）没有被这个子类接管，它照样会去问真实的 NfcManager；
/// 本用例不写卡，所以不需要，也不应该，替换更多东西——替换得越多，
/// 跑绿了能说明的事情就越少。
class _AvailableNfcHardware extends NfcHardwareService {
  @override
  Future<bool> isAvailable() async => true;
}

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Emulator NFC tap inside a capture window selects the scanned cage',
    (tester) async {
      _assertFixtureDefines();
      _ensurePhysicalTestView(tester);
      debugPrint('[nfc-e2e] clear local state');
      await _clearLocalAppState();
      debugPrint('[nfc-e2e] start app (forceHardware=$_forceHardware)');
      await _startApp();
      await binding.convertFlutterSurfaceToImage();

      await _waitFor(tester, find.byKey(const ValueKey('login-mode-selector')));
      await _login(tester, _controlUser);
      debugPrint('[nfc-e2e] login complete');
      await _openHouseDetail(tester);
      await _enterCages(tester);
      await _openSourceCage(tester);
      await _openRabbitDetail(tester, _reserveRabbitId);
      await _openMoveSheet(tester, _reserveRabbitId);
      await _takeScreenshot(binding, tester, '00-move-sheet');

      // 按下采集按钮。这一按之后窗口要么开起来（提示变成等待文案），
      // 要么当场被 isAvailable() 拦下（提示变成不支持文案）。
      await tester.tap(find.byKey(const ValueKey('nfc-cage-picker-button')));
      await _pumpUntilSettled(tester);
      final hint = _pickerHint(tester);
      debugPrint('[nfc-e2e] picker hint after tap: $hint');
      expect(
        hint,
        _waitingHint,
        reason: hint == _unavailableHint
            ? '采集窗口被 isAvailable() 拦下了：nfcHardwareServiceProvider 的 override 没有生效'
            : '按下采集按钮后应当进入等待碰标签状态',
      );
      await _takeScreenshot(binding, tester, '01-capture-listening');

      // 脚本在 logcat 里等这一行才开始发 intent。
      // 采集窗口收到第一条事件就会自己关掉，所以必须等窗口真的开着再注入，
      // 早一步注入会被 app.dart 的默认处理接走，直接把弹层顶成笼位详情页。
      debugPrint('[nfc-e2e] incapture-ready-for-injection');

      // 注入由脚本从设备外部发起（adb am start），这里只等结果。
      await _waitFor(
        tester,
        find.textContaining('已选中 $_targetCageNumber'),
        timeout: const Duration(seconds: _injectionTimeoutSeconds),
      );
      // 先把落地行打出去，脚本看到它才停止补发。
      // 补发一次就会越过已经关闭的采集窗口，被 app.dart 接走跳页，
      // 所以这一行要抢在截图之前打。
      debugPrint('[nfc-e2e] injected tap selected $_targetCageNumber');

      // 断言落在「目标笼真的被选中了」，不是「提示语出现过」。
      // 提示是 picker 自己写的字，底部这一行读的是弹层的 _selectedCageId，
      // 也就是点确认时真正会提交上去的那个笼位。
      final selection = _selectionSummary(tester);
      debugPrint('[nfc-e2e] selection summary: $selection');
      expect(
        selection,
        contains(_targetCageNumber),
        reason: '碰标签应当把 $_targetCageNumber 选成换笼目标',
      );
      expect(
        selection,
        isNot(contains('尚未选择目标笼位')),
        reason: '碰标签之后不能还停在未选择状态',
      );
      // 弹层还在，说明事件确实被采集窗口消费了，没被默认跳转顶掉。
      expect(
        find.byKey(const ValueKey('rabbit-move-cage-submit')),
        findsOneWidget,
        reason: '采集窗口内的碰标签不应该把换笼弹层顶掉',
      );
      await _takeScreenshot(binding, tester, '02-nfc-target-selected');

      binding.reportData ??= <String, dynamic>{};
      binding.reportData!.addAll({
        'runId': _runId,
        'houseId': _houseId,
        'movedRabbitId': _reserveRabbitId,
        'sourceCageId': _sourceCageId,
        'nfcTargetCageNumber': _targetCageNumber,
        'injectionChannel': 'native:DEBUG_NFC_TAG',
        'capturePath': 'NfcCagePicker',
        'hardwareOverride': _forceHardware,
      });
    },
    timeout: const Timeout(Duration(seconds: _injectionTimeoutSeconds + 300)),
  );
}

/// `main.dart` 的三行照抄，只多一个 override。
///
/// 不调 `app.main()` 是因为它的 ProviderScope 是 const 且没有 overrides，
/// 从外面没有任何注入点。抄这三行不算复制业务逻辑：它们是启动装配，
/// 真要变了（例如多一个 await），analyze 不会提醒，但这个用例会当场跑不起来。
Future<void> _startApp() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppConfig.load();
  runApp(
    ProviderScope(
      overrides: [
        if (_forceHardware)
          nfcHardwareServiceProvider.overrideWithValue(_AvailableNfcHardware()),
      ],
      child: const RabbitManagerApp(),
    ),
  );
}

void _assertFixtureDefines() {
  expect(_runId, isNotEmpty, reason: 'RABBIT_E2E_RUN_ID is required');
  expect(_houseId, greaterThan(0), reason: 'RABBIT_E2E_HOUSE_ID is required');
  expect(_firstCageId, greaterThan(0),
      reason: 'RABBIT_E2E_FIRST_CAGE_ID is required');
  expect(_reserveRabbitId, greaterThan(0),
      reason: 'RABBIT_E2E_RESERVE_RABBIT_ID is required');
}

String? _pickerHint(WidgetTester tester) {
  final finder = find.byKey(const ValueKey('nfc-cage-picker-hint'));
  if (finder.evaluate().isEmpty) {
    return null;
  }
  return tester.widget<Text>(finder).data;
}

String _selectionSummary(WidgetTester tester) {
  final finder = find.byKey(const ValueKey('rabbit-move-cage-selection'));
  expect(finder, findsOneWidget, reason: '换笼弹层应当常驻一行选中说明');
  return tester.widget<Text>(finder).data ?? '';
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

/// 打开后备兔所在的笼（1-2-1）。
///
/// 地图格子上不写笼位编号，所以按 id 点；fixture 的笼位 id 连号。
Future<void> _openSourceCage(WidgetTester tester) async {
  final cell = find.byKey(ValueKey('cage-map-cell-$_sourceCageId'));
  final scrollable = find.byKey(const ValueKey('house-cage-list-scroll'));
  await _scrollUntilPresent(tester, cell, scrollable: scrollable);
  await tester.ensureVisible(cell);
  await tester.pumpAndSettle();
  ScaffoldMessenger.maybeOf(tester.element(cell))?.removeCurrentSnackBar();
  await tester.pump();
  // 底部那一段被操作条压着，点下去会落在按钮上。先把格子顶上来。
  final logicalHeight =
      tester.view.physicalSize.height / tester.view.devicePixelRatio;
  final safeCenterY = logicalHeight - 320;
  final centerY = tester.getRect(cell).center.dy;
  if (centerY > safeCenterY) {
    await tester.drag(scrollable, Offset(0, -(centerY - safeCenterY + 24)));
    await tester.pumpAndSettle();
  }
  await tester.tap(cell);
  await _waitFor(tester, find.byKey(const ValueKey('cage-detail-back-button')));
  await _pumpUntilSettled(tester);
}

Future<void> _openRabbitDetail(WidgetTester tester, int rabbitId) async {
  final row = find.byKey(ValueKey('cage-rabbit-row-$rabbitId'));
  await _scrollUntilPresent(
    tester,
    row,
    scrollable: find.byKey(const ValueKey('cage-detail-scroll')),
  );
  await tester.ensureVisible(row);
  await tester.pumpAndSettle();
  await tester.tap(row);
  await _waitFor(
    tester,
    find.byKey(const ValueKey('rabbit-detail-page-content')),
  );
  expect(find.text('兔 #$rabbitId'), findsOneWidget);
}

Future<void> _openMoveSheet(WidgetTester tester, int rabbitId) async {
  final move = find.byKey(ValueKey('rabbit-detail-move-$rabbitId'));
  await _scrollUntilPresent(tester, move);
  await tester.ensureVisible(move);
  await tester.pumpAndSettle();
  await tester.tap(move);
  await _pumpUntilSettled(tester);
  await _waitFor(tester, find.byKey(const ValueKey('rabbit-move-cage-nfc')));
  await _waitFor(tester, find.byKey(const ValueKey('nfc-cage-picker-button')));
}

/// 拖动目标取 [Scrollable] 的 last 而不是 first：兔舍详情页上沿有横向滚动区，
/// `.first` 会选中它，纵向拖它永远不动。
Future<void> _scrollUntilPresent(
  WidgetTester tester,
  Finder finder, {
  Finder? scrollable,
  int maxDrags = 24,
}) async {
  for (var attempt = 0; attempt < maxDrags; attempt++) {
    if (finder.evaluate().isNotEmpty) return;
    final candidates = scrollable ?? find.byType(Scrollable);
    // 数据未到时一个 Scrollable 也没有，而 `.last` 遇空会直接抛。
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
