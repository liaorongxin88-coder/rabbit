import 'package:dio/dio.dart';
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
const _houseId = int.fromEnvironment('RABBIT_E2E_PRIMARY_HOUSE_ID');
const _motherAId = int.fromEnvironment('RABBIT_E2E_MOTHER_A_ID');
const _motherBId = int.fromEnvironment('RABBIT_E2E_MOTHER_B_ID');
const _fatherId = int.fromEnvironment('RABBIT_E2E_FATHER_ID');
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

String get _controlUser => 'batch_lifecycle_fixture_${_runId}_control';
String get _houseName => 'H-LIFECYCLE-$_runId';
String get _batchCode => 'B-LIFECYCLE-$_runId';
double get _expectedTextScale => double.parse(_expectedTextScaleValue);
double get _expectedEffectiveTextScale =>
    double.parse(_expectedEffectiveTextScaleValue);

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Android real-backend whole Batch lifecycle from creation to completion',
    (tester) async {
      _assertFixtureDefines();
      await _clearLocalAppState();
      await app.main();
      await binding.convertFlutterSurfaceToImage();

      await _waitFor(tester, find.byKey(const ValueKey('login-mode-selector')));
      await _takeScreenshot(binding, tester, '01-login');

      await _login(tester);
      final api = await _LifecycleApi.login();
      addTearDown(api.close);

      await _openHouse(tester);
      await _createBatch(tester);
      await _takeScreenshot(binding, tester, '02-batch-created');

      final batchId = await api.batchIdByCode(_batchCode);
      await _runAphrodisiacInBatchDetail(
        tester,
        batchId: batchId,
        count: 2,
      );
      await _submitBulkMating(
        binding,
        tester,
        batchId: batchId,
        count: 2,
        selectionScreenshot: '03-bulk-mating-selection',
        confirmationScreenshot: '04-bulk-mating-confirmation',
      );

      await _submitPregnancyCheck(
        binding,
        tester,
        rabbitId: _motherAId,
        result: '怀孕',
        screenshot: '05-pregnancy-mother-a',
      );
      await _submitPregnancyCheck(
        binding,
        tester,
        rabbitId: _motherBId,
        result: '空怀',
        screenshot: '06-empty-pregnancy-mother-b',
      );
      expect(await api.rabbitIsActive(_motherBId), isTrue);

      await _submitPrepartum(
        binding,
        tester,
        rabbitId: _motherAId,
        screenshot: '07-prepartum-first-cycle',
      );
      await _submitParturition(
        binding,
        tester,
        rabbitId: _motherAId,
        totalKits: 4,
        liveKits: 4,
        screenshot: '08-parturition-first-cycle',
      );

      await _runAphrodisiacInBatchDetail(
        tester,
        batchId: batchId,
        count: 1,
      );
      await _submitBulkMating(
        binding,
        tester,
        batchId: batchId,
        count: 1,
        confirmationScreenshot: '09-overlap-bulk-mating',
      );
      expect(await api.activeCycleCount(batchId, _motherAId), 2);

      await _submitWeaning(
        binding,
        tester,
        rabbitId: _motherAId,
        count: 4,
        maleCount: 2,
        femaleCount: 2,
        screenshot: '10-weaning-first-cycle',
      );
      await _submitPregnancyCheck(
        binding,
        tester,
        rabbitId: _motherAId,
        result: '怀孕',
        screenshot: '11-pregnancy-second-cycle',
      );
      await _submitPrepartum(
        binding,
        tester,
        rabbitId: _motherAId,
        screenshot: '12-prepartum-second-cycle',
      );
      await _submitParturition(
        binding,
        tester,
        rabbitId: _motherAId,
        totalKits: 3,
        liveKits: 3,
        screenshot: '13-parturition-second-cycle',
      );
      await _submitWeaning(
        binding,
        tester,
        rabbitId: _motherAId,
        count: 3,
        maleCount: 1,
        femaleCount: 2,
        screenshot: '14-weaning-second-cycle',
      );

      await _openHouse(tester);
      await tester.tap(find.text('进入笼位'));
      await _waitFor(
        tester,
        find.byKey(const ValueKey('house-outbound-action')),
      );
      await tester.tap(find.byKey(const ValueKey('house-outbound-action')));
      await _waitFor(tester, find.text('正常可出库'));
      _expectSummaryMetric(
        const ValueKey('outbound-summary-normal'),
        label: '正常可出库',
        value: 7,
      );
      _expectSummaryMetric(
        const ValueKey('outbound-summary-early-sale'),
        label: '可提前出售',
        value: 0,
      );
      _expectSummaryMetric(
        const ValueKey('outbound-summary-blocked'),
        label: '不可批量选择',
        value: 3,
      );
      expect(find.text('下一步 · 7 只'), findsOneWidget);
      await _takeScreenshot(binding, tester, '15-outbound-selection');

      await tester.tap(find.byKey(const ValueKey('outbound-continue-button')));
      await _waitFor(tester, find.text('冻结清单 7 只'));
      await _enterOutboundField(
        tester,
        const ValueKey('outbound-total-weight'),
        '14.7',
      );
      await _enterOutboundField(
        tester,
        const ValueKey('outbound-unit-price'),
        '18',
      );
      await _enterOutboundField(
        tester,
        const ValueKey('outbound-customer'),
        'Batch 生命周期验收客户',
      );
      await _enterOutboundField(
        tester,
        const ValueKey('outbound-remark'),
        'A059 完整 Batch 闭环',
      );
      FocusManager.instance.primaryFocus?.unfocus();
      await tester.pumpAndSettle(const Duration(milliseconds: 300));
      await _takeScreenshot(binding, tester, '16-outbound-confirmation');

      await tester.tap(find.byKey(const ValueKey('outbound-submit-button')));
      await _waitFor(
        tester,
        find.text('出库完成'),
        timeout: const Duration(seconds: 30),
      );
      expect(find.text('7 只'), findsWidgets);
      expect(find.text('14.70 kg'), findsOneWidget);
      await _takeScreenshot(binding, tester, '17-outbound-success');

      await _cullMotherThroughUi(tester, batchId);
      expect(await api.batchStatus(batchId), '已完成');
      expect(await api.activeBatchRabbitCount(batchId), 0);

      await tester.tap(find.byKey(const ValueKey('nav-home')));
      await _waitFor(tester, find.text('今日生产'));
      await tester.tap(find.byTooltip('刷新'));
      await tester.pumpAndSettle();
      await _takeScreenshot(binding, tester, '18-batch-completed');

      binding.reportData ??= <String, dynamic>{};
      binding.reportData!.addAll({
        'runId': _runId,
        'houseId': _houseId,
        'batchId': batchId,
        'motherAId': _motherAId,
        'motherBId': _motherBId,
        'fatherId': _fatherId,
        'breedingCycles': 3,
        'bulkMatingRequests': 2,
        'weanedCycles': 2,
        'emptyPregnancyCycles': 1,
        'commodityRabbitsSold': 7,
        'batchStatus': '已完成',
        'systemTextScale': _systemTextScale(tester),
        'effectiveTextScale': _currentTextScale(tester),
        'logicalSize': _logicalSize(tester).toString(),
      });
    },
  );
}

