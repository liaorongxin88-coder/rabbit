import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/main.dart' as app;
import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';

const _runId = String.fromEnvironment('RABBIT_E2E_RUN_ID');
const _suite = String.fromEnvironment(
  'RABBIT_E2E_SUITE',
  defaultValue: 'baseline',
);
const _scenariosJson = String.fromEnvironment('RABBIT_E2E_SCENARIOS_JSON');
const _usersJson = String.fromEnvironment('RABBIT_E2E_USERS_JSON');
const _userName = String.fromEnvironment('RABBIT_E2E_USERNAME');
const _password = String.fromEnvironment('RABBIT_E2E_PASSWORD');
const _houseId = int.fromEnvironment('RABBIT_E2E_HOUSE_ID');
const _batchId = int.fromEnvironment('RABBIT_E2E_BATCH_ID');

const _groups = <_ExpectedGroup>[
  _ExpectedGroup(
    stage: 'MATING',
    screenshotName: '01-statistics-mating',
    metrics: [
      _ExpectedMetric('MATING_DATE', '2024-04-22'),
      _ExpectedMetric('MATED_DOE_COUNT', '1,230'),
      _ExpectedMetric('CONCEPTION_RATE', '86.10%'),
      _ExpectedMetric('DOE_BUCK_RATIO', '20.50:1'),
    ],
  ),
  _ExpectedGroup(
    stage: 'PREGNANCY',
    screenshotName: '02-statistics-pregnancy',
    metrics: [
      _ExpectedMetric('PREGNANT_DOE_COUNT', '1,059'),
      _ExpectedMetric('ABORTION_RATE', '1.98%'),
    ],
  ),
  _ExpectedGroup(
    stage: 'BIRTH',
    screenshotName: '03-statistics-birth',
    metrics: [
      _ExpectedMetric('DELIVERED_LITTER_COUNT', '1,004'),
      _ExpectedMetric('TOTAL_KIT_COUNT', '10,040'),
      _ExpectedMetric('AVERAGE_KITS_PER_LITTER', '10.00'),
      _ExpectedMetric('LIVE_KIT_COUNT', '9,870'),
      _ExpectedMetric('LIVE_BIRTH_RATE', '98.31%'),
    ],
  ),
  _ExpectedGroup(
    stage: 'SELECTION',
    screenshotName: '04-statistics-selection',
    metrics: [
      _ExpectedMetric('KEPT_LITTER_COUNT', '987'),
      _ExpectedMetric('KEPT_KIT_COUNT', '9,490'),
      _ExpectedMetric('KEPT_LIVE_RATE', '96.15%'),
      _ExpectedMetric('AVERAGE_KEPT_PER_LITTER', '9.61'),
    ],
  ),
  _ExpectedGroup(
    stage: 'WEANING',
    screenshotName: '05-statistics-weaning',
    metrics: [
      _ExpectedMetric('WEANED_KIT_COUNT', '8,604'),
      _ExpectedMetric('AVERAGE_WEANING_WEIGHT', '0.74 kg'),
      _ExpectedMetric('WEANING_SURVIVAL_RATE', '90.66%'),
    ],
  ),
  _ExpectedGroup(
    stage: 'OUTBOUND',
    screenshotName: '06-statistics-outbound',
    metrics: [
      _ExpectedMetric('SOLD_RABBIT_COUNT', '6,834'),
      _ExpectedMetric('OUTBOUND_SURVIVAL_RATE', '79.43%'),
      _ExpectedMetric('SOLD_WEIGHT', '13,095.00 kg'),
      _ExpectedMetric('AVERAGE_SOLD_WEIGHT', '1.92 kg'),
    ],
  ),
  _ExpectedGroup(
    stage: 'SALES',
    screenshotName: '07-statistics-sales',
    metrics: [
      _ExpectedMetric('TOTAL_SALES_AMOUNT', '157,140.00 元'),
      _ExpectedMetric('SALES_PRICE_PER_KG', '12.00 元/kg'),
      _ExpectedMetric('SALES_PRICE_PER_RABBIT', '22.99 元/只'),
    ],
  ),
  _ExpectedGroup(
    stage: 'FEED_CONVERSION',
    screenshotName: '08-statistics-feed-conversion',
    metrics: [
      _ExpectedMetric('FULL_FEED_CONVERSION_RATIO', '3.68'),
      _ExpectedMetric('FATTENING_FEED_CONVERSION_RATIO', '3.84'),
      _ExpectedMetric('CARCASS_YIELD_RATE', '56.00%'),
    ],
  ),
];

List<_ExpectedMetric> get _metrics => [
      for (final group in _groups) ...group.metrics,
    ];

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'Android renders the acceptance fixture batch statistics',
    (tester) async {
      if (_suite == 'complex') {
        await _runComplexMatrix(binding, tester);
        return;
      }
      if (_suite != 'baseline') {
        fail('Unsupported RABBIT_E2E_SUITE: $_suite');
      }
      await _runBaseline(binding, tester);
    },
  );
}

