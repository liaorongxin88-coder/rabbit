import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
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

  test('all Batch writes forward an explicit requestId', () async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);

    await repository.createBatch(
      houseId: 8,
      batchCode: ' B-1000 ',
      femaleRabbitIds: const [102, 101, 102],
      requestId: 'create-request',
    );
    await repository.submitMating(
      houseId: 8,
      batchId: 9,
      femaleRabbitId: 101,
      maleRabbitId: 201,
      matingDate: DateTime(2026, 8, 14),
      requestId: 'mating-request',
    );
    await repository.submitMatingBulk(
      houseId: 8,
      batchId: 9,
      rabbitIds: const [103, 101, 102, 101],
      maleRabbitId: 201,
      matingDate: DateTime(2026, 8, 14),
      requestId: 'bulk-mating-request',
    );
    await repository.startAphrodisiac(
      houseId: 8,
      batchId: 9,
      rabbitIds: const [101, 102],
      requestId: 'aphrodisiac-start-request',
    );
    await repository.finishAphrodisiac(
      houseId: 8,
      batchId: 9,
      rabbitIds: const [101, 102],
      requestId: 'aphrodisiac-finish-request',
    );
    await repository.completeBatch(
      houseId: 8,
      batchId: 9,
      endDate: DateTime(2026, 8, 14),
      force: true,
      requestId: 'complete-request',
    );
    await repository.submitPregnancyCheck(
      houseId: 8,
      batchId: 9,
      rabbitId: 101,
      breedingCycleId: 301,
      checkDate: DateTime(2026, 8, 14),
      result: '怀孕',
      requestId: 'pregnancy-request',
    );
    await repository.submitPrepartumFinish(
      houseId: 8,
      batchId: 9,
      rabbitId: 101,
      breedingCycleId: 301,
      actionDate: DateTime(2026, 8, 14),
      requestId: 'prepartum-request',
    );
    await repository.submitParturition(
      houseId: 8,
      batchId: 9,
      rabbitId: 101,
      breedingCycleId: 301,
      birthDate: DateTime(2026, 8, 14),
      totalKits: 8,
      liveKits: 7,
      requestId: 'parturition-request',
    );
    await repository.submitWeaning(
      houseId: 8,
      batchId: 9,
      rabbitId: 101,
      breedingCycleId: 301,
      weaningDate: DateTime(2026, 8, 14),
      weaningCount: 7,
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
        'aphrodisiac-start-request',
        'aphrodisiac-finish-request',
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
        '/api/batches/9/mating',
        '/api/batches/9/mating/bulk',
        '/api/batches/9/aphrodisiac/start',
        '/api/batches/9/aphrodisiac/finish',
        '/api/batches/9/complete',
        '/api/batches/9/pregnancy-check',
        '/api/batches/9/prepartum/finish',
        '/api/batches/9/parturition',
        '/api/batches/9/weaning',
        '/api/batches/9/sale',
      ],
    );
    expect(adapter.requests[2].body,
        containsPair('femaleRabbitIds', [101, 102, 103]));
    expect(adapter.requests.first.body,
        containsPair('femaleRabbitIds', [101, 102]));
    expect(adapter.requests[2].body, containsPair('maleRabbitId', 201));
    expect(adapter.requests[2].body, containsPair('matingDate', '2026-08-14'));
    for (final request in adapter.requests) {
      expect(request.headers['Authorization'], 'Bearer operator-token');
      expect(request.headers['X-House-Id'], '8');
    }
  });

  test('same failed payload reuses requestId and changed payload rotates it',
      () async {
    final adapter = _CapturingAdapter(failFirstRequest: true);
    final repository = _repository(adapter);
    final controller = BatchWriteRequestController(
      requestId: 'stable-draft-request',
      requestIdFactory: () => 'changed-payload-request',
    );

    Future<void> submit(int totalKits) => repository.submitParturition(
          houseId: 8,
          batchId: 9,
          rabbitId: 101,
          birthDate: DateTime(2026, 8, 14),
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

  test('legacy callers still receive a fresh generated requestId', () async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);

    await repository.submitMating(
      houseId: 8,
      batchId: 9,
      femaleRabbitId: 101,
      maleRabbitId: 201,
      matingDate: DateTime(2026, 8, 14),
    );
    await repository.submitMating(
      houseId: 8,
      batchId: 9,
      femaleRabbitId: 102,
      maleRabbitId: 201,
      matingDate: DateTime(2026, 8, 14),
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

class _CapturedRequest {
  const _CapturedRequest({
    required this.path,
    required this.headers,
    required this.body,
  });

  final String path;
  final Map<String, dynamic> headers;
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
      body: Map<String, dynamic>.from(options.data as Map),
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
