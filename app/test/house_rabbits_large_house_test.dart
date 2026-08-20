import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_batch_membership.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/domain/models/repro_task.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_detail_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_rabbits_screen.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

void main() {
  testWidgets('large house shows complete total and lazily builds rabbit rows',
      (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final rabbits = _rabbits(1001);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith((_) async => const [_house]),
          houseCagesProvider(8).overrideWith((_) async => const [_cage]),
          houseRabbitsProvider(8).overrideWith((_) async => rabbits),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'control',
              isAdmin: true,
            ),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseRabbitsScreen(houseId: 8),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('共 1001 只 · 已全部加载'), findsOneWidget);
    expect(find.byKey(const ValueKey('house-rabbit-1')), findsOneWidget);
    expect(find.byKey(const ValueKey('house-rabbit-1001')), findsNothing);

    final list = tester.widget<ListView>(
      find.byKey(const ValueKey('house-rabbit-list')),
    );
    expect(list.childrenDelegate, isA<SliverChildBuilderDelegate>());
    expect(list.childrenDelegate.estimatedChildCount, 1006);
    expect(tester.takeException(), isNull);

    tester.view.physicalSize = const Size(412, 915);
    await tester.pump();

    expect(find.text('共 1001 只 · 已全部加载'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('house detail reports the fully loaded rabbit count',
      (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith((_) async => const [_house]),
          houseCagesProvider(8).overrideWith((_) async => const [_cage]),
          houseRabbitsProvider(8).overrideWith((_) async => _rabbits(1001)),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'control',
              isAdmin: true,
            ),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseDetailScreen(houseId: 8),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('1001'), findsOneWidget);
    expect(find.text('已全部加载'), findsNWidgets(2));
    expect(tester.takeException(), isNull);
  });

  testWidgets('doe detail separates pending tasks from batch relationships',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    const activeRequest = RabbitBatchMembershipRequest(
      houseId: 8,
      rabbitId: 31,
    );
    const historyRequest = RabbitBatchMembershipRequest(
      houseId: 8,
      rabbitId: 31,
      active: false,
    );
    const taskRequest = RabbitReproTasksRequest(
      houseId: 8,
      rabbitId: 31,
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith((_) async => const [_house]),
          houseCagesProvider(8).overrideWith((_) async => const [_doeCage]),
          houseRabbitsProvider(8).overrideWith((_) async => const [_doe]),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'control',
              isAdmin: true,
            ),
          ),
          rabbitBatchMembershipsProvider(activeRequest).overrideWith(
            (_) async => [
              RabbitBatchMembership(
                batchId: 61,
                rabbitId: 31,
                isActive: true,
                batchRole: 'breeding',
                joinDate: DateTime(2025, 8, 1),
                currentStage: 'AWAIT_PALPATION',
                currentCycleId: 701,
                nextEventDate: DateTime(2025, 8, 20),
                nextEventType: '摸胎',
              ),
            ],
          ),
          rabbitBatchMembershipsProvider(historyRequest).overrideWith(
            (_) async => const [],
          ),
          rabbitReproTasksProvider(taskRequest).overrideWith(
            (_) async => [
              ReproTask(
                id: 801,
                taskType: 'ESTRUS',
                taskLabel: '待催情',
                action: ReproAction.estrus,
                cycleId: 701,
                rabbitId: 31,
                dueTime: DateTime(2026, 2, 3),
                status: 'PENDING',
              ),
              ReproTask(
                id: 802,
                taskType: 'MATING',
                taskLabel: '待配种复核',
                action: ReproAction.mating,
                cycleId: 702,
                rabbitId: 31,
                dueTime: DateTime(2026, 2, 5),
                status: 'PENDING',
              ),
            ],
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const Scaffold(
            body: RabbitDetailSheet(
              houseId: 8,
              rabbit: _doe,
              cageDisplay: 'D-01',
              canEdit: true,
              pageMode: true,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('兔 #31'), findsOneWidget);
    expect(find.text('繁育流程'), findsOneWidget);
    expect(find.text('批次标签'), findsOneWidget);
    expect(find.text('待催情'), findsOneWidget);
    expect(find.text('待配种复核'), findsOneWidget);
    expect(find.text('提醒：2026-02-03'), findsOneWidget);
    expect(find.text('提醒：2026-02-05'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-repro-task-action-801')),
      findsOneWidget,
    );
    expect(find.text('批次 #61'), findsOneWidget);
    expect(find.textContaining('下一项：摸胎'), findsNothing);
    expect(find.text('2025-08-20'), findsNothing);
    expect(
      find.byKey(const ValueKey('rabbit-detail-outbound-31')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);

    final historyFilter = find.text('历史');
    await tester.ensureVisible(historyFilter);
    await tester.pumpAndSettle();
    await tester.tap(historyFilter);
    await tester.pumpAndSettle();
    expect(find.text('暂无历史批次标签'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('rabbit detail exposes membership loading error and empty states',
      (tester) async {
    const activeRequest = RabbitBatchMembershipRequest(
      houseId: 8,
      rabbitId: 31,
    );
    final pending = Completer<List<RabbitBatchMembership>>();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          rabbitBatchMembershipsProvider(activeRequest).overrideWith(
            (_) => pending.future,
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const Scaffold(
            body: RabbitDetailSheet(
              houseId: 8,
              rabbit: _doe,
              cageDisplay: 'D-01',
              canEdit: false,
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    expect(
      find.byKey(const ValueKey('rabbit-membership-loading')),
      findsOneWidget,
    );

    pending.complete(const []);
    await tester.pumpAndSettle();
    expect(find.text('暂无批次标签'), findsOneWidget);

    await tester.pumpWidget(
      ProviderScope(
        key: UniqueKey(),
        overrides: [
          rabbitBatchMembershipsProvider(activeRequest).overrideWith(
            (_) => Future<List<RabbitBatchMembership>>.error('关系读取失败'),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const Scaffold(
            body: RabbitDetailSheet(
              houseId: 8,
              rabbit: _doe,
              cageDisplay: 'D-01',
              canEdit: false,
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('关系读取失败'), findsOneWidget);
    expect(find.text('重试'), findsOneWidget);
  });

  testWidgets('rabbit list keeps one detail action usable on narrow screens',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith((_) async => const [_house]),
          houseCagesProvider(8).overrideWith((_) async => const [_cage]),
          houseRabbitsProvider(8).overrideWith((_) async => _rabbits(1)),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'control',
              isAdmin: true,
            ),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseRabbitsScreen(houseId: 8),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final houseOutbound = find.byKey(
      const ValueKey('house-rabbits-outbound-action'),
    );
    final rabbitDetail = find.byKey(
      const ValueKey('rabbit-row-detail-1'),
    );

    expect(houseOutbound, findsOneWidget);
    expect(rabbitDetail, findsOneWidget);
    expect(find.byKey(const ValueKey('rabbit-row-outbound-1')), findsNothing);
    expect(find.byKey(const ValueKey('rabbit-row-move-1')), findsNothing);
    expect(find.byKey(const ValueKey('rabbit-row-edit-1')), findsNothing);
    expect(find.text('整舍批量出库'), findsOneWidget);
    expect(find.text('查看详情'), findsOneWidget);
    expect(tester.getSize(houseOutbound).height, greaterThanOrEqualTo(48));
    expect(tester.getSize(rabbitDetail).height, greaterThanOrEqualTo(48));
    expect(tester.takeException(), isNull);
  });
}

const _house = RabbitHouse(
  id: 8,
  name: '规模兔舍',
  remark: '',
  layoutRows: 10,
  layoutCols: 20,
  layoutLayers: 5,
);

const _cage = Cage(
  id: 1,
  houseId: 8,
  cageNumber: 'A-001',
  status: '3',
  rabbitCount: 1001,
  isEnabled: true,
);

const _doeCage = Cage(
  id: 2,
  houseId: 8,
  cageNumber: 'D-01',
  status: '1',
  rabbitCount: 1,
  isEnabled: true,
);

const _doe = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 2,
  motherId: null,
  type: '0',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 4.2,
  isActive: true,
  currentStage: 'AWAIT_PALPATION',
  currentCycleId: 701,
);

List<Rabbit> _rabbits(int count) {
  return List.generate(
    count,
    (index) => Rabbit(
      id: index + 1,
      houseId: 8,
      cageId: 1,
      motherId: null,
      type: '2',
      gender: index.isEven ? '0' : '1',
      breed: '新西兰白兔',
      arrivalMethod: '自繁',
      arrivalDate: DateTime(2025, 1, 1),
      weight: 2.5,
      isActive: true,
    ),
  );
}
