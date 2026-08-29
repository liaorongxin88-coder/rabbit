import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/operation_events/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/operation_events/event.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('listOperationEvents sends the cursor contract and reads every field',
      () async {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'manager',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'manager-token'});
    final adapter = _OperationEventsAdapter();
    final client = ApiClient(
      SessionStore(),
      dio: Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
        ..httpClientAdapter = adapter,
    );
    addTearDown(client.dispose);

    final page = await OperationEventsRepository(client).listOperationEvents(
      houseId: 8,
      query: OperationEventsQuery(
        targetType: 'RABBIT',
        targetId: 31,
        operationCode: 'vaccination:create',
        cageId: 12,
        batchId: 61,
        occurredFrom: DateTime.fromMillisecondsSinceEpoch(1751328000000),
        occurredTo: DateTime.fromMillisecondsSinceEpoch(1751414400000),
        cursor: 'cursor-1',
        limit: 75,
      ),
    );

    expect(adapter.request.method, 'GET');
    expect(adapter.request.path, '/api/operation-events');
    expect(adapter.request.headers['Authorization'], 'Bearer manager-token');
    expect(adapter.request.headers['X-House-Id'], '8');
    expect(adapter.request.queryParameters, {
      'targetType': 'RABBIT',
      'targetId': 31,
      'operationCode': 'vaccination:create',
      'cageId': 12,
      'batchId': 61,
      'occurredFrom': 1751328000000,
      'occurredTo': 1751414400000,
      'cursor': 'cursor-1',
      'limit': 75,
    });
    expect(page.hasMore, isTrue);
    expect(page.nextCursor, 'cursor-2');
    expect(page.items, hasLength(1));

    final event = page.items.single;
    expect(event.id, 91);
    expect(event.occurredAt, DateTime.parse('2025-08-01T08:00:00Z'));
    expect(event.operationCode, 'vaccination:create');
    expect(event.eventType, 'VACCINATION');
    expect(event.eventLabel, '登记接种');
    expect(event.targetType, 'RABBIT');
    expect(event.targetId, 31);
    expect(event.cageId, 12);
    expect(event.batchId, 61);
    expect(event.rabbitId, 31);
    expect(event.cycleId, 701);
    expect(event.litterId, 801);
    expect(event.fromStage, 'AWAIT_PALPATION');
    expect(event.toStage, 'AWAIT_PREPARTUM');
    expect(event.operatorId, 7);
    expect(event.operatorName, 'manager');
  });

  test('targetId cannot be sent without targetType', () {
    const query = OperationEventsQuery(targetId: 31);

    expect(query.toQueryParameters, throwsArgumentError);
  });
}

class _OperationEventsAdapter implements HttpClientAdapter {
  late RequestOptions request;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    request = options;
    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': {
          'items': [
            {
              'id': 91,
              'occurredAt': '2025-08-01T08:00:00Z',
              'operationCode': 'vaccination:create',
              'eventType': 'VACCINATION',
              'eventLabel': '登记接种',
              'targetType': 'RABBIT',
              'targetId': 31,
              'cageId': 12,
              'batchId': 61,
              'rabbitId': 31,
              'cycleId': 701,
              'litterId': 801,
              'fromStage': 'AWAIT_PALPATION',
              'toStage': 'AWAIT_PREPARTUM',
              'operatorId': 7,
              'operatorName': 'manager',
            },
          ],
          'nextCursor': 'cursor-2',
          'hasMore': true,
        },
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