void _assertFixtureDefines() {
  expect(_runId, isNotEmpty, reason: 'RABBIT_E2E_RUN_ID is required');
  expect(_houseId, greaterThan(0));
  expect(_motherAId, greaterThan(0));
  expect(_motherBId, greaterThan(0));
  expect(_fatherId, greaterThan(0));
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
  await tester.tap(find.byKey(const ValueKey('legal-consent-checkbox')));
  await tester.tap(find.byKey(const ValueKey('account-login-button')));
  await _waitFor(tester, find.text('兔舍'));
}

Future<void> _openHouse(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('nav-houses')));
  final houseCard = find.byKey(const ValueKey('house-card-$_houseId'));
  await _waitFor(tester, houseCard);
  expect(
    find.descendant(of: houseCard, matching: find.text(_houseName)),
    findsOneWidget,
  );
  await tester.tap(houseCard);
  await _waitFor(
    tester,
    find.byKey(const ValueKey('house-batches-entry')),
  );
}

Future<void> _createBatch(WidgetTester tester) async {
  final batchEntry = find.byKey(const ValueKey('house-batches-entry'));
  await tester.ensureVisible(batchEntry);
  await tester.pumpAndSettle();
  await tester.tap(batchEntry);
  final createButton = find.byKey(const ValueKey('batch-create-button'));
  await _waitFor(tester, createButton);
  await tester.ensureVisible(createButton);
  await tester.tap(createButton);
  await _waitFor(tester, find.text('选择种母兔（已选 0 只）'));
  final codeField = find.byKey(const ValueKey('batch-code-field'));
  final remarkField = find.byKey(const ValueKey('batch-remark-field'));
  final motherSearch = find.byKey(const ValueKey('batch-mother-search'));
  expect(codeField, findsOneWidget);
  expect(remarkField, findsOneWidget);
  expect(motherSearch, findsOneWidget);
  await tester.enterText(codeField, _batchCode);
  await tester.enterText(
    remarkField,
    'A059 两窝重叠、空怀和整舍出笼闭环',
  );
  await tester.tap(find.textContaining('兔 #$_motherAId'));
  await tester.tap(find.textContaining('兔 #$_motherBId'));
  await _waitFor(tester, find.text('选择种母兔（已选 2 只）'));
  final submit = find.widgetWithText(ElevatedButton, '创建批次');
  await tester.ensureVisible(submit);
  await tester.tap(submit);
  await _waitFor(tester, find.textContaining('批次 $_batchCode 已创建'));
}

