import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/rabbit.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/ui/reproduction/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/batches/screens/detail.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';

/// 流产入口按阶段显隐。
///
/// 流产是非计划事件，只在孕期三个阶段成立。判据来自服务端的阶段字典，
/// 这里用它的真实形状做覆盖：若哪天客户端改成自己写死阶段名，这个用例仍然通过，
/// 但 `TransitionTableTest.dictionaryMatchesWhatRequireActuallyAccepts` 会在
/// 服务端侧发现字典与转换表分家。两层各守一边。
void main() {
  const houseId = 8;
  const batchId = 21;
  const request = BatchDetailRequest(houseId: houseId, batchId: batchId);

  /// 服务端字典的真实子集：只有孕期三个阶段带 ABORTION。
  const stageActions = <String, List<String>>{
    'AWAIT_ESTRUS': ['ESTRUS', 'POSTPONE', 'RETIRE'],
    'AWAIT_MATING': ['MATING', 'POSTPONE', 'RETIRE'],
    'AWAIT_PALPATION': ['PALPATION', 'ABORTION', 'POSTPONE', 'RETIRE'],
    'AWAIT_PREPARTUM': ['PREPARTUM', 'ABORTION', 'POSTPONE', 'RETIRE'],
    'AWAIT_DELIVERY': ['DELIVERY', 'ABORTION', 'POSTPONE', 'RETIRE'],
    'AWAIT_WEANING': ['WEANING', 'POSTPONE', 'RETIRE'],
  };

  setUp(() {
    // 用一块足够高的画布让全部成员一次性构建：这个用例关心的是「按钮在不在」，
    // 而不是列表怎么滚。懒构建列表反复滚动时会在重建瞬间丢失 Scrollable，
    // 那种失败与被测行为无关。
    final view =
        TestWidgetsFlutterBinding.instance.platformDispatcher.views.first;
    view.physicalSize = const Size(1200, 6000);
    view.devicePixelRatio = 1.0;
  });

  tearDown(() {
    final view =
        TestWidgetsFlutterBinding.instance.platformDispatcher.views.first;
    view.resetPhysicalSize();
    view.resetDevicePixelRatio();
  });

  Future<void> pumpWithMembers(
    WidgetTester tester,
    List<BatchRabbitItem> members, {
    Map<String, List<String>> dictionary = stageActions,
  }) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchDetailProvider(request).overrideWith(
            (_) async => const Batch(
              id: batchId,
              houseId: houseId,
              batchCode: 'AB-21',
              status: '进行中',
              startDate: null,
              endDate: null,
              remark: '',
            ),
          ),
          batchMembersProvider(request).overrideWith((_) async => members),
          housePermissionProvider(houseId).overrideWith(
            (_) async => const HousePermission(perms: 'control', isAdmin: true),
          ),
          reproStageActionsProvider(houseId)
              .overrideWith((_) async => dictionary),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home:
              const HouseBatchDetailScreen(houseId: houseId, batchId: batchId),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  BatchRabbitItem doe(int rabbitId, String stage, {int? cycleId = 900}) {
    return BatchRabbitItem(
      id: rabbitId,
      batchId: batchId,
      rabbitId: rabbitId,
      currentStatus: '',
      currentStage: stage,
      currentCycleId: cycleId,
      nextEventType: '',
      batchRole: 'breeding',
    );
  }

  testWidgets('流产入口只在孕期阶段出现', (tester) async {
    await pumpWithMembers(tester, [
      doe(2101, 'AWAIT_ESTRUS'),
      doe(2102, 'AWAIT_MATING'),
      doe(2103, 'AWAIT_PALPATION'),
      doe(2104, 'AWAIT_PREPARTUM'),
      doe(2105, 'AWAIT_DELIVERY'),
      doe(2106, 'AWAIT_WEANING'),
    ]);

    for (final rabbitId in [2103, 2104, 2105]) {
      expect(
        find.byKey(ValueKey('batch-member-abortion-$rabbitId')),
        findsOneWidget,
        reason: '孕期母兔 #$rabbitId 应可记录流产',
      );
    }
    // 还没怀上、以及已经生完的，都不该出现流产入口。
    for (final rabbitId in [2101, 2102, 2106]) {
      expect(
        find.byKey(ValueKey('batch-member-abortion-$rabbitId')),
        findsNothing,
        reason: '非孕期母兔 #$rabbitId 不应出现流产入口',
      );
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('没有周期的母兔不给流产入口', (tester) async {
    await pumpWithMembers(
        tester, [doe(2201, 'AWAIT_PALPATION', cycleId: null)]);

    // 阶段允许，但没有可写入的周期——入口只会导向一个必然失败的提交。
    expect(
        find.byKey(const ValueKey('batch-member-abortion-2201')), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('字典拉取失败时宁可不显示流产入口', (tester) async {
    await pumpWithMembers(
      tester,
      [doe(2301, 'AWAIT_PALPATION')],
      dictionary: const {},
    );

    // 少给一个入口只是不便；给一个点下去必定 409 的按钮是欺骗。
    expect(
        find.byKey(const ValueKey('batch-member-abortion-2301')), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('没有本批次开放周期的母兔显示为活动已结束', (tester) async {
    await pumpWithMembers(
      tester,
      const [
        BatchRabbitItem(
          id: 2401,
          batchId: batchId,
          rabbitId: 2401,
          currentStatus: '待催情',
          currentStage: null,
          currentCycleId: null,
          nextEventType: '',
          batchRole: 'breeding',
          isActive: true,
          batchCycleCount: 1,
        ),
      ],
    );

    expect(find.byKey(const ValueKey('batch-member-2401')), findsNothing);
    await tester.tap(
      find.byKey(const ValueKey('batch-member-activity-filter')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('活动已结束').last);
    await tester.pumpAndSettle();

    final member = find.byKey(const ValueKey('batch-member-2401'));
    expect(member, findsOneWidget);
    expect(
      find.descendant(of: member, matching: find.text('活动已结束')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('batch-member-action-2401')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });
}
