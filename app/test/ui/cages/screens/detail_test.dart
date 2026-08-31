import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/cages/summary.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/rabbits/batch_membership.dart';
import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/screens/detail.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/queue.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

void main() {
  testWidgets('view-only cage detail hides all editing actions',
      (tester) async {
    var nfcQueueRequested = false;
    const key = (houseId: 8, cageId: 10);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          cageSummaryProvider(key).overrideWith((_) async => _summary),
          cageRabbitsProvider(key).overrideWith((_) async => const <Rabbit>[]),
          houseCagesProvider(8).overrideWith((_) async => const [_cage]),
          housesProvider.overrideWith((_) async => const [_house]),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'view', isAdmin: false),
          ),
          nfcCageWriteQueueProvider(8).overrideWith((_) async {
            nfcQueueRequested = true;
            return const <NfcCageQueueItem>[];
          }),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const CageDetailScreen(houseId: 8, cageId: 10),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final appBar = tester.widget<AppBar>(find.byType(AppBar));
    expect(appBar.leading, isNotNull);
    expect(
      find.byKey(const ValueKey('cage-detail-back-button')),
      findsOneWidget,
    );
    expect(find.text('当前权限不可管理标签'), findsOneWidget);
    expect(nfcQueueRequested, isFalse);
    await tester.scrollUntilVisible(
      find.text('录入'),
      180,
      scrollable: find.byType(Scrollable).first,
    );
    final entryButton = tester.widget<FilledButton>(
      find.byKey(const ValueKey('cage-rabbit-entry')),
    );
    expect(entryButton.onPressed, isNull);
    final abnormalButton = tester.widget<OutlinedButton>(
      find.byKey(const ValueKey('cage-abnormal-entry')),
    );
    expect(abnormalButton.onPressed, isNull);
    expect(find.text('当前账号仅可查看，无法新增异常记录'), findsOneWidget);
  });

  testWidgets('control permission enables NFC and opens rabbit detail',
      (tester) async {
    const key = (houseId: 8, cageId: 10);
    final router = GoRouter(
      initialLocation: '/houses/8/cages/10',
      routes: [
        GoRoute(
          path: '/houses/:houseId/cages/:cageId',
          builder: (_, __) => const CageDetailScreen(houseId: 8, cageId: 10),
        ),
        GoRoute(
          path: '/houses/:houseId/rabbits/:rabbitId',
          builder: (_, __) => const Scaffold(body: Text('兔只详情页')),
        ),
      ],
    );
    addTearDown(router.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          cageSummaryProvider(key).overrideWith((_) async => _summary),
          cageRabbitsProvider(key).overrideWith((_) async => const [_rabbit]),
          houseCagesProvider(8).overrideWith((_) async => const [_cage]),
          housesProvider.overrideWith((_) async => const [_house]),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'control',
              isAdmin: false,
              permissions: ['rabbit:rabbits:add'],
            ),
          ),
          rabbitBatchMembershipsProvider(
            const RabbitBatchMembershipRequest(houseId: 8, rabbitId: 801),
          ).overrideWith(
            (_) async => const <RabbitBatchMembership>[],
          ),
          nfcCageWriteQueueProvider(8).overrideWith(
            (_) async => const [
              NfcCageQueueItem(
                cageId: 10,
                cageNumber: '1-1-1',
                bindingStatus: 'UNBOUND',
                tagUid: null,
                payload: 'r1.8.a.1.signature',
              ),
            ],
          ),
        ],
        child: MaterialApp.router(
          theme: buildAppTheme(),
          routerConfig: router,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byTooltip('写入标签'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('录入'),
      180,
      scrollable: find.byType(Scrollable).first,
    );
    final entryButton = tester.widget<FilledButton>(
      find.byKey(const ValueKey('cage-rabbit-entry')),
    );
    expect(entryButton.onPressed, isNotNull);

    final rabbitRow = find.byKey(const ValueKey('cage-rabbit-row-801'));
    await tester.ensureVisible(rabbitRow);
    await tester.drag(
      find.byType(Scrollable).first,
      const Offset(0, -120),
    );
    await tester.pumpAndSettle();
    await tester.tap(rabbitRow);
    await tester.pumpAndSettle();

    expect(find.text('兔只详情页'), findsOneWidget);
    expect(find.byKey(const ValueKey('cage-rabbit-menu-801')), findsNothing);
  });

  testWidgets('empty cage keeps abnormal entry disabled with a reason',
      (tester) async {
    await tester.pumpWidget(
      _cageDetailApp(
        rabbits: () async => const <Rabbit>[],
        permission: const HousePermission(perms: 'edit', isAdmin: false),
      ),
    );
    await tester.pumpAndSettle();

    final button = tester.widget<OutlinedButton>(
      find.byKey(const ValueKey('cage-abnormal-entry')),
    );
    expect(button.onPressed, isNull);
    expect(
      find.text('当前笼位没有在栏兔只，无法新增异常记录'),
      findsOneWidget,
    );
    expect(
      find.byTooltip('当前笼位没有在栏兔只，无法新增异常记录'),
      findsOneWidget,
    );
  });

  testWidgets('single-rabbit cage opens the shared abnormal sheet directly',
      (tester) async {
    await tester.pumpWidget(
      _cageDetailApp(
        rabbits: () async => const [_rabbit],
        permission: const HousePermission(perms: 'edit', isAdmin: false),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('cage-abnormal-entry')));
    await tester.pumpAndSettle();

    expect(find.text('新增异常记录'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-abnormal-target-801')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('cage-abnormal-rabbit-picker')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-abnormal-submit')),
      findsOneWidget,
    );
  });

  testWidgets(
      'multi-rabbit cage selects a target before opening abnormal sheet',
      (tester) async {
    await tester.pumpWidget(
      _cageDetailApp(
        rabbits: () async => const [_rabbit, _secondRabbit],
        permission: const HousePermission(perms: 'edit', isAdmin: false),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('cage-abnormal-entry')));
    await tester.pumpAndSettle();

    expect(find.text('选择异常兔只'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('cage-abnormal-rabbit-802')),
      findsOneWidget,
    );
    await tester.tap(
      find.byKey(const ValueKey('cage-abnormal-rabbit-802')),
    );
    await tester.pumpAndSettle();

    expect(find.text('新增异常记录'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-abnormal-target-802')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-abnormal-submit')),
      findsOneWidget,
    );
  });

  testWidgets(
      'rabbit loading failure keeps abnormal entry visible and disabled',
      (tester) async {
    await tester.pumpWidget(
      _cageDetailApp(
        rabbits: () => Future<List<Rabbit>>.error(StateError('读取失败')),
        permission: const HousePermission(perms: 'edit', isAdmin: false),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('cage-abnormal-entry')),
      findsOneWidget,
    );
    expect(
      tester
          .widget<OutlinedButton>(
            find.byKey(const ValueKey('cage-abnormal-entry')),
          )
          .onPressed,
      isNull,
    );
    expect(
      find.text('无法读取笼内兔只，请重试后新增异常记录'),
      findsOneWidget,
    );
  });

  testWidgets('cage detail back button returns to the cage list',
      (tester) async {
    const key = (houseId: 8, cageId: 10);
    final router = GoRouter(
      initialLocation: '/houses/8/cages/10',
      routes: [
        GoRoute(
          path: '/houses/:houseId/cages',
          builder: (_, __) => const Scaffold(body: Text('笼位列表页')),
        ),
        GoRoute(
          path: '/houses/:houseId/cages/:cageId',
          builder: (_, __) => const CageDetailScreen(houseId: 8, cageId: 10),
        ),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          cageSummaryProvider(key).overrideWith((_) async => _summary),
          cageRabbitsProvider(key).overrideWith((_) async => const <Rabbit>[]),
          houseCagesProvider(8).overrideWith((_) async => const [_cage]),
          housesProvider.overrideWith((_) async => const [_house]),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'view', isAdmin: false),
          ),
        ],
        child: MaterialApp.router(
          theme: buildAppTheme(),
          routerConfig: router,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('cage-detail-back-button')),
    );
    await tester.pumpAndSettle();

    expect(find.text('笼位列表页'), findsOneWidget);
  });
}

