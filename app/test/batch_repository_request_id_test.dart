import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/repro_repository.dart';
import 'package:rabbit_flutter/src/domain/models/repro_task.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/batch_providers.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  test('generic batch binding sends rabbitIds for commodity members', () async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);

    await repository.addBatchRabbits(
      houseId: 8,
      batchId: 9,
      rabbitIds: const [402, 401, 402],
      requestId: 'bind-rabbit-request',
    );

    expect(adapter.requests.single.path, '/api/batches/9/members');
    expect(adapter.requests.single.headers['X-House-Id'], '8');
    expect(adapter.requests.single.body['rabbitIds'], [401, 402]);
    expect(
      adapter.requests.single.body['requestId'],
      'bind-rabbit-request',
    );
    expect(
        adapter.requests.single.body.containsKey('femaleRabbitIds'), isFalse);
  });

  test('batch tag removal sends a stable request id as query', () async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);

    await repository.removeBatchRabbit(
      houseId: 8,
      batchId: 9,
      rabbitId: 401,
      requestId: 'remove-tag-request',
    );

    expect(adapter.requests.single.path, '/api/batches/9/members/401');
    expect(adapter.requests.single.headers['X-House-Id'], '8');
    expect(
      adapter.requests.single.query['requestId'],
      'remove-tag-request',
    );
    expect(adapter.requests.single.body, isEmpty);
  });

  test('all Batch writes forward an explicit requestId', () async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);
    final repro = _repro(adapter);

    await repository.createBatch(
      houseId: 8,
      batchCode: ' B-1000 ',
      femaleRabbitIds: const [102, 101, 102],
      requestId: 'create-request',
    );
    await repro.applyAction(
      houseId: 8,
      cycleId: 301,
      action: ReproAction.mating,
      maleRabbitId: 201,
      matingMethod: MatingMethod.natural,
      occurredAt: DateTime(2026, 8, 14),
      requestId: 'mating-request',
    );
    await repro.bulkApply(
      houseId: 8,
      action: ReproAction.mating,
      taskIds: const [703, 701, 702, 701],
      maleRabbitId: 201,
      occurredAt: DateTime(2026, 8, 14),
      requestId: 'bulk-mating-request',
    );
    await repro.applyAction(
      houseId: 8,
      cycleId: 301,
      action: ReproAction.estrus,
      occurredAt: DateTime(2026, 8, 14),
      requestId: 'estrus-request',
    );
    await repository.addBatchMembers(
      houseId: 8,
      batchId: 9,
      femaleRabbitIds: const [104, 102, 104],
      requestId: 'add-members-request',
    );
    await repository.completeBatch(
      houseId: 8,
      batchId: 9,
      endDate: DateTime(2026, 8, 14),
      force: true,
      requestId: 'complete-request',
    );
    await repro.applyAction(
      houseId: 8,
      cycleId: 301,
      action: ReproAction.palpation,
      palpationResult: PalpationResult.pregnant,
      occurredAt: DateTime(2026, 8, 14),
      requestId: 'pregnancy-request',
    );
    await repro.applyAction(
      houseId: 8,
      cycleId: 301,
      action: ReproAction.prepartum,
      occurredAt: DateTime(2026, 8, 14),
      requestId: 'prepartum-request',
    );
    await repro.applyAction(
      houseId: 8,
      cycleId: 301,
      action: ReproAction.delivery,
      outcome: 'BORN',
      totalKits: 8,
      liveKits: 7,
      occurredAt: DateTime(2026, 8, 14),
      requestId: 'parturition-request',
    );
    await repro.applyAction(
      houseId: 8,
      cycleId: 301,
      action: ReproAction.weaning,
      weanedCount: 7,
      occurredAt: DateTime(2026, 8, 14),
      requestId: 'weaning-request',
    );
    await repository.submitSale(
      houseId: 8,
      batchId: 9,
      rabbitIds: const [401, 402],
      saleDate: DateTime(2026, 8, 14),
      requestId: 'sale-request',
    );

    expect(
      adapter.requests.map((request) => request.body['requestId']),
      [
        'create-request',
        'mating-request',
        'bulk-mating-request',
        'estrus-request',
        'add-members-request',
        'complete-request',
        'pregnancy-request',
        'prepartum-request',
        'parturition-request',
        'weaning-request',
        'sale-request',
      ],
    );
    expect(
      adapter.requests.map((request) => request.path),
      [
        '/api/batches',
        // 六个生产动作共用同一个写入口：这正是 doe-breeding-v2 重构的目的。
        // 旧实现在这里是六条不同的 URL，每条自带一套校验与状态机。
        '/api/repro/cycles/301/actions',
        '/api/repro/tasks/bulk-actions',
        '/api/repro/cycles/301/actions',
        '/api/batches/9/members',
        '/api/batches/9/complete',
        '/api/repro/cycles/301/actions',
        '/api/repro/cycles/301/actions',
        '/api/repro/cycles/301/actions',
        '/api/repro/cycles/301/actions',
        '/api/batches/9/sale',
      ],
    );
    expect(adapter.requests.first.body,
        containsPair('femaleRabbitIds', [101, 102]));
    expect(
        adapter.requests[4].body, containsPair('femaleRabbitIds', [102, 104]));
    // 批量目标去重并排序：重复的 taskId 不能变成两次推进。
    expect(adapter.requests[2].body, containsPair('taskIds', [701, 702, 703]));
    expect(adapter.requests[1].body, containsPair('maleRabbitId', 201));
    expect(adapter.requests[1].body, containsPair('action', 'MATING'));
    for (final request in adapter.requests) {
      expect(request.headers['Authorization'], 'Bearer operator-token');
      expect(request.headers['X-House-Id'], '8');
    }
  });

  test('same failed payload reuses requestId and changed payload rotates it',
      () async {
    final adapter = _CapturingAdapter(failFirstRequest: true);
    final controller = BatchWriteRequestController(
      requestId: 'stable-draft-request',
      requestIdFactory: () => 'changed-payload-request',
    );

    Future<void> submit(int totalKits) => _repro(adapter).applyAction(
          houseId: 8,
          cycleId: 301,
          action: ReproAction.delivery,
          outcome: 'BORN',
          occurredAt: DateTime(2026, 8, 14),
          totalKits: totalKits,
          liveKits: 8,
          requestId: controller.requestIdFor(
            canonicalBatchWriteFingerprint({
              'action': 'parturition',
              'houseId': 8,
              'batchId': 9,
              'rabbitId': 101,
              'birthDate': '2026-08-14',
              'totalKits': totalKits,
              'liveKits': 8,
            }),
          ),
        );

    await expectLater(submit(8), throwsA(isA<ApiException>()));
    await submit(8);
    await submit(9);

    expect(adapter.requests, hasLength(3));
    expect(adapter.requests[0].body['requestId'], 'stable-draft-request');
    expect(adapter.requests[1].body['requestId'], 'stable-draft-request');
    expect(adapter.requests[2].body['requestId'], 'changed-payload-request');
  });

  test('canonical payload ignores text padding and id selection order', () {
    var sequence = 0;
    final controller = BatchWriteRequestController(
      requestIdFactory: () => 'request-${++sequence}',
    );

    final first = controller.requestIdFor(canonicalBatchWriteFingerprint({
      'action': 'createBatch',
      'houseId': 8,
      'batchCode': ' B-1000 ',
      'femaleRabbitIds': [103, 101, 103, 102],
      'remark': '  夏季批次 ',
    }));
    final equivalent = controller.requestIdFor(canonicalBatchWriteFingerprint({
      'remark': '夏季批次',
      'femaleRabbitIds': [102, 101, 103],
      'batchCode': 'B-1000',
      'houseId': 8,
      'action': 'createBatch',
    }));
    final changedIds = controller.requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': 'createBatch',
        'houseId': 8,
        'batchCode': 'B-1000',
        'femaleRabbitIds': [101, 102, 104],
        'remark': '夏季批次',
      }),
    );
    final changedBatch = controller.requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': 'createBatch',
        'houseId': 8,
        'batchCode': 'B-1001',
        'femaleRabbitIds': [101, 102, 104],
        'remark': '夏季批次',
      }),
    );
    final changedAction = controller.requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': 'finishAphrodisiac',
        'houseId': 8,
        'batchCode': 'B-1001',
        'femaleRabbitIds': [101, 102, 104],
        'remark': '夏季批次',
      }),
    );

    expect(first, 'request-1');
    expect(equivalent, first);
    expect(changedIds, 'request-2');
    expect(changedBatch, 'request-3');
    expect(changedAction, 'request-4');
  });

  test('explicitly starting a new draft clears the payload binding', () {
    var sequence = 0;
    final controller = BatchWriteRequestController(
      requestIdFactory: () => 'request-${++sequence}',
    );
    final fingerprint = canonicalBatchWriteFingerprint({'action': 'mating'});

    expect(controller.requestIdFor(fingerprint), 'request-1');
    expect(controller.startNewDraft(), 'request-2');
    expect(controller.requestIdFor(fingerprint), 'request-2');
  });

  test('callers that omit a requestId still get a fresh one each time',
      () async {
    final adapter = _CapturingAdapter();
    final repro = _repro(adapter);

    await repro.applyAction(
      houseId: 8,
      cycleId: 301,
      action: ReproAction.mating,
      maleRabbitId: 201,
      occurredAt: DateTime(2026, 8, 14),
    );
    await repro.applyAction(
      houseId: 8,
      cycleId: 302,
      action: ReproAction.mating,
      maleRabbitId: 201,
      occurredAt: DateTime(2026, 8, 14),
    );

    final requestIds = adapter.requests
        .map((request) => request.body['requestId'])
        .cast<String>()
        .toList();
    expect(requestIds, everyElement(isNotEmpty));
    expect(requestIds.toSet(), hasLength(2));
  });
}

BatchRepository _repository(_CapturingAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);
  return BatchRepository(client);
}

ReproRepository _repro(_CapturingAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);
  return ReproRepository(client);
}

class _CapturedRequest {
  const _CapturedRequest({
    required this.path,
    required this.headers,
    required this.query,
    required this.body,
  });

  final String path;
  final Map<String, dynamic> headers;
  final Map<String, dynamic> query;
  final Map<String, dynamic> body;
}

class _CapturingAdapter implements HttpClientAdapter {
  _CapturingAdapter({this.failFirstRequest = false});

  final bool failFirstRequest;
  final requests = <_CapturedRequest>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(_CapturedRequest(
      path: options.path,
      headers: Map<String, dynamic>.from(options.headers),
      query: Map<String, dynamic>.from(options.queryParameters),
      body: options.data is Map
          ? Map<String, dynamic>.from(options.data as Map)
          : const <String, dynamic>{},
    ));
    if (failFirstRequest && requests.length == 1) {
      throw DioException.connectionError(
        requestOptions: options,
        reason: 'fixture connection loss',
      );
    }
    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': options.path == '/api/batches'
            ? {
                'id': 9,
                'houseId': 8,
                'batchCode': 'B-1000',
                'status': '进行中',
              }
            : null,
      }),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
