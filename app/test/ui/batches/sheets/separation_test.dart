import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/weaning.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/sheets/separation.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({'userId': 7, 'userName': 'tester'});
    FlutterSecureStorage.setMockInitialValues({'token': 'test-token'});
  });

  testWidgets('shows loading, empty, and loading error states', (tester) async {
    final batches = Completer<List<Batch>>();
    await tester.pumpWidget(_testApp(loadBatches: () => batches.future));
    await _open(tester, settle: false);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    batches.complete(const <Batch>[]);
    await tester.pumpAndSettle();
    expect(find.text('没有可分笼的批次'), findsOneWidget);

    await tester.tap(find.byTooltip('关闭'));
    await tester.pumpAndSettle();
    await tester.pumpWidget(
      _testApp(loadBatches: () => Future.error('批次网络失败')),
    );
    await _open(tester);
    await tester.pumpAndSettle();
    expect(find.textContaining('生产资料加载失败'), findsOneWidget);
    expect(find.byKey(const ValueKey('production-retry')), findsOneWidget);
  });

  testWidgets('cage-first flow filters batches and records and preselects cage',
      (tester) async {
    await tester.pumpWidget(_testApp());
    await _open(tester);

    final batch = find.byKey(const ValueKey('production-batch'));
    await tester.tap(batch);
    await tester.pumpAndSettle();
    expect(find.text('OPEN-20'), findsWidgets);
    expect(find.text('DONE-21'), findsNothing);
    expect(find.text('OTHER-22'), findsNothing);
    await tester.tap(find.text('OPEN-20').last);
    await tester.pumpAndSettle();

    final record = find.byKey(const ValueKey('production-weaning-record'));
    await tester.tap(record);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('production-record-option-501')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('production-record-option-500')),
      findsNothing,
    );
    await tester.tap(find.text('母兔 #101 · 待分 6 只').last);
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('production-current-cage')),
      findsOneWidget,
    );
    expect(find.textContaining('当前笼位：C-01'), findsOneWidget);
    expect(find.byKey(const ValueKey('production-male-count')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('production-female-count')),
      findsOneWidget,
    );
    expect(find.text('不关联'), findsNWidgets(2));

    await _tapVisible(tester, 'production-father');
    await tester.pumpAndSettle();
    expect(find.text('兔 #88 · 已离场 · 推荐'), findsOneWidget);
  });

  testWidgets('weaning timestamp uses the Shanghai farm date', (tester) async {
    await tester.pumpWidget(
      _testApp(
        initialBatchId: 20,
        initialRecord: _utcBoundaryRecord,
        records: [_utcBoundaryRecord],
      ),
    );
    await _open(tester);

    expect(find.textContaining('2026-03-09'), findsOneWidget);
    expect(find.textContaining('2026-03-08'), findsNothing);
  });

  testWidgets('partial separation validates genders and sends selected parents',
      (tester) async {
    final adapter = _SeparationAdapter();
    await tester.pumpWidget(
      _testApp(
        adapter: adapter,
        initialBatchId: 20,
        initialRecord: _pendingRecord,
      ),
    );
    await _open(tester);

    await _replaceText(tester, 'production-count', '4');
    await _replaceText(tester, 'production-male-count', '3');
    await _replaceText(tester, 'production-female-count', '2');
    await _tapVisible(tester, 'production-submit');
    await tester.pumpAndSettle();
    expect(find.textContaining('之和必须等于'), findsOneWidget);
    expect(adapter.requests, isEmpty);

    await _replaceText(tester, 'production-male-count', '2');
    await _tapVisible(tester, 'production-mother');
    await tester.pumpAndSettle();
    await tester.tap(find.text('兔 #101 · 在场 · 推荐').last);
    await tester.pumpAndSettle();
    await _tapVisible(tester, 'production-father');
    await tester.pumpAndSettle();
    await tester.tap(find.text('兔 #88 · 已离场 · 推荐').last);
    await tester.pumpAndSettle();

    await _tapVisible(tester, 'production-submit');
    await tester.pump();
    await _waitForRequests(tester, adapter, 1);

    final body = adapter.requests.single;
    expect(body['motherRabbitId'], 101);
    expect(body['fatherRabbitId'], 88);
    expect(body['allocations'], [
      {'cageId': 12, 'count': 4, 'maleCount': 2, 'femaleCount': 2},
    ]);
  });

  testWidgets(
      'unknown remaining genders use count-only and parents default null',
      (tester) async {
    final adapter = _SeparationAdapter();
    await tester.pumpWidget(
      _testApp(
        adapter: adapter,
        initialBatchId: 20,
        initialRecord: _unknownGenderRecord,
        records: const [_unknownGenderRecord],
      ),
    );
    await _open(tester);

    expect(find.byKey(const ValueKey('production-male-count')), findsNothing);
    expect(find.byKey(const ValueKey('production-female-count')), findsNothing);
    await _replaceText(tester, 'production-count', '2');
    await _tapVisible(tester, 'production-submit');
    await tester.pump();
    await _waitForRequests(tester, adapter, 1);

    final body = adapter.requests.single;
    expect(body.containsKey('motherRabbitId'), isFalse);
    expect(body.containsKey('fatherRabbitId'), isFalse);
    expect(body['allocations'], [
      {'cageId': 12, 'count': 2},
    ]);
  });

  testWidgets('duplicate submit is ignored while request is in flight',
      (tester) async {
    final response = Completer<void>();
    final adapter = _SeparationAdapter(blockFirst: response.future);
    await tester.pumpWidget(
      _testApp(
        adapter: adapter,
        initialBatchId: 20,
        initialRecord: _pendingRecord,
      ),
    );
    await _open(tester);

    final submit = find.byKey(const ValueKey('production-submit'));
    await tester.ensureVisible(submit);
    await tester.pump();
    final onPressed = tester.widget<ElevatedButton>(submit).onPressed!;
    onPressed();
    onPressed();
    await tester.pump();
    await _waitForRequests(tester, adapter, 1);
    expect(adapter.requests, hasLength(1));

    response.complete();
    await tester.pumpAndSettle();
  });

  testWidgets('failed retry keeps request id and field change rotates it',
      (tester) async {
    final adapter = _SeparationAdapter(failures: const [503, 503]);
    await tester.pumpWidget(
      _testApp(
        adapter: adapter,
        initialBatchId: 20,
        initialRecord: _pendingRecord,
      ),
    );
    await _open(tester);

    await _submitAndWait(tester, adapter, 1);
    await _submitAndWait(tester, adapter, 2);
    expect(adapter.requestIds[1], adapter.requestIds[0]);

    await _replaceText(tester, 'production-count', '4');
    await _replaceText(tester, 'production-male-count', '2');
    await _replaceText(tester, 'production-female-count', '2');
    await _submitAndWait(tester, adapter, 3);
    expect(adapter.requestIds[2], isNot(adapter.requestIds[1]));
  });

  testWidgets('409 refreshes form values and waits for explicit resubmit',
      (tester) async {
    final adapter = _SeparationAdapter(failures: const [409]);
    var cageLoads = 0;
    var recordLoads = 0;
    await tester.pumpWidget(
      _testApp(
        adapter: adapter,
        initialBatchId: 20,
        initialRecord: _pendingRecord,
        loadCages: () async {
          cageLoads += 1;
          return const [_targetCage];
        },
        loadRecords: () async {
          recordLoads += 1;
          return recordLoads == 1
              ? const [_pendingRecord]
              : const [_refreshedRecord];
        },
      ),
    );
    await _open(tester);
    final initialCageLoads = cageLoads;
    final initialRecordLoads = recordLoads;

    await _submitAndWait(tester, adapter, 1);
    expect(find.textContaining('数据已刷新，请确认后再次提交'), findsOneWidget);
    expect(cageLoads, greaterThan(initialCageLoads));
    expect(recordLoads, greaterThan(initialRecordLoads));
    expect(adapter.requests, hasLength(1));
    expect(
      tester
          .widget<TextField>(
            find.byKey(const ValueKey('production-count')),
          )
          .controller
          ?.text,
      '4',
    );
    expect(
      tester
          .widget<TextField>(
            find.byKey(const ValueKey('production-male-count')),
          )
          .controller
          ?.text,
      '2',
    );
    expect(
      tester
          .widget<TextField>(
            find.byKey(const ValueKey('production-female-count')),
          )
          .controller
          ?.text,
      '2',
    );

    await _submitAndWait(tester, adapter, 2);
    expect(adapter.requestIds[1], isNot(adapter.requestIds[0]));
  });

  testWidgets('form fits common screens and keyboard insets', (tester) async {
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetViewInsets);

    for (final size in const [Size(360, 800), Size(412, 915)]) {
      tester.view.physicalSize = size;
      await tester.pumpWidget(
        _testApp(initialBatchId: 20, initialRecord: _pendingRecord),
      );
      await _open(tester);
      final count = find.byKey(const ValueKey('production-count'));
      await tester.ensureVisible(count);
      await tester.tap(count);
      tester.view.viewInsets = const FakeViewPadding(bottom: 300);
      await tester.pumpAndSettle();
      final submit = find.byKey(const ValueKey('production-submit'));
      await tester.ensureVisible(submit);
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull, reason: '$size with keyboard');
      final rect = tester.getRect(submit);
      expect(rect.bottom, lessThanOrEqualTo(size.height - 300));

      tester.view.viewInsets = FakeViewPadding.zero;
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();
    }
  });
}