Widget _cageDetailApp({
  required Future<List<Rabbit>> Function() rabbits,
  required HousePermission permission,
}) {
  const key = (houseId: 8, cageId: 10);
  return ProviderScope(
    overrides: [
      cageSummaryProvider(key).overrideWith((_) async => _summary),
      cageRabbitsProvider(key).overrideWith((_) => rabbits()),
      houseCagesProvider(8).overrideWith((_) async => const [_cage]),
      housesProvider.overrideWith((_) async => const [_house]),
      housePermissionProvider(8).overrideWith((_) async => permission),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: const CageDetailScreen(houseId: 8, cageId: 10),
    ),
  );
}

const _summary = CageSummary(
  cageId: 10,
  cageNumber: '1-1-1',
  rabbitCount: 0,
  isFed: false,
  lastFeedTime: null,
  lastFeedType: '',
  lastFeedAmount: null,
  lastFeedUnit: '',
  abnormalUndealCount: 0,
  lastAbnormalTime: null,
  lastAbnormalStatus: '',
);

const _cage = Cage(
  id: 10,
  houseId: 8,
  cageNumber: '1-1-1',
  status: '0',
  rabbitCount: 0,
  isEnabled: true,
);

const _house = RabbitHouse(
  id: 8,
  name: '测试兔舍',
  remark: '',
  layoutRows: 1,
  layoutCols: 1,
  layoutLayers: 1,
);

const _rabbit = Rabbit(
  id: 801,
  houseId: 8,
  cageId: 10,
  motherId: null,
  type: '2',
  gender: '1',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 2.8,
  isActive: true,
);

const _secondRabbit = Rabbit(
  id: 802,
  houseId: 8,
  cageId: 10,
  motherId: null,
  type: '2',
  gender: '0',
  breed: '伊拉兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 2.6,
  isActive: true,
);