Future<void> _runBaseline(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
) async {
  _assertFixtureDefines();
  await _clearLocalAppState();
  await app.main();
  await binding.convertFlutterSurfaceToImage();

  _assertPortrait(tester);
  await _waitFor(tester, find.byKey(const ValueKey('login-mode-selector')));
  await _login(
    tester,
    const _TestUser(
      role: 'OWNER',
      userName: _userName,
      password: _password,
      houseId: _houseId,
    ),
  );
  await _openBatchStatistics(tester, houseId: _houseId, batchId: _batchId);

  await _expectMetricOrderValuesAndCauses(tester, _metrics);

  final screenshotNames = <String>[];
  final actionEvidence = await _inspectAuthorizedActions(
    binding,
    tester,
    screenshotNames,
  );

  for (final group in _groups) {
    final groupFinder = find.byKey(
      ValueKey('batch-statistics-group-${group.stage}'),
    );
    await _alignAtTop(tester, groupFinder);
    _expectGroup(group, groupFinder);
    await _takeScreenshot(binding, tester, group.screenshotName);
    screenshotNames.add(group.screenshotName);
  }

  expect(tester.takeException(), isNull);
  binding.reportData ??= <String, dynamic>{};
  binding.reportData!.addAll(<String, dynamic>{
    'runId': _runId,
    'houseId': _houseId,
    'batchId': _batchId,
    'metricCodes': _metrics.map((metric) => metric.code).toList(),
    'metricDisplayValues': <String, String>{
      for (final metric in _metrics) metric.code: metric.displayValue!,
    },
    'groupStages': _groups.map((group) => group.stage).toList(),
    'screenshotNames': screenshotNames,
    'fixtureMutated': false,
    ...actionEvidence,
  });
}

Future<void> _runComplexMatrix(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
) async {
  if (_runId.isEmpty) {
    fail('Missing --dart-define=RABBIT_E2E_RUN_ID');
  }
  final scenarios = _parseComplexScenarios();
  final users = _parseComplexUsers(scenarios.first.houseId);
  final owner = users.singleWhere((user) => user.role == 'OWNER');
  final readOnly = users.singleWhere((user) => user.role == 'READ_ONLY');
  final outsider = users.singleWhere((user) => user.role == 'OUTSIDER');
  final securityScenario = scenarios.singleWhere(
    (scenario) => scenario.id == 'security-and-retry',
  );
  final supportScenario = scenarios.singleWhere(
    (scenario) => scenario.batchRole == 'support',
  );

  await _clearLocalAppState();
  await app.main();
  await binding.convertFlutterSurfaceToImage();
  _assertPortrait(tester);
  await _waitFor(tester, find.byKey(const ValueKey('login-mode-selector')));
  await _login(tester, owner);

  final screenshotNames = <String>[];
  final scenarioResults = <Map<String, dynamic>>[];
  Map<String, dynamic>? ownerSecurityEvidence;
  for (final scenario in scenarios) {
    await _openScenario(tester, scenario);
    await _expectScenario(tester, scenario);

    final scenarioScreenshots = <String>[];
    if (scenario.batchRole == 'primary') {
      for (final group in scenario.groups) {
        final groupFinder = find.byKey(
          ValueKey('batch-statistics-group-${group.stage}'),
        );
        await _alignAtTop(tester, groupFinder);
        _expectGroup(group, groupFinder);
        final name = '${scenario.id}-${group.screenshotName}';
        await _takeComplexScreenshot(binding, tester, name);
        screenshotNames.add(name);
        scenarioScreenshots.add(name);
      }
      if (scenario.id == 'mixed-data-quality') {
        final name = await _captureMissingCauseEvidence(
          binding,
          tester,
          scenario,
        );
        screenshotNames.add(name);
        scenarioScreenshots.add(name);
      }
    } else {
      final salesGroup = scenario.groups.singleWhere(
        (group) => group.stage == 'SALES',
      );
      final groupFinder = find.byKey(
        ValueKey('batch-statistics-group-${salesGroup.stage}'),
      );
      await _alignAtTop(tester, groupFinder);
      _expectGroup(salesGroup, groupFinder);
      final name = '${scenario.id}-support-sales';
      await _takeComplexScreenshot(binding, tester, name);
      screenshotNames.add(name);
      scenarioScreenshots.add(name);
    }

    if (scenario.id == securityScenario.id) {
      ownerSecurityEvidence = await _inspectOwnerSecurity(
        binding,
        tester,
        scenario,
        screenshotNames,
        scenarioScreenshots,
      );
    }
    scenarioResults.add(_scenarioResult(scenario, scenarioScreenshots));
  }

  await _logoutAndClearLocalAuth(tester);
  await _login(tester, readOnly);
  await _openScenario(tester, securityScenario);
  await _expectScenario(tester, securityScenario);
  _expectReadOnlyActions();
  final readOnlyWorkbookEvidence = await _downloadReadOnlyWorkbook(
    tester,
    securityScenario,
  );
  const readOnlyScreenshot = 'security-read-only';
  await _takeComplexScreenshot(binding, tester, readOnlyScreenshot);
  screenshotNames.add(readOnlyScreenshot);

  await _logoutAndClearLocalAuth(tester);
  await _login(tester, outsider);
  await _goTo(
    tester,
    '/houses/${securityScenario.houseId}/batches/${securityScenario.batchId}',
  );
  await _waitFor(
    tester,
    find.text('加载失败'),
    timeout: const Duration(seconds: 40),
  );
  expect(find.text('无兔场权限'), findsOneWidget);
  expect(find.byKey(const ValueKey('batch-statistics-content')), findsNothing);
  expect(find.text(securityScenario.batchCode), findsNothing);
  const outsiderScreenshot = 'security-outsider-denied';
  await _takeComplexScreenshot(binding, tester, outsiderScreenshot);
  screenshotNames.add(outsiderScreenshot);

  expect(tester.takeException(), isNull);
  binding.reportData ??= <String, dynamic>{};
  binding.reportData!.addAll(<String, dynamic>{
    'runId': _runId,
    'suite': 'complex',
    'houseId': securityScenario.houseId,
    'batchId': securityScenario.batchId,
    'metricCodes': _metrics.map((metric) => metric.code).toList(),
    'groupStages': _groups.map((group) => group.stage).toList(),
    'scenarioResults': scenarioResults,
    'screenshotNames': screenshotNames,
    'supportScenarioId': supportScenario.id,
    'securityEvidence': <String, dynamic>{
      'owner': ownerSecurityEvidence,
      'readOnly': <String, dynamic>{
        'statisticsVisible': true,
        'editVisible': false,
        'historyVisible': false,
        'exportVisible': true,
        ...readOnlyWorkbookEvidence,
        'screenshotName': readOnlyScreenshot,
      },
      'outsider': <String, dynamic>{
        'accessDenied': true,
        'message': '无兔场权限',
        'targetDataVisible': false,
        'screenshotName': outsiderScreenshot,
      },
    },
    'fixtureMutated': false,
  });
}

