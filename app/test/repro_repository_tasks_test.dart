import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  test('listTasks serializes includeFuture without changing existing filters',
      () async {
    final adapter = _TaskQueryAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final client = ApiClient(SessionStore(), dio: dio);
    addTearDown(client.dispose);
    final repository = ReproRepository(client);
    final dueBefore = DateTime(2026, 8, 20, 12);

    await repository.listTasks(
      houseId: 8,
      dueBefore: dueBefore,
      taskType: 'MATING',
      batchId: 61,
      cageId: 11,
      rabbitId: 31,
      page: 2,
      size: 80,
    );
    await repository.listTasks(
      houseId: 8,
      rabbitId: 31,
      includeFuture: true,
    );

    expect(adapter.queries, hasLength(2));
    expect(adapter.queries[0], {
      'dueBefore': dueBefore.millisecondsSinceEpoch,
      'includeFuture': false,
      'type': 'MATING',
      'batchId': 61,
      'cageId': 11,
      'rabbitId': 31,
      'page': 2,
      'size': 80,
    });
    expect(adapter.queries[1]['includeFuture'], isTrue);
    expect(adapter.queries[1]['rabbitId'], 31);
    expect(adapter.houseIds, ['8', '8']);
  });
}

class _TaskQueryAdapter implements HttpClientAdapter {
  final queries = <Map<String, dynamic>>[];
  final houseIds = <String>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    expect(options.path, '/api/tasks');
    queries.add(Map<String, dynamic>.from(options.queryParameters));
    houseIds.add(options.headers['X-House-Id'].toString());
    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': {
          'items': <Object>[],
          'total': 0,
          'page': 1,
          'size': 50,
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
