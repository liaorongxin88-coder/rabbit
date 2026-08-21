import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/sheets/create.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

void main() {
  testWidgets(
    '1000-doe batch supports lazy rendering search and bulk selection',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(360, 800));
      tester.platformDispatcher.textScaleFactorTestValue = 2;
      addTearDown(() => tester.binding.setSurfaceSize(null));
      addTearDown(
        tester.platformDispatcher.clearTextScaleFactorTestValue,
      );

      await tester.pumpWidget(_testApp(_does(1000)));
      await tester.tap(find.byKey(const ValueKey('open-create-batch')));
      await tester.pumpAndSettle();

      final list = find.byKey(const ValueKey('batch-mother-list'));
      await _dragUntilBuilt(
        tester,
        target: find.text('可选种母兔（已选 0 只）'),
        scrollable: list,
      );
      await tester.drag(
        list,
        const Offset(0, -320),
      );
      await tester.pumpAndSettle();
      expect(find.text('共 1000 只'), findsOneWidget);
      expect(
        find.byType(CheckboxListTile).evaluate().length,
        lessThan(30),
        reason: 'The list must build only visible rows for a large farm.',
      );
      expect(tester.takeException(), isNull);

      await tester.tap(find.byKey(const ValueKey('batch-select-filtered')));
      await tester.pump();
      expect(find.text('可选种母兔（已选 1000 只）'), findsOneWidget);
      expect(find.text('将 1000 只种母兔加入该批次'), findsOneWidget);

      final search = find.byKey(const ValueKey('batch-mother-search'));
      await tester.ensureVisible(search);
      await tester.enterText(search, '999');
      await tester.pump();
      expect(find.text('结果 1 / 1000 只'), findsOneWidget);
      expect(find.textContaining('兔 #999 ·'), findsOneWidget);
      expect(find.textContaining('兔 #998 ·'), findsNothing);
      expect(tester.takeException(), isNull);

      tester.testTextInput.hide();
      await tester.pumpAndSettle();
      final clearSearch = find.byKey(const ValueKey('batch-clear-search'));
      await tester.ensureVisible(clearSearch);
      await tester.pumpAndSettle();
      await tester.tap(clearSearch);
      await tester.pumpAndSettle();
      expect(find.text('共 1000 只'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('create-batch-submit')));
      await tester.pumpAndSettle();
      expect(find.text('确认创建大批次'), findsOneWidget);
      expect(find.textContaining('将 1000 只种母兔加入批次'), findsOneWidget);
      expect(find.text('返回核对'), findsOneWidget);
      expect(find.text('确认创建'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}

Future<void> _dragUntilBuilt(
  WidgetTester tester, {
  required Finder target,
  required Finder scrollable,
}) async {
  for (var attempt = 0; attempt < 8 && target.evaluate().isEmpty; attempt++) {
    await tester.drag(scrollable, const Offset(0, -180));
    await tester.pumpAndSettle();
  }
  expect(target, findsOneWidget);
}

Widget _testApp(List<Rabbit> rabbits) {
  return ProviderScope(
    overrides: [
      allActiveHouseRabbitsProvider(8).overrideWith((_) async => rabbits),
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
              key: const ValueKey('open-create-batch'),
              onPressed: () => showCreateBatchSheet(
                context: context,
                houseId: 8,
                houseName: '千只母兔规模测试兔舍',
              ),
              child: const Text('创建生产批次'),
            ),
          ),
        ),
      ),
    ),
  );
}

List<Rabbit> _does(int count) {
  return List.generate(
    count,
    (index) => Rabbit(
      id: index + 1,
      houseId: 8,
      cageId: index + 1,
      motherId: null,
      type: '0',
      gender: '0',
      breed: '新西兰白兔',
      arrivalMethod: '自繁',
      arrivalDate: DateTime(2025, 1, 1),
      weight: 4.2,
      isActive: true,
    ),
  );
}
