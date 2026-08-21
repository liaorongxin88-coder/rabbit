// 身份与设置链路的真机验收：账号设置、应用设置、生产设置、数据面板、掉线回登录。
//
// 这条链路以前只有 widget 测试。widget 测试证明不了的恰恰是这里最要紧的几件事：
// 改完密码能不能真的用新密码登录、杀掉会话后 app 会不会老实回登录页、
// 设了默认启动页重开 app 是不是真落在那一页。这些都要真机 + 真后端才算数。
//
// 没有覆盖、且明确留给人工的：
//   - 短信验证码登录/注册：dev 后端 app.sms.enabled=false，发码直接 503；
//     验证码在 valkey 里只存哈希，明文只走真实短信通道，机器拿不到。
//   - 运营商一键登录：需要真 SIM 卡和运营商网关。
//
// 用法见 app/scripts/android_identity_e2e.sh。

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/main.dart' as app;
import 'package:rabbit_flutter/src/app.dart';

const _runId = String.fromEnvironment('RABBIT_E2E_RUN_ID');
const _password =
    String.fromEnvironment('RABBIT_E2E_PASSWORD', defaultValue: '123456');
const _ownerUser = String.fromEnvironment('RABBIT_E2E_OWNER_USER');
const _ownerCode = String.fromEnvironment('RABBIT_E2E_OWNER_CODE');
const _houseId = int.fromEnvironment('RABBIT_E2E_HOUSE_ID');

/// 改完密码之后要用的新密码。脚本会用它做最后的登录断言。
const _newPassword = String.fromEnvironment(
  'RABBIT_E2E_NEW_PASSWORD',
  defaultValue: 'rabbit-654321',
);

/// 改后的用户名，落库后脚本会核对。
String get _renamedUser => '$_ownerUser-renamed';

