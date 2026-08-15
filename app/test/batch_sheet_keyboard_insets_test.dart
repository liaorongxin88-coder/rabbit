import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/create_batch_sheet.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/production_event_sheet.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/weaning_sheet.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

const _deviceSizes = [
  Size(360, 800),
  Size(393, 852),
  Size(412, 915),
];
const _keyboardHeights = [180.0, 300.0, 420.0];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'create Batch inputs and submit stay reachable above dynamic keyboards',
    (tester) async {
      _configureView(tester);
      await tester.pumpWidget(
        _testApp(
          overrides: [
            allActiveHouseRabbitsProvider(8).overrideWith(
              (_) async => const [
                Rabbit(
                  id: 101,
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
                ),
              ],
            ),
          ],
          openKey: const ValueKey('open-create-batch-keyboard-test'),
          onOpen: (context) => showCreateBatchSheet(
            context: context,
            houseId: 8,
            houseName: 'Keyboard Matrix House',
          ),
        ),
      );
      await _openSheet(
        tester,
        const ValueKey('open-create-batch-keyboard-test'),
      );

      final scrollSurface = find.byKey(const ValueKey('batch-mother-list'));
      final option = find.byKey(const ValueKey('batch-mother-option-101'));
      await _scrollToField(tester, scrollSurface, option);
      await tester.tap(option);
      await tester.pump();
      expect(
        tester
            .widget<ElevatedButton>(
              find.byKey(const ValueKey('create-batch-submit')),
            )
            .onPressed,
        isNotNull,
      );

      await _verifyKeyboardMatrix(
        tester,
        scrollSurface: scrollSurface,
        fields: {
          const ValueKey('batch-code-field'): 'B-KEYBOARD-MATRIX',
          const ValueKey('batch-remark-field'): 'Batch keyboard remark',
          const ValueKey('batch-mother-search'): '101',
        },
        submit: find.byKey(const ValueKey('create-batch-submit')),
      );
    },
  );

  testWidgets(
    'production inputs and submit stay reachable above dynamic keyboards',
    (tester) async {
      _configureView(tester);
      await tester.pumpWidget(
        _testApp(
          overrides: [
            allActiveHouseRabbitsProvider(8)
                .overrideWith((_) async => const <Rabbit>[]),
            houseCagesProvider(8).overrideWith((_) async => const <Cage>[]),
          ],
          openKey: const ValueKey('open-production-keyboard-test'),
          onOpen: (context) => showProductionEventSheet(
            context: context,
            event: _parturitionEvent,
          ),
        ),
      );
      await _openSheet(
        tester,
        const ValueKey('open-production-keyboard-test'),
      );

      await _verifyKeyboardMatrix(
        tester,
        scrollSurface: find.byKey(const ValueKey('production-event-form-list')),
        fields: {
          const ValueKey('parturition-total-kits'): '8',
          const ValueKey('parturition-live-kits'): '7',
          const ValueKey('production-event-remark'):
              'Parturition keyboard remark',
        },
        submit: find.byKey(const ValueKey('production-event-submit')),
      );
    },
  );

  testWidgets(
    'weaning inputs and submit stay reachable above dynamic keyboards',
    (tester) async {
      _configureView(tester);
      await tester.pumpWidget(
        _testApp(
          overrides: [
            houseCagesProvider(8).overrideWith((_) async => const <Cage>[]),
          ],
          openKey: const ValueKey('open-weaning-keyboard-test'),
          onOpen: (context) => showWeaningSheet(
            context: context,
            event: _weaningEvent,
          ),
        ),
      );
      await _openSheet(
        tester,
        const ValueKey('open-weaning-keyboard-test'),
      );

      await _verifyKeyboardMatrix(
        tester,
        scrollSurface: find.byKey(const ValueKey('weaning-form-list')),
        fields: {
          const ValueKey('weaning-count'): '8',
          const ValueKey('weaning-male-count'): '4',
          const ValueKey('weaning-female-count'): '4',
          const ValueKey('weaning-average-weight'): '2.4',
          const ValueKey('weaning-remark'): 'Weaning keyboard remark',
        },
        submit: find.byKey(const ValueKey('weaning-submit')),
      );
    },
  );
}

void _configureView(WidgetTester tester) {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = _deviceSizes.first;
  tester.platformDispatcher.textScaleFactorTestValue = 2;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetViewInsets);
  addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
}

Future<void> _openSheet(WidgetTester tester, Key openKey) async {
  await tester.tap(find.byKey(openKey));
  await tester.pumpAndSettle();
}