Future<void> _runAphrodisiacInBatchDetail(
  WidgetTester tester, {
  required int batchId,
  required int count,
}) async {
  final batchCard = find.byKey(ValueKey('batch-list-item-$batchId'));
  if (batchCard.evaluate().isEmpty) {
    await _openHouse(tester);
    await tester.tap(find.byKey(const ValueKey('house-batches-entry')));
  }
  await _waitFor(tester, batchCard);
  await tester.tap(batchCard);

  final startSelection = find.byKey(
    const ValueKey('batch-select-start-visible'),
  );
  await _scrollBatchDetailUntilVisible(tester, startSelection);
  await _waitForEnabled(tester, startSelection);
  await tester.tap(startSelection);
  await _waitFor(tester, find.text('已选择 $count 只母兔'));

  final submit = find.byKey(const ValueKey('batch-selected-submit'));
  await tester.ensureVisible(submit);
  await tester.tap(submit);
  if (count > 1) {
    final confirm = find.byKey(const ValueKey('batch-bulk-confirm'));
    await _waitFor(tester, confirm);
    await tester.tap(confirm);
  }
  await _waitFor(tester, find.textContaining('开始催情已提交'));

  final finishSelection = find.byKey(
    const ValueKey('batch-select-finish-visible'),
  );
  await _scrollBatchDetailUntilVisible(tester, finishSelection);
  await _waitForEnabled(tester, finishSelection);
  await tester.tap(finishSelection);
  await _waitFor(tester, find.text('已选择 $count 只母兔'));
  await tester.ensureVisible(submit);
  await tester.tap(submit);
  if (count > 1) {
    final confirm = find.byKey(const ValueKey('batch-bulk-confirm'));
    await _waitFor(tester, confirm);
    await tester.tap(confirm);
  }
  await _waitFor(tester, find.textContaining('完成催情已提交'));
}

Future<void> _refreshHome(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('nav-home')));
  await _waitFor(tester, find.text('今日生产'));
  await tester.tap(find.byTooltip('刷新'));
  await tester.pumpAndSettle();
}