Widget _testApp({
  _SeparationAdapter? adapter,
  int? initialBatchId,
  PendingWeaningRecord? initialRecord,
  List<PendingWeaningRecord> records = const [_emptyRecord, _pendingRecord],
  Future<List<Batch>> Function()? loadBatches,
  Future<List<Cage>> Function()? loadCages,
  Future<List<PendingWeaningRecord>> Function()? loadRecords,
}) {
  final network = adapter ?? _SeparationAdapter();
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = network;
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);
  const request = BatchDetailRequest(houseId: 8, batchId: 20);

  return ProviderScope(
    overrides: [
      batchRepositoryProvider.overrideWithValue(BatchRepository(client)),
      houseBatchesProvider(8).overrideWith(
        (_) =>
            loadBatches?.call() ??
            Future.value(const [_openBatch, _completedBatch, _otherBatch]),
      ),
      houseCagesProvider(8).overrideWith(
        (_) => loadCages?.call() ?? Future.value(const [_targetCage]),
      ),
      pendingWeaningRecordsProvider(request).overrideWith(
        (_) => loadRecords?.call() ?? Future.value(records),
      ),
      houseBreedingParentCandidatesProvider(8).overrideWith(
        (_) async => const [_mother, _inactiveFather, _otherHouseMother],
      ),
      houseRabbitsProvider(8).overrideWith((_) async => const <Rabbit>[]),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: Scaffold(
        body: Builder(
          builder: (context) => Center(
            child: FilledButton(
              key: const ValueKey('open-production-separation'),
              onPressed: () => showProductionWeaningSeparationSheet(
                context: context,
                houseId: 8,
                currentCage: _targetCage,
                initialBatchId: initialBatchId,
                initialRecord: initialRecord,
              ),
              child: const Text('打开'),
            ),
          ),
        ),
      ),
    ),
  );
}