Future<void> _verifyKeyboardMatrix(
  WidgetTester tester, {
  required Finder scrollSurface,
  required Map<Key, String> fields,
  required Finder submit,
}) async {
  expect(scrollSurface, findsOneWidget);
  expect(
    tester.widget<ScrollView>(scrollSurface).keyboardDismissBehavior,
    ScrollViewKeyboardDismissBehavior.onDrag,
  );
  expect(
    MediaQuery.textScalerOf(tester.element(scrollSurface)).scale(10),
    20,
  );

  for (final deviceSize in _deviceSizes) {
    tester.view.physicalSize = deviceSize;
    tester.view.resetViewInsets();
    await tester.pumpAndSettle();

    for (final keyboardHeight in _keyboardHeights) {
      for (final fieldEntry in fields.entries) {
        FocusManager.instance.primaryFocus?.unfocus();
        tester.testTextInput.hide();
        tester.view.resetViewInsets();
        await tester.pumpAndSettle();

        final field = find.byKey(fieldEntry.key);
        await _scrollToField(tester, scrollSurface, field);
        await tester.enterText(field, fieldEntry.value);
        await tester.pump();

        final editable = find.descendant(
          of: field,
          matching: find.byType(EditableText),
        );
        expect(editable, findsOneWidget);
        expect(
          tester.widget<EditableText>(editable).focusNode.hasFocus,
          isTrue,
        );
        expect(tester.testTextInput.hasAnyClients, isTrue);
        final focusedFieldElement = tester.element(field);

        tester.view.viewInsets = FakeViewPadding(bottom: keyboardHeight);
        await tester.pump();
        expect(tester.takeException(), isNull);
        expect(focusedFieldElement.mounted, isTrue);
        await Scrollable.ensureVisible(focusedFieldElement);
        await tester.pumpAndSettle();
        expect(field, findsOneWidget);

        final keyboardTop = deviceSize.height - keyboardHeight;
        _expectStrictlyAbove(
          tester,
          field,
          keyboardTop,
          reason:
              '${fieldEntry.key} at ${deviceSize.width}x${deviceSize.height} '
              'with ${keyboardHeight}px keyboard',
        );
        _expectStrictlyAbove(
          tester,
          submit,
          keyboardTop,
          reason: 'submit at ${deviceSize.width}x${deviceSize.height} with '
              '${keyboardHeight}px keyboard while ${fieldEntry.key} is focused',
        );
        expect(tester.takeException(), isNull);
      }
    }
  }
}

Future<void> _scrollToField(
  WidgetTester tester,
  Finder scrollSurface,
  Finder field,
) async {
  final scrollable = find.descendant(
    of: scrollSurface,
    matching: find.byWidgetPredicate(
      (widget) =>
          widget is Scrollable &&
          widget.axisDirection == AxisDirection.down &&
          widget.restorationId == null,
    ),
  );
  expect(scrollable, findsOneWidget);
  final position = tester.state<ScrollableState>(scrollable).position;
  position.jumpTo(position.minScrollExtent);
  await tester.pump();
  await tester.scrollUntilVisible(
    field,
    120,
    scrollable: scrollable,
    maxScrolls: 30,
  );
  await tester.pumpAndSettle();
}

void _expectStrictlyAbove(
  WidgetTester tester,
  Finder target,
  double keyboardTop, {
  required String reason,
}) {
  expect(target, findsOneWidget);
  final rect = tester.getRect(target);
  expect(rect.top, greaterThanOrEqualTo(0), reason: reason);
  expect(rect.bottom, lessThan(keyboardTop), reason: reason);
}

Widget _testApp({
  required List<Override> overrides,
  required Key openKey,
  required Future<void> Function(BuildContext context) onOpen,
}) {
  return ProviderScope(
    overrides: overrides,
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
              key: openKey,
              onPressed: () => onOpen(context),
              child: const Text('Open Batch form'),
            ),
          ),
        ),
      ),
    ),
  );
}

const _parturitionEvent = EventItem(
  recordId: 71,
  category: '生产周期',
  eventType: '分娩',
  eventDate: null,
  batchId: 9,
  rabbitId: 18,
  status: 'due',
  sourceHouseId: 8,
  sourceHouseName: 'Keyboard Matrix House',
);

const _weaningEvent = EventItem(
  recordId: 72,
  category: '生产周期',
  eventType: '断奶',
  eventDate: null,
  batchId: 9,
  rabbitId: 18,
  status: 'due',
  sourceHouseId: 8,
  sourceHouseName: 'Keyboard Matrix House',
);
