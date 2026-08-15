import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/domain/models/report_summary.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/dashboard/view_models/dashboard_providers.dart';
import 'package:rabbit_flutter/src/ui/dashboard/widgets/dashboard_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';

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
          find.byType(DropdownButton<int>),
          500,
          scrollable: find.byType(Scrollable).first,
        );
        await tester.pumpAndSettle();

        expect(find.byType(DropdownButton<int>), findsOneWidget);
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

    final grid = tester.widget<GridView>(
      find.byKey(const ValueKey('dashboard-metric-grid')),
    );
    final delegate =
        grid.gridDelegate as SliverGridDelegateWithFixedCrossAxisCount;
    expect(delegate.crossAxisCount, 3);
    expect(delegate.mainAxisExtent, 140);
    _expectMetricContentInsideCards(tester);
    expect(tester.takeException(), isNull);
  });
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

Widget _testApp() {
  return ProviderScope(
    overrides: [
      housesProvider.overrideWith((_) async => const [_house]),
      dashboardSummaryProvider.overrideWith(
        (_, query) async => DashboardSummary(
          selectedHouseId: query.houseId,
          houseCount: 1,
          year: query.year,
          totalRabbits: 1000,
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
        ),
      ),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: const DashboardScreen(),
    ),
  );
}

const _house = RabbitHouse(
  id: 8,
  name: '一号大型繁育兔舍超长名称',
  remark: '',
  layoutRows: 100,
  layoutCols: 20,
  layoutLayers: 3,
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
