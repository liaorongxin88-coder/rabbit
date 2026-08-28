import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/reproduction/event.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/reproduction/sheets/event.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';

void main() {
  testWidgets('failed parturition clears defaults and fixes live kits at zero',
      (tester) async {
    await _configureErgonomicSurface(tester);

    await tester.pumpWidget(_testApp());
    await tester.tap(find.byKey(const ValueKey('open-parturition')));
    await tester.pumpAndSettle();

    final failedSwitch = find.byKey(
      const ValueKey('parturition-failed-switch'),
    );
    await _dragUntilBuilt(
      tester,
      target: failedSwitch,
      scrollable: find.byType(ListView).last,
    );
    await tester.ensureVisible(failedSwitch);
    await tester.drag(
      find.byType(ListView).last,
      const Offset(0, -120),
    );
    await tester.pumpAndSettle();
    await tester.tap(failedSwitch);
    await tester.pump();
    expect(
      tester.widget<SwitchListTile>(failedSwitch).value,
      isTrue,
    );
    final failedHint = find.text('难产的总产仔数、活仔数和留仔数均固定为 0。');
    await _dragUntilBuilt(
      tester,
      target: failedHint,
      scrollable: find.byType(ListView).last,
    );
    expect(failedHint, findsOneWidget);

    final totalField = find.byKey(
      const ValueKey('parturition-total-kits'),
    );
    await _dragUntilBuilt(
      tester,
      target: totalField,
      scrollable: find.byType(ListView).last,
      scrollDelta: const Offset(0, 180),
    );

    final total = tester.widget<TextField>(
      totalField,
    );
    final live = tester.widget<TextField>(
      find.byKey(const ValueKey('parturition-live-kits')),
    );
    final kept = tester.widget<TextField>(
      find.byKey(const ValueKey('parturition-kept-kits')),
    );
    expect(total.controller!.text, '0');
    expect(live.controller!.text, '0');
    expect(kept.controller!.text, '0');
    expect(total.enabled, isFalse);
    expect(live.enabled, isFalse);
    expect(kept.enabled, isFalse);

    final reminderLabel = find.byKey(
      const ValueKey('next-reminder-stage-label'),
    );
    await _dragUntilBuilt(
      tester,
      target: reminderLabel,
      scrollable: find.byType(ListView).last,
    );
    expect(tester.widget<Text>(reminderLabel).data, '催情提醒日期');
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'future production reminder date is clamped before opening picker',
      (tester) async {
    await _configureErgonomicSurface(tester);
    await tester.pumpWidget(_testApp(
      event: EventItem(
        recordId: 72,
        category: '生产周期',
        eventType: '分娩',
        eventDate: DateTime(2099, 1, 1),
        batchId: 9,
        rabbitId: 18,
        status: 'upcoming',
        sourceHouseId: 8,
        sourceHouseName: '规模繁育兔舍',
      ),
    ));
    await tester.tap(find.byKey(const ValueKey('open-parturition')));
    await tester.pumpAndSettle();

    final dateTile = find.widgetWithText(ListTile, '执行时间 *');
    expect(dateTile, findsOneWidget);
    final now = farmNow();
    final today = '${now.year.toString().padLeft(4, '0')}-'
        '${now.month.toString().padLeft(2, '0')}-'
        '${now.day.toString().padLeft(2, '0')}';
    expect(find.descendant(of: dateTile, matching: find.textContaining(today)),
        findsOneWidget);
    await tester.tap(dateTile);
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    final picker = find.byType(DatePickerDialog);
    expect(picker, findsOneWidget);
    final cancel = find.descendant(
      of: picker,
      matching: find.text('取消'),
    );
    expect(cancel, findsOneWidget);
    await tester.tap(cancel);
    await tester.pumpAndSettle();
    expect(picker, findsNothing);
  });
}

Future<void> _configureErgonomicSurface(WidgetTester tester) async {
  await tester.binding.setSurfaceSize(const Size(360, 800));
  tester.platformDispatcher.textScaleFactorTestValue = 2;
  addTearDown(() => tester.binding.setSurfaceSize(null));
  addTearDown(
    tester.platformDispatcher.clearTextScaleFactorTestValue,
  );
}

Future<void> _dragUntilBuilt(
  WidgetTester tester, {
  required Finder target,
  required Finder scrollable,
  Offset scrollDelta = const Offset(0, -180),
}) async {
  for (var attempt = 0; attempt < 8 && target.evaluate().isEmpty; attempt++) {
    await tester.drag(scrollable, scrollDelta);
    await tester.pumpAndSettle();
  }
  expect(target, findsOneWidget);
}

Widget _testApp({EventItem? event}) {
  final resolvedEvent = event ??
      const EventItem(
        recordId: 71,
        category: '生产周期',
        eventType: '分娩',
        eventDate: null,
        batchId: 9,
        rabbitId: 18,
        status: 'due',
        sourceHouseId: 8,
        sourceHouseName: '规模繁育兔舍',
      );
  return ProviderScope(
    overrides: [
      allActiveHouseRabbitsProvider(8)
          .overrideWith((_) async => const <Rabbit>[]),
      houseCagesProvider(8).overrideWith((_) async => const <Cage>[]),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      builder: (context, child) {
        return MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: AppTypography.ergonomicTextScaler(
              MediaQuery.textScalerOf(context),
            ),
          ),
          child: child!,
        );
      },
      home: Scaffold(
        body: Builder(
          builder: (context) => Center(
            child: ElevatedButton(
              key: const ValueKey('open-parturition'),
              onPressed: () => showProductionEventSheet(
                context: context,
                event: resolvedEvent,
              ),
              child: const Text('打开分娩表单'),
            ),
          ),
        ),
      ),
    ),
  );
}
