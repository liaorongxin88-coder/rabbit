import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/data/repositories/houses/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/operation_events/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/operation_events/event.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/screens/detail.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/operation_events/screens/list.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

void main() {
  testWidgets('operation events back entry matches the cage management style',
      (tester) async {
    final repository = _FakeOperationEventsRepository(
      (_) async => _page([_event(id: 1)]),
    );
    final router = GoRouter(
      initialLocation: '/houses/8/operation-events',
      routes: [
        GoRoute(
          path: '/houses/:houseId',
          builder: (_, __) => const Scaffold(body: Text('兔舍详情页')),
        ),
        GoRoute(
          path: '/houses/:houseId/operation-events',
          builder: (_, __) => const HouseOperationEventsScreen(houseId: 8),
        ),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          operationEventsRepositoryProvider.overrideWithValue(repository),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'control',
              isAdmin: false,
              role: 'MANAGER',
              permissions: ['rabbit:audit:list'],
            ),
          ),
        ],
        child: MaterialApp.router(
          theme: buildAppTheme(),
          routerConfig: router,
        ),
      ),
    );
    await tester.pumpAndSettle();

    // 跟笼位管理页面一样：箭头配“返回”文字，触摸目标不低于 48。
    final back = find.byKey(const ValueKey('page-back-button'));
    expect(back, findsOneWidget);
    expect(
        find.descendant(of: back, matching: find.text('返回')), findsOneWidget);
    expect(
      find.descendant(of: back, matching: find.byIcon(Icons.arrow_back)),
      findsOneWidget,
    );
    expect(tester.getSize(back).height, greaterThanOrEqualTo(48));

    await tester.tap(back);
    await tester.pumpAndSettle();
    expect(find.text('兔舍详情页'), findsOneWidget);
    expect(router.routeInformationProvider.value.uri.path, '/houses/8');
  });

  testWidgets('operation events show a loading state while the page loads',
      (tester) async {
    final completer = Completer<OperationEventsPage>();
    final repository = _FakeOperationEventsRepository(
      (_) => completer.future,
    );

    await _pumpOperationEvents(tester, repository, settle: false);
    await tester.pump();
    await tester.pump();

    expect(
      find.byKey(const ValueKey('operation-events-loading')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('operation-events-list')), findsNothing);

    completer.complete(_page(const []));
    await tester.pumpAndSettle();
  });

  testWidgets('operation events show results and load the next cursor page',
      (tester) async {
    final repository = _FakeOperationEventsRepository((query) async {
      if (query.cursor == null) {
        return _page([_event(id: 1)], nextCursor: 'next-1', hasMore: true);
      }
      return _page([_event(id: 2)], hasMore: false);
    });

    await _pumpOperationEvents(tester, repository);

    expect(find.byKey(const ValueKey('operation-events-list')), findsOneWidget);
    expect(find.text('登记接种'), findsOneWidget);
    expect(find.text('操作人：值班员'), findsOneWidget);
    final loadMore = find.byKey(const ValueKey('operation-events-load-more'));
    await tester.ensureVisible(loadMore);
    await tester.tap(loadMore);
    await tester.pumpAndSettle();

    expect(repository.queries, hasLength(2));
    expect(repository.queries[1].cursor, 'next-1');
    expect(find.text('事件 #2'), findsOneWidget);
  });

  testWidgets('operation events use an explicit empty state', (tester) async {
    await _pumpOperationEvents(
      tester,
      _FakeOperationEventsRepository((_) async => _page(const [])),
    );

    expect(
      find.byKey(const ValueKey('operation-events-empty')),
      findsOneWidget,
    );
    expect(find.text('这里还没有操作记录'), findsOneWidget);
    expect(find.textContaining('投喂和接种'), findsOneWidget);
  });

  testWidgets('operation event retry only reloads the failed event section',
      (tester) async {
    var requests = 0;
    final repository = _FakeOperationEventsRepository((_) async {
      requests += 1;
      if (requests == 1) {
        throw const ApiException('网络不可用');
      }
      return _page([_event(id: 3)]);
    });

    await _pumpOperationEvents(tester, repository);

    expect(
      find.byKey(const ValueKey('operation-events-error')),
      findsOneWidget,
    );
    expect(find.textContaining('网络不可用'), findsOneWidget);
    final retry = find.text('重试');
    await tester.ensureVisible(retry);
    await tester.tap(retry);
    await tester.pumpAndSettle();

    expect(requests, 2);
    expect(find.byKey(const ValueKey('operation-events-list')), findsOneWidget);
    expect(find.text('事件 #3'), findsOneWidget);
  });

  testWidgets('viewer cannot see the operation events entry', (tester) async {
    final houses = _FakeHouseRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          houseRepositoryProvider.overrideWithValue(houses),
          housesProvider.overrideWith((_) => houses.listHouses()),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'view',
              isAdmin: false,
              role: 'VIEWER',
            ),
          ),
          houseCagesProvider(8).overrideWith((_) async => const []),
          houseRabbitsProvider(8).overrideWith((_) async => const []),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseDetailScreen(houseId: 8),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('house-operation-events-entry')),
      findsNothing,
    );
  });

  testWidgets('operation event list works at 360 by 800 and 200 percent text',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final longEvent = OperationEvent(
      id: 4,
      occurredAt: DateTime.parse('2025-08-01T08:00:00Z'),
      operationCode: 'vaccination:create:long-operation-code',
      eventType: 'VACCINATION',
      eventLabel: '这是一个用于验证操作记录在大字体窄屏下仍然可用的超长接种事件名称',
      targetType: 'RABBIT',
      targetId: 31,
      cageId: 12,
      batchId: 61,
      rabbitId: 31,
      cycleId: 701,
      litterId: 801,
      fromStage: 'AWAIT_PALPATION',
      toStage: 'AWAIT_PREPARTUM',
      operatorId: 7,
      operatorName: '当前负责接种登记和日常饲养管理的值班员',
    );

    await _pumpOperationEvents(
      tester,
      _FakeOperationEventsRepository((_) async => _page([longEvent])),
    );

    await tester.ensureVisible(find.text(longEvent.eventLabel));
    expect(tester.takeException(), isNull);
  });
}

