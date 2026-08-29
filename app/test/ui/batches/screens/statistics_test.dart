import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/statistics.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/ui/batches/screens/detail.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';

void main() {
  testWidgets('shows a loading state while batch statistics load',
      (tester) async {
    final completer = Completer<BatchStatistics>();

    await _pumpDetail(
      tester,
      statistics: () => completer.future,
      settle: false,
    );

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    completer.complete(const BatchStatistics.empty());
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('shows all batch statistics at 200 percent on a narrow screen',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await _pumpDetail(
      tester,
      textScaler: const TextScaler.linear(2),
      statistics: () async => const BatchStatistics(
        totalLitters: 3,
        totalKits: 28,
        totalLiveKits: 26,
        totalWeaned: 22,
      ),
    );

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-statistics-total-litters')),
    );
    _expectStatistic('batch-statistics-total-litters', 3);
    _expectStatistic('batch-statistics-total-kits', 28);
    _expectStatistic('batch-statistics-total-live-kits', 26);
    _expectStatistic('batch-statistics-total-weaned', 22);
    expect(find.text('产崽窝数'), findsOneWidget);
    expect(find.text('产崽总数'), findsOneWidget);
    expect(find.text('活崽总数'), findsOneWidget);
    expect(find.text('断奶数量'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('retains four zero statistics and shows the empty state',
      (tester) async {
    await _pumpDetail(
      tester,
      statistics: () async => const BatchStatistics.empty(),
    );

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-statistics-total-litters')),
    );
    _expectStatistic('batch-statistics-total-litters', 0);
    _expectStatistic('batch-statistics-total-kits', 0);
    _expectStatistic('batch-statistics-total-live-kits', 0);
    _expectStatistic('batch-statistics-total-weaned', 0);
    expect(
      find.byKey(const ValueKey('batch-statistics-empty')),
      findsOneWidget,
    );
    expect(find.text('暂无产崽记录'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('shows a retryable error when batch statistics fail to load',
      (tester) async {
    await _pumpDetail(
      tester,
      statistics: () async => throw const ApiException('网络不可用'),
    );

    expect(find.text('加载失败'), findsOneWidget);
    expect(find.text('网络不可用'), findsOneWidget);
    expect(find.text('重试'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('refresh reloads batch statistics', (tester) async {
    var requests = 0;
    await _pumpDetail(
      tester,
      statistics: () async => BatchStatistics(
        totalLitters: ++requests,
        totalKits: 0,
        totalLiveKits: 0,
        totalWeaned: 0,
      ),
    );

    expect(requests, 1);
    await tester.tap(find.byTooltip('刷新批次'));
    await tester.pumpAndSettle();

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-statistics-total-litters')),
    );
    expect(requests, 2);
    _expectStatistic('batch-statistics-total-litters', 2);
    expect(tester.takeException(), isNull);
  });
}

Future<void> _pumpDetail(
  WidgetTester tester, {
  required Future<BatchStatistics> Function() statistics,
  TextScaler textScaler = TextScaler.noScaling,
  bool settle = true,
}) async {
  const request = BatchDetailRequest(houseId: 8, batchId: 11);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        batchDetailProvider(request).overrideWith(
          (_) async => const Batch(
            id: 11,
            houseId: 8,
            batchCode: 'STAT-11',
            status: '进行中',
            startDate: null,
            endDate: null,
            remark: '',
          ),
        ),
        batchStatisticsProvider(request).overrideWith((_) => statistics()),
        batchMembersProvider(request).overrideWith((_) async => const []),
        pendingWeaningRecordsProvider(request)
            .overrideWith((_) async => const []),
        housePermissionProvider(8).overrideWith(
          (_) async => const HousePermission(perms: 'view', isAdmin: false),
        ),
      ],
      child: MaterialApp(
        theme: buildAppTheme(),
        home: MediaQuery(
          data: MediaQueryData(textScaler: textScaler),
          child: const HouseBatchDetailScreen(houseId: 8, batchId: 11),
        ),
      ),
    ),
  );
  await tester.pump();
  if (settle) {
    await tester.pumpAndSettle();
  }
}

void _expectStatistic(String key, int value) {
  expect(
    find.descendant(
      of: find.byKey(ValueKey(key)),
      matching: find.text('$value'),
    ),
    findsOneWidget,
  );
}

Future<void> _scrollDetailUntilVisible(
  WidgetTester tester,
  Finder target,
) async {
  final list = find.byKey(const ValueKey('batch-detail-member-list'));
  await tester.scrollUntilVisible(
    target,
    240,
    scrollable:
        find.descendant(of: list, matching: find.byType(Scrollable)).first,
  );
  await tester.pumpAndSettle();
}