void _assertFixtureDefines() {
  for (final entry in <String, String>{
    'RABBIT_E2E_RUN_ID': _runId,
    'RABBIT_E2E_USERNAME': _userName,
    'RABBIT_E2E_PASSWORD': _password,
  }.entries) {
    if (entry.value.isEmpty) {
      fail('Missing --dart-define=${entry.key}');
    }
  }
  if (_houseId <= 0) {
    fail('Missing --dart-define=RABBIT_E2E_HOUSE_ID');
  }
  if (_batchId <= 0) {
    fail('Missing --dart-define=RABBIT_E2E_BATCH_ID');
  }
}

const _primaryScenarioIds = <String>[
  'complex-available',
  'mixed-data-quality',
  'mixed-batch-rounding',
  'time-and-cycle-boundaries',
  'security-and-retry',
];
const _supportScenarioId = 'mixed-batch-rounding-support';

List<_ComplexScenario> _parseComplexScenarios() {
  final decoded = _decodeJsonList(
    _scenariosJson,
    'RABBIT_E2E_SCENARIOS_JSON',
  );
  final scenarios = decoded
      .map((item) => _ComplexScenario.fromJson(_jsonObject(item, 'scenario')))
      .toList(growable: false);
  if (scenarios.length != 6) {
    fail('RABBIT_E2E_SCENARIOS_JSON must contain exactly 6 scenarios');
  }
  final byId = <String, _ComplexScenario>{};
  for (final scenario in scenarios) {
    if (byId[scenario.id] != null) {
      fail('Duplicate complex scenario id: ${scenario.id}');
    }
    byId[scenario.id] = scenario;
  }
  final ordered = <_ComplexScenario>[];
  for (final id in _primaryScenarioIds) {
    final scenario = byId[id];
    if (scenario == null || scenario.batchRole != 'primary') {
      fail('Missing primary complex scenario: $id');
    }
    ordered.add(scenario);
  }
  final support = byId[_supportScenarioId];
  if (support == null || support.batchRole != 'support') {
    fail('Missing rounding support scenario: $_supportScenarioId');
  }
  ordered.add(support);
  if (byId.length != ordered.length) {
    fail('RABBIT_E2E_SCENARIOS_JSON contains an unknown scenario');
  }

  final targetHouseId = ordered.first.houseId;
  if (ordered.any((scenario) => scenario.houseId != targetHouseId)) {
    fail('All complex scenarios must belong to one target house');
  }
  final batchIds = ordered.map((scenario) => scenario.batchId).toSet();
  if (batchIds.length != ordered.length) {
    fail('Complex scenarios must use 6 distinct batch IDs');
  }
  return ordered;
}

List<_TestUser> _parseComplexUsers(int targetHouseId) {
  final decoded = _decodeJsonList(_usersJson, 'RABBIT_E2E_USERS_JSON');
  final users = decoded
      .map((item) => _TestUser.fromJson(_jsonObject(item, 'user')))
      .toList(growable: false);
  const expectedRoles = {'OWNER', 'READ_ONLY', 'OUTSIDER'};
  if (users.length != expectedRoles.length ||
      users.map((user) => user.role).toSet().length != expectedRoles.length ||
      !users.map((user) => user.role).toSet().containsAll(expectedRoles)) {
    fail('RABBIT_E2E_USERS_JSON must contain OWNER, READ_ONLY, and OUTSIDER');
  }
  final owner = users.singleWhere((user) => user.role == 'OWNER');
  final readOnly = users.singleWhere((user) => user.role == 'READ_ONLY');
  final outsider = users.singleWhere((user) => user.role == 'OUTSIDER');
  if (owner.houseId != targetHouseId || readOnly.houseId != targetHouseId) {
    fail('OWNER and READ_ONLY must use the complex target house');
  }
  if (outsider.houseId == targetHouseId) {
    fail('OUTSIDER must use the isolation house');
  }
  return users;
}