Future<void> _open(
  WidgetTester tester, {
  bool settle = true,
}) async {
  await tester.tap(find.byKey(const ValueKey('open-production-separation')));
  await tester.pump();
  if (settle) {
    await tester.pumpAndSettle();
  } else {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _tapVisible(WidgetTester tester, String key) async {
  final finder = find.byKey(ValueKey(key));
  await tester.ensureVisible(finder);
  await tester.pump();
  await tester.tap(finder);
}

Future<void> _replaceText(
  WidgetTester tester,
  String key,
  String value,
) async {
  final field = find.byKey(ValueKey(key));
  await tester.ensureVisible(field);
  await tester.enterText(field, value);
  await tester.pump();
}

Future<void> _submitAndWait(
  WidgetTester tester,
  _SeparationAdapter adapter,
  int count,
) async {
  await _tapVisible(tester, 'production-submit');
  await tester.pump();
  await _waitForRequests(tester, adapter, count);
  for (var attempt = 0; attempt < 30; attempt += 1) {
    await tester.pump(const Duration(milliseconds: 20));
    final submit = find.byKey(const ValueKey('production-submit'));
    if (submit.evaluate().isEmpty ||
        tester.widget<ElevatedButton>(submit).onPressed != null) {
      return;
    }
  }
  fail('分笼请求完成后表单仍处于提交状态');
}

Future<void> _waitForRequests(
  WidgetTester tester,
  _SeparationAdapter adapter,
  int count,
) async {
  for (var attempt = 0;
      attempt < 30 && adapter.requests.length < count;
      attempt += 1) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 10)),
    );
    await tester.pump(const Duration(milliseconds: 50));
  }
  expect(adapter.requests, hasLength(count));
}

