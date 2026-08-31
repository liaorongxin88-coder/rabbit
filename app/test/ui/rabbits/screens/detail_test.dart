import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/rabbits/batch_membership.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/screens/detail.dart';

void main() {
  testWidgets('active rabbit detail owns all individual operations',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final router = _router();
    addTearDown(router.dispose);

    await tester.pumpWidget(_app(router: router, rabbit: _activeRabbit));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('rabbit-detail-page-content')),
      findsOneWidget,
    );
    expect(find.text('兔 #31'), findsOneWidget);
    expect(find.text('在栏'), findsOneWidget);
    expect(find.text('商品兔'), findsWidgets);
    expect(find.text('1-2-1'), findsWidgets);
    expect(find.text('2025-08-23'), findsOneWidget);
    final outbound = find.byKey(
      const ValueKey('rabbit-detail-outbound-31'),
    );
    expect(outbound, findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-detail-replacement-31')),
      findsOneWidget,
    );
    expect(tester.widget(outbound), isA<OutlinedButton>());
    expect(
      find.byKey(const ValueKey('rabbit-detail-move-31')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-edit-31')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-departure-31')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-bind-batch-31')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-inline-actions')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-abnormal-31')),
      findsOneWidget,
    );
    expect(find.text('异常记录'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-add-abnormal-action')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-fixed-actions')),
      findsNothing,
    );
    expect(
      find.ancestor(
        of: find.byKey(const ValueKey('rabbit-detail-departure-31')),
        matching: find.byKey(const ValueKey('rabbit-detail-scroll')),
      ),
      findsOneWidget,
    );
    expect(find.byTooltip('关闭'), findsNothing);
    expect(tester.takeException(), isNull);

    await tester.tap(find.byKey(const ValueKey('page-back-button')));
    await tester.pumpAndSettle();
    expect(find.text('兔只列表页'), findsOneWidget);
  });

  testWidgets('rabbit detail abnormal action opens the shared abnormal sheet',
      (tester) async {
    final router = _router();
    addTearDown(router.dispose);

    await tester.pumpWidget(_app(router: router, rabbit: _activeRabbit));
    await tester.pumpAndSettle();

    final action = find.byKey(const ValueKey('rabbit-detail-abnormal-31'));
    await tester.ensureVisible(action);
    await tester.tap(action);
    await tester.pumpAndSettle();

    expect(find.text('新增异常记录'), findsOneWidget);
    expect(find.text('兔 #31 · 商品兔'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-abnormal-submit')),
      findsOneWidget,
    );
  });

  testWidgets('view-only rabbit detail hides abnormal action', (tester) async {
    final router = _router();
    addTearDown(router.dispose);

    await tester.pumpWidget(
      _app(
        router: router,
        rabbit: _activeRabbit,
        permission: const HousePermission(perms: 'view', isAdmin: false),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('rabbit-detail-abnormal-31')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-add-abnormal-action')),
      findsNothing,
    );
  });

  testWidgets('active breeder uses the sale action', (tester) async {
    final router = _router();
    addTearDown(router.dispose);

    await tester.pumpWidget(_app(router: router, rabbit: _activeBreeder));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('rabbit-detail-sale-31')), findsOneWidget);
    expect(
        find.byKey(const ValueKey('rabbit-detail-outbound-31')), findsNothing);
    expect(
      find.byKey(const ValueKey('rabbit-detail-replacement-31')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-promotion-31')),
      findsNothing,
    );
  });

  testWidgets('active replacement exposes sale and promotion actions',
      (tester) async {
    final router = _router();
    addTearDown(router.dispose);

    await tester.pumpWidget(_app(router: router, rabbit: _activeReplacement));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('rabbit-detail-sale-31')), findsOneWidget);
    expect(
        find.byKey(const ValueKey('rabbit-detail-outbound-31')), findsNothing);
    expect(
      find.byKey(const ValueKey('rabbit-detail-replacement-31')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-promotion-31')),
      findsOneWidget,
    );
  });

  testWidgets('replacement promotion requires control permission',
      (tester) async {
    final router = _router();
    addTearDown(router.dispose);

    await tester.pumpWidget(
      _app(
        router: router,
        rabbit: _activeReplacement,
        permission: const HousePermission(perms: 'edit', isAdmin: false),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('rabbit-detail-promotion-31')),
      findsNothing,
    );
  });

  testWidgets('commodity replacement action requires control permission',
      (tester) async {
    final router = _router();
    addTearDown(router.dispose);

    await tester.pumpWidget(
      _app(
        router: router,
        rabbit: _activeRabbit,
        permission: const HousePermission(perms: 'edit', isAdmin: false),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('rabbit-detail-replacement-31')),
      findsNothing,
    );
  });

  testWidgets('existing batch tags can be extended or removed', (tester) async {
    final router = _router();
    addTearDown(router.dispose);

    await tester.pumpWidget(
      _app(
        router: router,
        rabbit: _activeRabbit,
        memberships: const [
          RabbitBatchMembership(
            batchId: 9,
            rabbitId: 31,
            isActive: true,
            batchRole: 'fattening',
          ),
          RabbitBatchMembership(
            batchId: 10,
            rabbitId: 31,
            isActive: true,
            batchRole: 'breeding',
            currentStage: 'AWAIT_PALPATION',
            currentCycleId: 701,
            batchCycleCount: 2,
            batchOperationCount: 7,
            batchLitterCount: 1,
            batchTotalKits: 8,
            batchLiveKits: 7,
            batchWeanedKits: 6,
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('批次标签'), findsOneWidget);
    expect(find.text('用途：养育/售卖'), findsOneWidget);
    expect(find.text('本批次阶段：待摸胎'), findsOneWidget);
    expect(find.text('2 个周期 · 7 次操作 · 1 窝'), findsOneWidget);
    expect(find.text('产仔 8 · 活仔 7 · 断奶 6'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-bind-batch-31')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-membership-remove-9')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('departed rabbit stays viewable with terminal actions disabled',
      (tester) async {
    final router = _router();
    addTearDown(router.dispose);

    await tester.pumpWidget(_app(router: router, rabbit: _departedRabbit));
    await tester.pumpAndSettle();

    expect(find.text('兔 #31'), findsOneWidget);
    expect(find.text('已离场'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-detail-edit-31')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-move-31')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-departure-31')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-outbound-31')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-replacement-31')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-promotion-31')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-detail-abnormal-31')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });
}

Widget _app({
  required GoRouter router,
  required Rabbit rabbit,
  List<RabbitBatchMembership> memberships = const [],
  HousePermission permission = const HousePermission(
    perms: 'control',
    isAdmin: true,
  ),
}) {
  const detailRequest = RabbitDetailRequest(houseId: 8, rabbitId: 31);
  const membershipRequest = RabbitBatchMembershipRequest(
    houseId: 8,
    rabbitId: 31,
  );
  return ProviderScope(
    overrides: [
      rabbitDetailProvider(detailRequest).overrideWith((_) async => rabbit),
      houseCagesProvider(8).overrideWith((_) async => const [_cage]),
      housePermissionProvider(8).overrideWith((_) async => permission),
      rabbitBatchMembershipsProvider(membershipRequest).overrideWith(
        (_) async => memberships,
      ),
    ],
    child: MaterialApp.router(
      theme: buildAppTheme(),
      routerConfig: router,
    ),
  );
}

GoRouter _router() {
  return GoRouter(
    initialLocation: '/houses/8/rabbits/31',
    routes: [
      GoRoute(
        path: '/houses/:houseId/rabbits',
        builder: (_, __) => const Scaffold(body: Text('兔只列表页')),
      ),
      GoRoute(
        path: '/houses/:houseId/rabbits/:rabbitId',
        builder: (_, __) => const RabbitDetailScreen(
          houseId: 8,
          rabbitId: 31,
        ),
      ),
    ],
  );
}

const _cage = Cage(
  id: 12,
  houseId: 8,
  cageNumber: '1-2-1',
  status: '3',
  rabbitCount: 1,
  isEnabled: true,
);

final _activeRabbit = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 12,
  motherId: null,
  type: '2',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: DateTime.utc(2025, 8, 22, 16),
  weight: 2.5,
  isActive: true,
);

const _activeBreeder = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 12,
  motherId: null,
  type: '0',
  gender: '1',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 2.5,
  isActive: true,
);

const _activeReplacement = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 12,
  motherId: null,
  type: '1',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 2.5,
  isActive: true,
);

const _departedRabbit = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 12,
  motherId: null,
  type: '2',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 2.5,
  isActive: false,
);
