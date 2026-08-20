import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/screens/list.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/queue.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

void main() {
  testWidgets('small cage list is available in a single batch', (tester) async {
    await tester.pumpWidget(_testApp(_cages(12)));
    await tester.pumpAndSettle();
    await _switchToList(tester);

    expect(find.text('总笼位 12'), findsOneWidget);
    expect(_cageGridChildCount(tester), 12);
  });

  testWidgets('cage display uses doe production stage and defaults no status',
      (tester) async {
    const cages = [
      Cage(
        id: 1,
        houseId: 8,
        cageNumber: '1-1-1',
        rowCode: 'R1',
        layerIndex: 1,
        positionIndex: 1,
        status: '1',
        rabbitCount: 1,
        isEnabled: true,
      ),
      Cage(
        id: 2,
        houseId: 8,
        cageNumber: '1-2-1',
        rowCode: 'R1',
        layerIndex: 1,
        positionIndex: 2,
        status: '1',
        rabbitCount: 1,
        isEnabled: true,
      ),
    ];
    const doe = Rabbit(
      id: 101,
      houseId: 8,
      cageId: 1,
      motherId: null,
      type: '0',
      gender: '0',
      breed: '新西兰白兔',
      arrivalMethod: '0',
      arrivalDate: null,
      weight: null,
      isActive: true,
      currentStage: 'AWAIT_MATING',
    );
    const doeWithoutStage = Rabbit(
      id: 102,
      houseId: 8,
      cageId: 2,
      motherId: null,
      type: '0',
      gender: '0',
      breed: '新西兰白兔',
      arrivalMethod: '0',
      arrivalDate: null,
      weight: null,
      isActive: true,
    );

    await tester.pumpWidget(
      _testApp(cages, rabbits: const [doe, doeWithoutStage]),
    );
    await tester.pumpAndSettle();

    expect(
      find.descendant(
        of: find.byKey(const ValueKey('cage-map-cell-1')),
        matching: find.text('待配种'),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('cage-map-cell-2')),
        matching: find.text('无状态'),
      ),
      findsOneWidget,
    );

    await _switchToList(tester);
    expect(find.text('待配种'), findsOneWidget);
    expect(find.text('无状态'), findsOneWidget);
  });

  testWidgets('cage filters combine occupancy and known usage types',
      (tester) async {
    await tester.pumpWidget(_testApp(_filterCages));
    await tester.pumpAndSettle();
    await _switchToList(tester);
    await _expandFilters(tester);

    expect(find.text('笼位筛选'), findsOneWidget);
    expect(find.text('繁殖笼'), findsOneWidget);
    expect(find.text('后备笼'), findsOneWidget);
    expect(find.text('商品笼'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('cage-usage-doe-breeding-filter')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('cage-usage-buck-breeding-filter')),
      findsNothing,
    );
    expect(find.text('匹配 7 / 7 个笼位'), findsOneWidget);

    await tester.tap(
      find.byKey(const ValueKey('cage-occupancy-empty-filter')),
    );
    await tester.pumpAndSettle();

    expect(find.text('匹配 4 / 7 个笼位'), findsOneWidget);
    expect(_cageGridChildCount(tester), 4);

    final breedingFilter =
        find.byKey(const ValueKey('cage-usage-breeding-filter'));
    await tester.ensureVisible(breedingFilter);
    await tester.pumpAndSettle();
    await tester.tap(breedingFilter);
    await tester.pumpAndSettle();

    expect(find.text('匹配 1 / 7 个笼位'), findsOneWidget);
    expect(_cageGridChildCount(tester), 1);

    final occupiedFilter =
        find.byKey(const ValueKey('cage-occupancy-occupied-filter'));
    await tester.ensureVisible(occupiedFilter);
    await tester.pumpAndSettle();
    await tester.tap(occupiedFilter);
    await tester.pumpAndSettle();

    expect(find.text('匹配 1 / 7 个笼位'), findsOneWidget);
    expect(_cageGridChildCount(tester), 1);

    final resetFilter = find.byKey(const ValueKey('cage-filter-reset'));
    await tester.ensureVisible(resetFilter);
    await tester.pumpAndSettle();
    await tester.tap(resetFilter);
    await tester.pumpAndSettle();

    expect(find.text('匹配 7 / 7 个笼位'), findsOneWidget);
    expect(_cageGridChildCount(tester), 7);
  });

  testWidgets('breeding sex filters use only real active breeding occupants',
      (tester) async {
    await tester.pumpWidget(_testApp(_sexClassifiedBreedingCages));
    await tester.pumpAndSettle();
    await _switchToList(tester);
    await _expandFilters(tester);

    final doeFilter = find.byKey(
      const ValueKey('cage-usage-doe-breeding-filter'),
    );
    final buckFilter = find.byKey(
      const ValueKey('cage-usage-buck-breeding-filter'),
    );
    expect(doeFilter, findsOneWidget);
    expect(buckFilter, findsOneWidget);

    await tester.ensureVisible(doeFilter);
    await tester.pumpAndSettle();
    await tester.tap(doeFilter);
    await tester.pumpAndSettle();

    expect(find.text('匹配 1 / 4 个笼位'), findsOneWidget);
    expect(_cageGridChildCount(tester), 1);
    expect(find.text('种母-实际在栏'), findsOneWidget);
    expect(find.text('种公-实际在栏'), findsNothing);
    expect(find.text('繁殖-未分类在栏'), findsNothing);

    await tester.ensureVisible(buckFilter);
    await tester.pumpAndSettle();
    await tester.tap(buckFilter);
    await tester.pumpAndSettle();

    expect(find.text('匹配 1 / 4 个笼位'), findsOneWidget);
    expect(_cageGridChildCount(tester), 1);
    expect(find.text('种母-实际在栏'), findsNothing);
    expect(find.text('种公-实际在栏'), findsOneWidget);
    expect(find.text('繁殖-空笼'), findsNothing);
  });

  testWidgets('large cage list loads more near the page bottom',
      (tester) async {
    await tester.pumpWidget(_testApp(_cages(45)));
    await tester.pumpAndSettle();
    await _switchToList(tester);

    expect(find.text('总笼位 45'), findsOneWidget);
    expect(_cageGridChildCount(tester), 20);

    await tester.drag(
      find.byKey(const ValueKey('house-cage-list-scroll')),
      const Offset(0, -4000),
    );
    await tester.pumpAndSettle();

    expect(_cageGridChildCount(tester), 40);

    await tester.drag(
      find.byKey(const ValueKey('house-cage-list-scroll')),
      const Offset(0, -4000),
    );
    await tester.pumpAndSettle();

    expect(_cageGridChildCount(tester), 45);
  });

  testWidgets('changing a cage filter resets large-list pagination',
      (tester) async {
    final cages = List.generate(
      45,
      (index) => Cage(
        id: index + 1,
        houseId: 8,
        cageNumber: '商品区-${index + 1}',
        status: '3',
        rabbitCount: index < 35 ? 0 : 1,
        isEnabled: true,
      ),
    );
    await tester.pumpWidget(_testApp(cages));
    await tester.pumpAndSettle();
    await _switchToList(tester);

    expect(_cageGridChildCount(tester), 20);
    await tester.drag(
      find.byKey(const ValueKey('house-cage-list-scroll')),
      const Offset(0, -4000),
    );
    await tester.pumpAndSettle();
    expect(_cageGridChildCount(tester), 40);

    await tester.drag(
      find.byKey(const ValueKey('house-cage-list-scroll')),
      const Offset(0, 4000),
    );
    await tester.pumpAndSettle();
    await _expandFilters(tester);
    await tester.tap(
      find.byKey(const ValueKey('cage-occupancy-empty-filter')),
    );
    await tester.pumpAndSettle();

    expect(find.text('匹配 35 / 45 个笼位'), findsOneWidget);
    expect(_cageGridChildCount(tester), 20);
  });

  testWidgets('house outbound stays named and reachable on narrow screens',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

    await tester.pumpWidget(
      _testApp(
        const [
          Cage(
            id: 1,
            houseId: 8,
            cageNumber: '一号繁育区-R1-01-上层',
            rowCode: 'R1',
            status: '1',
            rabbitCount: 1,
            isEnabled: true,
          ),
        ],
        permission: const HousePermission(perms: 'edit', isAdmin: false),
      ),
    );
    await tester.pumpAndSettle();

    final outbound = find.byKey(const ValueKey('house-outbound-action'));
    expect(outbound, findsOneWidget);
    expect(find.text('整舍批量出库'), findsOneWidget);
    expect(tester.getSize(outbound).height, greaterThanOrEqualTo(48));
    expect(tester.takeException(), isNull);
  });

  for (final size in const [Size(360, 800), Size(412, 915)]) {
    testWidgets(
      'row outbound target stays 48dp at true 200 percent on '
      '${size.width.toInt()}x${size.height.toInt()}',
      (tester) async {
        await tester.binding.setSurfaceSize(size);
        tester.platformDispatcher.textScaleFactorTestValue = 2;
        addTearDown(() => tester.binding.setSurfaceSize(null));
        addTearDown(
          tester.platformDispatcher.clearTextScaleFactorTestValue,
        );

        await tester.pumpWidget(
          _testApp(
            const [
              Cage(
                id: 1,
                houseId: 8,
                cageNumber: '一号繁育区-R1-01-上层',
                rowCode: 'R1',
                status: '1',
                rabbitCount: 1,
                isEnabled: true,
              ),
            ],
            permission: const HousePermission(
              perms: 'edit',
              isAdmin: false,
            ),
          ),
        );
        await tester.pumpAndSettle();
        await _switchToList(tester);

        final outbound = find.byKey(const ValueKey('cage-row-outbound-1'));
        final outboundContext = tester.element(outbound);
        expect(MediaQuery.textScalerOf(outboundContext).scale(10), 20);
        expect(tester.getSize(outbound), const Size(48, 48));
        final grid = tester.widget<GridView>(
          find.byKey(const ValueKey('house-cage-grid')),
        );
        final delegate =
            grid.gridDelegate as SliverGridDelegateWithFixedCrossAxisCount;
        expect(delegate.crossAxisCount, 2);
        expect(delegate.mainAxisExtent, greaterThanOrEqualTo(180));
        expect(find.byTooltip('R1 排批量出库'), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }
}

/// 分层地图现在是默认视图，这个文件里验的是列表视图的分页与筛选，
/// 所以先切到列表。地图自己的行为在 cage_map_view_test.dart 里验。
Future<void> _switchToList(WidgetTester tester) async {
  final toggle = find.byKey(const ValueKey('cage-view-list-toggle'));
  await tester.ensureVisible(toggle);
  await tester.pumpAndSettle();
  await tester.tap(toggle);
  await tester.pumpAndSettle();
}

/// 筛选区默认折叠（展开后比地图还高），要点 chip 先展开。
Future<void> _expandFilters(WidgetTester tester) async {
  final toggle = find.byKey(const ValueKey('cage-filter-toggle'));
  await tester.ensureVisible(toggle);
  await tester.pumpAndSettle();
  await tester.tap(toggle);
  await tester.pumpAndSettle();
}

Widget _testApp(
  List<Cage> cages, {
  List<Rabbit> rabbits = const <Rabbit>[],
  HousePermission permission =
      const HousePermission(perms: 'view', isAdmin: false),
}) {
  return ProviderScope(
    overrides: [
      housesProvider.overrideWith((_) async => const [_house]),
      houseCagesProvider(8).overrideWith((_) async => cages),
      houseBreedingRabbitsProvider(8).overrideWith((_) async => rabbits),
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

int? _cageGridChildCount(WidgetTester tester) {
  final grid = tester.widget<GridView>(
    find.byKey(const ValueKey('house-cage-grid')),
  );
  return grid.childrenDelegate.estimatedChildCount;
}

List<Cage> _cages(int count) {
  return List.generate(
    count,
    (index) => Cage(
      id: index + 1,
      houseId: 8,
      cageNumber: '1-1-${index + 1}',
      status: '0',
      rabbitCount: 0,
      isEnabled: true,
    ),
  );
}

const _filterCages = [
  Cage(
    id: 1,
    houseId: 8,
    cageNumber: '繁殖-空',
    status: '1',
    rabbitCount: 0,
    isEnabled: true,
  ),
  Cage(
    id: 2,
    houseId: 8,
    cageNumber: '繁殖-有兔',
    status: '1',
    rabbitCount: 1,
    isEnabled: true,
  ),
  Cage(
    id: 3,
    houseId: 8,
    cageNumber: '后备-空',
    status: '2',
    rabbitCount: 0,
    isEnabled: true,
  ),
  Cage(
    id: 4,
    houseId: 8,
    cageNumber: '后备-有兔',
    status: '2',
    rabbitCount: 1,
    isEnabled: true,
  ),
  Cage(
    id: 5,
    houseId: 8,
    cageNumber: '商品-空',
    status: '3',
    rabbitCount: 0,
    isEnabled: true,
  ),
  Cage(
    id: 6,
    houseId: 8,
    cageNumber: '商品-有兔',
    status: '3',
    rabbitCount: 2,
    isEnabled: true,
  ),
  Cage(
    id: 7,
    houseId: 8,
    cageNumber: '未分类-空',
    status: '0',
    rabbitCount: 0,
    isEnabled: true,
  ),
];

const _sexClassifiedBreedingCages = [
  Cage(
    id: 21,
    houseId: 8,
    cageNumber: '种母-实际在栏',
    breedingOccupantGender: '0',
    status: '1',
    rabbitCount: 1,
    isEnabled: true,
  ),
  Cage(
    id: 22,
    houseId: 8,
    cageNumber: '种公-实际在栏',
    breedingOccupantGender: '1',
    status: '1',
    rabbitCount: 1,
    isEnabled: true,
  ),
  Cage(
    id: 23,
    houseId: 8,
    cageNumber: '繁殖-未分类在栏',
    status: '1',
    rabbitCount: 1,
    isEnabled: true,
  ),
  Cage(
    id: 24,
    houseId: 8,
    cageNumber: '繁殖-空笼',
    status: '1',
    rabbitCount: 0,
    isEnabled: true,
  ),
];

const _house = RabbitHouse(
  id: 8,
  name: '测试兔舍',
  remark: '',
  layoutRows: 5,
  layoutCols: 9,
  layoutLayers: 1,
);
