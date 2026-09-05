import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/feed/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/feed/log.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/feed/screens/entry.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

void main() {
  testWidgets('投喂录入在 360x800 和 200% 字号下可完成选择和提交', (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final gateway = _FakeFeedGateway();

    await tester.pumpWidget(_testApp(gateway));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('feed-cage-10')));
    await tester.enterText(find.byKey(const ValueKey('feed-amount')), '1.5');
    await _scrollFeedUntilBuilt(
      tester,
      find.byKey(const ValueKey('feed-submit')),
    );
    await tester.ensureVisible(find.byKey(const ValueKey('feed-submit')));
    expect(find.text('投喂完成 2 只'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('首页观察提醒会预选对应兔只所在笼位', (tester) async {
    await tester.pumpWidget(
      _testApp(_FakeFeedGateway(), initialRabbitId: 101),
    );
    await tester.pumpAndSettle();

    final cage = tester.widget<CheckboxListTile>(
      find.byKey(const ValueKey('feed-cage-10')),
    );
    expect(cage.value, isTrue);
    expect(find.text('已根据首页提醒选中兔 #101 所在笼位'), findsOneWidget);
  });

  testWidgets('网络失败后保留输入并用相同 requestId 重试', (tester) async {
    final gateway = _FakeFeedGateway()..error = const ApiException('网络暂不可用');
    await tester.pumpWidget(_testApp(gateway));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('feed-cage-10')));
    await tester.enterText(find.byKey(const ValueKey('feed-amount')), '2');
    await _tapSubmit(tester);
    await _scrollFeedUntilBuilt(
      tester,
      find.byKey(const ValueKey('feed-entry-error')),
    );

    expect(find.byKey(const ValueKey('feed-entry-error')), findsOneWidget);
    expect(find.textContaining('投喂信息已保留'), findsOneWidget);
    await tester.drag(
      find.byKey(const ValueKey('feed-entry-scroll')),
      const Offset(0, 1200),
    );
    await tester.pump();
    await _scrollFeedUntilBuilt(
      tester,
      find.byKey(const ValueKey('feed-amount')),
    );
    expect(
      tester
          .widget<TextFormField>(find.byKey(const ValueKey('feed-amount')))
          .controller!
          .text,
      '2',
    );

    await _tapSubmit(tester);
    expect(gateway.drafts, hasLength(2));
    expect(gateway.drafts[0].requestId, gateway.drafts[1].requestId);
  });

  testWidgets('混合归属要求逐组填写且合计等于投喂总量', (tester) async {
    final gateway = _FakeFeedGateway()
      ..preview = const FeedAllocationPreview([
        FeedAllocationGroup(
          batchId: 11,
          phase: FeedAllocationPhase.fattening,
          rabbitCount: 1,
        ),
        FeedAllocationGroup(
          batchId: null,
          phase: FeedAllocationPhase.unassigned,
          rabbitCount: 1,
        ),
      ]);
    await tester.pumpWidget(_testApp(gateway));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('feed-cage-10')));
    await tester.enterText(find.byKey(const ValueKey('feed-amount')), '2');
    await _scrollFeedUntilBuilt(
      tester,
      find.byKey(const ValueKey('feed-allocation-preview')),
    );
    await tester.tap(find.byKey(const ValueKey('feed-allocation-preview')));
    await tester.pumpAndSettle();
    final batchAllocation =
        find.byKey(const ValueKey('feed-allocation-11:FATTENING'));
    await _scrollFeedUntilBuilt(tester, batchAllocation);
    await tester.enterText(batchAllocation, '1.25');
    final unassignedAllocation =
        find.byKey(const ValueKey('feed-allocation-unassigned:UNASSIGNED'));
    await _scrollFeedUntilBuilt(tester, unassignedAllocation);
    await tester.enterText(unassignedAllocation, '0.75');
    await _tapSubmit(tester);

    expect(gateway.drafts.single.allocations, hasLength(2));
    expect(gateway.drafts.single.allocations.first.amountKg, 1.25);
    expect(gateway.drafts.single.allocations.last.batchId, isNull);
  });

  testWidgets('单一未归属分组仍要求人工确认投喂量', (tester) async {
    final gateway = _FakeFeedGateway()
      ..preview = const FeedAllocationPreview([
        FeedAllocationGroup(
          batchId: null,
          phase: FeedAllocationPhase.unassigned,
          rabbitCount: 2,
        ),
      ]);
    await tester.pumpWidget(_testApp(gateway));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('feed-cage-10')));
    await tester.enterText(find.byKey(const ValueKey('feed-amount')), '2');
    await _scrollFeedUntilBuilt(
      tester,
      find.byKey(const ValueKey('feed-allocation-preview')),
    );
    await tester.tap(find.byKey(const ValueKey('feed-allocation-preview')));
    await tester.pumpAndSettle();
    final allocation =
        find.byKey(const ValueKey('feed-allocation-unassigned:UNASSIGNED'));
    await _scrollFeedUntilBuilt(tester, allocation);
    expect(tester.widget<TextFormField>(allocation).initialValue, isEmpty);
    await tester.enterText(allocation, '2');
    await _tapSubmit(tester);

    expect(gateway.drafts.single.allocations.single.batchId, isNull);
    expect(gateway.drafts.single.allocations.single.amountKg, 2);
  });

  testWidgets('保存时归属冲突会刷新预览并保留总量', (tester) async {
    final gateway = _FakeFeedGateway()
      ..previews.addAll(const [
        FeedAllocationPreview([
          FeedAllocationGroup(
            batchId: 11,
            phase: FeedAllocationPhase.fattening,
            rabbitCount: 2,
          ),
        ]),
        FeedAllocationPreview([
          FeedAllocationGroup(
            batchId: 12,
            phase: FeedAllocationPhase.fattening,
            rabbitCount: 2,
          ),
        ]),
      ])
      ..errors.add(const ApiException('归属已变化', businessCode: 409));
    await tester.pumpWidget(_testApp(gateway));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('feed-cage-10')));
    await tester.enterText(find.byKey(const ValueKey('feed-amount')), '2');
    await _tapSubmit(tester);

    expect(gateway.previewCalls, 2);
    await _scrollFeedUntilBuilt(tester, find.textContaining('归属已刷新'));
    expect(find.textContaining('归属已刷新'), findsOneWidget);
    await tester.drag(
      find.byKey(const ValueKey('feed-entry-scroll')),
      const Offset(0, 1200),
    );
    await tester.pump();
    await _scrollFeedUntilBuilt(
      tester,
      find.byKey(const ValueKey('feed-amount')),
    );
    expect(
      tester
          .widget<TextFormField>(find.byKey(const ValueKey('feed-amount')))
          .controller!
          .text,
      '2',
    );
    await _scrollFeedUntilBuilt(
      tester,
      find.byKey(const ValueKey('feed-allocation-12:FATTENING')),
    );
    expect(
      find.byKey(const ValueKey('feed-allocation-12:FATTENING')),
      findsOneWidget,
    );

    await _tapSubmit(tester);
    expect(gateway.drafts, hasLength(2));
    expect(gateway.drafts[0].requestId, isNot(gateway.drafts[1].requestId));
  });

  testWidgets('提交期间连点只发出一次投喂请求', (tester) async {
    final gateway = _FakeFeedGateway()..pending = Completer<void>();
    await tester.pumpWidget(_testApp(gateway));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('feed-cage-10')));
    await tester.enterText(find.byKey(const ValueKey('feed-amount')), '2');
    await _tapSubmit(tester, settle: false);
    await _tapSubmit(tester, settle: false);
    await _tapSubmit(tester, settle: false);

    expect(gateway.drafts, hasLength(1));
    gateway.pending!.complete();
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('feed-entry-success')), findsOneWidget);
  });

  testWidgets('兔舍切换会清空表单且忽略旧兔舍延迟预览', (tester) async {
    final oldPreview = Completer<FeedAllocationPreview>();
    final gateway = _FakeFeedGateway()..previewWaiters.add(oldPreview);
    final houseId = ValueNotifier(8);
    addTearDown(houseId.dispose);

    await tester.pumpWidget(_houseSwitchApp(gateway, houseId));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('feed-cage-10')));
    await tester.enterText(find.byKey(const ValueKey('feed-amount')), '2');
    await _scrollFeedUntilBuilt(
      tester,
      find.byKey(const ValueKey('feed-allocation-preview')),
    );
    await tester.tap(find.byKey(const ValueKey('feed-allocation-preview')));
    await tester.pump();
    expect(gateway.previewHouseIds, [8]);

    houseId.value = 9;
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.byKey(const ValueKey('feed-cage-20')), findsOneWidget);
    expect(
      tester
          .widget<TextFormField>(find.byKey(const ValueKey('feed-amount')))
          .controller!
          .text,
      isEmpty,
    );
    expect(
      tester
          .widget<CheckboxListTile>(find.byKey(const ValueKey('feed-cage-20')))
          .value,
      isFalse,
    );

    oldPreview.complete(
      const FeedAllocationPreview([
        FeedAllocationGroup(
          batchId: 11,
          phase: FeedAllocationPhase.fattening,
          rabbitCount: 2,
        ),
      ]),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('feed-allocation-11:FATTENING')),
      findsNothing,
    );
    expect(find.byKey(const ValueKey('feed-entry-success')), findsNothing);
    expect(find.byKey(const ValueKey('feed-entry-error')), findsNothing);
  });

  testWidgets('兔舍切换会忽略旧兔舍延迟保存结果', (tester) async {
    final pendingSave = Completer<void>();
    final gateway = _FakeFeedGateway()..pending = pendingSave;
    final houseId = ValueNotifier(8);
    addTearDown(houseId.dispose);

    await tester.pumpWidget(_houseSwitchApp(gateway, houseId));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('feed-cage-10')));
    await tester.enterText(find.byKey(const ValueKey('feed-amount')), '2');
    await _tapSubmit(tester, settle: false);
    expect(gateway.drafts, hasLength(1));

    houseId.value = 9;
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    pendingSave.complete();
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('feed-cage-20')), findsOneWidget);
    expect(
      tester
          .widget<TextFormField>(find.byKey(const ValueKey('feed-amount')))
          .controller!
          .text,
      isEmpty,
    );
    expect(find.byKey(const ValueKey('feed-entry-success')), findsNothing);
    expect(find.byKey(const ValueKey('feed-entry-error')), findsNothing);
  });

  testWidgets('NFC 不可用时提示当前页面并保留手动选择入口', (tester) async {
    const channel = MethodChannel('com.rabbit.app.flutter/nfc_intents');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (_) async {
      throw PlatformException(code: 'denied');
    });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null),
    );

    await tester.pumpWidget(_testApp(_FakeFeedGateway()));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('feed-nfc-capture')));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('feed-nfc-hint')), findsOneWidget);
    expect(find.text('无法使用 NFC，请检查系统授权后重试'), findsOneWidget);
    expect(find.byKey(const ValueKey('feed-cage-10')), findsOneWidget);
  });
}

