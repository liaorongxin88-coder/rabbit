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

      // 建批即入轨：每头母兔都应落在待催情并带一条催情待办。
      // 这一条曾经不成立（建批不开周期），导致整个生产流程从界面无法开始。
      await _assertBatchState(api, batchId, step: '建批后', expected: _does(a: 'AWAIT_ESTRUS/ESTRUS', b: 'AWAIT_ESTRUS/ESTRUS'));

      await _runAphrodisiacInBatchDetail(
        tester,
        batchId: batchId,
        count: 2,
      );
      await _assertBatchState(api, batchId, step: '批量催情后', expected: _does(a: 'AWAIT_MATING/MATING', b: 'AWAIT_MATING/MATING'));

      await _submitBulkMating(
        binding,
        tester,
        batchId: batchId,
        count: 2,
        selectionScreenshot: '03-bulk-mating-selection',
        confirmationScreenshot: '04-bulk-mating-confirmation',
      );
      await _assertBatchState(api, batchId, step: '批量配种后', expected: _does(a: 'AWAIT_PALPATION/PALPATION', b: 'AWAIT_PALPATION/PALPATION'));

      await _submitPregnancyCheck(
        binding,
        tester,
        rabbitId: _motherAId,
        result: 'PREGNANT',
        screenshot: '05-pregnancy-mother-a',
      );
      // 只能动 A：B 必须原地不动。旧实现里“推进一头连带刷到旁人”是真实发生过的。
      await _assertBatchState(api, batchId, step: '母兔A摸胎怀孕后', expected: _does(a: 'AWAIT_PREPARTUM/PREPARTUM', b: 'AWAIT_PALPATION/PALPATION'));

      await _submitPregnancyCheck(
        binding,
        tester,
        rabbitId: _motherBId,
        result: 'EMPTY',
        screenshot: '06-empty-pregnancy-mother-b',
      );
      expect(await api.rabbitIsActive(_motherBId), isTrue);
      // 空怀不是终点：新模型会自动关掉这一轮并接续下一轮（回到待催情），
      // 而不是像旧模型那样把母兔直接踢出生产流程。
      await _assertBatchState(api, batchId, step: '母兔B摸胎空怀后', expected: _does(a: 'AWAIT_PREPARTUM/PREPARTUM', b: 'AWAIT_ESTRUS/ESTRUS'));

      await _submitPrepartum(
        binding,
        tester,
        rabbitId: _motherAId,
        screenshot: '07-prepartum-first-cycle',
      );
      await _assertBatchState(api, batchId, step: '母兔A备产后', expected: _does(a: 'AWAIT_DELIVERY/DELIVERY', b: 'AWAIT_ESTRUS/ESTRUS'));

      await _submitParturition(
        binding,
        tester,
        rabbitId: _motherAId,
        totalKits: 4,
        liveKits: 4,
        screenshot: '08-parturition-first-cycle',
      );
      // 产仔后进入哺乳：待办主体从周期换成窝，但仍挂在这头母兔名下。
      await _assertBatchState(api, batchId, step: '母兔A接产后', expected: _does(a: 'AWAIT_WEANING/WEANING', b: 'AWAIT_ESTRUS/ESTRUS'));

      // 此时只有母兔 B 处于待催情：她的空怀周期已自动关闭并接续了新一轮。
      await _runAphrodisiacInBatchDetail(
        tester,
        batchId: batchId,
        count: 1,
      );
      // 两头都可配，但性质不同：母兔 B 刚催完情处于待配种；
      // 母兔 A 还在哺乳，对她配种即血配——哺乳周期不占流水线，
      // 所以会另开一条新的怀孕周期，下面的 activeCycleCount 就是在钉这件事。
      await _submitBulkMating(
        binding,
        tester,
        batchId: batchId,
        count: 2,
        confirmationScreenshot: '09-overlap-bulk-mating',
      );
      expect(await api.activeCycleCount(batchId, _motherAId), 2);
      // 血配后母兔 A 同时持有哺乳周期与新怀孕周期。
      // 投影列要指向流水线那一条（待摸胎）——这里曾有过“分笼把并行周期的
      // 阶段覆盖掉”的漂移缺陷，所以这一步必须单独钉住。
      await _assertBatchState(api, batchId, step: '血配后', expected: _does(a: 'AWAIT_PALPATION/PALPATION', b: 'AWAIT_PALPATION/PALPATION'));

      await _submitWeaning(
        binding,
        tester,
        rabbitId: _motherAId,
        count: 4,
        maleCount: 2,
        femaleCount: 2,
        screenshot: '10-weaning-first-cycle',
      );
      // 分笼只结束哺乳周期，不得碰她并行的怀孕周期：
      // A 仍停在待摸胎，而不是被拍回待催情。
      await _assertBatchState(api, batchId, step: '母兔A首轮分笼后', expected: _does(a: 'AWAIT_PALPATION/PALPATION', b: 'AWAIT_PALPATION/PALPATION'));

      await _submitPregnancyCheck(
        binding,
        tester,
        rabbitId: _motherAId,
        result: 'PREGNANT',
        screenshot: '11-pregnancy-second-cycle',
      );
      await _assertBatchState(api, batchId, step: '母兔A二轮摸胎后', expected: _does(a: 'AWAIT_PREPARTUM/PREPARTUM', b: 'AWAIT_PALPATION/PALPATION'));

      await _submitPrepartum(
        binding,
        tester,
        rabbitId: _motherAId,
        screenshot: '12-prepartum-second-cycle',
      );
      await _assertBatchState(api, batchId, step: '母兔A二轮备产后', expected: _does(a: 'AWAIT_DELIVERY/DELIVERY', b: 'AWAIT_PALPATION/PALPATION'));

      await _submitParturition(
        binding,
        tester,
        rabbitId: _motherAId,
        totalKits: 3,
        liveKits: 3,
        screenshot: '13-parturition-second-cycle',
      );
      await _assertBatchState(api, batchId, step: '母兔A二轮接产后', expected: _does(a: 'AWAIT_WEANING/WEANING', b: 'AWAIT_PALPATION/PALPATION'));

      await _submitWeaning(
        binding,
        tester,
        rabbitId: _motherAId,
        count: 3,
        maleCount: 1,
        femaleCount: 2,
        screenshot: '14-weaning-second-cycle',
      );
      // 本轮她没有并行的流水线周期，所以分笼后自动接续下一轮（回到待催情）。
      await _assertBatchState(api, batchId, step: '母兔A二轮分笼后', expected: _does(a: 'AWAIT_ESTRUS/ESTRUS', b: 'AWAIT_PALPATION/PALPATION'));

      // 流产：此刻 A 在待催情、B 在待摸胎，正好一头不允许一头允许。
      // 入口显隐由服务端阶段字典驱动，这里在真机上同时钉住两面。
      await _submitAbortion(
        binding,
        tester,
        batchId: batchId,
        rabbitId: _motherBId,
        notOfferedForRabbitId: _motherAId,
        stillbirthCount: 2,
        screenshot: '15-abortion-mother-b',
      );
      // 流产与空怀同理：本轮结束并自动接续下一轮，母兔不离场。
      await _assertBatchState(api, batchId, step: '母兔B流产后', expected: _does(a: 'AWAIT_ESTRUS/ESTRUS', b: 'AWAIT_ESTRUS/ESTRUS'));
      expect(await api.rabbitIsActive(_motherBId), isTrue);

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
      await _takeScreenshot(binding, tester, '16-outbound-selection');

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
      await _takeScreenshot(binding, tester, '17-outbound-confirmation');

      await tester.tap(find.byKey(const ValueKey('outbound-submit-button')));
      await _waitFor(
        tester,
        find.text('出库完成'),
        timeout: const Duration(seconds: 30),
      );
      expect(find.text('7 只'), findsWidgets);
      expect(find.text('14.70 kg'), findsOneWidget);
      await _takeScreenshot(binding, tester, '18-outbound-success');

      // 两头母兔都要离场后批次才能结束。
      //
      // 新模型下批次只是个标签，结束标签不得隐含「终止母兔的生理过程」，
      // 所以只要还有未结束的生产周期，结束批次就会被拒绝。
      // 母兔 B 空怀后会自动接续新一轮（而不是像旧模型那样直接退出批次），
      // 因此她也需要显式离场。
      await _cullMotherThroughUi(tester, batchId, rabbitId: _motherAId);
      await _cullMotherThroughUi(
        tester,
        batchId,
        rabbitId: _motherBId,
        fromOutboundScreen: false,
      );
      // 收尾校验：两头都离场，且一条待办都不能残留。
      // 母兔已不存在却还在今日清单里，正是离场不结周期那个缺陷的外部表现。
      await _assertBatchState(api, batchId, step: '两头母兔离场后', expected: _does(a: 'RETIRED/', b: 'RETIRED/'));
      expect(await api.activeBatchRabbitCount(batchId), 0);

      // doe-breeding-v2 之后批次不再自动完成（后端已删除 checkAndCompleteBatch）：
      // 批次只是个标签，什么时候收标签由人决定，系统不得替人宣布生产结束。
      // 所以这里要显式点「结束 Batch」；此时活跃成员为 0，不需要勾强制。
      await _completeBatchThroughUi(tester, batchId);
      expect(await api.batchStatus(batchId), '已完成');

      await _backToHomeTop(tester);
      await tester.tap(find.byTooltip('刷新'));
      await tester.pumpAndSettle();
      await _takeScreenshot(binding, tester, '19-batch-completed');

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

  // doe-breeding-v2 取消了「催情中」这个中间态，所以这里只剩一步：
  // 待催情一个动作直接推到待配种，而不是旧的「开始」+「完成」两趟。
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
  await _waitFor(tester, find.textContaining('催情已提交'));
}

