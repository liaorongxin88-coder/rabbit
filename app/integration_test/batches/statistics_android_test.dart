import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:integration_test/integration_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/main.dart' as app;

const _runId = String.fromEnvironment('RABBIT_E2E_RUN_ID');
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
      _assertFixtureDefines();
      await _clearLocalAppState();
      await app.main();
      await binding.convertFlutterSurfaceToImage();

      _assertPortrait(tester);
      await _waitFor(tester, find.byKey(const ValueKey('login-mode-selector')));
      await _login(tester);
      await _goTo(tester, '/houses/$_houseId/batches/$_batchId');

      final detailList = find.byKey(const ValueKey('batch-detail-member-list'));
      await _waitFor(tester, detailList, timeout: const Duration(seconds: 40));
      await _revealInDetail(tester, find.text('批次统计'));
      await _waitForStatistics(tester);

      _expectMetricOrderAndValues();

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
        await _takeScreenshot(
          binding,
          tester,
          group.screenshotName,
        );
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
          for (final metric in _metrics) metric.code: metric.displayValue,
        },
        'groupStages': _groups.map((group) => group.stage).toList(),
        'screenshotNames': screenshotNames,
        'fixtureMutated': false,
        ...actionEvidence,
      });
    },
  );
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

Future<void> _clearLocalAppState() async {
  final preferences = await SharedPreferences.getInstance();
  await preferences.clear();
  await const FlutterSecureStorage().deleteAll();
}

Future<void> _login(WidgetTester tester) async {
  await tester.tap(find.text('账号'));
  await _pumpFrames(tester);
  await _waitFor(
    tester,
    find.byKey(const ValueKey('account-login')),
  );
  await _waitForCaptchaDisabled(tester);

  await tester.enterText(
    find.byKey(const ValueKey('account-username-field')),
    _userName,
  );
  await tester.enterText(
    find.byKey(const ValueKey('account-password-field')),
    _password,
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

void _expectMetricOrderAndValues() {
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
    _metrics.map((metric) => 'batch-statistic-${metric.code}').toList(),
    reason: 'The 28 fixed metrics must render in the approved order',
  );

  for (final metric in _metrics) {
    final metricFinder = find.byKey(ValueKey('batch-statistic-${metric.code}'));
    expect(metricFinder, findsOneWidget, reason: metric.code);
    expect(
      find.descendant(
        of: metricFinder,
        matching: find.text('数据可用'),
      ),
      findsOneWidget,
      reason: '${metric.code} must be AVAILABLE',
    );
    expect(
      find.descendant(
        of: metricFinder,
        matching: find.text(metric.displayValue),
      ),
      findsOneWidget,
      reason: '${metric.code} display value',
    );
  }
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
  const _ExpectedMetric(this.code, this.displayValue);

  final String code;
  final String displayValue;
}