Future<void> _scrollFeedUntilBuilt(
  WidgetTester tester,
  Finder target,
) async {
  final list = find.byKey(const ValueKey('feed-entry-scroll'));
  for (var attempt = 0; attempt < 8; attempt++) {
    if (target.evaluate().isNotEmpty) {
      return;
    }
    await tester.drag(list, const Offset(0, -280));
    await tester.pump();
  }
  fail('Expected feed form control was not built');
}

Future<void> _tapSubmit(
  WidgetTester tester, {
  bool settle = true,
}) async {
  final submit = find.byKey(const ValueKey('feed-submit'));
  await _scrollFeedUntilBuilt(tester, submit);
  await tester.ensureVisible(submit);
  await tester.tap(submit);
  if (settle) {
    await tester.pumpAndSettle();
  } else {
    await tester.pump();
  }
}

Widget _houseSwitchApp(
  FeedGateway gateway,
  ValueNotifier<int> houseId,
) {
  return ProviderScope(
    overrides: [
      feedRepositoryProvider.overrideWithValue(gateway),
      housePermissionProvider(8).overrideWith(
        (_) async => const HousePermission(perms: 'edit', isAdmin: false),
      ),
      housePermissionProvider(9).overrideWith(
        (_) async => const HousePermission(perms: 'edit', isAdmin: false),
      ),
      houseCagesProvider(8).overrideWith((_) async => const [_cage]),
      houseCagesProvider(9).overrideWith((_) async => const [_secondHouseCage]),
      allActiveHouseRabbitsProvider(8).overrideWith(
        (_) async => const [_firstRabbit, _secondRabbit],
      ),
      allActiveHouseRabbitsProvider(9).overrideWith(
        (_) async => const [_secondHouseRabbit],
      ),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: ValueListenableBuilder<int>(
        valueListenable: houseId,
        builder: (_, value, __) => FeedEntryScreen(houseId: value),
      ),
    ),
  );
}

