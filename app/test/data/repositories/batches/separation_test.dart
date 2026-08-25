import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/batches/weaning.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  test('separation sends allocation, optional parents, and parses replay',
      () async {
    final adapter = _SeparationAdapter();
    final repository = _repository(adapter);

    final result = await repository.separatePendingWeaning(
      houseId: 8,
      batchId: 20,
      weaningRecordId: 501,
      allocations: const [
        CageAllocation(
          cageId: 12,
          count: 4,
          maleCount: 2,
          femaleCount: 2,
        ),
      ],
      motherRabbitId: 101,
      fatherRabbitId: 88,
      requestId: 'stable-separation-request',
    );

    expect(adapter.path, '/api/batches/20/weaning-records/501/separation');
    expect(adapter.headers['X-House-Id'], '8');
    expect(adapter.body, {
      'allocations': [
        {'cageId': 12, 'count': 4, 'maleCount': 2, 'femaleCount': 2},
      ],
      'motherRabbitId': 101,
      'fatherRabbitId': 88,
      'requestId': 'stable-separation-request',
    });
    expect(result.separatedCount, 4);
    expect(result.waitingCount, 2);
    expect(result.generatedRabbitIds, [9001, 9002, 9003, 9004]);
    expect(result.replayed, isTrue);
  });

  test('unselected parents and unknown genders are omitted', () async {
    final adapter = _SeparationAdapter();
    final repository = _repository(adapter);

    await repository.separatePendingWeaning(
      houseId: 8,
      batchId: 20,
      weaningRecordId: 501,
      allocations: const [CageAllocation(cageId: 12, count: 1)],
      requestId: 'count-only-request',
    );

    expect(adapter.body['allocations'], [
      {'cageId': 12, 'count': 1},
    ]);
    expect(adapter.body.containsKey('motherRabbitId'), isFalse);
    expect(adapter.body.containsKey('fatherRabbitId'), isFalse);
    expect(
      (adapter.body['allocations'] as List).single.containsKey('maleCount'),
      isFalse,
    );
  });
}

BatchRepository _repository(_SeparationAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);
  return BatchRepository(client);
}

class _SeparationAdapter implements HttpClientAdapter {
  String path = '';
  Map<String, dynamic> headers = {};
  Map<String, dynamic> body = {};

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    path = options.path;
    headers = Map<String, dynamic>.from(options.headers);
    body = Map<String, dynamic>.from(options.data as Map);
    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': {
          'weaningRecordId': 501,
          'separatedCount': 4,
          'waitingCount': 2,
          'generatedRabbitIds': [9001, 9002, 9003, 9004],
          'replayed': true,
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
