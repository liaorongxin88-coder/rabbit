import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_batch_membership.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/widgets/rabbit_detail_screen.dart';

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
    final outbound = find.byKey(
      const ValueKey('rabbit-detail-outbound-31'),
    );
    expect(outbound, findsOneWidget);
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
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('批次标签'), findsOneWidget);
    expect(find.text('用途：养育/售卖'), findsOneWidget);
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
    expect(tester.takeException(), isNull);
  });
}

Widget _app({
  required GoRouter router,
  required Rabbit rabbit,
  List<RabbitBatchMembership> memberships = const [],
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
      housePermissionProvider(8).overrideWith(
        (_) async => const HousePermission(
          perms: 'control',
          isAdmin: true,
        ),
      ),
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

const _activeRabbit = Rabbit(
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
