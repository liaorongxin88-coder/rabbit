import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/domain/reports/dashboard.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/dashboard/screens/overview.dart';
import 'package:rabbit_flutter/src/ui/dashboard/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';

void main() {
  for (final size in const [Size(360, 800), Size(412, 915)]) {
    testWidgets(
      'dashboard grids stay readable at 200 percent on '
      '${size.width.toInt()}x${size.height.toInt()}',
      (tester) async {
        await tester.binding.setSurfaceSize(size);
        tester.platformDispatcher.textScaleFactorTestValue = 2;
        addTearDown(() => tester.binding.setSurfaceSize(null));
        addTearDown(
          tester.platformDispatcher.clearTextScaleFactorTestValue,
        );

        await tester.pumpWidget(_testApp());
        await tester.pumpAndSettle();
        await tester.scrollUntilVisible(
          find.byKey(const ValueKey('dashboard-metric-grid')),
          300,
          scrollable: find.byType(Scrollable).first,
        );
        await tester.pumpAndSettle();

        final grid = tester.widget<GridView>(
          find.byKey(const ValueKey('dashboard-metric-grid')),
        );
        final delegate =
            grid.gridDelegate as SliverGridDelegateWithFixedCrossAxisCount;
        expect(delegate.crossAxisCount, 2);
        expect(
          delegate.mainAxisExtent,
          greaterThanOrEqualTo(size.width <= 360 ? 190 : 160),
        );
        expect(delegate.mainAxisExtent, lessThanOrEqualTo(220));
        _expectMetricContentInsideCards(tester);
        expect(tester.takeException(), isNull);

        await tester.scrollUntilVisible(
          find.byKey(const ValueKey('dashboard-year-selector')),
          500,
          scrollable: find.byType(Scrollable).first,
        );
        await tester.pumpAndSettle();

        expect(
          find.byKey(const ValueKey('dashboard-year-selector')),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets(
    'dashboard keeps a compact three-column grid at standard text size',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(412, 915));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(_testApp());
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('dashboard-metric-grid')),
        300,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.pumpAndSettle();

      final grid = tester.widget<GridView>(
        find.byKey(const ValueKey('dashboard-metric-grid')),
      );
      final delegate =
          grid.gridDelegate as SliverGridDelegateWithFixedCrossAxisCount;
      expect(delegate.crossAxisCount, 3);
      expect(delegate.mainAxisExtent, 140);
      _expectMetricContentInsideCards(tester);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('defaults to one house and all batches', (tester) async {
    final queries = <DashboardQuery>[];
    await tester.pumpWidget(
      _testApp(
        houses: _houses,
        batches: (_) async => [_batch81],
        summary: (query) async {
          queries.add(query);
          return _summary(query);
        },
      ),
    );
    await tester.pumpAndSettle();

    expect(queries.last, (houseId: 8, batchId: null, year: _year));
    final houseSelector = tester.widget<DropdownButton<int>>(
      find.byKey(const ValueKey('dashboard-house-selector')),
    );
    final batchSelector = tester.widget<DropdownButton<int>>(
      find.byKey(const ValueKey('dashboard-batch-selector')),
    );
    expect(houseSelector.value, 8);
    expect(batchSelector.value, 0);
    expect(batchSelector.onChanged, isNotNull);
    expect(find.textContaining('一号兔舍 / 全部批次'), findsOneWidget);
  });

  testWidgets('switching houses clears batch before requesting the new scope',
      (tester) async {
    final queries = <DashboardQuery>[];
    await tester.pumpWidget(
      _testApp(
        houses: _houses,
        batches: (houseId) async => houseId == 8 ? [_batch81] : [_batch91],
        summary: (query) async {
          queries.add(query);
          return _summary(query, totalRabbits: query.houseId ?? 100);
        },
      ),
    );
    await tester.pumpAndSettle();

    await _select(tester, 'dashboard-house-selector', '一号兔舍');
    await _select(tester, 'dashboard-batch-selector', '一号批次');
    expect(queries.last, (houseId: 8, batchId: 81, year: _year));

    await _select(tester, 'dashboard-house-selector', '二号兔舍');

    expect(queries.last, (houseId: 9, batchId: null, year: _year));
    final batchSelector = tester.widget<DropdownButton<int>>(
      find.byKey(const ValueKey('dashboard-batch-selector')),
    );
    expect(batchSelector.value, 0);
    expect(find.textContaining('二号兔舍 / 全部批次'), findsOneWidget);
  });

  testWidgets('all houses clears and disables batch selection', (tester) async {
    await tester.pumpWidget(
      _testApp(
        houses: _houses,
        batches: (_) async => [_batch81],
      ),
    );
    await tester.pumpAndSettle();

    var batchSelector = tester.widget<DropdownButton<int>>(
      find.byKey(const ValueKey('dashboard-batch-selector')),
    );
    expect(batchSelector.onChanged, isNotNull);
    expect(batchSelector.value, 0);

    await _select(tester, 'dashboard-batch-selector', '一号批次');
    await _select(tester, 'dashboard-house-selector', '全部兔舍');

    batchSelector = tester.widget<DropdownButton<int>>(
      find.byKey(const ValueKey('dashboard-batch-selector')),
    );
    expect(batchSelector.onChanged, isNull);
    expect(batchSelector.value, 0);
    expect(find.text('选择单一兔舍后可选批次'), findsOneWidget);
  });

  testWidgets('batch selector shows loading and empty states', (tester) async {
    final batches = Completer<List<Batch>>();
    await tester.pumpWidget(
      _testApp(
        houses: _houses,
        batches: (_) => batches.future,
      ),
    );
    await tester.pumpAndSettle();

    await _select(
      tester,
      'dashboard-house-selector',
      '一号兔舍',
      settle: false,
    );
    await tester.pump();
    expect(find.text('正在加载批次'), findsOneWidget);

    batches.complete(const <Batch>[]);
    await tester.pumpAndSettle();
    expect(find.text('当前兔舍暂无批次'), findsOneWidget);
    final selector = tester.widget<DropdownButton<int>>(
      find.byKey(const ValueKey('dashboard-batch-selector')),
    );
    expect(selector.onChanged, isNull);
  });

  testWidgets('batch selector shows an error with retry action',
      (tester) async {
    await tester.pumpWidget(
      _testApp(
        houses: _houses,
        batches: (_) async => throw Exception('批次接口不可用'),
      ),
    );
    await tester.pumpAndSettle();

    await _select(tester, 'dashboard-house-selector', '一号兔舍');

    expect(find.text('批次加载失败'), findsOneWidget);
    expect(find.byTooltip('重新加载批次'), findsOneWidget);
  });

  testWidgets('late report response cannot replace a newer house scope',
      (tester) async {
    final first = Completer<DashboardSummary>();
    final second = Completer<DashboardSummary>();
    await tester.pumpWidget(
      _testApp(
        houses: _houses,
        batches: (_) async => const <Batch>[],
        summary: (query) {
          if (query.houseId == 8) {
            return first.future;
          }
          if (query.houseId == 9) {
            return second.future;
          }
          return Future.value(_summary(query, totalRabbits: 100));
        },
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    await _select(
      tester,
      'dashboard-house-selector',
      '二号兔舍',
      settle: false,
    );
    await tester.pump();

    second.complete(
      _summary((houseId: 9, batchId: null, year: _year), totalRabbits: 900),
    );
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<Text>(
            find.byKey(const ValueKey('dashboard-hero-value-在养兔只')),
          )
          .data,
      '900',
    );

    first.complete(
      _summary((houseId: 8, batchId: null, year: _year), totalRabbits: 800),
    );
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<Text>(
            find.byKey(const ValueKey('dashboard-hero-value-在养兔只')),
          )
          .data,
      '900',
    );
    expect(find.text('800'), findsNothing);
    expect(find.textContaining('二号兔舍 / 全部批次'), findsOneWidget);
  });
}

Future<void> _select(
  WidgetTester tester,
  String key,
  String label, {
  bool settle = true,
}) async {
  await tester.tap(find.byKey(ValueKey(key)));
  if (settle) {
    await tester.pumpAndSettle();
  } else {
    await tester.pump(const Duration(seconds: 1));
  }
  await tester.tap(find.text(label).last);
  if (settle) {
    await tester.pumpAndSettle();
  }
}

void _expectMetricContentInsideCards(WidgetTester tester) {
  for (final metric in _metrics) {
    final card = find.byKey(
      ValueKey('dashboard-metric-card-${metric.label}'),
    );
    final label = find.descendant(
      of: card,
      matching: find.text(metric.label),
    );
    final value = find.descendant(
      of: card,
      matching: find.text('${metric.value}'),
    );

    expect(card, findsOneWidget);
    expect(label, findsOneWidget);
    expect(value, findsOneWidget);

    final cardRect = tester.getRect(card);
    final labelRect = tester.getRect(label);
    final valueRect = tester.getRect(value);
    expect(labelRect.top, greaterThanOrEqualTo(cardRect.top));
    expect(labelRect.bottom, lessThanOrEqualTo(cardRect.bottom));
    expect(valueRect.top, greaterThanOrEqualTo(labelRect.bottom));
    expect(valueRect.bottom, lessThanOrEqualTo(cardRect.bottom));
  }
}

Widget _testApp({
  List<RabbitHouse> houses = const [_house],
  Future<List<Batch>> Function(int houseId)? batches,
  Future<DashboardSummary> Function(DashboardQuery query)? summary,
}) {
  return ProviderScope(
    overrides: [
      housesProvider.overrideWith((_) async => houses),
      houseBatchesProvider.overrideWith(
        (_, houseId) => batches?.call(houseId) ?? Future.value(const <Batch>[]),
      ),
      dashboardSummaryProvider.overrideWith(
        (_, query) => summary?.call(query) ?? Future.value(_summary(query)),
      ),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: const DashboardScreen(),
    ),
  );
}

DashboardSummary _summary(
  DashboardQuery query, {
  int totalRabbits = 1000,
}) {
  return DashboardSummary(
    selectedHouseId: query.houseId,
    selectedBatchId: query.batchId,
    houseCount: query.houseId == null ? 2 : 1,
    year: query.year,
    totalRabbits: totalRabbits,
    seedRabbits: 1000,
    maleRabbits: 20,
    femaleRabbits: 1000,
    bredRabbits: 900,
    readyForBreeding: 100,
    litters: 880,
    nursingKits: 6160,
    commodityRabbits: 6000,
    replacementRabbits: 160,
    liveRate: 0.93,
    monthlyBirths: List<int>.filled(12, 80),
    monthlyWeaned: List<int>.filled(12, 70),
  );
}

const _year = 2026;

const _house = RabbitHouse(
  id: 8,
  name: '一号大型繁育兔舍超长名称',
  remark: '',
  layoutRows: 100,
  layoutCols: 20,
  layoutLayers: 3,
);

const _houses = [
  RabbitHouse(
    id: 8,
    name: '一号兔舍',
    remark: '',
    layoutRows: 10,
    layoutCols: 10,
    layoutLayers: 2,
  ),
  RabbitHouse(
    id: 9,
    name: '二号兔舍',
    remark: '',
    layoutRows: 10,
    layoutCols: 10,
    layoutLayers: 2,
  ),
];

final _batch81 = Batch(
  id: 81,
  houseId: 8,
  batchCode: '一号批次',
  status: '进行中',
  startDate: DateTime(2026, 1, 1),
  endDate: null,
  remark: '',
);

final _batch91 = Batch(
  id: 91,
  houseId: 9,
  batchCode: '二号批次',
  status: '进行中',
  startDate: DateTime(2026, 2, 1),
  endDate: null,
  remark: '',
);

const _metrics = [
  (label: '种兔数量', value: 1000),
  (label: '公兔数量', value: 20),
  (label: '母兔数量', value: 1000),
  (label: '繁殖周期中', value: 900),
  (label: '未在周期中', value: 100),
  (label: '已分娩窝数', value: 880),
  (label: '哺乳期数量', value: 6160),
  (label: '商品兔数量', value: 6000),
  (label: '后备兔数量', value: 160),
];