/// 每次操作后，校验批次下<b>所有</b>母兔的状态。
///
/// 关键在于“所有”而不是“刚操作的那一头”：新模型里一个动作会连带写周期、
/// 待办与母兔投影三处，注定存在“误伤旁人”的可能——历史上就出现过给 A 分笼
/// 把 B 的阶段覆盖掉、以及给某头母兔离场后别人的待办一起消失的缺陷。
/// 只断言当事母兔永远发现不了这类问题。
///
/// [expected] 必须覆盖批次下全部在批母兔；少写一头即视为用例缺陷。
/// 构造「母兔A / 母兔B」的期望表。
///
/// 不直接写 map 字面量：两个 id 都来自 fromEnvironment，静态分析时均为 0，
/// 字面量会被当成重复键。更要紧的是：若两个 id 真的相等，字面量会静默地
/// 只保留后一个，断言因此凭空少一头；这里显式拒绝重复。
Map<int, String> _does({required String a, required String b}) {
  assert(_motherAId != _motherBId, '两头母兔的 id 不得相同');
  return Map<int, String>.fromEntries([
    MapEntry(_motherAId, a),
    MapEntry(_motherBId, b),
  ]);
}

Future<void> _assertBatchState(
  _LifecycleApi api,
  int batchId, {
  required String step,
  required Map<int, String> expected,
}) async {
  final actual = await api.batchDoeStates(batchId);

  final missing = expected.keys.where((id) => !actual.containsKey(id)).toList();
  expect(missing, isEmpty, reason: '[$step] 批次里找不到母兔 $missing');
  final unchecked =
      actual.keys.where((id) => !expected.containsKey(id)).toList();
  expect(unchecked, isEmpty,
      reason: '[$step] 这些母兔没有被断言到，请补全期望：$unchecked');

  final diffs = <String>[];
  expected.forEach((rabbitId, want) {
    final got = actual[rabbitId]!;
    if (got.signature != want) {
      diffs.add('  兔 #$rabbitId 期望=$want 实际=${got.signature}（$got）');
    }
  });
  expect(
    diffs,
    isEmpty,
    reason: '[$step] 批次全员状态校验失败：\n${diffs.join('\n')}',
  );
}