Future<void> _submitBulkMating(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester, {
  required int batchId,
  required int count,
  String? selectionScreenshot,
  required String confirmationScreenshot,
}) async {
  final select = find.byKey(const ValueKey('batch-select-mating-visible'));
  final detailList = find.byKey(const ValueKey('batch-detail-member-list'));
  if (detailList.evaluate().isEmpty) {
    await _openHouse(tester);
    await tester.tap(find.byKey(const ValueKey('house-batches-entry')));
    final batchCard = find.byKey(ValueKey('batch-list-item-$batchId'));
    await _waitFor(tester, batchCard);
    await tester.tap(batchCard);
  }
  await _scrollBatchDetailUntilVisible(tester, select);
  await _waitForEnabled(tester, select);
  await tester.tap(select);
  await _waitFor(tester, find.text('已选择 $count 只母兔'));
  if (selectionScreenshot != null) {
    await _takeScreenshot(binding, tester, selectionScreenshot);
  }

  final submit = find.byKey(const ValueKey('batch-mating-submit'));
  await tester.ensureVisible(submit);
  await tester.tap(submit);
  await _waitFor(tester, find.text('批量配种'));
  await _waitFor(
    tester,
    find.byKey(const ValueKey('batch-mating-date')),
  );
  expect(
    find.textContaining('已选择 $count 只母兔'),
    findsWidgets,
  );

  final father = find.byKey(
    const ValueKey('batch-mating-male-$_fatherId'),
  );
  await _waitFor(tester, father);
  await tester.ensureVisible(father);
  await tester.tap(father);
  await _takeScreenshot(binding, tester, confirmationScreenshot);

  final confirm = find.byKey(const ValueKey('batch-mating-confirm'));
  await tester.ensureVisible(confirm);
  await tester.tap(confirm);
  await _waitFor(
    tester,
    find.textContaining('已完成批量配种，共 $count 只母兔'),
  );
}

Future<void> _cullMotherThroughUi(WidgetTester tester, int batchId) async {
  await tester.tap(find.text('返回首页'));
  await _waitFor(tester, find.text('今日生产'));
  await _openHouse(tester);
  await tester.tap(find.byKey(const ValueKey('house-batches-entry')));
  final batchCard = find.byKey(ValueKey('batch-list-item-$batchId'));
  await _waitFor(tester, batchCard);
  await tester.tap(batchCard);

  final departure = find.byKey(
    const ValueKey('batch-member-departure-$_motherAId'),
  );
  await _scrollBatchDetailUntilVisible(tester, departure);
  await tester.tap(departure);
  await _waitFor(tester, find.text('母兔离场'));
  final reason = find.byKey(const ValueKey('rabbit-departure-reason'));
  await tester.ensureVisible(reason);
  await tester.enterText(reason, '繁殖性能下降');
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  final risk = find.byKey(const ValueKey('rabbit-departure-confirm-risk'));
  await tester.ensureVisible(risk);
  await tester.tap(risk);
  await tester.pumpAndSettle();
  expect(tester.widget<CheckboxListTile>(risk).value, isTrue);
  final submit = find.byKey(const ValueKey('rabbit-departure-submit'));
  await tester.ensureVisible(submit);
  expect(tester.widget<FilledButton>(submit).onPressed, isNotNull);
  await tester.tap(submit);
  await _waitFor(tester, find.textContaining('已记录淘汰离场'));
}

Future<void> _openProductionEvent(
  WidgetTester tester, {
  required String tab,
  required int rabbitId,
  required String sheetTitle,
  required Key formListKey,
}) async {
  await _refreshHome(tester);
  final tabFinder = find.descendant(
    of: find.byType(TabBar),
    matching: find.text(tab),
  );
  await _waitFor(tester, tabFinder);
  await tester.ensureVisible(tabFinder);
  await tester.pumpAndSettle();
  await tester.tap(tabFinder);
  await tester.pumpAndSettle();
  final target = find.byKey(
    ValueKey('production-event-rabbit-$rabbitId'),
  );
  await _waitFor(tester, target);
  await tester.tap(target.first);
  await _waitFor(
    tester,
    find.byKey(formListKey),
  );
  expect(find.text(sheetTitle), findsWidgets);
}

Future<void> _submitPregnancyCheck(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester, {
  required int rabbitId,
  required String result,
  required String screenshot,
}) async {
  await _openProductionEvent(
    tester,
    tab: '摸胎',
    rabbitId: rabbitId,
    sheetTitle: '记录摸胎',
    formListKey: const ValueKey('production-event-form-list'),
  );
  final resultOption = find.byKey(ValueKey('pregnancy-result-$result'));
  await _waitFor(tester, resultOption);
  await tester.ensureVisible(resultOption);
  await tester.tap(resultOption);
  await tester.pumpAndSettle();
  await _takeScreenshot(binding, tester, screenshot);
  await _confirmProductionSheet(tester, '记录摸胎 已完成');
}

