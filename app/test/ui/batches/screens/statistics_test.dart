import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/sharing/files.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/statistics.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/ui/batches/screens/detail.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/statistics.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';

import '../../../domain/batches/statistics_fixture.dart';

void main() {
  testWidgets('statistics load independently without blocking batch operations',
      (tester) async {
    final completer = Completer<BatchStatistics>();

    await _pumpDetail(tester,
        statistics: (_) => completer.future, settle: false);
    await tester.pump();

    expect(
      find.byKey(const ValueKey('batch-detail-member-list')),
      findsOneWidget,
    );
    expect(find.text('STATS-11'), findsOneWidget);
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-statistics-loading')),
    );
    expect(
      find.byKey(const ValueKey('batch-statistics-loading')),
      findsOneWidget,
    );

    completer.complete(_statistics());
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('batch-statistics-content')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('renders eight groups and 28 metrics at 360x800 and 200% text',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

    await _pumpDetail(tester, statistics: (_) async => _statistics());

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-statistics-content')),
    );
    for (final group in batchMetricLayout) {
      expect(
        find.byKey(ValueKey('batch-statistics-group-${group.stage}')),
        findsOneWidget,
      );
    }
    for (final code in testBatchMetricCodes) {
      expect(find.byKey(ValueKey('batch-statistic-$code')), findsOneWidget);
    }
    expect(find.text('86.10%'), findsOneWidget);
    expect(find.text('取数时间：2026-09-05 16:30:00'), findsOneWidget);
    expect(find.text('历史数据缺失'), findsWidgets);
    expect(find.text('历史销售缺少批次重量快照'), findsNothing);
    final soldWeightDetails = find.byKey(
      const ValueKey('batch-statistic-details-SOLD_WEIGHT'),
    );
    final detailList = find.byKey(const ValueKey('batch-detail-member-list'));
    for (var attempt = 0; attempt < 12; attempt++) {
      final center = tester.getCenter(soldWeightDetails);
      if (center.dy >= 0 && center.dy <= 800) break;
      await tester.drag(detailList, const Offset(0, -500));
      await tester.pump();
    }
    expect(tester.getCenter(soldWeightDetails).dy, inInclusiveRange(0, 800));
    await tester.tap(soldWeightDetails);
    await tester.pumpAndSettle();
    expect(find.textContaining('历史销售缺少批次重量快照'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('sales metrics share a wide row and flatten independently',
      (tester) async {
    Future<List<Rect>> salesRects(Size size, {double textScale = 1}) async {
      await tester.binding.setSurfaceSize(size);
      tester.platformDispatcher.textScaleFactorTestValue = textScale;
      await _pumpDetail(tester, statistics: (_) async => _statistics());
      final finders = [
        find.byKey(const ValueKey('batch-statistic-TOTAL_SALES_AMOUNT')),
        find.byKey(const ValueKey('batch-statistic-SALES_PRICE_PER_KG')),
        find.byKey(const ValueKey('batch-statistic-SALES_PRICE_PER_RABBIT')),
      ];
      await _scrollDetailUntilVisible(tester, finders.last);
      expect(finders[0], findsOneWidget);
      expect(finders[1], findsOneWidget);
      expect(finders[2], findsOneWidget);
      return finders.map(tester.getRect).toList(growable: false);
    }

    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

    final wide = await salesRects(const Size(900, 1200));
    expect(wide.map((rect) => rect.top).toSet(), hasLength(1));
    expect(wide[0].left, lessThan(wide[1].left));
    expect(wide[1].left, lessThan(wide[2].left));

    final narrow = await salesRects(const Size(412, 915));
    expect(narrow[0].top, lessThan(narrow[1].top));
    expect(narrow[1].top, lessThan(narrow[2].top));

    final enlarged = await salesRects(const Size(900, 1200), textScale: 2);
    expect(enlarged[0].top, lessThan(enlarged[1].top));
    expect(enlarged[1].top, lessThan(enlarged[2].top));
    expect(tester.takeException(), isNull);
  });

  testWidgets('statistics failure keeps the rest of the batch usable',
      (tester) async {
    await _pumpDetail(
      tester,
      statistics: (_) async => throw const ApiException('网络不可用'),
    );

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-statistics-error')),
    );
    expect(
      find.byKey(const ValueKey('batch-statistics-error')),
      findsOneWidget,
    );
    expect(find.textContaining('网络不可用'), findsOneWidget);
    expect(find.text('重试统计'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('batch-detail-member-list')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('retrying statistics reloads only the statistics request',
      (tester) async {
    var statisticsRequests = 0;
    var batchRequests = 0;

    await _pumpDetail(
      tester,
      statistics: (_) async {
        statisticsRequests++;
        throw const ApiException('网络不可用');
      },
      onBatchRequest: () => batchRequests++,
    );

    await _scrollDetailUntilVisible(tester, find.text('重试统计'));
    await tester.tap(find.text('重试统计'));
    await tester.pumpAndSettle();

    expect(statisticsRequests, 2);
    expect(batchRequests, 1);
  });

  testWidgets('refresh failure retains the last snapshot and calculated time',
      (tester) async {
    var requests = 0;
    await _pumpDetail(
      tester,
      statistics: (_) async {
        requests++;
        if (requests == 1) return _statistics();
        throw const ApiException('刷新失败');
      },
    );

    await tester.tap(find.byTooltip('刷新批次'));
    await tester.pumpAndSettle();
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-statistics-content')),
    );

    expect(requests, 2);
    expect(find.textContaining('已保留上次成功统计'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('batch-statistics-calculated-at')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('batch-statistic-MATING_DATE')),
        findsOneWidget);
  });

  testWidgets('permitted export is single-flight and uses system file sharing',
      (tester) async {
    final repository = _FakeBatchRepository();
    final sharing = _FakeFileShareService()
      ..delay = const Duration(milliseconds: 100);
    final directory = Directory.systemTemp.createTempSync('share-success-');
    final downloaded = File('${directory.path}/batch-11-statistics.xlsx')
      ..writeAsBytesSync([80, 75, 3, 4]);
    addTearDown(() {
      if (directory.existsSync()) directory.deleteSync(recursive: true);
    });
    repository.downloadFile = downloaded;
    await _pumpDetail(
      tester,
      statistics: (_) async => _statistics(),
      repository: repository,
      sharing: sharing,
    );
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-statistics-export')),
    );

    await tester.tap(find.byKey(const ValueKey('batch-statistics-export')));
    await tester.pump();
    expect(sharing.calls, 1);
    await tester.tap(
      find.byKey(const ValueKey('batch-statistics-export')),
      warnIfMissed: false,
    );
    expect(repository.downloadCalls, 1);
    await tester.pump(const Duration(milliseconds: 100));
    await tester.pumpAndSettle();

    expect(sharing.calls, 1);
    expect(sharing.file?.path, endsWith('batch-11-statistics.xlsx'));
    expect(sharing.existedDuringShare, isTrue);
    expect(downloaded.existsSync(), isFalse);
    expect(
        find.byKey(const ValueKey('batch-statistics-content')), findsOneWidget);
  });

  testWidgets(
      'share failure deletes the downloaded file and retains statistics',
      (tester) async {
    final directory = Directory.systemTemp.createTempSync('share-failure-');
    final downloaded = File('${directory.path}/batch-11-statistics.xlsx')
      ..writeAsBytesSync([80, 75, 3, 4]);
    addTearDown(() {
      if (directory.existsSync()) directory.deleteSync(recursive: true);
    });
    final repository = _FakeBatchRepository()
      ..pendingDownload = (Completer<File>()..complete(downloaded));
    final sharing = _FakeFileShareService()
      ..error = const ApiException('系统分享失败');
    await _pumpDetail(
      tester,
      statistics: (_) async => _statistics(),
      repository: repository,
      sharing: sharing,
    );
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-statistics-export')),
    );

    await tester.tap(find.byKey(const ValueKey('batch-statistics-export')));
    await tester.pumpAndSettle();

    expect(sharing.existedDuringShare, isTrue);
    expect(downloaded.existsSync(), isFalse);
    expect(find.textContaining('系统分享失败'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('batch-statistics-content')),
      findsOneWidget,
    );
  });

  testWidgets('export failure stays local and retains statistics',
      (tester) async {
    final repository = _FakeBatchRepository()
      ..downloadError = const ApiException('报表服务不可用');
    await _pumpDetail(
      tester,
      statistics: (_) async => _statistics(),
      repository: repository,
      sharing: _FakeFileShareService(),
    );
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-statistics-export')),
    );
    await tester.tap(find.byKey(const ValueKey('batch-statistics-export')));
    await tester.pumpAndSettle();

    expect(find.textContaining('报表服务不可用'), findsOneWidget);
    expect(
        find.byKey(const ValueKey('batch-statistics-content')), findsOneWidget);
  });

  testWidgets('stale house export is discarded before system sharing',
      (tester) async {
    final repository = _FakeBatchRepository();
    final sharing = _FakeFileShareService();
    final pending = Completer<File>();
    repository.pendingDownload = pending;
    final directory = Directory.systemTemp.createTempSync('share-stale-');
    final downloaded = File('${directory.path}/old-house.xlsx')
      ..writeAsBytesSync([80, 75, 3, 4]);
    final scope = ValueNotifier<({int houseId, int batchId})>(
      (houseId: 8, batchId: 11),
    );
    addTearDown(scope.dispose);
    addTearDown(() {
      if (directory.existsSync()) directory.deleteSync(recursive: true);
    });

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchRepositoryProvider.overrideWithValue(repository),
          fileShareServiceProvider.overrideWithValue(sharing),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: Scaffold(
            body: SingleChildScrollView(
              child: ValueListenableBuilder<({int houseId, int batchId})>(
                valueListenable: scope,
                builder: (_, value, __) => BatchStatisticsSection(
                  houseId: value.houseId,
                  batch: Batch(
                    id: value.batchId,
                    houseId: value.houseId,
                    batchCode: 'STATS-${value.batchId}',
                    status: '进行中',
                    startDate: null,
                    endDate: null,
                    remark: '',
                  ),
                  state: BatchStatisticsState(statistics: _statistics()),
                  canEdit: false,
                  canViewAudit: false,
                  canExport: true,
                  onRetry: () {},
                  onChanged: () async {},
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('batch-statistics-export')));
    await tester.pump();
    scope.value = (houseId: 9, batchId: 12);
    await tester.pump();
    pending.complete(downloaded);
    await tester.pumpAndSettle();

    expect(repository.downloadCalls, 1);
    expect(sharing.calls, 0);
    expect(downloaded.existsSync(), isFalse);
    expect(
      find.byKey(const ValueKey('batch-statistics-export-error')),
      findsNothing,
    );
  });

  testWidgets('carcass edit and history permissions are independent',
      (tester) async {
    await _pumpDetail(
      tester,
      statistics: (_) async => _statistics(),
      permission: const HousePermission(
        perms: 'view',
        isAdmin: false,
        permissions: ['rabbit:batches:query', 'rabbit:batches:edit'],
      ),
    );
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-carcass-yield-edit')),
    );
    expect(
      find.byKey(const ValueKey('batch-carcass-yield-edit')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('batch-carcass-yield-history')),
      findsNothing,
    );

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();
    await _pumpDetail(
      tester,
      statistics: (_) async => _statistics(),
      permission: const HousePermission(
        perms: 'view',
        isAdmin: false,
        permissions: ['rabbit:batches:query', 'rabbit:audit:list'],
      ),
    );
    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-carcass-yield-history')),
    );
    expect(
      find.byKey(const ValueKey('batch-carcass-yield-edit')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('batch-carcass-yield-history')),
      findsOneWidget,
    );
  });

  testWidgets('permission-specific statistics controls stay hidden',
      (tester) async {
    await _pumpDetail(
      tester,
      statistics: (_) async => _statistics(),
      permission: const HousePermission(
        perms: 'view',
        isAdmin: false,
        permissions: ['rabbit:batches:query'],
      ),
    );

    await _scrollDetailUntilVisible(
      tester,
      find.byKey(const ValueKey('batch-statistics-content')),
    );
    expect(
        find.byKey(const ValueKey('batch-carcass-yield-edit')), findsNothing);
    expect(
      find.byKey(const ValueKey('batch-carcass-yield-history')),
      findsNothing,
    );
    expect(find.byKey(const ValueKey('batch-statistics-export')), findsNothing);
  });
}

Future<void> _pumpDetail(
  WidgetTester tester, {
  required Future<BatchStatistics> Function(CancelToken token) statistics,
  bool settle = true,
  VoidCallback? onBatchRequest,
  HousePermission permission = const HousePermission(
    perms: 'control',
    isAdmin: false,
    permissions: [
      'rabbit:batches:query',
      'rabbit:batches:edit',
      'rabbit:audit:list',
      'rabbit:reports:export',
    ],
  ),
  BatchRepository? repository,
  FileShareService? sharing,
}) async {
  const request = BatchDetailRequest(houseId: 8, batchId: 11);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        if (repository != null)
          batchRepositoryProvider.overrideWithValue(repository),
        if (sharing != null)
          fileShareServiceProvider.overrideWithValue(sharing),
        batchDetailProvider(request).overrideWith((_) async {
          onBatchRequest?.call();
          return const Batch(
            id: 11,
            houseId: 8,
            batchCode: 'STATS-11',
            status: '进行中',
            startDate: null,
            endDate: null,
            remark: '',
          );
        }),
        batchStatisticsProvider(request).overrideWith(
          (_) => BatchStatisticsController(load: statistics),
        ),
        batchMembersProvider(request).overrideWith((_) async => const []),
        pendingWeaningRecordsProvider(request)
            .overrideWith((_) async => const []),
        housePermissionProvider(8).overrideWith((_) async => permission),
      ],
      child: MaterialApp(
        theme: buildAppTheme(),
        home: const HouseBatchDetailScreen(houseId: 8, batchId: 11),
      ),
    ),
  );
  await tester.pump();
  if (settle) await tester.pumpAndSettle();
}

