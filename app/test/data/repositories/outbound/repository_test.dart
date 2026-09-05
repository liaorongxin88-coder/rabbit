import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/outbound/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/outbound/workflow.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('submit sends required common price and batch/unassigned weights',
      () async {
    SharedPreferences.setMockInitialValues({'userId': 7, 'userName': 'sale'});
    FlutterSecureStorage.setMockInitialValues({'token': 'sale-token'});
    final adapter = _OutboundAdapter();
    final client = ApiClient(
      SessionStore(),
      dio: Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
        ..httpClientAdapter = adapter,
      appBuildLoader: () async => '4020',
    );
    addTearDown(client.dispose);

    await OutboundRepository(client).submit(
      houseId: 8,
      task: _task,
      items: const [
        OutboundSelectedItem(
          rabbitId: 1,
          stateVersion: 4,
          selectionType: 'NORMAL',
        ),
        OutboundSelectedItem(
          rabbitId: 2,
          stateVersion: 7,
          selectionType: 'EARLY_SALE',
          earlySaleReason: '客户提前采购',
        ),
      ],
      requestId: 'outbound-request-1',
      saleTime: DateTime(2026, 9, 5),
      totalWeight: 6.5,
      unitPrice: 18.25,
      batchAllocations: const [
        OutboundBatchAllocation(batchId: 101, actualWeightKg: 3.2),
        OutboundBatchAllocation(batchId: null, actualWeightKg: 3.3),
      ],
    );

    final request = adapter.request!;
    expect(request.path, '/api/outbound/tasks/task-1/submit');
    expect(request.headers['Authorization'], 'Bearer sale-token');
    expect(request.headers['X-House-Id'], '8');
    expect(request.headers['X-App-Build'], '4020');
    expect(request.data.containsKey('unitPrice'), isFalse);
    expect(request.data['unitPricePerKg'], 18.25);
    expect(request.data['batchAllocations'], [
      {'batchId': 101, 'actualWeightKg': 3.2},
      {'batchId': null, 'actualWeightKg': 3.3},
    ]);
    expect(request.data['earlySaleReasons'], {'2': '客户提前采购'});
  });

  test('server draft saves and restores batch allocation weights', () async {
    SharedPreferences.setMockInitialValues({'userId': 7, 'userName': 'sale'});
    FlutterSecureStorage.setMockInitialValues({'token': 'sale-token'});
    final adapter = _OutboundAdapter();
    final client = ApiClient(
      SessionStore(),
      dio: Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
        ..httpClientAdapter = adapter,
      appBuildLoader: () async => '4020',
    );
    addTearDown(client.dispose);

    final saved = await OutboundRepository(client).saveDraft(
      houseId: 8,
      task: _task,
      status: 'WAITING_CONFIRMATION',
      items: const [],
      saleTime: DateTime(2026, 9, 5),
      totalWeight: 6.5,
      unitPrice: 18.25,
      batchAllocations: const [
        OutboundBatchAllocation(batchId: 101, actualWeightKg: 3.2),
        OutboundBatchAllocation(batchId: null, actualWeightKg: 3.3),
      ],
    );

    final request = adapter.request!;
    expect(request.method, 'PUT');
    expect(request.path, '/api/outbound/tasks/task-1');
    expect(request.data.containsKey('unitPrice'), isFalse);
    expect(request.data['unitPricePerKg'], 18.25);
    expect(request.data['batchAllocations'], [
      {'batchId': 101, 'actualWeightKg': 3.2},
      {'batchId': null, 'actualWeightKg': 3.3},
    ]);
    expect(
        saved.batchAllocations.map((item) => item.key), ['101', 'unassigned']);
    expect(
      saved.batchAllocations.map((item) => item.actualWeightKg),
      [3.2, 3.3],
    );
    expect(saved.unitPrice, 18.25);
  });

  test('server draft round-trips empty and partial allocation lists', () async {
    for (final allocations in <List<OutboundBatchAllocation>>[
      const [],
      const [
        OutboundBatchAllocation(batchId: 101, actualWeightKg: 3.2),
      ],
    ]) {
      SharedPreferences.setMockInitialValues({'userId': 7, 'userName': 'sale'});
      FlutterSecureStorage.setMockInitialValues({'token': 'sale-token'});
      final adapter = _OutboundAdapter();
      final client = ApiClient(
        SessionStore(),
        dio: Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
          ..httpClientAdapter = adapter,
        appBuildLoader: () async => '4020',
      );
      addTearDown(client.dispose);

      final saved = await OutboundRepository(client).saveDraft(
        houseId: 8,
        task: _task,
        status: 'WAITING_CONFIRMATION',
        items: const [],
        saleTime: DateTime(2026, 9, 5),
        totalWeight: null,
        unitPrice: null,
        batchAllocations: allocations,
      );

      expect(
        (adapter.request!.data as Map)['batchAllocations'],
        allocations.map((item) => item.toJson()).toList(),
      );
      expect(saved.batchAllocations, hasLength(allocations.length));
      if (allocations.isNotEmpty) {
        expect(saved.batchAllocations.single.key, '101');
        expect(saved.batchAllocations.single.actualWeightKg, 3.2);
      }
    }
  });
}

const _task = OutboundTask(
  taskId: 'task-1',
  houseId: 8,
  entryType: 'HOUSE',
  status: 'WAITING_CONFIRMATION',
  revision: 2,
  resumed: false,
  summary: OutboundSummary(
    normal: 1,
    earlySale: 1,
    needsAction: 0,
    blocked: 0,
  ),
  rabbits: [],
  selectedItems: [],
);

class _OutboundAdapter implements HttpClientAdapter {
  RequestOptions? request;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    request = options;
    final data = options.method == 'PUT'
        ? {
            ..._task.toJson(),
            'revision': 3,
            'unitPricePerKg': (options.data as Map)['unitPricePerKg'],
            'batchAllocations':
                (options.data as Map)['batchAllocations'] as Object,
          }
        : {
            'status': 'COMPLETED',
            'requestId': 'outbound-request-1',
            'taskId': 'task-1',
            'saleOrderId': 91,
            'rabbitCount': 2,
            'cageCount': 1,
            'rowCount': 1,
            'totalWeight': 6.5,
            'totalAmount': 118.63,
            'message': '完成',
            'conflicts': <Object>[],
          };
    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': data,
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