Future<void> _refreshHome(WidgetTester tester) async {
  await _backToHomeTop(tester);
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

/// 显式结束 Batch（活跃成员为 0 时无需强制）。
Future<void> _completeBatchThroughUi(WidgetTester tester, int batchId) async {
  await _backToHomeTop(tester);
  await _openHouse(tester);
  await tester.tap(find.byKey(const ValueKey('house-batches-entry')));
  final batchCard = find.byKey(ValueKey('batch-list-item-$batchId'));
  await _waitFor(tester, batchCard);
  await tester.tap(batchCard);

  final completeButton = find.byKey(const ValueKey('batch-complete-button'));
  await _scrollBatchDetailUntilVisible(tester, completeButton);
  await tester.tap(completeButton);
  await _waitFor(tester, find.text('结束这个 Batch？'));
  // 没有活跃成员时弹窗不会出现强制勾，确认键直接可点。
  expect(find.byKey(const ValueKey('batch-complete-force')), findsNothing);
  await tester.tap(find.byKey(const ValueKey('batch-complete-confirm')));
  await _waitFor(tester, find.text('Batch 已结束'));
  await tester.pumpAndSettle();
}

Future<void> _cullMotherThroughUi(
  WidgetTester tester,
  int batchId, {
  required int rabbitId,
  bool fromOutboundScreen = true,
}) async {
  if (fromOutboundScreen) {
    await tester.tap(find.text('返回首页'));
    await _waitFor(tester, find.text('今日生产'));
  } else {
    await _backToHomeTop(tester);
  }
  await _openHouse(tester);
  await tester.tap(find.byKey(const ValueKey('house-batches-entry')));
  final batchCard = find.byKey(ValueKey('batch-list-item-$batchId'));
  await _waitFor(tester, batchCard);
  await tester.tap(batchCard);

  final departure = find.byKey(
    ValueKey('batch-member-departure-$rabbitId'),
  );
  await _scrollBatchDetailUntilVisible(tester, departure);
  await tester.tap(departure);
  // 离场表单已改成全兔种通用（笼内任何一只都能登记），标题从「母兔离场」改为「登记离场」。
  await _waitFor(tester, find.text('登记离场'));
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
  final target = find.byKey(
    ValueKey('production-event-rabbit-$rabbitId'),
  );
  final tabFinder = find.descendant(
    of: find.byType(TabBar),
    matching: find.text(tab),
  );

  // 刷新要可重试，不能只刷一次。
  //
  // 新生成的待办（如分笼）默认落在二十多天后，靠外部的时间压缩守护进程
  // 每 0.25 秒把它改到今天。若单次刷新正好落在压缩之前，列表里就没有这条；
  // 而 _waitFor 只轮询控件、不会重新拉取服务端，于是永远等不到。
  Object? lastError;
  for (var attempt = 0; attempt < 6; attempt++) {
    await _refreshHome(tester);
    await _waitFor(tester, tabFinder);
    await tester.ensureVisible(tabFinder);
    await tester.pumpAndSettle();
    await tester.tap(tabFinder);
    await tester.pumpAndSettle();
    if (target.evaluate().isNotEmpty) {
      lastError = null;
      break;
    }
    lastError = '第 ${attempt + 1} 次刷新后仍未看到 兔 #$rabbitId 的「$tab」待办';
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(seconds: 2)),
    );
  }
  if (lastError != null) {
    fail('$lastError');
  }
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
  /// 摸胎结论的服务端枚举名（PREGNANT / EMPTY / UNSURE）。
  /// 单选项的 key 跟着服务端词汇走，客户端不再自己维护一份中文映射。
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

