import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/move.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
      'move cage sheet stays usable above dynamic keyboard in common viewports',
      (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 800);
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetViewInsets);
    addTearDown(
      tester.platformDispatcher.clearTextScaleFactorTestValue,
    );

    await tester.pumpWidget(_testApp());
    await tester.tap(find.byKey(const ValueKey('open-move-cage-sheet')));
    await tester.pumpAndSettle();

    expect(find.text('换笼位'), findsOneWidget);

    // 先聚焦输入框（开屏就可见），再下去选目标：向下滚之后输入框会被销毁（sliver 按需构建），
    // 那是滚动列表的正常行为，不是缺陷。
    final search = find.byKey(const ValueKey('rabbit-move-cage-search'));
    await tester.tap(search);
    await tester.pump();
    final editableText = find.descendant(
      of: search,
      matching: find.byType(EditableText),
    );
    expect(
        tester.widget<EditableText>(editableText).focusNode.hasFocus, isTrue);

    // 默认是地图选择。这批 fixture 笼位没有排/层/位坐标，所以落在「未编排」里，
    // 仍然可点——旧数据不能因为换了视图就选不了。
    final target = find.byKey(const ValueKey('cage-map-cell-12'));
    await tester.ensureVisible(target);
    await tester.pumpAndSettle();
    await tester.tap(target);
    await tester.pumpAndSettle();

    final submit = find.byKey(const ValueKey('rabbit-move-cage-submit'));
    expect(tester.widget<ElevatedButton>(submit).onPressed, isNotNull);

    for (final deviceSize in const [
      Size(360, 800),
      Size(393, 852),
      Size(412, 915),
    ]) {
      tester.view.physicalSize = deviceSize;
      for (final keyboardHeight in const [180.0, 300.0, 420.0]) {
        tester.view.viewInsets = FakeViewPadding(bottom: keyboardHeight);
        await tester.pumpAndSettle();

        expect(
          tester.takeException(),
          isNull,
          reason: '$deviceSize with keyboard $keyboardHeight',
        );
        final submitRect = tester.getRect(submit);
        expect(submitRect.top, greaterThanOrEqualTo(0));
        expect(
          submitRect.bottom,
          lessThanOrEqualTo(deviceSize.height - keyboardHeight),
        );
      }
    }

    tester.view.physicalSize = const Size(800, 360);
    tester.view.viewInsets = const FakeViewPadding(bottom: 180);
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    final landscapeSubmitRect = tester.getRect(submit);
    expect(landscapeSubmitRect.top, greaterThanOrEqualTo(0));
    expect(landscapeSubmitRect.bottom, lessThanOrEqualTo(180));
  });

  testWidgets('typing a full cage number selects that target', (tester) async {
    await tester.pumpWidget(_testApp());
    await tester.tap(find.byKey(const ValueKey('open-move-cage-sheet')));
    await tester.pumpAndSettle();

    final submit = find.byKey(const ValueKey('rabbit-move-cage-submit'));
    expect(
      tester.widget<ElevatedButton>(submit).onPressed,
      isNull,
      reason: '还没选目标时不能允许提交',
    );

    await tester.enterText(
      find.byKey(const ValueKey('rabbit-move-cage-search')),
      'A-02',
    );
    await tester.pumpAndSettle();

    expect(find.text('已选中 A-02 · 空笼'), findsOneWidget);
    expect(tester.widget<ElevatedButton>(submit).onPressed, isNotNull);
    expect(
      tester
          .widget<Text>(
            find.byKey(const ValueKey('rabbit-move-cage-selection')),
          )
          .data,
      contains('A-02'),
    );
  });

  testWidgets('typing a cage that cannot take the rabbit says why',
      (tester) async {
    await tester.pumpWidget(_testApp());
    await tester.tap(find.byKey(const ValueKey('open-move-cage-sheet')));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('rabbit-move-cage-search')),
      'A-04',
    );
    await tester.pumpAndSettle();

    // 种母兔进满的商品兔笼既不能入笼也不能对调，必须当场说清，
    // 而不是等提交后给一个 400。
    expect(
      find.byKey(const ValueKey('rabbit-move-cage-number-hint')),
      findsOneWidget,
    );
    expect(find.textContaining('不能接收该兔'), findsOneWidget);
    expect(
      tester
          .widget<ElevatedButton>(
            find.byKey(const ValueKey('rabbit-move-cage-submit')),
          )
          .onPressed,
      isNull,
    );
  });

  testWidgets('swap targets are marked before submitting', (tester) async {
    await tester.pumpWidget(_testApp());
    await tester.tap(find.byKey(const ValueKey('open-move-cage-sheet')));
    await tester.pumpAndSettle();

    final swapTarget = find.byKey(const ValueKey('cage-map-cell-15'));
    await tester.ensureVisible(swapTarget);
    await tester.pumpAndSettle();
    await tester.tap(swapTarget);
    await tester.pumpAndSettle();

    // 底部常驻提示里必须写清这是一次对调，否则用户以为只动了自己那只兔。
    expect(
      tester
          .widget<Text>(
            find.byKey(const ValueKey('rabbit-move-cage-selection')),
          )
          .data,
      contains('将与笼内兔只对调'),
    );
    expect(find.text('对调'), findsWidgets);
  });

  testWidgets('only real swap candidates are marked, commodity cages are not',
      (tester) async {
    await tester.pumpWidget(_testApp());
    await tester.tap(find.byKey(const ValueKey('open-move-cage-sheet')));
    await tester.pumpAndSettle();

    // A-05 是被占用的后备兔笼（会对调），A-04 是满的商品兔笼（无对调路径）。
    // 地图会把不可选的笼也画出来，所以标注必须自己把关，不能靠“不显示”遮丑。
    expect(find.text('对调'), findsOneWidget);
    expect(find.text('当前'), findsOneWidget);
  });

  testWidgets('输入别层的笼位编号，地图会自己切到那一层', (tester) async {
    await tester.binding.setSurfaceSize(const Size(500, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(_layeredApp());
    await tester.tap(find.byKey(const ValueKey('open-move-cage-sheet')));
    await tester.pumpAndSettle();

    // 兔子在 1 层，地图默认也停在 1 层。
    expect(find.byKey(const ValueKey('cage-map-cell-22')), findsOneWidget);
    expect(find.byKey(const ValueKey('cage-map-cell-32')), findsNothing);

    await tester.enterText(
      find.byKey(const ValueKey('rabbit-move-cage-search')),
      'B-02-L2',
    );
    await tester.pumpAndSettle();

    // 选中的笼在 2 层。地图不跟着切的话，用户看到「已选中」却在屏幕上找不到那一格。
    expect(
      find.byKey(const ValueKey('cage-map-cell-32')),
      findsOneWidget,
      reason: '选中的笼必须出现在眼前，不能让人自己去猜它在哪一层',
    );
    expect(
      find.text('目标：B-02-L2 · 空笼'),
      findsOneWidget,
      reason: '底部的常驻摘要要能确认真选中了，而不只是地图切了层',
    );
  });

  testWidgets('层切换器只在真的有多层时出现', (tester) async {
    await tester.pumpWidget(_testApp());
    await tester.tap(find.byKey(const ValueKey('open-move-cage-sheet')));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('cage-map-layer-switcher')),
      findsNothing,
      reason: '单层兔舍不该被塞一个只有一个选项的切换器',
    );
  });
}

