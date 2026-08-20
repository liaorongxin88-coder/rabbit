import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/cage_summary.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_batch_membership.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/cage_providers.dart';
import 'package:rabbit_flutter/src/ui/cages/widgets/cage_detail_screen.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/nfc_queue_provider.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

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
    await tester.tap(rabbitRow);
    await tester.pumpAndSettle();

    expect(find.text('兔只详情页'), findsOneWidget);
    expect(find.byKey(const ValueKey('cage-rabbit-menu-801')), findsNothing);
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
