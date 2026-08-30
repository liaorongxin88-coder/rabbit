import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/main.dart' as app;

const _runId = String.fromEnvironment('RABBIT_E2E_RUN_ID');
const _password = String.fromEnvironment(
  'RABBIT_E2E_PASSWORD',
  defaultValue: '123456',
);
const _houseId = int.fromEnvironment('RABBIT_E2E_HOUSE_ID');
const _breederId = int.fromEnvironment('RABBIT_E2E_BREEDER_ID');
const _replacementId = int.fromEnvironment('RABBIT_E2E_REPLACEMENT_ID');
const _commodityId = int.fromEnvironment('RABBIT_E2E_COMMODITY_ID');
const _batchId = int.fromEnvironment('RABBIT_E2E_BATCH_ID');
const _weaningRecordId = int.fromEnvironment('RABBIT_E2E_WEANING_RECORD_ID');

String get _controlUser => 'client_additions_fixture_${_runId}_control';
String get _houseName => 'H-ADDITIONS-$_runId';

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Android validates the four appended client modifications against the real backend',
    (tester) async {
      _assertFixtureDefines();
      await _clearLocalAppState();
      await app.main();
      await binding.convertFlutterSurfaceToImage();

      await _waitFor(tester, find.byKey(const ValueKey('login-mode-selector')));
      await _login(tester);

      await _verifyDailyCareReminder(binding, tester);
      await _verifyHousePrefixedBatchCode(binding, tester);
      await _verifyBreederAndReplacementSale(binding, tester);
      await _verifyDeferredSeparation(binding, tester);

      expect(tester.takeException(), isNull);
    },
  );
}

void _assertFixtureDefines() {
  expect(_runId, isNotEmpty);
  expect(_houseId, greaterThan(0));
  expect(_breederId, greaterThan(0));
  expect(_replacementId, greaterThan(0));
  expect(_commodityId, greaterThan(0));
  expect(_batchId, greaterThan(0));
  expect(_weaningRecordId, greaterThan(0));
}

Future<void> _verifyDailyCareReminder(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
) async {
  await _goTo(tester, '/');
  await _waitFor(tester, find.text('今日提醒'));

  final dailyTab = find.widgetWithText(Tab, '日常');
  await _waitFor(tester, dailyTab);
  await tester.tap(dailyTab);
  await tester.pumpAndSettle();

  final reminder = find.text('幼兔适应观察');
  await _scrollUntilPresent(
    tester,
    reminder,
    scrollable: find.byKey(const ValueKey('home-scroll')),
  );
  expect(reminder, findsWidgets);
  expect(
    find.byKey(const ValueKey('production-event-rabbit-$_commodityId')),
    findsOneWidget,
  );
  expect(find.text('兔 #$_commodityId'), findsOneWidget);
  expect(find.text('母兔 #$_commodityId'), findsNothing);
  expect(find.text('观察适应情况，按生长和体况分群。'), findsOneWidget);
  await _takeScreenshot(binding, tester, '01-daily-care-reminder');
}

Future<void> _verifyHousePrefixedBatchCode(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
) async {
  await _goTo(tester, '/houses/$_houseId/batches');
  final createButton = find.byKey(const ValueKey('batch-create-button'));
  await _waitFor(tester, createButton);
  await tester.tap(createButton);

  final codeField = find.byKey(const ValueKey('batch-code-field'));
  await _waitFor(tester, codeField);
  final editable = find.descendant(
    of: codeField,
    matching: find.byType(EditableText),
  );
  expect(editable, findsOneWidget);
  final code = tester.widget<EditableText>(editable).controller.text;
  expect(
    code,
    matches(RegExp('^${RegExp.escape(_houseName)}-\\d{8}-\\d{4}\$')),
  );
  expect(code.length, lessThanOrEqualTo(100));
  await _takeScreenshot(binding, tester, '02-house-prefixed-batch-code');

  Navigator.of(tester.element(codeField)).pop();
  await tester.pumpAndSettle();
}

