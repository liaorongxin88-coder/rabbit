import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_cages_screen.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/nfc_queue_provider.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

void main() {
  testWidgets('cage section opens on the layered map, not the flat grid',
      (tester) async {
    await tester.pumpWidget(_testApp(_rack));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('cage-map')), findsOneWidget);
    expect(find.byKey(const ValueKey('house-cage-grid')), findsNothing);
    expect(find.byKey(const ValueKey('cage-map-row-R1')), findsOneWidget);
    expect(find.byKey(const ValueKey('cage-map-row-R2')), findsOneWidget);
  });

  testWidgets('the first row of cages is visible without scrolling',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(412, 915));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(_testApp(_rack));
    await tester.pumpAndSettle();

    // 真机验收抳出过这个问题：数量 chip + 切换 + 搜索 + 展开的筛选把地图整个推到屏外，
    // 于是“更直观地找笼”反而要先滚一屏。第一排必须在首屏内。
    final cell = find.byKey(const ValueKey('cage-map-cell-1'));
    expect(cell, findsOneWidget);
    final rect = tester.getRect(cell);
    expect(
      rect.bottom,
      lessThanOrEqualTo(915),
      reason: '第一排笼位应在首屏内，不应需要滚动才能看到',
    );
  });

  testWidgets('map shows layers top-down and position axis left-to-right',
      (tester) async {
    await tester.pumpWidget(_testApp(_rack));
    await tester.pumpAndSettle();

    expect(find.text('2层'), findsWidgets);
    expect(find.text('1层'), findsWidgets);

    final upper = tester.getCenter(find.byKey(const ValueKey('cage-map-cell-3')));
    final lower = tester.getCenter(find.byKey(const ValueKey('cage-map-cell-1')));
    expect(
      upper.dy,
      lessThan(lower.dy),
      reason: '2 层要显示在 1 层上面，跟物理货架一致',
    );

    final first = tester.getCenter(find.byKey(const ValueKey('cage-map-cell-1')));
    final second =
        tester.getCenter(find.byKey(const ValueKey('cage-map-cell-2')));
    expect(first.dx, lessThan(second.dx));
  });

  testWidgets('legend explains every colour and doubles as a tally',
      (tester) async {
    await tester.pumpWidget(_testApp(_rack));
    await tester.pumpAndSettle();

    final legend = find.byKey(const ValueKey('cage-map-legend'));
    expect(legend, findsOneWidget);
    // _rack: 空笼 2（有空位）、满笼 1、待投喂 1、异常 1、停用 1。
    // 排标题上也会出现“待投喂 1”这种字样，所以必须限定在图例内查。
    for (final label in const ['有空位 2', '已满 1', '待投喂 1', '异常 1', '停用 1']) {
      expect(
        find.descendant(of: legend, matching: find.text(label)),
        findsOneWidget,
        reason: '图例应列出 $label',
      );
    }
  });

  testWidgets('unfed, over-capacity and disabled cages are told apart',
      (tester) async {
    await tester.pumpWidget(_testApp(_rack));
    await tester.pumpAndSettle();

    // 待投喂：有兔且今日未投喂。
    expect(
      await _cellSemantics(tester, 4),
      allOf(contains('待投喂'), contains('1 只')),
    );
    // 异常：空闲状态却记着在栏数，账不平。
    expect(
      await _cellSemantics(tester, 5),
      allOf(contains('异常'), contains('标记为空闲却记着 2 只')),
    );
    // 停用空笼只是不可用，不算异常。
    expect(await _cellSemantics(tester, 6), contains('停用'));
  });

  testWidgets('filtering dims cages instead of collapsing the map',
      (tester) async {
    await tester.pumpWidget(_testApp(_rack));
    await tester.pumpAndSettle();

    // 筛选默认折叠，先展开。
    final filterToggle = find.byKey(const ValueKey('cage-filter-toggle'));
    await tester.ensureVisible(filterToggle);
    await tester.pumpAndSettle();
    await tester.tap(filterToggle);
    await tester.pumpAndSettle();

    final emptyFilter =
        find.byKey(const ValueKey('cage-occupancy-empty-filter'));
    await tester.ensureVisible(emptyFilter);
    await tester.pumpAndSettle();
    await tester.tap(emptyFilter);
    await tester.pumpAndSettle();

    // 位置不能因为筛选而塌缩，否则「第几排第几位」就不可信了。
    expect(find.byKey(const ValueKey('cage-map-cell-1')), findsOneWidget);
    expect(find.byKey(const ValueKey('cage-map-cell-4')), findsOneWidget);
    expect(_cellOpacity(tester, 1), 1);
    expect(_cellOpacity(tester, 4), lessThan(0.5));
  });

  testWidgets('cages without coordinates stay reachable in an unplaced group',
      (tester) async {
    await tester.pumpWidget(
      _testApp(const [
        Cage(
          id: 1,
          houseId: 8,
          cageNumber: 'R1-C1-L1',
          rowCode: 'R1',
          layerIndex: 1,
          positionIndex: 1,
          status: '0',
          rabbitCount: 0,
          isEnabled: true,
        ),
        Cage(
          id: 2,
          houseId: 8,
          cageNumber: '历史遗留笼',
          status: '0',
          rabbitCount: 0,
          isEnabled: true,
        ),
      ]),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('cage-map-unplaced')), findsOneWidget);
    expect(find.text('未编排 1 笼'), findsOneWidget);
    expect(find.text('历史遗留笼'), findsOneWidget);
  });

  testWidgets('map pages by row so a big house does not render everything',
      (tester) async {
    await tester.pumpWidget(_testApp(_manyRows(9)));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('cage-map-row-R1')), findsOneWidget);
    expect(find.byKey(const ValueKey('cage-map-row-R7')), findsNothing);

    final more = find.byKey(const ValueKey('cage-map-more-rows'));
    expect(find.text('显示更多排（还有 3 排）'), findsOneWidget);
    await tester.ensureVisible(more);
    await tester.pumpAndSettle();
    await tester.tap(more);
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('cage-map-row-R7')), findsOneWidget);
  });

  for (final size in const [Size(360, 800), Size(412, 915)]) {
    testWidgets(
      'map row outbound stays 48dp at true 200 percent on '
      '${size.width.toInt()}x${size.height.toInt()}',
      (tester) async {
        await tester.binding.setSurfaceSize(size);
        tester.platformDispatcher.textScaleFactorTestValue = 2;
        addTearDown(() => tester.binding.setSurfaceSize(null));
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

        await tester.pumpWidget(
          _testApp(
            _rack,
            permission: const HousePermission(perms: 'edit', isAdmin: false),
          ),
        );
        await tester.pumpAndSettle();

        final outbound =
            find.byKey(const ValueKey('cage-map-row-outbound-R1'));
        expect(tester.getSize(outbound), const Size(48, 48));
        expect(find.byTooltip('R1 排批量出库'), findsOneWidget);
        expect(
          MediaQuery.textScalerOf(tester.element(outbound)).scale(10),
          20,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('cells grow with text scale so occupancy stays readable',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

    await tester.pumpWidget(_testApp(_rack));
    await tester.pumpAndSettle();
    final normal = tester.getSize(find.byKey(const ValueKey('cage-map-cell-1')));

    tester.platformDispatcher.textScaleFactorTestValue = 2;
    await tester.pumpWidget(_testApp(_rack));
    await tester.pumpAndSettle();
    final scaled = tester.getSize(find.byKey(const ValueKey('cage-map-cell-1')));

    expect(scaled.width, greaterThan(normal.width));
    expect(scaled.height, greaterThanOrEqualTo(normal.height));
    expect(tester.takeException(), isNull);
  });
}

Future<String> _cellSemantics(WidgetTester tester, int cageId) async {
  final cell = find.byKey(ValueKey('cage-map-cell-$cageId'));
  expect(cell, findsOneWidget);
  // 屏外的格子不会生成语义节点，getSemantics 会一路往上拿到整页的拼接文本，
  // 所以先滚到可见再读。
  await tester.ensureVisible(cell);
  await tester.pumpAndSettle();
  return tester.getSemantics(cell).label;
}

double _cellOpacity(WidgetTester tester, int cageId) {
  final cell = find.byKey(ValueKey('cage-map-cell-$cageId'));
  final opacity = tester.widget<Opacity>(
    find.ancestor(of: cell, matching: find.byType(Opacity)).first,
  );
  return opacity.opacity;
}

/// R1 两层两位 + R2 一层三位，覆盖全部五种关注度。
const _rack = [
  // R1 1层：空笼、满笼
  Cage(
    id: 1,
    houseId: 8,
    cageNumber: 'R1-C1-L1',
    rowCode: 'R1',
    layerIndex: 1,
    positionIndex: 1,
    status: '0',
    rabbitCount: 0,
    isEnabled: true,
  ),
  Cage(
    id: 2,
    houseId: 8,
    cageNumber: 'R1-C2-L1',
    rowCode: 'R1',
    layerIndex: 1,
    positionIndex: 2,
    status: '1',
    rabbitCount: 1,
    isEnabled: true,
  ),
  // R1 2层：空笼
  Cage(
    id: 3,
    houseId: 8,
    cageNumber: 'R1-C1-L2',
    rowCode: 'R1',
    layerIndex: 2,
    positionIndex: 1,
    status: '0',
    rabbitCount: 0,
    isEnabled: true,
  ),
  // R2：待投喂、异常、停用
  Cage(
    id: 4,
    houseId: 8,
    cageNumber: 'R2-C1-L1',
    rowCode: 'R2',
    layerIndex: 1,
    positionIndex: 1,
    status: '1',
    rabbitCount: 1,
    isEnabled: true,
    isFed: false,
  ),
  Cage(
    id: 5,
    houseId: 8,
    cageNumber: 'R2-C2-L1',
    rowCode: 'R2',
    layerIndex: 1,
    positionIndex: 2,
    status: '0',
    rabbitCount: 2,
    isEnabled: true,
  ),
  Cage(
    id: 6,
    houseId: 8,
    cageNumber: 'R2-C3-L1',
    rowCode: 'R2',
    layerIndex: 1,
    positionIndex: 3,
    status: '0',
    rabbitCount: 0,
    isEnabled: false,
  ),
];

List<Cage> _manyRows(int rows) {
  return List.generate(
    rows,
    (index) => Cage(
      id: index + 1,
      houseId: 8,
      cageNumber: 'R${index + 1}-C1-L1',
      rowCode: 'R${index + 1}',
      layerIndex: 1,
      positionIndex: 1,
      status: '0',
      rabbitCount: 0,
      isEnabled: true,
    ),
  );
}

Widget _testApp(
  List<Cage> cages, {
  HousePermission permission =
      const HousePermission(perms: 'view', isAdmin: false),
}) {
  return ProviderScope(
    overrides: [
      housesProvider.overrideWith((_) async => const [_house]),
      houseCagesProvider(8).overrideWith((_) async => cages),
      housePermissionProvider(8).overrideWith((_) async => permission),
      nfcCageWriteQueueProvider(8)
          .overrideWith((_) async => const <NfcCageQueueItem>[]),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: const HouseCagesScreen(houseId: 8),
    ),
  );
}

const _house = RabbitHouse(
  id: 8,
  name: '测试兔舍',
  remark: '',
  layoutRows: 2,
  layoutCols: 3,
  layoutLayers: 2,
);