BatchStatistics _statistics() => BatchStatistics.fromJson(
      testStatisticsPayload(missingSoldWeight: true),
    );

class _FakeBatchRepository extends BatchRepository {
  _FakeBatchRepository()
      : super(
          ApiClient(
            SessionStore(),
            appBuildLoader: () async => '4020',
          ),
        );

  var downloadCalls = 0;
  Completer<File>? pendingDownload;
  File? downloadFile;
  Object? downloadError;

  @override
  Future<File> downloadBatchStatistics({
    required int houseId,
    required int batchId,
    Directory? directory,
  }) {
    downloadCalls++;
    final error = downloadError;
    if (error != null) return Future<File>.error(error);
    return pendingDownload?.future ??
        Future<File>.value(
          downloadFile ?? File('/tmp/batch-$batchId-statistics.xlsx'),
        );
  }
}

class _FakeFileShareService implements FileShareService {
  var calls = 0;
  var existedDuringShare = false;
  File? file;
  Object? error;
  Duration delay = Duration.zero;

  @override
  Future<void> shareSpreadsheet(File file) async {
    calls++;
    this.file = file;
    existedDuringShare = file.existsSync();
    final failure = error;
    if (failure != null) throw failure;
    if (delay > Duration.zero) await Future<void>.delayed(delay);
  }
}

Future<void> _scrollDetailUntilVisible(
  WidgetTester tester,
  Finder target,
) async {
  final list = find.byKey(const ValueKey('batch-detail-member-list'));
  for (var attempt = 0; attempt < 10 && target.evaluate().isEmpty; attempt++) {
    await tester.drag(list, const Offset(0, -260));
    await tester.pump();
  }
  if (target.evaluate().isEmpty) {
    fail('Expected batch statistics control was not built');
  }
  await tester.ensureVisible(target);
  await tester.pump();
}
