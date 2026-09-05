import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/carcass_yield.dart';
import 'package:rabbit_flutter/src/ui/batches/sheets/carcass_yield.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';

void main() {
  testWidgets('carcass yield keeps requestId for unchanged retry at 200% text',
      (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final repository = _FakeBatchRepository()
      ..saveError = const ApiException('网络暂不可用');

    await tester.pumpWidget(_app(repository, history: false));
    await tester.tap(find.byKey(const ValueKey('open-carcass-yield')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('carcass-yield-percent')),
      '56',
    );
    await tester.enterText(
      find.byKey(const ValueKey('carcass-yield-source')),
      '测试屠宰场',
    );
    await tester.tap(find.byKey(const ValueKey('carcass-yield-submit')));
    await tester.pumpAndSettle();

    expect(find.textContaining('网络暂不可用'), findsOneWidget);
    final firstRequestId = repository.drafts.single.requestId;
    repository.saveError = null;
    await tester.tap(find.byKey(const ValueKey('carcass-yield-submit')));
    await tester.pumpAndSettle();

    expect(repository.drafts, hasLength(2));
    expect(repository.drafts.last.requestId, firstRequestId);
    expect(repository.drafts.last.yieldRate, 0.56);
    expect(repository.drafts.last.sourceUnit, '测试屠宰场');
    expect(find.byKey(const ValueKey('carcass-yield-percent')), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('correction requires an explicit change reason', (tester) async {
    final repository = _FakeBatchRepository();
    await tester.pumpWidget(
      _app(repository, history: false, hasExistingValue: true),
    );
    await tester.tap(find.byKey(const ValueKey('open-carcass-yield')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('carcass-yield-percent')),
      '56',
    );
    await tester.enterText(
      find.byKey(const ValueKey('carcass-yield-source')),
      '测试屠宰场',
    );
    await tester.tap(find.byKey(const ValueKey('carcass-yield-submit')));
    await tester.pumpAndSettle();

    expect(find.text('请填写修改说明'), findsOneWidget);
    expect(repository.drafts, isEmpty);
  });

  testWidgets('history pagination preserves order and retries the failed page',
      (tester) async {
    final repository = _FakeBatchRepository()
      ..historyPages[1] = BatchCarcassYieldPage(
        items: [_record, _olderRecord],
        total: 21,
        page: 1,
        pageSize: 20,
      )
      ..historyPages[2] = BatchCarcassYieldPage(
        items: [_olderRecord],
        total: 21,
        page: 2,
        pageSize: 20,
      )
      ..historyErrors[2] = const ApiException('历史暂不可用');
    await tester.pumpWidget(_app(repository, history: true));
    await tester.tap(find.byKey(const ValueKey('open-carcass-yield')));
    await tester.pumpAndSettle();

    final latest = find.textContaining('56.00% · 测试屠宰场');
    final older = find.textContaining('55.00% · 旧屠宰场');
    expect(tester.getTopLeft(latest).dy, lessThan(tester.getTopLeft(older).dy));

    await tester.tap(find.byTooltip('下一页'));
    await tester.pumpAndSettle();
    expect(repository.historyRequests, [1, 2]);
    expect(find.text('版本历史读取失败，重试'), findsOneWidget);

    repository.historyErrors.remove(2);
    await tester.tap(find.text('版本历史读取失败，重试'));
    await tester.pumpAndSettle();
    expect(repository.historyRequests, [1, 2, 2]);
    expect(find.textContaining('第 2/2 页 · 共 21 条'), findsOneWidget);
    expect(older, findsOneWidget);

    await tester.tap(find.byTooltip('上一页'));
    await tester.pumpAndSettle();
    expect(repository.historyRequests, [1, 2, 2, 1]);
    expect(find.textContaining('第 1/2 页 · 共 21 条'), findsOneWidget);
  });

  testWidgets('history shows immutable version details', (tester) async {
    final repository = _FakeBatchRepository();
    await tester.pumpWidget(_app(repository, history: true));
    await tester.tap(find.byKey(const ValueKey('open-carcass-yield')));
    await tester.pumpAndSettle();

    expect(find.text('出肉率版本历史'), findsOneWidget);
    expect(find.textContaining('56.00% · 测试屠宰场'), findsOneWidget);
    expect(find.textContaining('首次录入'), findsOneWidget);
    expect(find.textContaining('操作员'), findsOneWidget);
    expect(find.textContaining('2026-09-05 16:00'), findsOneWidget);
    expect(find.textContaining('第 1/1 页 · 共 1 条'), findsOneWidget);
  });
}

Widget _app(
  _FakeBatchRepository repository, {
  required bool history,
  bool hasExistingValue = false,
}) {
  return ProviderScope(
    overrides: [batchRepositoryProvider.overrideWithValue(repository)],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: Scaffold(
        body: Builder(
          builder: (context) => FilledButton(
            key: const ValueKey('open-carcass-yield'),
            onPressed: () {
              if (history) {
                showBatchCarcassYieldHistorySheet(
                  context: context,
                  houseId: 8,
                  batch: _batch,
                );
              } else {
                showBatchCarcassYieldSheet(
                  context: context,
                  houseId: 8,
                  batch: _batch,
                  hasExistingValue: hasExistingValue,
                );
              }
            },
            child: const Text('打开'),
          ),
        ),
      ),
    ),
  );
}

const _batch = Batch(
  id: 11,
  houseId: 8,
  batchCode: 'STATS-11',
  status: '进行中',
  startDate: null,
  endDate: null,
  remark: '',
);

class _FakeBatchRepository extends BatchRepository {
  _FakeBatchRepository()
      : super(
          ApiClient(
            SessionStore(),
            appBuildLoader: () async => '4020',
          ),
        );

  final drafts = <BatchCarcassYieldDraft>[];
  final historyRequests = <int>[];
  final historyPages = <int, BatchCarcassYieldPage>{};
  final historyErrors = <int, Object>{};
  Object? saveError;

  @override
  Future<BatchCarcassYieldRecord> createCarcassYield({
    required int houseId,
    required int batchId,
    required BatchCarcassYieldDraft draft,
  }) async {
    drafts.add(draft);
    final error = saveError;
    if (error != null) throw error;
    return _record;
  }

  @override
  Future<BatchCarcassYieldPage> listCarcassYields({
    required int houseId,
    required int batchId,
    int page = 1,
    int pageSize = 20,
    cancelToken,
  }) async {
    historyRequests.add(page);
    final error = historyErrors[page];
    if (error != null) throw error;
    return historyPages[page] ??
        BatchCarcassYieldPage(
          items: [_record],
          total: 1,
          page: page,
          pageSize: pageSize,
        );
  }
}

final _olderRecord = BatchCarcassYieldRecord(
  id: 90,
  houseId: 8,
  batchId: 11,
  yieldRate: 0.55,
  sourceUnit: '旧屠宰场',
  measuredDate: DateTime(2024, 7, 1),
  changeReason: '上次录入',
  requestId: 'yield-request-0',
  createdBy: 7,
  createdByName: '操作员',
  createdAt: DateTime.utc(2026, 9, 4, 8),
);

final _record = BatchCarcassYieldRecord(
  id: 91,
  houseId: 8,
  batchId: 11,
  yieldRate: 0.56,
  sourceUnit: '测试屠宰场',
  measuredDate: DateTime(2024, 8, 1),
  changeReason: '首次录入',
  requestId: 'yield-request-1',
  createdBy: 7,
  createdByName: '操作员',
  createdAt: DateTime.utc(2026, 9, 5, 8),
);
