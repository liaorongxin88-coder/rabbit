import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/reproduction/event.dart';
import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/home/screens/overview.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';

void main() {
  testWidgets('production queue filters remain usable at true 200 percent text',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final now = DateTime.now();
    final events = [
      EventItem(
        recordId: 11,
        category: '生产周期',
        eventType: '配种',
        eventDate: now.subtract(const Duration(days: 1)),
        batchId: 7,
        rabbitId: 101,
        status: 'overdue',
        sourceHouseId: 1,
        sourceHouseName: '一号兔舍',
      ),
      EventItem(
        recordId: 12,
        category: '生产周期',
        eventType: '摸胎',
        eventDate: now,
        batchId: 7,
        rabbitId: 102,
        status: 'due',
        sourceHouseId: 1,
        sourceHouseName: '一号兔舍',
      ),
      EventItem(
        recordId: 13,
        category: '生产周期',
        eventType: '分娩',
        eventDate: now.add(const Duration(days: 1)),
        batchId: 8,
        rabbitId: 201,
        status: 'upcoming',
        sourceHouseId: 2,
        sourceHouseName: '二号兔舍',
      ),
    ];

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith(
            (_) async => const [
              RabbitHouse(
                id: 1,
                name: '一号兔舍',
                remark: '',
                layoutRows: 1,
                layoutCols: 1,
                layoutLayers: 1,
              ),
              RabbitHouse(
                id: 2,
                name: '二号兔舍',
                remark: '',
                layoutRows: 1,
                layoutCols: 1,
                layoutLayers: 1,
              ),
            ],
          ),
          homeEventsProvider.overrideWith((_) async => events),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              textScaler: const TextScaler.linear(2),
            ),
            child: child!,
          ),
          home: const HomeScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('3 / 3'), findsOneWidget);
    expect(tester.takeException(), isNull);

    final flowSection = find.byKey(
      const ValueKey('home-production-flow-section'),
    );
    await tester.ensureVisible(flowSection);
    await tester.pumpAndSettle();
    final matingTab = find.descendant(
      of: find.byType(TabBar),
      matching: find.text('配种'),
    );
    await tester.ensureVisible(matingTab);
    await tester.tap(matingTab);
    await tester.pumpAndSettle();

    final countBadge = find.byKey(
      const ValueKey('production-event-count-badge-配种'),
    );
    final badgeRect = tester.getRect(countBadge);
    final tabBarRect = tester.getRect(find.byType(TabBar));
    expect(badgeRect.height, lessThan(40));
    expect(badgeRect.top, greaterThan(tabBarRect.top));
    expect(badgeRect.bottom, lessThan(tabBarRect.bottom));

    final search = find.byKey(const ValueKey('production-work-search'));
    await tester.ensureVisible(search);
    await tester.enterText(search, '101');
    await tester.pumpAndSettle();

    expect(find.text('1 / 3'), findsOneWidget);
    expect(find.text('母兔 #101'), findsOneWidget);

    final clearFilters = find.byKey(
      const ValueKey('production-filter-clear'),
    );
    await tester.ensureVisible(clearFilters);
    await tester.pumpAndSettle();
    await tester.tap(clearFilters);
    await tester.pumpAndSettle();
    expect(find.text('3 / 3'), findsOneWidget);

    final alertOverdue = find.byKey(
      const ValueKey('home-alert-filter-overdue'),
    );
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.drag(
      find.byKey(const ValueKey('home-scroll')),
      const Offset(0, 1000),
    );
    await tester.pumpAndSettle();
    await tester.ensureVisible(alertOverdue);
    await tester.pumpAndSettle();
    await tester.tap(alertOverdue);
    await tester.pumpAndSettle();

    expect(find.text('1 / 3'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('production-due-filter-all')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('production-due-filter-overdue')),
      findsNothing,
    );

    await tester.tap(alertOverdue);
    await tester.pumpAndSettle();
    expect(find.text('3 / 3'), findsOneWidget);

    await tester.tap(alertOverdue);
    await tester.pumpAndSettle();

    expect(find.text('1 / 3'), findsOneWidget);
    expect(find.text('母兔 #101'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('home exposes the estrus flow tab for filtered reminders',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final event = EventItem(
      recordId: 21,
      category: '生产周期',
      eventType: '催情',
      eventDate: DateTime.now(),
      batchId: 9,
      rabbitId: 301,
      status: 'due',
      sourceHouseId: 1,
      sourceHouseName: '一号兔舍',
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith(
            (_) async => const [
              RabbitHouse(
                id: 1,
                name: '一号兔舍',
                remark: '',
                layoutRows: 1,
                layoutCols: 1,
                layoutLayers: 1,
              ),
            ],
          ),
          homeEventsProvider.overrideWith((_) async => [event]),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HomeScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final estrusTab = find.descendant(
      of: find.byType(TabBar),
      matching: find.text('催情'),
    );
    expect(estrusTab, findsOneWidget);
    await tester.ensureVisible(estrusTab);
    await tester.tap(estrusTab);
    await tester.pumpAndSettle();
    expect(find.text('母兔 #301'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('home groups overview, filters, and production flow into frames',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith(
            (_) async => const [
              RabbitHouse(
                id: 1,
                name: '一号兔舍',
                remark: '',
                layoutRows: 1,
                layoutCols: 1,
                layoutLayers: 1,
              ),
            ],
          ),
          homeEventsProvider.overrideWith((_) async => const []),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HomeScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('鸿兔智管'), findsOneWidget);
    for (final sectionKey in const [
      ValueKey('home-production-overview-section'),
      ValueKey('home-work-queue-filter-section'),
      ValueKey('home-production-flow-section'),
    ]) {
      final section = find.byKey(sectionKey);
      expect(section, findsOneWidget);
      expect(
        find.descendant(of: section, matching: find.byType(Card)),
        findsOneWidget,
      );
    }
    expect(find.text('今日提醒'), findsOneWidget);
    expect(find.text('筛选任务'), findsOneWidget);
    expect(find.text('提醒事件'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('replacement event requires control permission', (tester) async {
    final event = EventItem(
      recordId: 41,
      category: '后备成熟',
      eventType: '后备兔成熟',
      eventDate: DateTime.now(),
      batchId: null,
      rabbitId: 501,
      status: 'due',
      sourceHouseId: 8,
      sourceHouseName: '测试兔舍',
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith(
            (_) async => const [
              RabbitHouse(
                id: 8,
                name: '测试兔舍',
                remark: '',
                layoutRows: 1,
                layoutCols: 1,
                layoutLayers: 1,
              ),
            ],
          ),
          homeEventsProvider.overrideWith((_) async => [event]),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'edit', isAdmin: false),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HomeScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final replacementTab = find.descendant(
      of: find.byType(TabBar),
      matching: find.text('后备兔'),
    );
    await tester.ensureVisible(replacementTab);
    await tester.tap(replacementTab);
    await tester.pumpAndSettle();

    final action = find.byKey(const ValueKey('production-event-rabbit-501'));
    expect(action, findsOneWidget);
    await tester.tap(action);
    await tester.pumpAndSettle();

    expect(find.text('当前权限无法将后备兔转为种兔'), findsOneWidget);
    expect(
        find.byKey(const ValueKey('production-event-form-list')), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('rabbit reminder opens the matching rabbit detail route',
      (tester) async {
    final event = EventItem(
      recordId: 31,
      category: '生产周期',
      eventType: '配种',
      eventDate: DateTime.now(),
      batchId: 9,
      rabbitId: 401,
      status: 'due',
      sourceHouseId: 8,
      sourceHouseName: '测试兔舍',
    );
    final router = GoRouter(
      initialLocation: '/',
      routes: [
        GoRoute(path: '/', builder: (_, __) => const HomeScreen()),
        GoRoute(
          path: '/houses/:houseId/rabbits/:rabbitId',
          builder: (_, state) => Scaffold(
            body: Text(
              '兔只详情 ${state.pathParameters['houseId']}/'
              '${state.pathParameters['rabbitId']}',
            ),
          ),
        ),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith(
            (_) async => const [
              RabbitHouse(
                id: 8,
                name: '测试兔舍',
                remark: '',
                layoutRows: 1,
                layoutCols: 1,
                layoutLayers: 1,
              ),
            ],
          ),
          homeEventsProvider.overrideWith((_) async => [event]),
        ],
        child: MaterialApp.router(
          theme: buildAppTheme(),
          routerConfig: router,
        ),
      ),
    );
    await tester.pumpAndSettle();

    final detailLink = find.byKey(
      const ValueKey('production-event-rabbit-detail-401'),
    );
    await tester.drag(
      find.byKey(const ValueKey('home-scroll')),
      const Offset(0, -320),
    );
    await tester.pumpAndSettle();

    final statusRail = find.byKey(
      const ValueKey('production-event-status-rail-401'),
    );
    final eventCard = find.ancestor(
      of: statusRail,
      matching: find.byType(Card),
    );
    final railRect = tester.getRect(statusRail);
    final cardRect = tester.getRect(eventCard);
    expect(railRect.left, greaterThan(cardRect.left));
    expect(railRect.top, greaterThan(cardRect.top));
    expect(railRect.bottom, lessThan(cardRect.bottom));

    await tester.tap(detailLink);
    await tester.pumpAndSettle();

    expect(find.text('兔只详情 8/401'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('home shows daily commodity care titles and instructions',
      (tester) async {
    final event = EventItem(
      recordId: 61,
      category: '生产',
      eventType: '生长饲喂观察',
      eventDate: DateTime.now(),
      batchId: null,
      rabbitId: 701,
      status: 'due',
      sourceHouseId: 8,
      sourceHouseName: '测试兔舍',
      content: '观察采食、饮水和投料量。',
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith(
            (_) async => const [
              RabbitHouse(
                id: 8,
                name: '测试兔舍',
                remark: '',
                layoutRows: 1,
                layoutCols: 1,
                layoutLayers: 1,
              ),
            ],
          ),
          homeEventsProvider.overrideWith((_) async => [event]),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HomeScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final dailyTab = find.descendant(
      of: find.byType(TabBar),
      matching: find.text('日常'),
    );
    await tester.ensureVisible(dailyTab);
    await tester.tap(dailyTab);
    await tester.pumpAndSettle();

    expect(find.text('生长饲喂观察'), findsOneWidget);
    expect(find.text('观察采食、饮水和投料量。'), findsOneWidget);
    expect(find.text('兔 #701'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