Widget _testApp(FeedGateway gateway, {int? initialRabbitId}) {
  return ProviderScope(
    overrides: [
      feedRepositoryProvider.overrideWithValue(gateway),
      housePermissionProvider(8).overrideWith(
        (_) async => const HousePermission(perms: 'edit', isAdmin: false),
      ),
      houseCagesProvider(8).overrideWith((_) async => const [_cage]),
      allActiveHouseRabbitsProvider(8).overrideWith(
        (_) async => const [_firstRabbit, _secondRabbit],
      ),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: FeedEntryScreen(
        houseId: 8,
        initialRabbitId: initialRabbitId,
      ),
    ),
  );
}

class _FakeFeedGateway implements FeedGateway {
  final drafts = <FeedLogDraft>[];
  final errors = <ApiException>[];
  final previews = <FeedAllocationPreview>[];
  final previewWaiters = <Completer<FeedAllocationPreview>>[];
  final previewHouseIds = <int>[];
  ApiException? error;
  Completer<void>? pending;
  var previewCalls = 0;
  FeedAllocationPreview preview = const FeedAllocationPreview([
    FeedAllocationGroup(
      batchId: 11,
      phase: FeedAllocationPhase.fattening,
      rabbitCount: 2,
    ),
  ]);

  @override
  Future<void> addFeedLog({
    required int houseId,
    required FeedLogDraft draft,
  }) {
    drafts.add(draft);
    if (errors.isNotEmpty) {
      return Future<void>.error(errors.removeAt(0));
    }
    if (error != null) {
      return Future<void>.error(error!);
    }
    return pending?.future ?? Future<void>.value();
  }