/// 在批次详情里给一头母兔记流产，并顺带验证入口的阶段准入。
///
/// 流产是非计划事件，不对应任何待办，所以不从今日清单进，而是母兔行上的独立入口。
/// [notOfferedForRabbitId] 是这个用例真正的重量：它要求同一屏上另一头不处于
/// 孕期的母兔**没有**这个按钮。只断言“能点”会放过“到处都能点”，而后者才是
/// 真实风险：给用户一个点下去必定 409 的按钮。
Future<void> _submitAbortion(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester, {
  required int batchId,
  required int rabbitId,
  required int notOfferedForRabbitId,
  required int stillbirthCount,
  required String screenshot,
}) async {
  final batchCard = find.byKey(ValueKey('batch-list-item-$batchId'));
  if (batchCard.evaluate().isEmpty) {
    await _openHouse(tester);
    await tester.tap(find.byKey(const ValueKey('house-batches-entry')));
  }
  await _waitFor(tester, batchCard);
  await tester.tap(batchCard);

  // 先看不该有入口的那头：先把它的行滚进视口，否则 findsNothing 是空证。
  await _scrollBatchDetailUntilVisible(
    tester,
    find.byKey(ValueKey('batch-member-$notOfferedForRabbitId')),
  );
  expect(
    find.byKey(ValueKey('batch-member-abortion-$notOfferedForRabbitId')),
    findsNothing,
    reason: '非孕期母兔 #$notOfferedForRabbitId 不应出现流产入口',
  );

  final target = find.byKey(ValueKey('batch-member-abortion-$rabbitId'));
  await _scrollBatchDetailUntilVisible(tester, target);
  await tester.tap(target);
  await _waitFor(tester, find.byKey(const ValueKey('abortion-submit')));

  await _enterOutboundField(
    tester,
    const ValueKey('abortion-stillbirth-count'),
    '$stillbirthCount',
  );
  await tester.tap(find.byKey(const ValueKey('abortion-confirm')));
  await tester.pumpAndSettle();
  await _takeScreenshot(binding, tester, screenshot);

  final submit = find.byKey(const ValueKey('abortion-submit'));
  await tester.ensureVisible(submit);
  await tester.tap(submit);
  await _waitFor(tester, find.textContaining('已记录流产'));
  await tester.pumpAndSettle();
}