class _SeparationAdapter implements HttpClientAdapter {
  _SeparationAdapter({
    this.failures = const [],
    this.blockFirst,
  });

  final List<int> failures;
  final Future<void>? blockFirst;
  final requests = <Map<String, dynamic>>[];

  List<String> get requestIds =>
      requests.map((request) => request['requestId'] as String).toList();

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(Map<String, dynamic>.from(options.data as Map));
    if (requests.length == 1 && blockFirst != null) {
      await blockFirst;
    }
    final index = requests.length - 1;
    if (index < failures.length) {
      final status = failures[index];
      return _json(
        {'code': status, 'message': 'fixture conflict'},
        status,
        wrapped: false,
      );
    }
    return _json({
      'weaningRecordId': 501,
      'separatedCount': 4,
      'waitingCount': 2,
      'generatedRabbitIds': [9001, 9002, 9003, 9004],
      'replayed': requests.length > 1,
    }, 200);
  }

  static ResponseBody _json(
    Object? data,
    int status, {
    bool wrapped = true,
  }) {
    return ResponseBody.fromString(
      jsonEncode(
        wrapped ? {'code': 0, 'message': 'ok', 'data': data} : data,
      ),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

const _openBatch = Batch(
  id: 20,
  houseId: 8,
  batchCode: 'OPEN-20',
  status: '进行中',
  startDate: null,
  endDate: null,
  remark: '',
);

const _completedBatch = Batch(
  id: 21,
  houseId: 8,
  batchCode: 'DONE-21',
  status: '已完成',
  startDate: null,
  endDate: null,
  remark: '',
);

const _otherBatch = Batch(
  id: 22,
  houseId: 9,
  batchCode: 'OTHER-22',
  status: '进行中',
  startDate: null,
  endDate: null,
  remark: '',
);

const _targetCage = Cage(
  id: 12,
  houseId: 8,
  cageNumber: 'C-01',
  status: '3',
  rabbitCount: 2,
  isEnabled: true,
);

const _emptyRecord = PendingWeaningRecord(
  id: 500,
  batchId: 20,
  rabbitId: 100,
  weaningCount: 3,
  waitingCount: 0,
);

const _pendingRecord = PendingWeaningRecord(
  id: 501,
  batchId: 20,
  rabbitId: 101,
  breedingCycleId: 301,
  sireRabbitId: 88,
  weaningCount: 8,
  waitingCount: 6,
  maleCount: 4,
  femaleCount: 4,
  waitingMaleCount: 3,
  waitingFemaleCount: 3,
);

const _refreshedRecord = PendingWeaningRecord(
  id: 501,
  batchId: 20,
  rabbitId: 101,
  breedingCycleId: 301,
  sireRabbitId: 88,
  weaningCount: 8,
  waitingCount: 4,
  maleCount: 4,
  femaleCount: 4,
  waitingMaleCount: 2,
  waitingFemaleCount: 2,
);

final _utcBoundaryRecord = PendingWeaningRecord(
  id: 503,
  batchId: 20,
  rabbitId: 101,
  breedingCycleId: 301,
  weaningDate: DateTime.utc(2026, 3, 8, 16),
  weaningCount: 8,
  waitingCount: 4,
  waitingMaleCount: 2,
  waitingFemaleCount: 2,
);

const _unknownGenderRecord = PendingWeaningRecord(
  id: 502,
  batchId: 20,
  rabbitId: 101,
  breedingCycleId: 301,
  weaningCount: 8,
  waitingCount: 3,
);

const _mother = Rabbit(
  id: 101,
  houseId: 8,
  cageId: 1,
  motherId: null,
  type: '0',
  gender: '0',
  breed: '母系',
  arrivalMethod: '1',
  arrivalDate: null,
  weight: null,
  isActive: true,
);

const _inactiveFather = Rabbit(
  id: 88,
  houseId: 8,
  cageId: 2,
  motherId: null,
  type: '0',
  gender: '1',
  breed: '父系',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: null,
  isActive: false,
);

const _otherHouseMother = Rabbit(
  id: 999,
  houseId: 9,
  cageId: 1,
  motherId: null,
  type: '0',
  gender: '0',
  breed: '跨舍',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: null,
  isActive: true,
);