  @override
  Future<FeedAllocationPreview> previewAllocations({
    required int houseId,
    required List<int> rabbitIds,
    required DateTime feedTime,
  }) async {
    previewCalls++;
    previewHouseIds.add(houseId);
    if (previewWaiters.isNotEmpty) {
      return previewWaiters.removeAt(0).future;
    }
    return previews.isEmpty ? preview : previews.removeAt(0);
  }
}

const _cage = Cage(
  id: 10,
  houseId: 8,
  cageNumber: '1-1-1',
  rowCode: '1',
  layerIndex: 1,
  positionIndex: 1,
  status: '3',
  rabbitCount: 2,
  isEnabled: true,
);

const _secondHouseCage = Cage(
  id: 20,
  houseId: 9,
  cageNumber: '2-1-1',
  rowCode: '2',
  layerIndex: 1,
  positionIndex: 1,
  status: '3',
  rabbitCount: 1,
  isEnabled: true,
);

const _firstRabbit = Rabbit(
  id: 101,
  houseId: 8,
  cageId: 10,
  motherId: null,
  type: '2',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '自产',
  arrivalDate: null,
  weight: 2.5,
  isActive: true,
);

const _secondHouseRabbit = Rabbit(
  id: 201,
  houseId: 9,
  cageId: 20,
  motherId: null,
  type: '2',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '自产',
  arrivalDate: null,
  weight: 2.7,
  isActive: true,
);

const _secondRabbit = Rabbit(
  id: 102,
  houseId: 8,
  cageId: 10,
  motherId: null,
  type: '2',
  gender: '1',
  breed: '新西兰白兔',
  arrivalMethod: '自产',
  arrivalDate: null,
  weight: 2.6,
  isActive: true,
);