/// 回首页并确保滚到顶部。
///
/// 首页是惰加载 ListView，前面找待办时已经把它拖到下方；而「今日生产」在最顶部。
/// 已经停在首页时再点一次底部导航不会重置滚动位置，于是控件真存在却搜不到。
/// 这是取景问题，不是业务状态问题，所以先拉回顶部再断言。
Future<void> _backToHomeTop(WidgetTester tester) async {
  // 上一步的输入框可能还聚焦，软键盘会盖住底部导航；
  // 此时 tap 落在键盘上而不是导航项，日志里表现为 hit test 不中。
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  final nav = find.byKey(const ValueKey('nav-home'));
  await tester.ensureVisible(nav);
  await tester.tap(nav);
  await tester.pumpAndSettle();
  final target = find.text('今日生产');
  final scrollable = find.byType(Scrollable);
  for (var i = 0; i < 8 && target.evaluate().isEmpty; i++) {
    if (scrollable.evaluate().isEmpty) break;
    await tester.drag(scrollable.first, const Offset(0, 420));
    await tester.pumpAndSettle();
  }
  await _waitFor(tester, target);
}

Future<void> _scrollBatchDetailUntilVisible(
  WidgetTester tester,
  Finder target,
) async {
  final list = find.byKey(const ValueKey('batch-detail-member-list'));
  await _waitFor(tester, list);
  if (target.evaluate().isNotEmpty) return;

  // 提交后 provider 会失效重拉，列表在重建的那一瞬间没有 Scrollable，
  // 此时 scrollUntilVisible 会直接抛 Bad state: No element。
  // 这是时序抖动，不是被测行为，所以重试而不是失败。
  Object? lastError;
  for (var attempt = 0; attempt < 5; attempt++) {
    final scrollable =
        find.descendant(of: list, matching: find.byType(Scrollable));
    if (scrollable.evaluate().isEmpty) {
      await tester.pump(const Duration(milliseconds: 200));
      continue;
    }
    try {
      await tester.scrollUntilVisible(
        target,
        280,
        scrollable: scrollable.first,
      );
      await tester.pumpAndSettle();
      return;
    } catch (error) {
      lastError = error;
      await tester.pump(const Duration(milliseconds: 200));
    }
  }
  if (target.evaluate().isEmpty) {
    fail('滚动至 $target 失败（最后一次错误：$lastError）');
  }
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

/// 一头母兔在某个时刻的可观测状态。
class _DoeState {
  const _DoeState({
    required this.stage,
    required this.cycleId,
    required this.activeMember,
    required this.pendingTask,
  });

  /// rabbits.current_stage（未入轨为空串）。
  final String stage;
  final int? cycleId;
  final bool activeMember;

  /// 当前未完成待办的类型（无则为空串）。
  final String pendingTask;

  /// 断言时只比阶段与待办：周期 id 每轮都会变，写死它只会让用例脆弱。
  String get signature => '$stage/$pendingTask';

  @override
  String toString() =>
      '阶段=$stage 待办=${pendingTask.isEmpty ? '无' : pendingTask}'
      ' 周期=${cycleId ?? '-'} 在批=${activeMember ? '是' : '否'}';
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

  /// 批次下每头母兔的可观测状态快照。
  ///
  /// 同时拉取成员列表（阶段与当前周期来自 rabbits 投影列）与待办中心，
  /// 因为「阶段对了但没待办」与「有待办但阶段不对」都是真实发生过的故障，
  /// 只看其中一边都会漏。待办用足够远的 dueBefore，否则默认只返回今日及逆期。
  Future<Map<int, _DoeState>> batchDoeStates(int batchId) async {
    final members = await _get('/api/batches/$batchId/batch-rabbits');
    final tasks = await _get(
      '/api/tasks',
      query: {
        'batchId': batchId,
        'size': 200,
        'dueBefore': DateTime.now()
            .add(const Duration(days: 3650))
            .millisecondsSinceEpoch,
      },
    );
    final taskByRabbit = <int, String>{};
    for (final raw in ((tasks as Map)['items'] as List? ?? const [])) {
      final task = Map<String, dynamic>.from(raw as Map);
      final rabbitId = (task['rabbitId'] as num?)?.toInt();
      if (rabbitId != null) {
        taskByRabbit[rabbitId] = task['taskType']?.toString() ?? '';
      }
    }

    final states = <int, _DoeState>{};
    for (final raw in (members as List)) {
      final row = Map<String, dynamic>.from(raw as Map);
      if (row['batchRole'] != 'breeding') {
        continue;
      }
      final rabbitId = (row['rabbitId'] as num).toInt();
      states[rabbitId] = _DoeState(
        stage: row['currentStage']?.toString() ?? '',
        cycleId: (row['currentCycleId'] as num?)?.toInt(),
        activeMember: row['isActive'] == true,
        pendingTask: taskByRabbit[rabbitId] ?? '',
      );
    }
    return states;
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
