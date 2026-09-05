import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/rabbit.dart';
import 'package:rabbit_flutter/src/domain/batches/tracking.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/batches/screens/detail.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

void main() {
  testWidgets(
      'large Batch detail lazily builds members and remains usable at 200 percent',
      (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    const request = BatchDetailRequest(houseId: 8, batchId: 11);
    final members = List.generate(
      1001,
      (index) => BatchRabbitItem(
        id: index + 1,
        batchId: 11,
        rabbitId: index + 1000,
        currentStatus: index.isEven ? '待催情' : '已配种',
        currentStage: index == 1000
            ? null
            : (index.isEven ? 'AWAIT_ESTRUS' : 'AWAIT_PALPATION'),
        nextEventType: index.isEven ? '' : '摸胎',
        batchRole: 'breeding',
        rabbitGender: '0',
        isActive: true,
      ),
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchDetailProvider(request).overrideWith(
            (_) async => const Batch(
              id: 11,
              houseId: 8,
              batchCode: 'SCALE-11',
              status: '进行中',
              startDate: null,
              endDate: null,
              remark: '千母兔规模验证',
            ),
          ),
          batchMembersProvider(request).overrideWith((_) async => members),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'control', isAdmin: true),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              textScaler: const TextScaler.linear(2),
            ),
            child: child!,
          ),
          home: const HouseBatchDetailScreen(houseId: 8, batchId: 11),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('SCALE-11'), findsOneWidget);
    expect(find.text('全部标签'), findsOneWidget);
    final list = tester.widget<ListView>(
      find.byKey(const ValueKey('batch-detail-member-list')),
    );
    expect(list.childrenDelegate, isA<SliverChildBuilderDelegate>());
    expect(list.childrenDelegate.estimatedChildCount, members.length + 11);
    expect(find.byKey(const ValueKey('batch-member-2000')), findsNothing);

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-search')),
    );
    expect(find.text('显示 1001 / 1001 个标签'), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.enterText(
      find.byKey(const ValueKey('batch-member-search')),
      '2000',
    );
    await tester.pumpAndSettle();
    expect(find.text('显示 1 / 1001 个标签'), findsOneWidget);
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-2000')),
    );
    final plannedMember = find.byKey(const ValueKey('batch-member-2000'));
    expect(plannedMember, findsOneWidget);
    expect(
      find.descendant(of: plannedMember, matching: find.text('待配种绑定')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('batch-member-remove-2000')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('read-only Batch detail exposes data without mutation controls',
      (tester) async {
    const request = BatchDetailRequest(houseId: 8, batchId: 12);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchDetailProvider(request).overrideWith(
            (_) async => const Batch(
              id: 12,
              houseId: 8,
              batchCode: 'READONLY-12',
              status: '进行中',
              startDate: null,
              endDate: null,
              remark: '',
            ),
          ),
          batchMembersProvider(request).overrideWith(
            (_) async => const [
              BatchRabbitItem(
                id: 1,
                batchId: 12,
                rabbitId: 1001,
                currentStatus: '待催情',
                currentStage: 'AWAIT_ESTRUS',
                nextEventType: '',
                batchRole: 'breeding',
              ),
            ],
          ),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'view', isAdmin: false),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseBatchDetailScreen(houseId: 8, batchId: 12),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _scrollDetailUntilVisible(
      tester,
      find.text('当前为只读权限，可查看但不能推进生产状态。'),
    );
    expect(find.text('当前为只读权限，可查看但不能推进生产状态。'), findsOneWidget);
    expect(find.byKey(const ValueKey('batch-complete-button')), findsNothing);
    expect(
        find.byKey(const ValueKey('batch-select-start-visible')), findsNothing);
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-1001')),
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('batch-member-1001')),
        matching: find.byKey(const ValueKey('batch-member-departure-1001')),
      ),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('batch-member-remove-1001')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('mother tag shows batch-scoped totals and operation timeline',
      (tester) async {
    const request = BatchDetailRequest(houseId: 8, batchId: 19);
    const trackingRequest = BatchTrackingRequest(
      houseId: 8,
      batchId: 19,
      motherRabbitId: 1901,
    );
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchDetailProvider(request).overrideWith(
            (_) async => const Batch(
              id: 19,
              houseId: 8,
              batchCode: 'TRACK-19',
              status: '进行中',
              startDate: null,
              endDate: null,
              remark: '',
            ),
          ),
          batchMembersProvider(request).overrideWith(
            (_) async => [
              BatchRabbitItem(
                id: 1,
                batchId: 19,
                rabbitId: 1901,
                currentStatus: '旧状态',
                currentStage: 'AWAIT_PALPATION',
                currentCycleId: 901,
                nextEventType: '',
                batchRole: 'breeding',
                batchCycleCount: 2,
                batchOperationCount: 7,
                batchLitterCount: 1,
                batchTotalKits: 8,
                batchLiveKits: 7,
                batchWeanedKits: 6,
                batchLastOperationAt: DateTime(2026, 8, 20, 10, 30),
              ),
            ],
          ),
          batchTrackingEventsProvider(trackingRequest).overrideWith(
            (_) async => [
              BatchTrackingEvent(
                id: 71,
                cycleId: 901,
                motherRabbitId: 1901,
                batchId: 19,
                eventType: 'PALPATION_PREGNANT',
                eventLabel: '摸胎-怀孕',
                fromStageLabel: '待摸胎',
                toStageLabel: '待备产',
                occurredAt: DateTime(2026, 8, 20, 10, 30),
                operatorName: '生产员甲',
              ),
            ],
          ),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'view', isAdmin: false),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseBatchDetailScreen(houseId: 8, batchId: 19),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final card = find.byKey(const ValueKey('batch-member-1901'));
    await _scrollDetailUntilVisible(tester, card);
    expect(find.text('本批次 · 2 个周期 · 7 次操作'), findsOneWidget);
    expect(find.text('1 窝 · 产仔 8 · 活仔 7 · 断奶 6'), findsOneWidget);

    final open = find.byKey(
      const ValueKey('batch-member-tracking-open-1901'),
    );
    await tester.ensureVisible(open);
    await tester.tap(open);
    await tester.pumpAndSettle();

    expect(find.text('批次生产记录'), findsOneWidget);
    expect(find.text('批次 #19 · 母兔 #1901'), findsOneWidget);
    expect(
        find.byKey(const ValueKey('batch-tracking-event-71')), findsOneWidget);
    expect(find.text('摸胎-怀孕'), findsOneWidget);
    expect(find.textContaining('生产员甲'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('surrounding status whitespace does not disable Batch actions',
      (tester) async {
    const request = BatchDetailRequest(houseId: 8, batchId: 15);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchDetailProvider(request).overrideWith(
            (_) async => const Batch(
              id: 15,
              houseId: 8,
              batchCode: 'TRIM-15',
              status: '进行中',
              startDate: null,
              endDate: null,
              remark: '',
            ),
          ),
          batchMembersProvider(request).overrideWith(
            (_) async => const [
              BatchRabbitItem(
                id: 1,
                batchId: 15,
                rabbitId: 1501,
                currentStatus: ' 待催情 ',
                currentStage: 'AWAIT_ESTRUS',
                nextEventType: '',
                batchRole: 'breeding',
              ),
            ],
          ),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'control', isAdmin: true),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseBatchDetailScreen(houseId: 8, batchId: 15),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-select-start-visible')),
    );
    final selectButton = tester.widget<OutlinedButton>(
      find.byKey(const ValueKey('batch-select-start-visible')),
    );
    expect(selectButton.onPressed, isNotNull);
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-1501')),
    );
    expect(
      find.byKey(const ValueKey('batch-member-departure-1501')),
      findsOneWidget,
    );
    expect(find.text('待催情'), findsAtLeastNWidgets(1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('member status filter normalizes after a state refresh',
      (tester) async {
    const request = BatchDetailRequest(houseId: 8, batchId: 13);
    var members = const [
      BatchRabbitItem(
        id: 1,
        batchId: 13,
        rabbitId: 1301,
        currentStatus: '催情中',
        currentStage: 'AWAIT_MATING',
        nextEventType: '',
        batchRole: 'breeding',
      ),
    ];
    final container = ProviderContainer(
      overrides: [
        batchDetailProvider(request).overrideWith(
          (_) async => const Batch(
            id: 13,
            houseId: 8,
            batchCode: 'STATUS-13',
            status: '进行中',
            startDate: null,
            endDate: null,
            remark: '',
          ),
        ),
        batchMembersProvider(request).overrideWith((_) async => members),
        housePermissionProvider(8).overrideWith(
          (_) async => const HousePermission(perms: 'control', isAdmin: true),
        ),
      ],
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseBatchDetailScreen(houseId: 8, batchId: 13),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-status-filter')),
    );
    await tester.tap(find.byKey(const ValueKey('batch-member-status-filter')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('待配种').last);
    await tester.pumpAndSettle();
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-filter-summary')),
    );
    expect(find.text('显示 1 / 1 个标签'), findsOneWidget);

    members = const [
      BatchRabbitItem(
        id: 1,
        batchId: 13,
        rabbitId: 1301,
        currentStatus: '待配种',
        currentStage: 'AWAIT_PALPATION',
        nextEventType: '配种',
        batchRole: 'breeding',
      ),
    ];
    container.invalidate(batchMembersProvider(request));
    await tester.pumpAndSettle();

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-filter-summary')),
    );
    expect(find.text('显示 1 / 1 个标签'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('changing the member filter clears selections outside the view',
      (tester) async {
    const request = BatchDetailRequest(houseId: 8, batchId: 14);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchDetailProvider(request).overrideWith(
            (_) async => const Batch(
              id: 14,
              houseId: 8,
              batchCode: 'FILTER-14',
              status: '进行中',
              startDate: null,
              endDate: null,
              remark: '',
            ),
          ),
          batchMembersProvider(request).overrideWith(
            (_) async => const [
              BatchRabbitItem(
                id: 1,
                batchId: 14,
                rabbitId: 1401,
                currentStatus: '待催情',
                currentStage: 'AWAIT_ESTRUS',
                nextEventType: '',
                batchRole: 'breeding',
              ),
              BatchRabbitItem(
                id: 2,
                batchId: 14,
                rabbitId: 1402,
                currentStatus: '催情中',
                currentStage: 'AWAIT_MATING',
                nextEventType: '',
                batchRole: 'breeding',
              ),
            ],
          ),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'control', isAdmin: true),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseBatchDetailScreen(houseId: 8, batchId: 14),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-select-start-visible')),
    );
    await tester.tap(find.byKey(const ValueKey('batch-select-start-visible')));
    await tester.pumpAndSettle();
    expect(find.text('已选择 1 只母兔'), findsOneWidget);

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-search')),
      delta: -260,
    );
    await tester.enterText(
      find.byKey(const ValueKey('batch-member-search')),
      '1402',
    );
    await tester.pumpAndSettle();

    expect(find.text('显示 1 / 2 个标签'), findsOneWidget);
    expect(find.text('已选择 1 只母兔'), findsNothing);
    expect(find.byKey(const ValueKey('batch-selected-submit')), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('batch detail removes bulk mating but retains single mating',
      (tester) async {
    const request = BatchDetailRequest(houseId: 8, batchId: 16);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchDetailProvider(request).overrideWith(
            (_) async => const Batch(
              id: 16,
              houseId: 8,
              batchCode: 'MATING-16',
              status: '进行中',
              startDate: null,
              endDate: null,
              remark: '',
            ),
          ),
          batchMembersProvider(request).overrideWith(
            (_) async => const [
              BatchRabbitItem(
                id: 1,
                batchId: 16,
                rabbitId: 1601,
                currentStatus: '待配种',
                currentStage: 'AWAIT_MATING',
                currentCycleId: 901,
                nextEventType: '配种',
                batchRole: 'breeding',
              ),
            ],
          ),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'control', isAdmin: true),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseBatchDetailScreen(houseId: 8, batchId: 16),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('batch-select-mating-visible')),
      findsNothing,
    );
    expect(find.text('批量配种'), findsNothing);
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-1601')),
    );
    expect(
      find.byKey(const ValueKey('batch-member-action-1601')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('edit permission exposes add mother entry and excludes members',
      (tester) async {
    const request = BatchDetailRequest(houseId: 8, batchId: 17);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchDetailProvider(request).overrideWith(
            (_) async => const Batch(
              id: 17,
              houseId: 8,
              batchCode: 'ADD-17',
              status: '进行中',
              startDate: null,
              endDate: null,
              remark: '',
            ),
          ),
          batchMembersProvider(request).overrideWith(
            (_) async => const [
              BatchRabbitItem(
                id: 1,
                batchId: 17,
                rabbitId: 1701,
                currentStatus: '待配种',
                currentStage: 'AWAIT_MATING',
                nextEventType: '旧下一步',
                batchRole: 'breeding',
              ),
            ],
          ),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'control', isAdmin: true),
          ),
          allActiveHouseRabbitsProvider(8).overrideWith(
            (_) async => const [
              Rabbit(
                id: 1701,
                houseId: 8,
                cageId: 1,
                motherId: null,
                type: '0',
                gender: '0',
                breed: '已在批次',
                arrivalMethod: '自繁',
                arrivalDate: null,
                weight: null,
                isActive: true,
              ),
              Rabbit(
                id: 1702,
                houseId: 8,
                cageId: 2,
                motherId: null,
                type: '1',
                gender: '0',
                breed: '后备母兔',
                arrivalMethod: '自繁',
                arrivalDate: null,
                weight: null,
                isActive: true,
              ),
            ],
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseBatchDetailScreen(houseId: 8, batchId: 17),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final addButton = find.byKey(const ValueKey('batch-add-members-button'));
    expect(addButton, findsOneWidget);
    expect(tester.widget<OutlinedButton>(addButton).onPressed, isNotNull);
    await tester.tap(addButton);
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('batch-add-members-list')),
      findsOneWidget,
    );
    expect(find.text('当前批次已有 1 只成员'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('batch-add-member-option-1701')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('batch-add-member-option-1702')),
      findsOneWidget,
    );
    await tester.tap(
      find.byKey(const ValueKey('batch-add-members-close')),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('next step uses every recognized currentStage mapping',
      (tester) async {
    const request = BatchDetailRequest(houseId: 8, batchId: 18);
    const stages = [
      ('AWAIT_ESTRUS', '催情'),
      ('AWAIT_MATING', '配种'),
      ('AWAIT_PALPATION', '摸胎'),
      ('AWAIT_PREPARTUM', '备产'),
      ('AWAIT_DELIVERY', '分娩'),
      ('AWAIT_WEANING', '分笼'),
    ];
    final members = [
      for (var index = 0; index < stages.length; index++)
        BatchRabbitItem(
          id: index + 1,
          batchId: 18,
          rabbitId: 1801 + index,
          currentStatus: '旧状态',
          currentStage: stages[index].$1,
          nextEventType: '旧下一步',
          batchRole: 'breeding',
        ),
      const BatchRabbitItem(
        id: 98,
        batchId: 18,
        rabbitId: 1898,
        currentStatus: '旧状态',
        currentStage: 'FUTURE_SERVER_STAGE',
        nextEventType: '未知阶段的旧下一步',
        batchRole: 'breeding',
      ),
      const BatchRabbitItem(
        id: 99,
        batchId: 18,
        rabbitId: 1899,
        currentStatus: '旧状态',
        currentStage: 'READY',
        nextEventType: '旧下一步',
        batchRole: 'breeding',
      ),
    ];

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchDetailProvider(request).overrideWith(
            (_) async => const Batch(
              id: 18,
              houseId: 8,
              batchCode: 'STAGE-18',
              status: '进行中',
              startDate: null,
              endDate: null,
              remark: '',
            ),
          ),
          batchMembersProvider(request).overrideWith((_) async => members),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'view', isAdmin: false),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseBatchDetailScreen(houseId: 8, batchId: 18),
        ),
      ),
    );
    await tester.pumpAndSettle();

    for (final (_, label) in stages) {
      final nextStep = find.text('下一步 $label');
      await _scrollDetailUntilVisible(tester, nextStep);
      expect(nextStep, findsOneWidget);
    }
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-1899')),
    );
    expect(find.text('下一步 旧下一步'), findsNothing);
    expect(find.text('下一步 未知阶段的旧下一步'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('doe next-step date matches the reminder page calendar',
      (tester) async {
    const request = BatchDetailRequest(houseId: 8, batchId: 19);
    // 2026-02-20 00:30 Asia/Shanghai 到期。接口回的是 UTC 时刻，直接读 month/day
    // 会变成 02月19日，而提醒页对同一条待办显示 02月20日。
    final dueAt = DateTime.utc(2026, 2, 19, 16, 30);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchDetailProvider(request).overrideWith(
            (_) async => const Batch(
              id: 19,
              houseId: 8,
              batchCode: 'DATE-19',
              status: '进行中',
              startDate: null,
              endDate: null,
              remark: '',
            ),
          ),
          batchMembersProvider(request).overrideWith(
            (_) async => [
              BatchRabbitItem(
                id: 1,
                batchId: 19,
                rabbitId: 1901,
                currentStatus: '待配种',
                currentStage: 'AWAIT_MATING',
                nextEventType: '',
                batchRole: 'breeding',
                nextEventDate: dueAt,
              ),
            ],
          ),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'view', isAdmin: false),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseBatchDetailScreen(houseId: 8, batchId: 19),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final nextStep = find.text('下一步 配种（02月20日）');
    await _scrollDetailUntilVisible(tester, nextStep);
    expect(nextStep, findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  // 已完成的批次也要能改名：翻历史记录时把一个错名字改对是正当需求，
  // 而且批次的身份始终是主键，改名不影响任何生产数据。
  testWidgets('renames a completed Batch from the detail header',
      (tester) async {
    const request = BatchDetailRequest(houseId: 8, batchId: 21);
    final repository = _RecordingBatchRepository();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchRepositoryProvider.overrideWithValue(repository),
          batchDetailProvider(request).overrideWith(
            (_) async => const Batch(
              id: 21,
              houseId: 8,
              batchCode: '批次-20260220-1530',
              status: '已完成',
              startDate: null,
              endDate: null,
              remark: '',
            ),
          ),
          batchMembersProvider(request).overrideWith(
            (_) async => const <BatchRabbitItem>[],
          ),
          batchStatisticsProvider(request).overrideWith(
            (_) => BatchStatisticsController(
              enabled: false,
              load: (_) async => throw StateError('statistics not under test'),
            ),
          ),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(perms: 'control', isAdmin: true),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseBatchDetailScreen(houseId: 8, batchId: 21),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('批次-20260220-1530'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('batch-rename-button')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('batch-rename-field')),
      '二月配种批',
    );
    await tester.tap(find.byKey(const ValueKey('batch-rename-confirm')));
    await tester.pumpAndSettle();

    expect(repository.renames, hasLength(1));
    expect(repository.renames.single['batchId'], 21);
    expect(repository.renames.single['batchCode'], '二月配种批');
    expect(repository.renames.single['requestId'], isNotNull);
    expect(tester.takeException(), isNull);
  });
}

class _RecordingBatchRepository extends BatchRepository {
  factory _RecordingBatchRepository() {
    return _RecordingBatchRepository._(ApiClient(SessionStore()));
  }

  _RecordingBatchRepository._(this.client) : super(client);

  final ApiClient client;
  final renames = <Map<String, Object?>>[];

  @override
  Future<Batch> renameBatch({
    required int houseId,
    required int batchId,
    required String batchCode,
    String? requestId,
  }) async {
    renames.add({
      'houseId': houseId,
      'batchId': batchId,
      'batchCode': batchCode,
      'requestId': requestId,
    });
    return Batch(
      id: batchId,
      houseId: houseId,
      batchCode: batchCode,
      status: '已完成',
      startDate: null,
      endDate: null,
      remark: '',
    );
  }
}

Future<void> _scrollDetailUntilVisible(
  WidgetTester tester,
  Finder target, {
  double delta = 260,
}) async {
  final list = find.byKey(const ValueKey('batch-detail-member-list'));
  await tester.scrollUntilVisible(
    target,
    delta,
    scrollable:
        find.descendant(of: list, matching: find.byType(Scrollable)).first,
  );
  await tester.pumpAndSettle();
}
