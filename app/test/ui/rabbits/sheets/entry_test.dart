import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/reproduction/entry_point.dart';
import 'package:rabbit_flutter/src/ui/reproduction/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/entry.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'breeding intake stages follow sex and keep actions above dynamic keyboards',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(360, 800);
      tester.platformDispatcher.textScaleFactorTestValue = 2;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetViewInsets);
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

      await tester.pumpWidget(_entryTestApp());
      await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('确定'));
      await tester.pumpAndSettle();

      final reproductiveStage = find.byKey(
        const ValueKey('rabbit-reproductive-stage'),
      );
      final reproEntryStage = find.byKey(const ValueKey('rabbit-repro-stage'));
      expect(find.byKey(const ValueKey('rabbit-growth-stage')), findsOneWidget);
      // 种母兔不再提供旧的繁殖阶段下拉：后端已拒收手录值，
      // 她们走服务端下发的生产阶段入轨。
      expect(reproductiveStage, findsNothing);
      expect(reproEntryStage, findsOneWidget);

      // 从【待分笼】入轨需要分娩日与活仔数，字段随服务端字典出现。
      await tester.ensureVisible(reproEntryStage);
      await tester.pumpAndSettle();
      await tester.tap(reproEntryStage);
      await tester.pumpAndSettle();
      await tester.tap(find.text('待分笼').last);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('rabbit-stage-entered-at')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('rabbit-birth-date')), findsOneWidget);
      expect(find.byKey(const ValueKey('rabbit-live-kits')), findsOneWidget);
      expect(find.byKey(const ValueKey('rabbit-mating-date')), findsNothing);

      final male = find.text('公');
      await tester.ensureVisible(male);
      await tester.pumpAndSettle();
      await tester.tap(male);
      await tester.pumpAndSettle();
      // 种公兔仍然是旧的两选一，它不进生产周期。
      expect(reproEntryStage, findsNothing);
      expect(reproductiveStage, findsOneWidget);
      await tester.ensureVisible(reproductiveStage);
      await tester.pumpAndSettle();
      await tester.tap(reproductiveStage);
      await tester.pumpAndSettle();
      expect(find.text('可配'), findsOneWidget);
      expect(find.text('休整'), findsOneWidget);
      expect(find.text('妊娠'), findsNothing);
      final ready = find.text('可配');
      await tester.ensureVisible(ready);
      await tester.tap(ready);
      await tester.pumpAndSettle();

      final breed = find.byKey(const ValueKey('rabbit-entry-breed'));
      await tester.ensureVisible(breed);
      await tester.tap(breed);
      await tester.pump();
      final editableText = find.descendant(
        of: breed,
        matching: find.byType(EditableText),
      );
      expect(
        tester.widget<EditableText>(editableText).focusNode.hasFocus,
        isTrue,
      );

      final submit = find.byKey(const ValueKey('rabbit-entry-submit'));
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
    },
  );

  // 调用方（笼位详情页）靠这个 Future 决定何时刷笼内列表。早先的写法在类型页
  // pop 后用 post-frame 回调另开表单且不等，于是刷新在兔子创建前就跑完了——
  // 真机上表现为录入完看不到新兔，得退出重进页面。
  testWidgets('entry flow future completes only after the form closes',
      (tester) async {
    var finished = false;
    await tester
        .pumpWidget(_entryTestApp(onFlowFinished: () => finished = true));
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('rabbit-entry-submit')), findsOneWidget);
    expect(finished, isFalse, reason: '表单还开着时不得当作流程结束');

    await tester.tap(find.text('取消').last);
    await tester.pumpAndSettle();
    expect(finished, isTrue, reason: '表单关闭后调用方才能刷列表');
  });

  testWidgets('replacement locks to reserve and commodity omits reproduction',
      (tester) async {
    await tester.pumpWidget(_replacementEditTestApp());
    await tester.tap(find.byKey(const ValueKey('open-rabbit-edit-sheet')));
    await tester.pumpAndSettle();

    expect(find.text('繁殖阶段：后备（后备兔固定记录为后备阶段）'), findsOneWidget);
    expect(find.text('笼位 #12（只读）'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-reproductive-stage')),
      findsNothing,
    );
    await tester.tap(find.byTooltip('关闭'));
    await tester.pumpAndSettle();

    await tester.pumpWidget(_commodityEntryTestApp());
    await tester.tap(find.byKey(const ValueKey('open-rabbit-entry-sheet')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('rabbit-growth-stage')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-reproductive-stage')),
      findsNothing,
    );
    expect(find.text('繁殖阶段'), findsNothing);
  });
}

Widget _entryTestApp({VoidCallback? onFlowFinished}) {
  return _testApp(
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-entry-sheet'),
        onPressed: () async {
          await showRabbitEntryTypeSheet(
            context: context,
            houseId: 8,
            cage: _breedingCage,
          );
          onFlowFinished?.call();
        },
        child: const Text('录入'),
      ),
    ),
  );
}

