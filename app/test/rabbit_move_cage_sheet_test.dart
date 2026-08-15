import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/widgets/rabbit_move_cage_sheet.dart';

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
    await tester.tap(find.text('A-02 · 空笼'));
    await tester.pumpAndSettle();

    final submit = find.byKey(const ValueKey('rabbit-move-cage-submit'));
    expect(tester.widget<ElevatedButton>(submit).onPressed, isNotNull);
    final search = find.byKey(const ValueKey('rabbit-move-cage-search'));
    await tester.tap(search);
    await tester.pump();
    final editableText = find.descendant(
      of: search,
      matching: find.byType(EditableText),
    );
    expect(
        tester.widget<EditableText>(editableText).focusNode.hasFocus, isTrue);

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
