import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/batch.dart';
import 'package:rabbit_flutter/src/domain/models/batch_rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/batch_providers.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/house_batch_detail_screen.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';

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
        currentStage: index.isEven ? 'AWAIT_ESTRUS' : 'AWAIT_PALPATION',
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
    expect(find.text('全部成员'), findsOneWidget);
    final list = tester.widget<ListView>(
      find.byKey(const ValueKey('batch-detail-member-list')),
    );
    expect(list.childrenDelegate, isA<SliverChildBuilderDelegate>());
    expect(list.childrenDelegate.estimatedChildCount, 1008);
    expect(find.byKey(const ValueKey('batch-member-2000')), findsNothing);

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-search')),
    );
    expect(find.text('显示 1001 / 1001 个成员'), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.enterText(
      find.byKey(const ValueKey('batch-member-search')),
      '2000',
    );
    await tester.pumpAndSettle();
    expect(find.text('显示 1 / 1001 个成员'), findsOneWidget);
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-2000')),
    );
    expect(find.byKey(const ValueKey('batch-member-2000')), findsOneWidget);
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
    expect(find.text('显示 1 / 1 个成员'), findsOneWidget);

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

    expect(find.text('显示 1 / 1 个成员'), findsOneWidget);
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

    expect(find.text('显示 1 / 2 个成员'), findsOneWidget);
    expect(find.text('已选择 1 只母兔'), findsNothing);
    expect(find.byKey(const ValueKey('batch-selected-submit')), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('batch mating selects current eligible mothers as one draft',
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
                nextEventType: '配种',
                batchRole: 'breeding',
                isActive: true,
              ),
              BatchRabbitItem(
                id: 2,
                batchId: 16,
                rabbitId: 1602,
                currentStatus: '哺乳中',
                currentStage: 'AWAIT_WEANING',
                nextEventType: '配种',
                batchRole: 'breeding',
                isActive: true,
              ),
              BatchRabbitItem(
                id: 3,
                batchId: 16,
                rabbitId: 1603,
                currentStatus: '已配种',
                currentStage: 'AWAIT_PALPATION',
                nextEventType: '摸胎',
                batchRole: 'breeding',
                isActive: true,
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

    final select = find.byKey(const ValueKey('batch-select-mating-visible'));
    await _scrollDetailUntilVisible(tester, select);
    expect(select, findsOneWidget);
    expect(tester.widget<OutlinedButton>(select).onPressed, isNotNull);
    await tester.tap(select);
    await tester.pumpAndSettle();

    expect(find.text('已选择 2 只母兔'), findsOneWidget);
    expect(find.byKey(const ValueKey('batch-mating-submit')), findsOneWidget);
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-1601')),
    );
    expect(find.byKey(const ValueKey('batch-member-1601')), findsOneWidget);
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-1602')),
    );
    expect(find.byKey(const ValueKey('batch-member-1602')), findsOneWidget);
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-member-1603')),
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('batch-member-1603')),
        matching: find.byType(Checkbox),
      ),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });
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