Widget _replacementEditTestApp() {
  return _testApp(
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-edit-sheet'),
        onPressed: () => showRabbitEditSheet(
          context: context,
          houseId: 8,
          rabbit: _replacementRabbit,
          cages: const [_replacementCage],
        ),
        child: const Text('编辑'),
      ),
    ),
  );
}

Widget _commodityEntryTestApp() {
  return _testApp(
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-entry-sheet'),
        onPressed: () => showRabbitEntryTypeSheet(
          context: context,
          houseId: 8,
          cage: _commodityCage,
        ),
        child: const Text('录入'),
      ),
    ),
  );
}

Widget _testApp({required Widget child}) {
  return ProviderScope(
    overrides: [
      // 入轨字典来自服务端；组件测试里用一份与后端 EntryPoint 表一致的子集。
      reproEntryPointsProvider.overrideWith(
        (ref, houseId) async => const [
          ReproEntryPoint(
            stage: 'AWAIT_ESTRUS',
            stageLabel: '待催情',
            requiredFacts: [
              ReproRequiredFact(fact: 'STAGE_ENTERED_AT', label: '进入该阶段的日期'),
            ],
          ),
          ReproEntryPoint(
            stage: 'AWAIT_PALPATION',
            stageLabel: '待摸胎',
            requiredFacts: [
              ReproRequiredFact(fact: 'MATING_DATE', label: '配种日期'),
            ],
          ),
          ReproEntryPoint(
            stage: 'AWAIT_WEANING',
            stageLabel: '待分笼',
            requiredFacts: [
              ReproRequiredFact(fact: 'BIRTH_DATE', label: '分娩日期'),
              ReproRequiredFact(fact: 'LIVE_KITS', label: '活仔数'),
            ],
          ),
        ],
      ),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      builder: (context, page) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: AppTypography.ergonomicTextScaler(
            MediaQuery.textScalerOf(context),
          ),
        ),
        child: page!,
      ),
      home: Scaffold(body: Center(child: child)),
    ),
  );
}

const _breedingCage = Cage(
  id: 11,
  houseId: 8,
  cageNumber: 'A-01',
  status: '1',
  rabbitCount: 0,
  isEnabled: true,
);

const _replacementCage = Cage(
  id: 12,
  houseId: 8,
  cageNumber: 'B-01',
  status: '2',
  rabbitCount: 1,
  isEnabled: true,
);

const _commodityCage = Cage(
  id: 13,
  houseId: 8,
  cageNumber: 'C-01',
  status: '3',
  rabbitCount: 0,
  isEnabled: true,
);

const _replacementRabbit = Rabbit(
  id: 801,
  houseId: 8,
  cageId: 12,
  motherId: null,
  type: '1',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 3.2,
  isActive: true,
  growthStage: 'GROWING',
  reproductiveStage: null,
);