/// 两层各两位的排，用来验「选中的笼在别层」这件事。
Widget _layeredApp() {
  const rabbit = Rabbit(
    id: 801,
    houseId: 8,
    cageId: 21,
    motherId: null,
    type: '0',
    gender: '0',
    breed: 'New Zealand White',
    arrivalMethod: '0',
    arrivalDate: null,
    weight: 4.2,
    isActive: true,
  );
  const cages = [
    Cage(
      id: 21,
      houseId: 8,
      cageNumber: 'B-01-L1',
      rowCode: 'B',
      layerIndex: 1,
      positionIndex: 1,
      status: '1',
      rabbitCount: 1,
      isEnabled: true,
    ),
    Cage(
      id: 22,
      houseId: 8,
      cageNumber: 'B-02-L1',
      rowCode: 'B',
      layerIndex: 1,
      positionIndex: 2,
      status: '0',
      rabbitCount: 0,
      isEnabled: true,
    ),
    Cage(
      id: 31,
      houseId: 8,
      cageNumber: 'B-01-L2',
      rowCode: 'B',
      layerIndex: 2,
      positionIndex: 1,
      status: '0',
      rabbitCount: 0,
      isEnabled: true,
    ),
    Cage(
      id: 32,
      houseId: 8,
      cageNumber: 'B-02-L2',
      rowCode: 'B',
      layerIndex: 2,
      positionIndex: 2,
      status: '0',
      rabbitCount: 0,
      isEnabled: true,
    ),
  ];

  return ProviderScope(
    child: MaterialApp(
      theme: buildAppTheme(),
      home: Builder(
        builder: (context) => Scaffold(
          body: Center(
            child: ElevatedButton(
              key: const ValueKey('open-move-cage-sheet'),
              onPressed: () => showRabbitMoveCageSheet(
                context: context,
                houseId: 8,
                rabbit: rabbit,
                cages: cages,
              ),
              child: const Text('换笼位'),
            ),
          ),
        ),
      ),
    ),
  );
}

Widget _testApp() {
  const rabbit = Rabbit(
    id: 801,
    houseId: 8,
    cageId: 11,
    motherId: null,
    type: '0',
    gender: '0',
    breed: 'New Zealand White',
    arrivalMethod: '0',
    arrivalDate: null,
    weight: 4.2,
    isActive: true,
  );
  const cages = [
    Cage(
      id: 11,
      houseId: 8,
      cageNumber: 'A-01',
      status: '1',
      rabbitCount: 1,
      isEnabled: true,
    ),
    Cage(
      id: 12,
      houseId: 8,
      cageNumber: 'A-02',
      status: '0',
      rabbitCount: 0,
      isEnabled: true,
    ),
    Cage(
      id: 13,
      houseId: 8,
      cageNumber: 'A-03-VERY-LONG-CAGE-NUMBER-FOR-LAYOUT',
      status: '0',
      rabbitCount: 0,
      isEnabled: true,
    ),
    // 满的商品兔笼：种母兔既不能入笼也不能对调。
    Cage(
      id: 14,
      houseId: 8,
      cageNumber: 'A-04',
      status: '3',
      rabbitCount: Cage.commodityCapacity,
      isEnabled: true,
    ),
    // 被占用的后备兔笼：种母兔进去走两笼对调。
    Cage(
      id: 15,
      houseId: 8,
      cageNumber: 'A-05',
      status: '2',
      rabbitCount: 1,
      isEnabled: true,
    ),
  ];

  return ProviderScope(
    child: MaterialApp(
      theme: buildAppTheme(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: AppTypography.ergonomicTextScaler(
            MediaQuery.textScalerOf(context),
          ),
        ),
        child: child!,
      ),
      home: Scaffold(
        body: Builder(
          builder: (context) => Center(
            child: FilledButton(
              key: const ValueKey('open-move-cage-sheet'),
              onPressed: () => showRabbitMoveCageSheet(
                context: context,
                houseId: 8,
                rabbit: rabbit,
                cages: cages,
              ),
              child: const Text('打开'),
            ),
          ),
        ),
      ),
    ),
  );
}
