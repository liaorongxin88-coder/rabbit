import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/main.dart' as app;

const _ownerPassword = String.fromEnvironment('RABBIT_E2E_OWNER_PASSWORD');
const _managerPassword = String.fromEnvironment('RABBIT_E2E_MANAGER_PASSWORD');
const _staffPassword = String.fromEnvironment('RABBIT_E2E_STAFF_PASSWORD');
const _viewerPassword = String.fromEnvironment('RABBIT_E2E_VIEWER_PASSWORD');

const _accounts = <_Account>[
  _Account('owner', _ownerPassword, 'OWNER', '所有者'),
  _Account('manager', _managerPassword, 'MANAGER', '设备管理员'),
  _Account('staff', _staffPassword, 'STAFF', '生产人员'),
  _Account('viewer', _viewerPassword, 'VIEWER', '游客'),
];

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('线上经营角色登录、导航和权限提示验收', (tester) async {
    for (final account in _accounts) {
      if (account.password.isEmpty) {
        fail('缺少 ${account.role} 的密码 dart-define');
      }
    }

    await _bootApp(tester, binding);

    for (final account in _accounts) {
      await _login(tester, account);
      await _waitFor(tester, find.byKey(const ValueKey('nav-houses')));
      await binding.takeScreenshot('online-${account.role.toLowerCase()}-home');

      await _tapKey(tester, 'nav-houses');
      final houseCard = find.byWidgetPredicate((widget) {
        final key = widget.key;
        return key is ValueKey<String> && key.value.startsWith('house-card-');
      }).first;
      await _waitFor(tester, houseCard);
      await tester.tap(houseCard);
      await tester.pumpAndSettle();
      await _waitFor(tester, find.text('兔舍详情'));
      await _waitFor(
        tester,
        find.textContaining('我的角色：${account.roleLabel}'),
      );

      if (account.role == 'VIEWER') {
        await _waitFor(tester, find.textContaining('您当前为只读权限'));
      }
      await binding.takeScreenshot(
        'online-${account.role.toLowerCase()}-house-permission',
      );

      if (account.role == 'OWNER') {
        await _tapKey(tester, 'nav-profile');
        await _waitFor(
          tester,
          find.byKey(const ValueKey('profile-entry-reminders')),
        );
        await _tapKey(tester, 'profile-entry-reminders');
        await _waitFor(tester, find.text('我的事件提醒'));
        await _waitFor(
          tester,
          find.byKey(const ValueKey('reminder-enabled')),
        );
        await binding.takeScreenshot('online-owner-reminder-settings');
      }

      await _logout(tester);
    }

    binding.reportData = <String, dynamic>{
      'roles': _accounts.map((account) => account.role).toList(),
      'writeActionsExecuted': false,
    };
  });
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

Future<void> _login(WidgetTester tester, _Account account) async {
  await _waitFor(tester, find.text('登录后管理兔舍、预警和生产流程。'));
  await _tapText(tester, '账号');
  await _waitFor(
    tester,
    find.byKey(const ValueKey('account-username-field')),
  );
  await _enterByKey(tester, 'account-username-field', account.userName);
  await _enterByKey(tester, 'account-password-field', account.password);

  primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  await _reveal(tester, find.byKey(const ValueKey('legal-consent-checkbox')));
  await _tapKey(tester, 'legal-consent-checkbox');
  await _reveal(tester, find.byKey(const ValueKey('account-login-button')));
  await _tapKey(tester, 'account-login-button');
  await _waitFor(
    tester,
    find.byKey(const ValueKey('nav-profile')),
    timeout: const Duration(seconds: 40),
  );
}

Future<void> _logout(WidgetTester tester) async {
  await _tapKey(tester, 'nav-profile');
  await _waitFor(
    tester,
    find.byKey(const ValueKey('profile-entry-account')),
  );
  await _reveal(tester, find.byKey(const ValueKey('profile-logout-button')));
  await _tapKey(tester, 'profile-logout-button');
  await _waitFor(tester, find.text('登录后管理兔舍、预警和生产流程。'));
}

Future<void> _tapKey(WidgetTester tester, String key) async {
  final finder = find.byKey(ValueKey(key));
  await _reveal(tester, finder);
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pump(const Duration(milliseconds: 700));
}

Future<void> _tapText(WidgetTester tester, String text) async {
  final finder = find.text(text).first;
  await _reveal(tester, finder);
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pump(const Duration(milliseconds: 700));
}

Future<void> _enterByKey(WidgetTester tester, String key, String value) async {
  final finder = find.byKey(ValueKey(key));
  await _reveal(tester, finder);
  await tester.ensureVisible(finder);
  await tester.enterText(finder, value);
  await tester.pump(const Duration(milliseconds: 300));
  primaryFocus?.unfocus();
  await tester.pumpAndSettle();
}

Future<void> _reveal(WidgetTester tester, Finder finder) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 250));
    if (finder.evaluate().isNotEmpty) {
      return;
    }
  }
  for (var i = 0; i < 12; i++) {
    if (finder.evaluate().isNotEmpty) {
      return;
    }
    final scrollables = find.byType(Scrollable);
    if (scrollables.evaluate().isEmpty) {
      break;
    }
    await tester.drag(scrollables.first, const Offset(0, -160));
    await tester.pumpAndSettle();
  }
}

Future<void> _waitFor(
  WidgetTester tester,
  Finder finder, {
  Duration timeout = const Duration(seconds: 25),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(milliseconds: 250));
    if (finder.evaluate().isNotEmpty) {
      return;
    }
  }
  fail('等不到 $finder。${_visibleTexts()}');
}

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
  return texts.isEmpty ? '当前页面没有文本' : '当前页面：${texts.join(' / ')}';
}

class _Account {
  const _Account(this.userName, this.password, this.role, this.roleLabel);

  final String userName;
  final String password;
  final String role;
  final String roleLabel;
}
