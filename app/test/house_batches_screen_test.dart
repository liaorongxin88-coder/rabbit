import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/models/batch.dart';
import 'package:rabbit_flutter/src/domain/models/batch_rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/batch_providers.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/house_batch_detail_screen.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/house_batches_screen.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_detail_screen.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

void main() {
  testWidgets(
      'large Batch list filters lazily without overflowing at large text',
      (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final batches = _batches(1001);

    await tester.pumpWidget(
      _screenApp(
        batchOverride:
            houseBatchesProvider(8).overrideWith((_) async => batches),
        textScaler: const TextScaler.linear(2),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('共 1001 个 Batch · 已全部加载'), findsOneWidget);
    final list = tester.widget<ListView>(
      find.byKey(const ValueKey('batch-list')),
    );
    expect(list.childrenDelegate, isA<SliverChildBuilderDelegate>());
    expect(list.childrenDelegate.estimatedChildCount, 1006);
    expect(find.byKey(const ValueKey('batch-list-item-1001')), findsNothing);
    expect(tester.takeException(), isNull);

    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('batch-search-field')),
      240,
      scrollable: find
          .descendant(
            of: find.byKey(const ValueKey('batch-list')),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    await tester.enterText(
      find.byKey(const ValueKey('batch-search-field')),
      'B-1001',
    );
    await tester.pumpAndSettle();

    expect(find.text('显示 1 / 1001 个 Batch'), findsOneWidget);
    expect(find.byKey(const ValueKey('batch-list-item-1001')), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('batch-search-clear')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('batch-status-filter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('已完成').last);
    await tester.pumpAndSettle();

    expect(find.text('显示 333 / 1001 个 Batch'), findsOneWidget);
    expect(tester.takeException(), isNull);

    tester.view.physicalSize = const Size(412, 915);
    await tester.pump();
    expect(tester.takeException(), isNull);
  });

  testWidgets('loading state waits for the complete Batch result',
      (tester) async {
    final pending = Completer<List<Batch>>();
    await tester.pumpWidget(
      _screenApp(
        batchOverride:
            houseBatchesProvider(8).overrideWith((_) => pending.future),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(find.text('正在加载全部 Batch...'), findsOneWidget);

    pending.complete(const <Batch>[]);
    await tester.pumpAndSettle();
    expect(find.text('暂无生产批次'), findsOneWidget);
  });

  testWidgets('Batch error retries into empty state and opens creation sheet',
      (tester) async {
    var attempts = 0;
    await tester.pumpWidget(
      _screenApp(
        batchOverride: houseBatchesProvider(8).overrideWith((_) async {
          attempts += 1;
          if (attempts == 1) {
            throw Exception('fixture Batch network error');
          }
          return const <Batch>[];
        }),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('加载失败'), findsOneWidget);
    expect(find.textContaining('fixture Batch network error'), findsOneWidget);

    await tester.tap(find.text('重试'));
    await tester.pumpAndSettle();

    expect(attempts, 2);
    expect(find.text('暂无生产批次'), findsOneWidget);
    expect(find.byKey(const ValueKey('batch-create-button')), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('batch-create-button')));
    await tester.pumpAndSettle();

    expect(find.text('创建生产批次'), findsOneWidget);
    final emptyMotherMessage = find.text('暂无种母兔，请先在笼位录入种母兔。');
    await tester.scrollUntilVisible(
      emptyMotherMessage,
      160,
      scrollable: find
          .descendant(
            of: find.byKey(const ValueKey('batch-mother-list')),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    expect(emptyMotherMessage, findsOneWidget);
  });

  testWidgets('Batch list trims status values consistently', (tester) async {
    const batch = Batch(
      id: 16,
      houseId: 8,
      batchCode: 'TRIM-16',
      status: ' 已完成 ',
      startDate: null,
      endDate: null,
      remark: '',
    );

    await tester.pumpWidget(
      _screenApp(
        batchOverride:
            houseBatchesProvider(8).overrideWith((_) async => const [batch]),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('已完成'), findsOneWidget);
    expect(find.text(' 已完成 '), findsNothing);
    await tester.tap(find.byKey(const ValueKey('batch-status-filter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('已完成').last);
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('batch-list-item-16')), findsOneWidget);
    expect(find.text('显示 1 / 1 个 Batch'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('house detail navigates through the Batch entry', (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final router = GoRouter(
      initialLocation: '/houses/8',
      routes: [
        GoRoute(
          path: '/houses/:houseId',
          builder: (_, __) => const HouseDetailScreen(houseId: 8),
        ),
        GoRoute(
          path: '/houses/:houseId/batches',
          builder: (_, __) => const HouseBatchesScreen(houseId: 8),
        ),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith((_) async => const [_house]),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'control',
              isAdmin: true,
            ),
          ),
          houseCagesProvider(8).overrideWith((_) async => const []),
          houseRabbitsProvider(8).overrideWith((_) async => const []),
          houseBatchesProvider(8).overrideWith(
            (_) async => const <Batch>[],
          ),
        ],
        child: MaterialApp.router(
          theme: buildAppTheme(),
          routerConfig: router,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('house-batches-entry')),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.byKey(const ValueKey('house-batches-entry')));
    await tester.pumpAndSettle();

    expect(find.byType(HouseBatchesScreen), findsOneWidget);
    expect(find.text('Batch 列表'), findsOneWidget);
  });

  testWidgets('Batch list card opens the matching Batch detail route',
      (tester) async {
    const batch = Batch(
      id: 42,
      houseId: 8,
      batchCode: 'NAV-42',
      status: '进行中',
      startDate: null,
      endDate: null,
      remark: '路由参数验收',
    );
    const request = BatchDetailRequest(houseId: 8, batchId: 42);
    final router = GoRouter(
      initialLocation: '/houses/8/batches',
      routes: [
        GoRoute(
          path: '/houses/:houseId/batches',
          builder: (_, __) => const HouseBatchesScreen(houseId: 8),
        ),
        GoRoute(
          path: '/houses/:houseId/batches/:batchId',
          builder: (_, state) => HouseBatchDetailScreen(
            houseId: int.parse(state.pathParameters['houseId']!),
            batchId: int.parse(state.pathParameters['batchId']!),
          ),
        ),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith((_) async => const [_house]),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'control', isAdmin: true),
          ),
          allActiveHouseRabbitsProvider(8).overrideWith((_) async => const []),
          houseBatchesProvider(8).overrideWith((_) async => const [batch]),
          batchDetailProvider(request).overrideWith((_) async => batch),
          batchMembersProvider(request).overrideWith(
            (_) async => const [
              BatchRabbitItem(
                id: 1,
                batchId: 42,
                rabbitId: 4201,
                currentStatus: '待催情',
                nextEventType: '',
                batchRole: 'breeding',
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

    await tester.tap(find.byKey(const ValueKey('batch-list-item-42')));
    await tester.pumpAndSettle();

    expect(find.byType(HouseBatchDetailScreen), findsOneWidget);
    expect(find.text('NAV-42'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('batch-member-4201')),
      260,
      scrollable: find
          .descendant(
            of: find.byKey(const ValueKey('batch-detail-member-list')),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    await tester.pumpAndSettle();
    expect(find.text('母兔 #4201'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

Widget _screenApp({
  required Override batchOverride,
  TextScaler textScaler = TextScaler.noScaling,
}) {
  return ProviderScope(
    overrides: [
      housesProvider.overrideWith((_) async => const [_house]),
      housePermissionProvider(8).overrideWith(
        (_) async => const HousePermission(
          perms: 'control',
          isAdmin: true,
        ),
      ),
      allActiveHouseRabbitsProvider(8).overrideWith((_) async => const []),
      batchOverride,
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(textScaler: textScaler),
        child: child!,
      ),
      home: const HouseBatchesScreen(houseId: 8),
    ),
  );
}

const _house = RabbitHouse(
  id: 8,
  name: '规模生产兔舍',
  remark: '',
  layoutRows: 10,
  layoutCols: 20,
  layoutLayers: 5,
);

List<Batch> _batches(int count) {
  const statuses = ['计划中', '进行中', '已完成'];
  return List.generate(
    count,
    (index) => Batch(
      id: index + 1,
      houseId: 8,
      batchCode: 'B-${(index + 1).toString().padLeft(4, '0')}',
      status: statuses[index % statuses.length],
      startDate: DateTime(2026, 1, 1).add(Duration(days: index)),
      endDate: index % statuses.length == 2
          ? DateTime(2026, 2, 1).add(Duration(days: index))
          : null,
      remark: '第 ${index + 1} 个规模测试 Batch',
    ),
  );
}