Future<void> _verifyBreederAndReplacementSale(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
) async {
  await _goTo(tester, '/houses/$_houseId/rabbits/$_breederId');
  final breederSale =
      find.byKey(const ValueKey('rabbit-detail-sale-$_breederId'));
  await _waitFor(tester, breederSale);
  await tester.ensureVisible(breederSale);
  expect(find.text('出售出栏'), findsWidgets);
  await _takeScreenshot(binding, tester, '03-breeder-sale-entry');

  await tester.tap(breederSale);
  final saleSubmit = find.byKey(const ValueKey('rabbit-sale-submit'));
  await _waitFor(tester, saleSubmit);
  final confirmation = find.byKey(const ValueKey('rabbit-sale-confirm'));
  await _scrollUntilPresent(
    tester,
    confirmation,
    scrollable: find.byType(ListView).last,
  );
  await tester.tap(confirmation);
  await tester.pumpAndSettle();
  await _takeScreenshot(binding, tester, '04-breeder-sale-sheet');

  await tester.tap(saleSubmit);
  await _waitFor(tester, find.text('兔 #$_breederId 已出售出栏'));
  await _waitFor(tester, find.text('已离场'));
  expect(find.byKey(const ValueKey('rabbit-detail-sale-$_breederId')),
      findsNothing);
  await _takeScreenshot(binding, tester, '05-breeder-sold');

  await _goTo(tester, '/houses/$_houseId/rabbits/$_replacementId');
  final replacementSale =
      find.byKey(const ValueKey('rabbit-detail-sale-$_replacementId'));
  await _waitFor(tester, replacementSale);
  await tester.ensureVisible(replacementSale);
  expect(replacementSale, findsOneWidget);
  expect(find.text('后备兔'), findsWidgets);
  await _takeScreenshot(binding, tester, '06-replacement-sale-entry');
}

Future<void> _verifyDeferredSeparation(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
) async {
  await _goTo(tester, '/houses/$_houseId/batches/$_batchId');
  final separate =
      find.byKey(const ValueKey('pending-weaning-separate-$_weaningRecordId'));
  await _scrollUntilPresent(
    tester,
    separate,
    scrollable: find.byKey(const ValueKey('batch-detail-member-list')),
  );
  expect(find.textContaining('待分笼 4 / 4 只'), findsOneWidget);
  await _takeScreenshot(binding, tester, '07-pending-separation');

  await tester.tap(separate);
  final cageField = find.byKey(const ValueKey('pending-weaning-cage'));
  await _waitFor(tester, cageField);
  await tester.tap(cageField);
  await tester.pumpAndSettle();
  await tester.tap(find.textContaining('1-4-1 · 还可放').last);
  await tester.enterText(
    find.byKey(const ValueKey('pending-weaning-count')),
    '2',
  );
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  await _takeScreenshot(binding, tester, '08-separation-sheet');

  await tester.tap(find.byKey(const ValueKey('pending-weaning-submit')));
  await _waitFor(tester, find.text('分笼完成'));
  final remaining = find.textContaining('待分笼 2 / 4 只');
  await _scrollUntilPresent(
    tester,
    remaining,
    scrollable: find.byKey(const ValueKey('batch-detail-member-list')),
  );
  expect(remaining, findsOneWidget);
  await _takeScreenshot(binding, tester, '09-separation-complete');
}

Future<void> _clearLocalAppState() async {
  final preferences = await SharedPreferences.getInstance();
  await preferences.clear();
  await const FlutterSecureStorage().deleteAll();
}

Future<void> _login(WidgetTester tester) async {
  await tester.tap(find.text('账号'));
  await tester.pumpAndSettle();
  await tester.enterText(
    find.byKey(const ValueKey('account-username-field')),
    _controlUser,
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

Future<void> _goTo(WidgetTester tester, String location) async {
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  final scaffold = find.byType(Scaffold);
  await _waitFor(tester, scaffold);
  GoRouter.of(tester.element(scaffold.first)).go(location);
  await tester.pumpAndSettle();
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 200)),
  );
  await tester.pump();
}

Future<void> _scrollUntilPresent(
  WidgetTester tester,
  Finder target, {
  required Finder scrollable,
}) async {
  for (var attempt = 0; attempt < 16 && target.evaluate().isEmpty; attempt++) {
    await _waitFor(tester, scrollable);
    await tester.drag(scrollable.first, const Offset(0, -260));
    await tester.pumpAndSettle();
  }
  await _waitFor(tester, target);
  await tester.ensureVisible(target.first);
  await tester.pumpAndSettle();
}

Future<void> _waitFor(
  WidgetTester tester,
  Finder finder, {
  Duration timeout = const Duration(seconds: 30),
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
  expect(tester.takeException(), isNull,
      reason: 'Flutter exception before $name');
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 150)),
  );
  await tester.pump();
  await binding.takeScreenshot(name);
}
