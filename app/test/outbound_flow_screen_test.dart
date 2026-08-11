import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/outbound_repository.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/outbound/view_models/outbound_controller.dart';
import 'package:rabbit_flutter/src/ui/outbound/widgets/outbound_flow_screen.dart';

import 'outbound_controller_test.dart' show FakeOutboundGateway;

void main() {
  for (final size in [const Size(360, 800), const Size(412, 915)]) {
    testWidgets(
        'outbound selection and confirmation fit ${size.width.toInt()}x${size.height.toInt()}',
        (tester) async {
      SharedPreferences.setMockInitialValues({});
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final gateway = FakeOutboundGateway();
      final entry = OutboundEntry(
          userId: size.width.toInt(), houseId: 8, entryType: 'HOUSE');

      await tester.pumpWidget(
        ProviderScope(
          overrides: [outboundRepositoryProvider.overrideWithValue(gateway)],
          child: MaterialApp(
              theme: buildAppTheme(), home: OutboundFlowScreen(entry: entry)),
        ),
      );
      await _pumpUntilFound(tester, find.text('正常可出库'));

      expect(find.text('正常可出库'), findsOneWidget);
      expect(find.text('下一步 · 1 只'), findsOneWidget);
      final nextButton = find.byKey(const ValueKey('outbound-continue-button'));
      expect(tester.getSize(nextButton).height, greaterThanOrEqualTo(48));
      expect(tester.takeException(), isNull);

      await tester.tap(find.text('下一步 · 1 只'));
      await tester.pumpAndSettle();
      expect(find.text('销售信息'), findsOneWidget);
      expect(find.text('确认出库 1 只'), findsOneWidget);
      final submitButton = find.byKey(const ValueKey('outbound-submit-button'));
      expect(tester.getSize(submitButton).height, greaterThanOrEqualTo(48));
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('outbound core flow remains usable with 200 percent text',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final gateway = FakeOutboundGateway();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [outboundRepositoryProvider.overrideWithValue(gateway)],
        child: MaterialApp(
          theme: buildAppTheme(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              textScaler: AppTypography.ergonomicTextScaler(
                const TextScaler.linear(2),
              ),
            ),
            child: child!,
          ),
          home: const OutboundFlowScreen(
            entry: OutboundEntry(userId: 200, houseId: 8, entryType: 'HOUSE'),
          ),
        ),
      ),
    );
    await _pumpUntilFound(tester, find.text('正常可出库'));

    final context = tester.element(find.byType(OutboundFlowScreen));
    expect(MediaQuery.textScalerOf(context).scale(10), 15);
    expect(find.text('下一步 · 1 只'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.ensureVisible(find.text('下一步 · 1 只'));
    await tester.tap(find.text('下一步 · 1 只'));
    await tester.pumpAndSettle();
    expect(find.text('确认出库 1 只'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('early-sale dialog closes without disposing an active field',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [outboundRepositoryProvider.overrideWithValue(gateway)],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const OutboundFlowScreen(
            entry: OutboundEntry(userId: 201, houseId: 8, entryType: 'HOUSE'),
          ),
        ),
      ),
    );
    await _pumpUntilFound(tester, find.text('正常可出库'));

    await tester.tap(
      find.byKey(const ValueKey('outbound-summary-early-sale')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('1-1-1'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('兔只操作'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('提前出售'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('outbound-early-sale-reason')),
      '客户提前采购',
    );
    await tester.tap(
      find.byKey(const ValueKey('outbound-early-sale-confirm')),
    );
    await tester.pumpAndSettle();

    expect(find.text('提前出售 · 客户提前采购'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
      'conflict details are revealed after the confirmation form scrolls',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    final gateway = FakeOutboundGateway()..returnConflict = true;

    await tester.pumpWidget(
      ProviderScope(
        overrides: [outboundRepositoryProvider.overrideWithValue(gateway)],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const OutboundFlowScreen(
            entry: OutboundEntry(userId: 202, houseId: 8, entryType: 'HOUSE'),
          ),
        ),
      ),
    );
    await _pumpUntilFound(tester, find.text('正常可出库'));
    await tester.tap(find.text('下一步 · 1 只'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('outbound-total-weight')),
      '2.5',
    );
    await tester.drag(find.byType(ListView), const Offset(0, -500));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('outbound-submit-button')));
    await tester.pumpAndSettle();

    final conflictTitle = find.text('1 只兔状态冲突');
    expect(conflictTitle, findsOneWidget);
    expect(tester.getTopLeft(conflictTitle).dy, greaterThanOrEqualTo(0));
    expect(tester.takeException(), isNull);
  });
}

Future<void> _pumpUntilFound(WidgetTester tester, Finder finder,
    {int maxPumps = 100}) async {
  for (var i = 0; i < maxPumps; i++) {
    await tester.pump(const Duration(milliseconds: 20));
    if (finder.evaluate().isNotEmpty) return;
  }
  fail('Expected widget was not found');
}