List<dynamic> _decodeJsonList(String source, String defineName) {
  if (source.isEmpty) {
    fail('Missing --dart-define=$defineName');
  }
  Object? decoded;
  try {
    decoded = jsonDecode(source);
  } catch (_) {
    fail('$defineName must contain valid JSON');
  }
  if (decoded is! List) {
    fail('$defineName must contain a JSON array');
  }
  return decoded;
}

Map<String, dynamic> _jsonObject(Object? value, String label) {
  if (value is! Map) {
    fail('Complex $label must be a JSON object');
  }
  return Map<String, dynamic>.from(value);
}

String _requiredJsonText(
  Map<String, dynamic> json,
  String field,
  String label,
) {
  final value = json[field];
  if (value is! String || value.trim().isEmpty) {
    fail('Complex $label requires non-empty $field');
  }
  return value.trim();
}

int _requiredJsonId(
  Map<String, dynamic> json,
  String field,
  String label,
) {
  final value = json[field];
  if (value is! num || !value.isFinite || value != value.roundToDouble()) {
    fail('Complex $label requires integer $field');
  }
  final parsed = value.toInt();
  if (parsed <= 0) {
    fail('Complex $label requires positive $field');
  }
  return parsed;
}

Future<void> _clearLocalAppState() async {
  final preferences = await SharedPreferences.getInstance();
  await preferences.clear();
  await const FlutterSecureStorage().deleteAll();
}

Future<void> _login(WidgetTester tester, _TestUser user) async {
  await tester.tap(
    find.descendant(
      of: find.byKey(const ValueKey('login-mode-selector')),
      matching: find.text('账号'),
    ),
  );
  await _pumpFrames(tester);
  await _waitFor(
    tester,
    find.byKey(const ValueKey('account-login')),
  );
  await _waitForCaptchaDisabled(tester);

  await tester.enterText(
    find.byKey(const ValueKey('account-username-field')),
    user.userName,
  );
  await tester.enterText(
    find.byKey(const ValueKey('account-password-field')),
    user.password,
  );
  FocusManager.instance.primaryFocus?.unfocus();
  await _pumpFrames(tester);

  final consent = find.byKey(const ValueKey('legal-consent-checkbox'));
  for (var attempt = 0; attempt < 8 && consent.evaluate().isEmpty; attempt++) {
    final scrollables = find.byType(Scrollable);
    if (scrollables.evaluate().isEmpty) break;
    await tester.drag(scrollables.first, const Offset(0, -120));
    await _pumpFrames(tester);
  }
  await _waitFor(tester, consent);
  await tester.ensureVisible(consent);
  await tester.tap(consent);
  await _pumpFrames(tester);

  final loginButton = find.byKey(const ValueKey('account-login-button'));
  await tester.ensureVisible(loginButton);
  await tester.tap(loginButton);
  await _waitFor(
    tester,
    find.byKey(const ValueKey('nav-houses')),
    timeout: const Duration(seconds: 40),
  );
}

Future<void> _waitForCaptchaDisabled(WidgetTester tester) async {
  final captcha = find.byKey(const ValueKey('image-captcha-field'));
  final deadline = DateTime.now().add(const Duration(seconds: 20));
  DateTime? absentSince;
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(milliseconds: 100));
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 50)),
    );
    if (captcha.evaluate().isEmpty) {
      absentSince ??= DateTime.now();
      if (DateTime.now().difference(absentSince) >=
          const Duration(milliseconds: 600)) {
        return;
      }
    } else {
      absentSince = null;
    }
  }
  fail(
    'The fixture backend must disable image captcha with business code 501. '
    '${_visibleTexts()}',
  );
}

Future<void> _goTo(WidgetTester tester, String location) async {
  FocusManager.instance.primaryFocus?.unfocus();
  await _pumpFrames(tester);
  final scaffold = find.byType(Scaffold);
  await _waitFor(tester, scaffold);
  GoRouter.of(tester.element(scaffold.first)).go(location);
  await tester.pump();
}

Future<void> _revealInDetail(WidgetTester tester, Finder target) async {
  final list = find.byKey(const ValueKey('batch-detail-member-list'));
  await _waitFor(tester, list);
  for (var attempt = 0; attempt < 24; attempt++) {
    if (target.evaluate().isNotEmpty) {
      await tester.ensureVisible(target.first);
      await _pumpFrames(tester);
      return;
    }
    await tester.drag(list, const Offset(0, -280));
    await tester.pump(const Duration(milliseconds: 120));
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 50)),
    );
  }
  fail('Could not reveal $target. ${_visibleTexts()}');
}

Future<void> _waitForStatistics(WidgetTester tester) async {
  final content = find.byKey(const ValueKey('batch-statistics-content'));
  final error = find.byKey(const ValueKey('batch-statistics-error'));
  final deadline = DateTime.now().add(const Duration(seconds: 60));
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(milliseconds: 100));
    if (content.evaluate().isNotEmpty) return;
    if (error.evaluate().isNotEmpty) {
      fail('Batch statistics failed to load. ${_visibleTexts()}');
    }
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 50)),
    );
  }
  fail('Timed out waiting for batch statistics. ${_visibleTexts()}');
}