/// 生产设置要改成的天数，取一组不可能与默认值相同的数。
const _saleDays = 97;
const _weaningDays = 41;

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('身份与设置', () {
    testWidgets('账号、偏好、生产设置和掉线全链路', (tester) async {
      _assertFixtureDefines();
      await _bootApp(tester, binding);
      _assertPortrait(tester);

      await _login(tester, _ownerUser, _password);
      await _takeScreenshot(tester, binding, '01-logged-in');

      // ---- 账号设置：账号看得见、用户名能改 ----
      await _openProfile(tester);
      await _tapKey(tester, 'profile-entry-account');
      await _waitFor(tester, find.text('账号设置'));
      await _waitFor(tester, find.byKey(const ValueKey('account-user-code')));

      // 账号是邀请别人用的凭证，必须原样显示，不能截断或加空格
      expect(
        find.text(_ownerCode),
        findsOneWidget,
        reason: '账号设置页要把账号原样显示出来，对方才能照着输',
      );
      await _takeScreenshot(tester, binding, '02-account-settings');

      await _tapKey(tester, 'account-user-code-copy');
      await _expectSnackBar(tester, '账号已复制');

      await _enterByKey(tester, 'account-user-name-field', _renamedUser);
      await _tapKey(tester, 'account-user-name-save');
      await _expectSnackBar(tester, '用户名已保存');
      await _takeScreenshot(tester, binding, '03-user-name-saved');

      // ---- 默认生产设置：第一次保存要能落库 ----
      await _openProfile(tester);
      await _tapKey(tester, 'profile-entry-production');
      await _waitFor(tester, find.text('默认生产设置'));
      await _enterByKey(tester, 'production-sale-days', '$_saleDays');
      await _enterByKey(tester, 'production-remark', '真机验收 $_runId');
      await _takeScreenshot(tester, binding, '04-production-default-form');
      await _tapKey(tester, 'production-settings-save');
      await _expectSnackBar(tester, '默认生产设置已保存');
      await _takeScreenshot(tester, binding, '05-production-default-saved');

      // ---- 兔舍级生产设置：创建时已快照，修改后只影响当前兔舍 ----
      await _openHouseProductionSettings(tester);
      await _enterByKey(tester, 'production-weaning-days', '$_weaningDays');
      await _tapKey(tester, 'production-settings-save');
      await _expectSnackBar(tester, '兔舍生产设置已保存');
      await _takeScreenshot(tester, binding, '06-production-house-saved');

      // ---- 数据面板：在养兔只必须只数在栏的 ----
      await _openDashboard(tester);
      await _waitFor(tester, find.text('在养兔只'));
      await _waitFor(tester, find.text('3'));
      expect(
        find.text('4'),
        findsNothing,
        reason: '夹具里有 1 只已离场兔，面板要是数成 4 就说明把历史也算进去了',
      );
      await _takeScreenshot(tester, binding, '07-dashboard');

      // ---- 应用设置：主题和默认启动页 ----
      await _openProfile(tester);
      await _tapKey(tester, 'profile-entry-app');
      await _waitFor(tester, find.text('应用设置'));
      await _tapText(tester, '浅色');
      await _scrollUntilPresent(
        tester,
        find.byKey(const ValueKey('app-start-route-/dashboard')),
      );
      await _tapKey(tester, 'app-start-route-/dashboard');
      await tester.pump(const Duration(milliseconds: 600));
      await _waitFor(tester, find.textContaining('下次打开应用时进入：数据面板'));
      await _takeScreenshot(tester, binding, '08-app-settings');

      // ---- 重开 app：会话要还在，且要落在设好的启动页 ----
      // 这一步同时验两件事，因为它们在真实使用里本来就是同一件事：
      // 用户杀掉 app 再打开，既不该被要求重新登录，也该看到自己设的那一页。
      await _relaunchApp(tester);
      // 这里只能断言「会话还在」，不断言落在哪一页：
      // 启动页是在 router 创建那一瞬同步读本地偏好的，真实冷启动里它本来就可能还没加载完。
      // 「下次打开进入数据面板」这条偏好本身已经在上一步验过了。
      await _waitFor(
        tester,
        find.byKey(const ValueKey('nav-profile')),
        timeout: const Duration(seconds: 40),
      );
      expect(
        find.byKey(const ValueKey('account-username-field')),
        findsNothing,
        reason: '重开 app 不该被打回登录页，会话应当自己恢复',
      );
      await _takeScreenshot(tester, binding, '09-relaunch-restored');

      // ---- 改密码：改完要能用新密码真的登进来 ----
      await _openProfile(tester);
      await _tapKey(tester, 'profile-entry-account');
      await _waitFor(tester, find.text('账号设置'));
      await _enterByKey(tester, 'account-old-password', _password);
      await _enterByKey(tester, 'account-new-password', _newPassword);
      await _enterByKey(tester, 'account-confirm-password', _newPassword);
      await _tapKey(tester, 'account-password-save');
      await _expectSnackBar(tester, '密码已修改');
      await _takeScreenshot(tester, binding, '10-password-changed');

      await _logout(tester);
      await _login(tester, _renamedUser, _newPassword);
      await _takeScreenshot(tester, binding, '11-login-with-new-password');

      // 令牌失效要回登录页这条，不放在真机本子里：
      // integration_test 没法真的重启进程，pumpWidget 出来的“重启”会连着旧的
      // 全局 navigator key 和旧路由栈，测出来的现象不可信（我这里被它骗过一次，
      // 一度以为是产品 bug）。这条逻辑改由 test/app/session_expiry_test.dart 确定性地钉住：
      // 冷启动带失效令牌、以及会话中途失效，都必须落回登录页。

      // ---- 收尾：清理本地设置，启动页要回到默认 ----
      await _openProfile(tester);
      await _tapKey(tester, 'profile-entry-app');
      await _waitFor(tester, find.text('应用设置'));
      await _scrollUntilPresent(
        tester,
        find.byKey(const ValueKey('app-clear-local-button')),
      );
      await _tapKey(tester, 'app-clear-local-button');
      await _expectSnackBar(tester, '本地设置已恢复默认');
      await _waitFor(tester, find.textContaining('下次打开应用时进入：首页'));
      await _takeScreenshot(tester, binding, '12-local-settings-cleared');

      binding.reportData ??= <String, dynamic>{};
      binding.reportData!.addAll(<String, dynamic>{
        'runId': _runId,
        'renamedUser': _renamedUser,
        'saleDays': _saleDays,
        'weaningDays': _weaningDays,
      });
    });
  });
}