Future<void> _submitPrepartum(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester, {
  required int rabbitId,
  required String screenshot,
}) async {
  await _openProductionEvent(
    tester,
    tab: '备产',
    rabbitId: rabbitId,
    sheetTitle: '完成备产',
    formListKey: const ValueKey('production-event-form-list'),
  );
  await _takeScreenshot(binding, tester, screenshot);
  await _confirmProductionSheet(tester, '完成备产 已完成');
}

Future<void> _submitParturition(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester, {
  required int rabbitId,
  required int totalKits,
  required int liveKits,
  required String screenshot,
}) async {
  await _openProductionEvent(
    tester,
    tab: '分娩',
    rabbitId: rabbitId,
    sheetTitle: '记录分娩',
    formListKey: const ValueKey('production-event-form-list'),
  );
  final totalKitsField = find.byKey(
    const ValueKey('parturition-total-kits'),
  );
  final liveKitsField = find.byKey(
    const ValueKey('parturition-live-kits'),
  );
  await _enterFormField(
    tester,
    listKey: const ValueKey('production-event-form-list'),
    field: totalKitsField,
    value: '$totalKits',
  );
  await _enterFormField(
    tester,
    listKey: const ValueKey('production-event-form-list'),
    field: liveKitsField,
    value: '$liveKits',
  );
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  await _takeScreenshot(binding, tester, screenshot);
  await _confirmProductionSheet(tester, '记录分娩 已完成');
}

Future<void> _submitWeaning(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester, {
  required int rabbitId,
  required int count,
  required int maleCount,
  required int femaleCount,
  required String screenshot,
}) async {
  await _openProductionEvent(
    tester,
    tab: '断奶',
    rabbitId: rabbitId,
    sheetTitle: '断奶并放入笼位',
    formListKey: const ValueKey('weaning-form-list'),
  );
  await _enterFormField(
    tester,
    listKey: const ValueKey('weaning-form-list'),
    field: find.byKey(const ValueKey('weaning-count')),
    value: '$count',
  );
  await _enterFormField(
    tester,
    listKey: const ValueKey('weaning-form-list'),
    field: find.byKey(const ValueKey('weaning-male-count')),
    value: '$maleCount',
  );
  await _enterFormField(
    tester,
    listKey: const ValueKey('weaning-form-list'),
    field: find.byKey(const ValueKey('weaning-female-count')),
    value: '$femaleCount',
  );
  await _enterFormField(
    tester,
    listKey: const ValueKey('weaning-form-list'),
    field: find.byKey(const ValueKey('weaning-average-weight')),
    value: '1.2',
  );
  await _enterFormField(
    tester,
    listKey: const ValueKey('weaning-form-list'),
    field: find.byKey(const ValueKey('weaning-remark')),
    value: 'A059 Batch 生命周期断奶',
  );
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  await _takeScreenshot(binding, tester, screenshot);
  final submit = find.widgetWithText(ElevatedButton, '确认断奶');
  await tester.ensureVisible(submit);
  await tester.tap(submit);
  await _waitFor(tester, find.textContaining('断奶完成'));
}

Future<void> _confirmProductionSheet(
  WidgetTester tester,
  String successMessage,
) async {
  final submit = find.widgetWithText(ElevatedButton, '确认');
  await tester.ensureVisible(submit);
  await tester.tap(submit);
  await _waitFor(tester, find.text(successMessage));
}

Future<void> _enterOutboundField(
  WidgetTester tester,
  ValueKey<String> key,
  String value,
) async {
  final finder = find.byKey(key);
  await tester.ensureVisible(finder);
  await tester.tap(finder);
  await tester.enterText(finder, value);
  await tester.pump(const Duration(milliseconds: 150));
}