Future<void> _expectMetricOrderValuesAndCauses(
  WidgetTester tester,
  List<_ExpectedMetric> metrics,
) async {
  final renderedMetricKeys = find
      .byWidgetPredicate((widget) {
        final key = widget.key;
        return key is ValueKey<String> &&
            key.value.startsWith('batch-statistic-') &&
            !key.value.startsWith('batch-statistic-details-');
      })
      .evaluate()
      .map((element) => (element.widget.key! as ValueKey<String>).value)
      .toList(growable: false);

  expect(
    renderedMetricKeys,
    metrics.map((metric) => 'batch-statistic-${metric.code}').toList(),
    reason: 'The 28 fixed metrics must render in the approved order',
  );

  for (final metric in metrics) {
    final metricFinder = find.byKey(ValueKey('batch-statistic-${metric.code}'));
    expect(metricFinder, findsOneWidget, reason: metric.code);
    final statusText = find.descendant(
      of: metricFinder,
      matching: find.text(_statusLabel(metric.status)),
    );
    // Unavailable metrics repeat the state in the badge and primary value.
    expect(
      statusText,
      findsNWidgets(metric.status == 'AVAILABLE' ? 1 : 2),
      reason: '${metric.code} status',
    );
    if (metric.status == 'AVAILABLE') {
      expect(
        find.descendant(
          of: metricFinder,
          matching: find.text(metric.visibleValue),
        ),
        findsOneWidget,
        reason: '${metric.code} visible value',
      );
    }
    if (metric.missingCauses.isNotEmpty) {
      await _expectMetricMissingCauses(tester, metric);
    }
  }
}

Future<void> _expectMetricMissingCauses(
  WidgetTester tester,
  _ExpectedMetric metric,
) async {
  final details = find.byKey(
    ValueKey('batch-statistic-details-${metric.code}'),
  );
  await _alignAtTop(tester, details);
  await tester.tap(details);
  await _pumpFrames(tester);
  final causeTexts = find
      .descendant(of: details, matching: find.byType(Text))
      .evaluate()
      .map((element) {
        final text = element.widget as Text;
        return text.data ?? text.textSpan?.toPlainText() ?? '';
      })
      .where((text) => RegExp(r'（[A-Z_]+）$').hasMatch(text))
      .toList(growable: false);
  expect(
    causeTexts,
    metric.missingCauses.map((cause) => cause.visibleText).toList(),
    reason: '${metric.code} missing cause order',
  );
  await tester.tap(details);
  await _pumpFrames(tester);
}

void _expectGroup(_ExpectedGroup group, Finder groupFinder) {
  expect(groupFinder, findsOneWidget);
  final renderedCodes = find
      .descendant(
        of: groupFinder,
        matching: find.byWidgetPredicate((widget) {
          final key = widget.key;
          return key is ValueKey<String> &&
              key.value.startsWith('batch-statistic-') &&
              !key.value.startsWith('batch-statistic-details-');
        }),
      )
      .evaluate()
      .map(
        (element) => (element.widget.key! as ValueKey<String>)
            .value
            .substring('batch-statistic-'.length),
      )
      .toList(growable: false);
  expect(
    renderedCodes,
    group.metrics.map((metric) => metric.code).toList(),
    reason: '${group.stage} metric order',
  );
}

Future<void> _openBatchStatistics(
  WidgetTester tester, {
  required int houseId,
  required int batchId,
}) async {
  await _goTo(tester, '/houses/$houseId/batches/$batchId');
  final detailList = find.byKey(const ValueKey('batch-detail-member-list'));
  await _waitFor(tester, detailList, timeout: const Duration(seconds: 40));
  await _revealInDetail(tester, find.text('批次统计'));
  await _waitForStatistics(tester);
}

Future<void> _openScenario(
  WidgetTester tester,
  _ComplexScenario scenario,
) async {
  await _openBatchStatistics(
    tester,
    houseId: scenario.houseId,
    batchId: scenario.batchId,
  );
  final content = find.byKey(const ValueKey('batch-statistics-content'));
  expect(
    find.descendant(
      of: content,
      matching: find.textContaining(scenario.batchCode),
    ),
    findsOneWidget,
    reason: '${scenario.id} batch code',
  );
}

Future<void> _expectScenario(
  WidgetTester tester,
  _ComplexScenario scenario,
) async {
  final renderedGroupKeys = find
      .byWidgetPredicate((widget) {
        final key = widget.key;
        return key is ValueKey<String> &&
            key.value.startsWith('batch-statistics-group-');
      })
      .evaluate()
      .map((element) => (element.widget.key! as ValueKey<String>).value)
      .toList(growable: false);
  expect(
    renderedGroupKeys,
    scenario.groups
        .map((group) => 'batch-statistics-group-${group.stage}')
        .toList(),
    reason: '${scenario.id} group order',
  );
  await _expectMetricOrderValuesAndCauses(tester, scenario.metrics);
}