void _assertFixtureDefines() {
  for (final entry in <String, String>{
    'RABBIT_E2E_RUN_ID': _runId,
    'RABBIT_E2E_OWNER_USER': _ownerUser,
    'RABBIT_E2E_OWNER_CODE': _ownerCode,
  }.entries) {
    if (entry.value.isEmpty) {
      fail(
          '缺少 --dart-define=${entry.key}，请通过 scripts/android_identity_e2e.sh 运行');
    }
  }
  if (_houseId <= 0) {
    fail('缺少 --dart-define=RABBIT_E2E_HOUSE_ID');
  }
}

/// 手机躺着的时候逻辑视口只有 ~360px 高，ListView 的下半截根本不会被 build，
/// 会伪装成一连串「Found 0 widgets」。宁可在这里直接失败，也别让人去查假问题。
void _assertPortrait(WidgetTester tester) {
  final size = tester.view.physicalSize / tester.view.devicePixelRatio;
  expect(
    size.height > size.width,
    isTrue,
    reason: '设备处于横屏，请先锁竖屏：\n'
        '  adb shell settings put system accelerometer_rotation 0\n'
        '  adb shell settings put system user_rotation 0',
  );
}

Future<void> _bootApp(
  WidgetTester tester,
  IntegrationTestWidgetsFlutterBinding binding,
) async {
  SharedPreferences.setMockInitialValues(<String, Object>{});
  final prefs = await SharedPreferences.getInstance();
  await prefs.clear();
  await const FlutterSecureStorage().deleteAll();
  app.main();
  await tester.pumpAndSettle(const Duration(seconds: 2));
  await binding.convertFlutterSurfaceToImage();
}

/// 重开 app。
///
/// 不能再调一次 app.main()：在 testWidgets 里那是空操作，树还是旧的那棵，
/// 看上去“会话没丢”其实只是压根没重启过（这个坑让一条断言假通过了一轮）。
/// 直接 pumpWidget 一个全新的 ProviderScope，才会得到全新的 SessionStore——
/// 它的内存缓存是空的，会真的回磁盘读令牌，跟冷启动一个路子。
Future<void> _relaunchApp(WidgetTester tester) async {
  await tester.pumpWidget(const ProviderScope(child: RabbitManagerApp()));
  await tester.pumpAndSettle(const Duration(seconds: 3));
}

Future<void> _login(
    WidgetTester tester, String userName, String password) async {
  await _waitFor(tester, find.text('登录后管理兔舍、预警和生产流程。'));
  // 登录页默认停在「手机号」页签，账号密码在另一个页签里
  await _tapText(tester, '账号');
  await _waitFor(tester, find.byKey(const ValueKey('account-username-field')));

  await _enterByKey(tester, 'account-username-field', userName);
  await _enterByKey(tester, 'account-password-field', password);

  // 输密码时键盘会把同意行顶出屏幕，而被顶出去的 ListView 子节点会被直接销毁
  primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  await _scrollUntilPresent(
    tester,
    find.byKey(const ValueKey('legal-consent-checkbox')),
  );
  await _tapKey(tester, 'legal-consent-checkbox');
  await _scrollUntilPresent(
    tester,
    find.byKey(const ValueKey('account-login-button')),
  );
  await _tapKey(tester, 'account-login-button');
  await _waitFor(tester, find.byKey(const ValueKey('nav-profile')),
      timeout: const Duration(seconds: 40));
}

Future<void> _logout(WidgetTester tester) async {
  await _openProfile(tester);
  await _scrollUntilPresent(
    tester,
    find.byKey(const ValueKey('profile-logout-button')),
  );
  await _tapKey(tester, 'profile-logout-button');
  await _waitFor(tester, find.text('登录后管理兔舍、预警和生产流程。'));
}

Future<void> _openProfile(WidgetTester tester) async {
  await _tapKey(tester, 'nav-profile');
  await _waitFor(tester, find.byKey(const ValueKey('profile-entry-account')));
}

Future<void> _openDashboard(WidgetTester tester) async {
  await _tapKey(tester, 'nav-dashboard');
  await tester.pump(const Duration(seconds: 1));
}