Future<void> _enterFormField(
  WidgetTester tester, {
  required ValueKey<String> listKey,
  required Finder field,
  required String value,
}) async {
  final list = find.byKey(listKey);
  await _waitFor(tester, list);
  await tester.scrollUntilVisible(
    field,
    180,
    scrollable:
        find.descendant(of: list, matching: find.byType(Scrollable)).first,
  );
  await tester.pumpAndSettle();
  await tester.enterText(field, value);
  await tester.pump(const Duration(milliseconds: 100));
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

Future<void> _scrollBatchDetailUntilVisible(
  WidgetTester tester,
  Finder target,
) async {
  final list = find.byKey(const ValueKey('batch-detail-member-list'));
  await _waitFor(tester, list);
  await tester.scrollUntilVisible(
    target,
    280,
    scrollable:
        find.descendant(of: list, matching: find.byType(Scrollable)).first,
  );
  await tester.pumpAndSettle();
}

Future<void> _waitForEnabled(WidgetTester tester, Finder finder) async {
  final deadline = DateTime.now().add(const Duration(seconds: 20));
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(milliseconds: 100));
    if (finder.evaluate().isNotEmpty &&
        tester.widget<OutlinedButton>(finder).onPressed != null) {
      return;
    }
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 50)),
    );
  }
  fail('Timed out waiting for enabled $finder');
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

void _expectSummaryMetric(
  ValueKey<String> key, {
  required String label,
  required int value,
}) {
  final metric = find.byKey(key);
  expect(metric, findsOneWidget);
  expect(
      find.descendant(of: metric, matching: find.text(label)), findsOneWidget);
  expect(find.descendant(of: metric, matching: find.text('$value')),
      findsOneWidget);
}

void _expectTextScale(WidgetTester tester) {
  expect(_systemTextScale(tester), closeTo(_expectedTextScale, 0.15));
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

class _LifecycleApi {
  _LifecycleApi(this._dio, this._token);

  final Dio _dio;
  final String _token;

  static Future<_LifecycleApi> login() async {
    final dio = Dio(
      BaseOptions(
        baseUrl: _apiBaseUrl,
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 20),
        headers: const {'Content-Type': 'application/json'},
      ),
    );
    final response = await dio.post<Map<String, dynamic>>(
      '/api/auth/login',
      data: {'userName': _controlUser, 'password': _password},
    );
    final body = response.data ?? const <String, dynamic>{};
    expect(body['code'], 0);
    final data = Map<String, dynamic>.from(body['data'] as Map);
    return _LifecycleApi(dio, data['token'] as String);
  }

  Options get _houseOptions => Options(
        headers: {
          'Authorization': 'Bearer $_token',
          'X-House-Id': '$_houseId',
        },
      );

  Future<Object?> _get(String path, {Map<String, dynamic>? query}) async {
    final response = await _dio.get<Map<String, dynamic>>(
      path,
      queryParameters: query,
      options: _houseOptions,
    );
    return _unwrap(response.data);
  }

  Object? _unwrap(Map<String, dynamic>? body) {
    final value = body ?? const <String, dynamic>{};
    expect(value['code'], 0, reason: value['msg']?.toString());
    return value['data'];
  }

  Future<int> batchIdByCode(String code) async {
    final data = await _get(
      '/api/batches',
      query: {'page': 1, 'pageSize': 20},
    );
    final rows = List<Map<String, dynamic>>.from(
      (data as List).map((item) => Map<String, dynamic>.from(item as Map)),
    );
    final batch = rows.singleWhere((item) => item['batchCode'] == code);
    return (batch['id'] as num).toInt();
  }

  Future<int> activeCycleCount(int batchId, int motherId) async {
    final data = await _get(
      '/api/batches/$batchId/breeding-cycles',
      query: {'motherRabbitId': motherId, 'activeOnly': true},
    );
    return (data as List).length;
  }

  Future<bool> rabbitIsActive(int rabbitId) async {
    final data = Map<String, dynamic>.from(
      await _get('/api/rabbits/$rabbitId') as Map,
    );
    return data['isActive'] == true;
  }

  Future<String> batchStatus(int batchId) async {
    final data = Map<String, dynamic>.from(
      await _get('/api/batches/$batchId') as Map,
    );
    return data['status'] as String;
  }

  Future<int> activeBatchRabbitCount(int batchId) async {
    final data = await _get(
      '/api/batches/$batchId/batch-rabbits',
      query: {'active': true},
    );
    return (data as List).length;
  }

  void close() => _dio.close(force: true);
}