Future<String> _captureMissingCauseEvidence(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
  _ComplexScenario scenario,
) async {
  final candidates = scenario.metrics
      .where((metric) => metric.missingCauses.isNotEmpty)
      .toList(growable: false)
    ..sort(
      (first, second) =>
          second.missingCauses.length.compareTo(first.missingCauses.length),
    );
  if (candidates.isEmpty) {
    fail('${scenario.id} must include unavailable metrics');
  }
  final metric = candidates.first;
  final details = find.byKey(
    ValueKey('batch-statistic-details-${metric.code}'),
  );
  await _alignAtTop(tester, details);
  await tester.tap(details);
  await _pumpFrames(tester);
  for (final cause in metric.missingCauses) {
    expect(
      find.descendant(of: details, matching: find.text(cause.visibleText)),
      findsOneWidget,
    );
  }
  final name = '${scenario.id}-missing-causes';
  await _takeComplexScreenshot(binding, tester, name);
  await tester.tap(details);
  await _pumpFrames(tester);
  return name;
}

Future<Map<String, dynamic>> _inspectOwnerSecurity(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
  _ComplexScenario scenario,
  List<String> allScreenshotNames,
  List<String> scenarioScreenshotNames,
) async {
  final edit = find.byKey(const ValueKey('batch-carcass-yield-edit'));
  final history = find.byKey(const ValueKey('batch-carcass-yield-history'));
  final export = find.byKey(const ValueKey('batch-statistics-export'));
  expect(edit, findsOneWidget);
  expect(history, findsOneWidget);
  expect(export, findsOneWidget);

  await _alignAtTop(tester, edit);
  const actionsScreenshot = 'security-owner-actions';
  await _takeComplexScreenshot(binding, tester, actionsScreenshot);
  allScreenshotNames.add(actionsScreenshot);
  scenarioScreenshotNames.add(actionsScreenshot);

  await tester.tap(history);
  await _waitFor(tester, find.text('出肉率版本历史'));
  await _waitFor(tester, find.textContaining('共 1 条'));
  final carcassMetric = scenario.metrics.singleWhere(
    (metric) => metric.code == 'CARCASS_YIELD_RATE',
  );
  expect(
    find.textContaining('${carcassMetric.visibleValue} ·'),
    findsOneWidget,
    reason: 'OWNER must see the unique security carcass-yield version',
  );
  const historyScreenshot = 'security-owner-history';
  await _takeComplexScreenshot(binding, tester, historyScreenshot);
  allScreenshotNames.add(historyScreenshot);
  scenarioScreenshotNames.add(historyScreenshot);
  await tester.tap(find.byTooltip('关闭').last);
  await _pumpFrames(tester);
  await _waitFor(tester, history);

  return <String, dynamic>{
    'editVisible': true,
    'historyVisible': true,
    'exportVisible': true,
    'historyVersionCount': 1,
    'screenshotNames': <String>[
      actionsScreenshot,
      historyScreenshot,
    ],
  };
}

void _expectReadOnlyActions() {
  for (final key in <String>[
    'batch-carcass-yield-edit',
    'batch-carcass-yield-history',
    'batch-rename-button',
    'batch-add-members-button',
    'batch-complete-button',
  ]) {
    expect(find.byKey(ValueKey(key)), findsNothing,
        reason: '$key is read-only');
  }
  expect(
    find.byKey(const ValueKey('batch-statistics-export')),
    findsOneWidget,
    reason: 'READ_ONLY must see the statistics export entrance',
  );
}

Future<Map<String, dynamic>> _downloadReadOnlyWorkbook(
  WidgetTester tester,
  _ComplexScenario scenario,
) async {
  final content = find.byKey(const ValueKey('batch-statistics-content'));
  await _waitFor(tester, content);
  final container = ProviderScope.containerOf(tester.element(content));
  final evidence = await tester.runAsync<Map<String, dynamic>>(() async {
    final file =
        await container.read(batchRepositoryProvider).downloadBatchStatistics(
              houseId: scenario.houseId,
              batchId: scenario.batchId,
            );
    try {
      final bytes = await file.readAsBytes();
      if (bytes.length < 2 || ascii.decode(bytes.sublist(0, 2)) != 'PK') {
        fail('READ_ONLY batch statistics export is not an OOXML workbook');
      }
      return <String, dynamic>{
        'xlsxDownloaded': true,
        'xlsxByteLength': bytes.length,
        'xlsxZipSignature': 'PK',
      };
    } finally {
      if (await file.exists()) await file.delete();
    }
  });
  if (evidence == null) {
    fail('READ_ONLY batch statistics export did not complete');
  }
  return evidence;
}

Future<void> _logoutAndClearLocalAuth(WidgetTester tester) async {
  final profile = find.byKey(const ValueKey('nav-profile'));
  await _waitFor(tester, profile);
  await tester.tap(profile);
  await _pumpFrames(tester);
  final logout = find.byKey(const ValueKey('profile-logout-button'));
  await _waitFor(tester, logout);
  await tester.ensureVisible(logout);
  await tester.tap(logout);
  await _waitFor(tester, find.byKey(const ValueKey('login-mode-selector')));
  await _clearLocalAppState();
}