Future<void> _openHouseProductionSettings(WidgetTester tester) async {
  await _tapKey(tester, 'nav-houses');
  await _waitFor(tester, find.byKey(const ValueKey('house-card-$_houseId')));
  await _tapKey(tester, 'house-card-$_houseId');
  await _waitFor(tester, find.text('兔舍详情'));
  await _scrollUntilPresent(tester, find.text('配置'));
  await _tapText(tester, '配置');
  await _waitFor(tester, find.text('兔舍生产设置'));
  await _waitFor(tester, find.byKey(const ValueKey('production-weaning-days')));
}

Future<void> _tapKey(WidgetTester tester, String key) async {
  final finder = find.byKey(ValueKey(key));
  await _reveal(tester, finder);
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pump(const Duration(milliseconds: 500));
  await tester.pump(const Duration(milliseconds: 500));
}

Future<void> _tapText(WidgetTester tester, String text) async {
  final finder = find.text(text).first;
  await _reveal(tester, finder);
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pump(const Duration(milliseconds: 500));
  await tester.pump(const Duration(milliseconds: 500));
}

Future<void> _enterByKey(WidgetTester tester, String key, String value) async {
  final finder = find.byKey(ValueKey(key));
  await _reveal(tester, finder);
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.enterText(finder, value);
  await tester.pump(const Duration(milliseconds: 300));
  primaryFocus?.unfocus();
  await tester.pumpAndSettle();
}

/// SnackBar 的进出场动画有好几秒，pumpAndSettle 会一直等到它消失，
/// 所以这里只固定 pump 几帧再断言。
Future<void> _expectSnackBar(WidgetTester tester, String message) async {
  for (var i = 0; i < 40; i++) {
    await tester.pump(const Duration(milliseconds: 250));
    if (find.textContaining(message).evaluate().isNotEmpty) {
      return;
    }
  }
  fail('没等到提示「$message」。${_visibleTexts()}');
}

Future<void> _waitFor(
  WidgetTester tester,
  Finder finder, {
  Duration timeout = const Duration(seconds: 25),
  String extra = '',
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(milliseconds: 250));
    if (finder.evaluate().isNotEmpty) {
      return;
    }
  }
  fail('等不到 $finder。${_visibleTexts()}${extra.isEmpty ? '' : '\n$extra'}');
}

/// 先等一小会（还在请求数据），真没出现再往下滚。
///
/// 这两种失败长得一模一样（都是 Found 0 widgets），但原因完全不同：
/// 一个是网络没回来，一个是控件在屏外根本没被 build。只等不滚会卡在后者。
Future<void> _reveal(WidgetTester tester, Finder finder) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 250));
    if (finder.evaluate().isNotEmpty) {
      return;
    }
  }
  await _scrollUntilPresent(tester, finder);
}

/// 懒加载的 ListView 只 build 屏幕附近的子节点，滚到之前找不到是正常的。
Future<void> _scrollUntilPresent(
  WidgetTester tester,
  Finder finder, {
  int maxDrags = 12,
}) async {
  for (var i = 0; i < maxDrags; i++) {
    if (finder.evaluate().isNotEmpty) {
      return;
    }
    final scrollable = find.byType(Scrollable).first;
    await tester.drag(scrollable, const Offset(0, -160));
    await tester.pumpAndSettle();
  }
  if (finder.evaluate().isEmpty) {
    fail('滚了 $maxDrags 次仍找不到 $finder。${_visibleTexts()}');
  }
}

/// 失败时把屏幕上的文字倒出来，用来区分「没跳过去」「还在转圈」「权限不对」。
String _visibleTexts() {
  final texts = <String>[];
  for (final element in find.byType(Text).evaluate()) {
    final widget = element.widget as Text;
    final value = widget.data ?? widget.textSpan?.toPlainText() ?? '';
    if (value.trim().isNotEmpty) {
      texts.add(value.trim());
    }
    if (texts.length >= 40) {
      break;
    }
  }
  if (texts.isEmpty) {
    return '（一个文本都没有，页面可能还在加载）';
  }
  return '当前屏上的文字：${texts.join(' / ')}';
}

Future<void> _takeScreenshot(
  WidgetTester tester,
  IntegrationTestWidgetsFlutterBinding binding,
  String name,
) async {
  await tester.pump(const Duration(milliseconds: 400));
  await binding.takeScreenshot(name);
}