Future<void> _pumpOperationEvents(
  WidgetTester tester,
  _FakeOperationEventsRepository repository, {
  bool settle = true,
}) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        operationEventsRepositoryProvider.overrideWithValue(repository),
        housePermissionProvider(8).overrideWith(
          (_) async => const HousePermission(
            perms: 'control',
            isAdmin: false,
            role: 'MANAGER',
            permissions: ['rabbit:audit:list'],
          ),
        ),
      ],
      child: MaterialApp(
        theme: buildAppTheme(),
        home: const HouseOperationEventsScreen(houseId: 8),
      ),
    ),
  );
  await tester.pump();
  if (settle) {
    await tester.pumpAndSettle();
  }
}

OperationEvent _event({required int id}) {
  return OperationEvent(
    id: id,
    occurredAt: DateTime.parse('2025-08-01T08:00:00Z'),
    operationCode: 'vaccination:create',
    eventType: 'VACCINATION',
    eventLabel: id == 1 ? '登记接种' : '事件 #$id',
    targetType: 'RABBIT',
    targetId: 31,
    cageId: 12,
    batchId: 61,
    rabbitId: 31,
    cycleId: null,
    litterId: null,
    fromStage: null,
    toStage: null,
    operatorId: 7,
    operatorName: '值班员',
  );
}

OperationEventsPage _page(
  List<OperationEvent> items, {
  String? nextCursor,
  bool hasMore = false,
}) {
  return OperationEventsPage(
    items: items,
    nextCursor: nextCursor,
    hasMore: hasMore,
  );
}

class _FakeOperationEventsRepository extends OperationEventsRepository {
  _FakeOperationEventsRepository(this._load) : super(ApiClient(SessionStore()));

  final Future<OperationEventsPage> Function(OperationEventsQuery query) _load;
  final queries = <OperationEventsQuery>[];

  @override
  Future<OperationEventsPage> listOperationEvents({
    required int houseId,
    OperationEventsQuery query = const OperationEventsQuery(),
    CancelToken? cancelToken,
  }) {
    queries.add(query);
    return _load(query);
  }
}

class _FakeHouseRepository extends HouseRepository {
  _FakeHouseRepository() : super(ApiClient(SessionStore()));

  @override
  Future<List<RabbitHouse>> listHouses() async => const [
        RabbitHouse(
          id: 8,
          name: '测试兔舍',
          remark: '',
          layoutRows: 2,
          layoutCols: 3,
          layoutLayers: 2,
        ),
      ];
}