Map<String, dynamic> _scenarioResult(
  _ComplexScenario scenario,
  List<String> screenshotNames,
) {
  return <String, dynamic>{
    'id': scenario.id,
    'batchRole': scenario.batchRole,
    'houseId': scenario.houseId,
    'batchId': scenario.batchId,
    'batchCode': scenario.batchCode,
    'metricCodes': scenario.metrics.map((metric) => metric.code).toList(),
    'metricDisplayValues': <String, String?>{
      for (final metric in scenario.metrics) metric.code: metric.displayValue,
    },
    'metricVisibleValues': <String, String>{
      for (final metric in scenario.metrics) metric.code: metric.visibleValue,
    },
    'metricStatuses': <String, String>{
      for (final metric in scenario.metrics) metric.code: metric.status,
    },
    'metricMissingCauses': <String, List<Map<String, String>>>{
      for (final metric in scenario.metrics)
        metric.code: metric.missingCauses
            .map((cause) => cause.toJson())
            .toList(growable: false),
    },
    'groupStages': scenario.groups.map((group) => group.stage).toList(),
    'screenshotNames': screenshotNames,
  };
}

Future<void> _takeComplexScreenshot(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
  String name,
) async {
  await _takeScreenshot(binding, tester, name);
}

Future<Map<String, dynamic>> _inspectAuthorizedActions(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
  List<String> screenshotNames,
) async {
  final edit = find.byKey(const ValueKey('batch-carcass-yield-edit'));
  final history = find.byKey(const ValueKey('batch-carcass-yield-history'));
  final export = find.byKey(const ValueKey('batch-statistics-export'));
  final evidence = <String, dynamic>{
    'carcassYieldFormInspected': false,
    'carcassYieldHistoryInspected': false,
    'exportEntranceVisible': export.evaluate().isNotEmpty,
  };

  if (export.evaluate().isNotEmpty) {
    await _alignAtTop(tester, export);
    expect(
      find.descendant(of: export, matching: find.text('导出 Excel')),
      findsOneWidget,
    );
    const name = '00-statistics-export-entrance';
    await _takeScreenshot(binding, tester, name);
    screenshotNames.add(name);
  }

  if (edit.evaluate().isNotEmpty) {
    await _alignAtTop(tester, edit);
    await tester.tap(edit);
    final percent = find.byKey(const ValueKey('carcass-yield-percent'));
    await _waitFor(tester, percent);
    expect(find.byKey(const ValueKey('carcass-yield-source')), findsOneWidget);
    expect(find.byKey(const ValueKey('carcass-yield-submit')), findsOneWidget);
    const name = '00a-carcass-yield-form';
    await _takeScreenshot(binding, tester, name);
    screenshotNames.add(name);
    evidence['carcassYieldFormInspected'] = true;
    await tester.tap(find.byTooltip('关闭').last);
    await _pumpFrames(tester);
    await _waitFor(tester, edit);
  }

  if (history.evaluate().isNotEmpty) {
    await _alignAtTop(tester, history);
    await tester.tap(history);
    await _waitFor(tester, find.text('出肉率版本历史'));
    await _waitFor(tester, find.textContaining('56.00% · 测试屠宰场'));
    const name = '00b-carcass-yield-history';
    await _takeScreenshot(binding, tester, name);
    screenshotNames.add(name);
    evidence['carcassYieldHistoryInspected'] = true;
    await tester.tap(find.byTooltip('关闭').last);
    await _pumpFrames(tester);
    await _waitFor(tester, history);
  }

  return evidence;
}

Future<void> _alignAtTop(WidgetTester tester, Finder finder) async {
  await _waitFor(tester, finder);
  await tester.ensureVisible(finder.first);
  await _pumpFrames(tester);
}

Future<void> _pumpFrames(
  WidgetTester tester, {
  Duration duration = const Duration(milliseconds: 350),
}) async {
  final deadline = DateTime.now().add(duration);
  while (DateTime.now().isBefore(deadline)) {
    await tester.pump(const Duration(milliseconds: 50));
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 25)),
    );
  }
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
  fail('Timed out waiting for $finder. ${_visibleTexts()}');
}

Future<void> _takeScreenshot(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
  String name,
) async {
  await _pumpFrames(tester);
  _assertPortrait(tester);
  expect(
    tester.takeException(),
    isNull,
    reason: 'Flutter exception before $name',
  );
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 150)),
  );
  await tester.pump();
  await binding.takeScreenshot(name);
}

void _assertPortrait(WidgetTester tester) {
  final size = tester.view.physicalSize / tester.view.devicePixelRatio;
  expect(
    size.height,
    greaterThan(size.width),
    reason: 'The Android fixture run must use portrait orientation',
  );
}

String _visibleTexts() {
  final texts = <String>[];
  for (final element in find.byType(Text).evaluate()) {
    final widget = element.widget as Text;
    final value = widget.data ?? widget.textSpan?.toPlainText() ?? '';
    if (value.trim().isNotEmpty) texts.add(value.trim());
    if (texts.length >= 40) break;
  }
  return texts.isEmpty
      ? 'No visible text'
      : 'Visible text: ${texts.join(' / ')}';
}

class _ExpectedGroup {
  const _ExpectedGroup({
    required this.stage,
    required this.screenshotName,
    required this.metrics,
  });

  final String stage;
  final String screenshotName;
  final List<_ExpectedMetric> metrics;
}

class _ExpectedMetric {
  const _ExpectedMetric(
    this.code,
    this.displayValue, {
    this.status = 'AVAILABLE',
    this.missingCauses = const [],
  });

