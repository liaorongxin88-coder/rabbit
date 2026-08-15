import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/widgets/rabbit_entry_flow.dart';

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
      expect(find.byKey(const ValueKey('rabbit-growth-stage')), findsOneWidget);
      expect(reproductiveStage, findsOneWidget);

      await tester.ensureVisible(reproductiveStage);
      await tester.pumpAndSettle();
      await tester.tap(reproductiveStage);
      await tester.pumpAndSettle();
      expect(find.text('空怀'), findsOneWidget);
      expect(find.text('妊娠'), findsOneWidget);
      expect(find.text('可配'), findsNothing);
      final pregnant = find.text('妊娠');
      await tester.ensureVisible(pregnant);
      await tester.tap(pregnant);
      await tester.pumpAndSettle();

      final male = find.text('公');
      await tester.ensureVisible(male);
      await tester.pumpAndSettle();
      await tester.tap(male);
      await tester.pumpAndSettle();
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

  testWidgets('replacement locks to reserve and commodity omits reproduction',
      (tester) async {
    await tester.pumpWidget(_replacementEditTestApp());
    await tester.tap(find.byKey(const ValueKey('open-rabbit-edit-sheet')));
    await tester.pumpAndSettle();

    expect(find.text('繁殖阶段：后备（后备兔固定记录为后备阶段）'), findsOneWidget);
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

Widget _entryTestApp() {
  return _testApp(
    child: Builder(
      builder: (context) => FilledButton(
        key: const ValueKey('open-rabbit-entry-sheet'),
        onPressed: () => showRabbitEntryTypeSheet(
          context: context,
          houseId: 8,
          cage: _breedingCage,
        ),
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
