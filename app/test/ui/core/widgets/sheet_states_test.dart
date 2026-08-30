import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/reproduction/event.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/batches/sheets/create.dart';
import 'package:rabbit_flutter/src/ui/reproduction/sheets/event.dart';
import 'package:rabbit_flutter/src/ui/reproduction/sheets/weaning.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

import 'litter_repository_harness.dart';

void main() {
  testWidgets(
    'create batch loading error and retry fit narrow true 200 percent text',
    (tester) async {
      await _setSurface(tester, const Size(360, 800));
      final firstAttempt = Completer<List<Rabbit>>();
      final retryAttempt = Completer<List<Rabbit>>();
      var attempts = 0;

      await tester.pumpWidget(
        _testApp(
          overrides: [
            allActiveHouseRabbitsProvider(8).overrideWith((_) {
              attempts += 1;
              return attempts == 1 ? firstAttempt.future : retryAttempt.future;
            }),
          ],
          onOpen: (context) => showCreateBatchSheet(
            context: context,
            houseId: 8,
            houseName: '一号规模繁育兔舍',
          ),
        ),
      );
      await _openSheet(tester);

      _expectLoadingState(tester, '正在加载可选种母兔');
      firstAttempt.completeError(
        StateError('technical rabbit payload'),
        StackTrace.current,
      );
      await tester.pumpAndSettle();

      _expectErrorActions(tester);
      expect(find.text('无法加载可选兔只，请检查网络后重试。'), findsOneWidget);
      expect(find.textContaining('technical rabbit payload'), findsNothing);
      expect(tester.takeException(), isNull);

      await tester.tap(find.byKey(const ValueKey('batch-sheet-error-retry')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(attempts, 2);
      _expectLoadingState(tester, '正在加载可选种母兔');
      await tester.tap(
        find.byKey(const ValueKey('batch-sheet-loading-close')),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('batch-sheet-loading')), findsNothing);
    },
  );

  testWidgets(
    'production sheet cage error preserves API message and retries at 200 percent',
    (tester) async {
      await _setSurface(tester, const Size(412, 915));
      final firstAttempt = Completer<List<Cage>>();
      final retryAttempt = Completer<List<Cage>>();
      var attempts = 0;

      await tester.pumpWidget(
        _testApp(
          overrides: [
            allActiveHouseRabbitsProvider(8)
                .overrideWith((_) async => const <Rabbit>[]),
            houseCagesProvider(8).overrideWith((_) {
              attempts += 1;
              return attempts == 1 ? firstAttempt.future : retryAttempt.future;
            }),
          ],
          onOpen: (context) => showProductionEventSheet(
            context: context,
            event: _parturitionEvent,
          ),
        ),
      );
      await _openSheet(tester);

      _expectLoadingState(tester, '正在加载笼位信息');
      firstAttempt.completeError(
        const ApiException('笼位服务暂时不可用'),
        StackTrace.current,
      );
      await tester.pumpAndSettle();

      _expectErrorActions(tester);
      expect(find.text('笼位服务暂时不可用'), findsOneWidget);
      expect(tester.takeException(), isNull);

      await tester.tap(find.byKey(const ValueKey('batch-sheet-error-retry')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(attempts, 2);
      _expectLoadingState(tester, '正在加载笼位信息');
      await tester.tap(
        find.byKey(const ValueKey('batch-sheet-loading-close')),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('batch-sheet-loading')), findsNothing);
    },
  );

  testWidgets(
    'weaning sheet records waiting inventory without loading cages',
    (tester) async {
      await _setSurface(tester, const Size(360, 800));
      final cageRequest = Completer<List<Cage>>();
      final litterHarness = LitterRepositoryHarness(
        cycleId: 72,
        motherRabbitId: 18,
      );
      addTearDown(litterHarness.dispose);

      await tester.pumpWidget(
        _testApp(
          overrides: [
            reproRepositoryProvider.overrideWithValue(litterHarness.repository),
            houseCagesProvider(8).overrideWith((_) => cageRequest.future),
          ],
          onOpen: (context) => showWeaningSheet(
            context: context,
            event: _weaningEvent,
          ),
        ),
      );
      await _openSheet(tester);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));

      expect(cageRequest.isCompleted, isFalse);
      expect(find.byKey(const ValueKey('batch-sheet-loading')), findsNothing);
      expect(find.byKey(const ValueKey('batch-sheet-error')), findsNothing);
      expect(
        find.textContaining('断奶仅记录待分笼数量'),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('weaning-submit')), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}

Future<void> _setSurface(WidgetTester tester, Size size) async {
  await tester.binding.setSurfaceSize(size);
  addTearDown(() => tester.binding.setSurfaceSize(null));
}

Future<void> _openSheet(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('open-batch-sheet')));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 350));
}

void _expectLoadingState(WidgetTester tester, String message) {
  final loading = find.byKey(const ValueKey('batch-sheet-loading'));
  final context = tester.element(loading);
  expect(MediaQuery.textScalerOf(context).scale(10), 20);
  expect(find.text(message), findsOneWidget);
  expect(
    tester.getSize(
      find.byKey(const ValueKey('batch-sheet-loading-close')),
    ),
    const Size(48, 48),
  );
  expect(tester.takeException(), isNull);
}

void _expectErrorActions(WidgetTester tester) {
  final error = find.byKey(const ValueKey('batch-sheet-error'));
  final context = tester.element(error);
  expect(MediaQuery.textScalerOf(context).scale(10), 20);
  expect(find.text('加载失败'), findsOneWidget);
  for (final key in const [
    ValueKey('batch-sheet-error-retry'),
    ValueKey('batch-sheet-error-close'),
  ]) {
    expect(tester.getSize(find.byKey(key)).height, greaterThanOrEqualTo(48));
  }
}

Widget _testApp({
  required List<Override> overrides,
  required Future<void> Function(BuildContext context) onOpen,
}) {
  return ProviderScope(
    overrides: overrides,
    child: MaterialApp(
      theme: buildAppTheme(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: const TextScaler.linear(2),
        ),
        child: child!,
      ),
      home: Scaffold(
        body: Builder(
          builder: (context) => Center(
            child: FilledButton(
              key: const ValueKey('open-batch-sheet'),
              onPressed: () => onOpen(context),
              child: const Text('打开生产表单'),
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
  sourceHouseName: '一号规模繁育兔舍',
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
  sourceHouseName: '一号规模繁育兔舍',
);