  factory _ExpectedMetric.fromJson(Map<String, dynamic> json) {
    final code = _requiredJsonText(json, 'code', 'metric');
    final status = _requiredJsonText(json, 'status', 'metric $code');
    const statuses = {
      'AVAILABLE',
      'NOT_APPLICABLE',
      'NOT_RECORDED',
      'DATA_MISSING',
    };
    if (!statuses.contains(status)) {
      fail('Complex metric $code has unknown status $status');
    }
    final displayValue = json['displayValue'];
    if (displayValue != null && displayValue is! String) {
      fail('Complex metric $code displayValue must be a string or null');
    }
    final rawCauses = json['missingCauses'];
    if (rawCauses is! List) {
      fail('Complex metric $code requires missingCauses');
    }
    final causes = rawCauses
        .map(
          (item) => _ExpectedCause.fromJson(
            _jsonObject(item, 'metric $code missing cause'),
          ),
        )
        .toList(growable: false);
    if (status == 'AVAILABLE') {
      if (displayValue is! String || displayValue.trim().isEmpty) {
        fail('AVAILABLE metric $code requires displayValue');
      }
      if (causes.isNotEmpty) {
        fail('AVAILABLE metric $code cannot contain missingCauses');
      }
    } else if (displayValue != null) {
      fail('Unavailable metric $code must use null displayValue');
    } else if (causes.isEmpty) {
      fail('Unavailable metric $code requires missingCauses');
    }
    return _ExpectedMetric(
      code,
      displayValue as String?,
      status: status,
      missingCauses: List.unmodifiable(causes),
    );
  }

  final String code;
  final String? displayValue;
  final String status;
  final List<_ExpectedCause> missingCauses;

  String get visibleValue => displayValue ?? _statusLabel(status);
}

class _ExpectedCause {
  const _ExpectedCause({required this.code, required this.message});

  factory _ExpectedCause.fromJson(Map<String, dynamic> json) {
    return _ExpectedCause(
      code: _requiredJsonText(json, 'code', 'missing cause'),
      message: _requiredJsonText(json, 'message', 'missing cause'),
    );
  }

  final String code;
  final String message;

  String get visibleText => '$message（$code）';

  Map<String, String> toJson() => <String, String>{
        'code': code,
        'message': message,
      };
}

class _ComplexScenario {
  const _ComplexScenario({
    required this.id,
    required this.batchRole,
    required this.houseId,
    required this.batchId,
    required this.batchCode,
    required this.metrics,
  });

  factory _ComplexScenario.fromJson(Map<String, dynamic> json) {
    final id = _requiredJsonText(json, 'id', 'scenario');
    final batchRole = _requiredJsonText(json, 'batchRole', 'scenario $id');
    if (batchRole != 'primary' && batchRole != 'support') {
      fail('Complex scenario $id has invalid batchRole');
    }
    final rawMetrics = json['metrics'];
    if (rawMetrics is! List) {
      fail('Complex scenario $id requires metrics');
    }
    final metrics = rawMetrics
        .map(
          (item) => _ExpectedMetric.fromJson(
            _jsonObject(item, 'scenario $id metric'),
          ),
        )
        .toList(growable: false);
    final expectedCodes = _metrics.map((metric) => metric.code).toList();
    final actualCodes = metrics.map((metric) => metric.code).toList();
    if (!_sameStrings(actualCodes, expectedCodes)) {
      fail('Complex scenario $id must contain the ordered 28 metric codes');
    }
    return _ComplexScenario(
      id: id,
      batchRole: batchRole,
      houseId: _requiredJsonId(json, 'houseId', 'scenario $id'),
      batchId: _requiredJsonId(json, 'batchId', 'scenario $id'),
      batchCode: _requiredJsonText(json, 'batchCode', 'scenario $id'),
      metrics: List.unmodifiable(metrics),
    );
  }

  final String id;
  final String batchRole;
  final int houseId;
  final int batchId;
  final String batchCode;
  final List<_ExpectedMetric> metrics;

  List<_ExpectedGroup> get groups {
    final byCode = <String, _ExpectedMetric>{
      for (final metric in metrics) metric.code: metric,
    };
    return [
      for (final group in _groups)
        _ExpectedGroup(
          stage: group.stage,
          screenshotName: group.screenshotName,
          metrics: [
            for (final metric in group.metrics) byCode[metric.code]!,
          ],
        ),
    ];
  }
}

class _TestUser {
  const _TestUser({
    required this.role,
    required this.userName,
    required this.password,
    required this.houseId,
  });

  factory _TestUser.fromJson(Map<String, dynamic> json) {
    final role = _requiredJsonText(json, 'role', 'user');
    return _TestUser(
      role: role,
      userName: _requiredJsonText(json, 'userName', 'user $role'),
      password: _requiredJsonText(json, 'password', 'user $role'),
      houseId: _requiredJsonId(json, 'houseId', 'user $role'),
    );
  }

  final String role;
  final String userName;
  final String password;
  final int houseId;
}

bool _sameStrings(List<String> first, List<String> second) {
  if (first.length != second.length) return false;
  for (var index = 0; index < first.length; index++) {
    if (first[index] != second[index]) return false;
  }
  return true;
}

String _statusLabel(String status) => switch (status) {
      'AVAILABLE' => '数据可用',
      'NOT_APPLICABLE' => '暂无可计算数据',
      'NOT_RECORDED' => '未录入',
      'DATA_MISSING' => '历史数据缺失',
      _ => throw StateError('Unknown expected metric status'),
    };
