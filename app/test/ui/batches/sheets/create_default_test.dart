import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/sheets/create.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

void main() {
  testWidgets(
    'prefills a house-scoped default and keeps a manual batch code',
    (tester) async {
      final navigatorKey = GlobalKey<NavigatorState>();
      final fixedTime = DateTime.utc(2026, 2, 3, 4, 5, 6, 7);
      await tester.pumpWidget(_testApp(navigatorKey, fixedTime));

      await tester.tap(find.byKey(const ValueKey('open-east-batch')));
      await tester.pumpAndSettle();
      expect(_batchCode(tester), '东一舍-20260203-1205');

      await tester.enterText(
        find.byKey(const ValueKey('batch-code-field')),
        '人工批次-复配',
      );
      await tester.pump();
      expect(_batchCode(tester), '人工批次-复配');

      await navigatorKey.currentState!.maybePop();
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('open-west-batch')));
      await tester.pumpAndSettle();
      expect(_batchCode(tester), '西二舍-20260203-1205');
    },
  );
}

String _batchCode(WidgetTester tester) {
  return tester
      .widget<TextField>(find.byKey(const ValueKey('batch-code-field')))
      .controller!
      .text;
}

Widget _testApp(GlobalKey<NavigatorState> navigatorKey, DateTime fixedTime) {
  return ProviderScope(
    overrides: [
      allActiveHouseRabbitsProvider(1).overrideWith(
        (_) async => const <Rabbit>[],
      ),
      allActiveHouseRabbitsProvider(2).overrideWith(
        (_) async => const <Rabbit>[],
      ),
    ],
    child: MaterialApp(
      navigatorKey: navigatorKey,
      theme: buildAppTheme(),
      home: Scaffold(
        body: Builder(
          builder: (context) => Column(
            children: [
              FilledButton(
                key: const ValueKey('open-east-batch'),
                onPressed: () => showCreateBatchSheet(
                  context: context,
                  houseId: 1,
                  houseName: '东一舍',
                  now: () => fixedTime,
                ),
                child: const Text('东一舍'),
              ),
              FilledButton(
                key: const ValueKey('open-west-batch'),
                onPressed: () => showCreateBatchSheet(
                  context: context,
                  houseId: 2,
                  houseName: '西二舍',
                  now: () => fixedTime,
                ),
                child: const Text('西二舍'),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
