import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('listBatches loads every page instead of stopping at the first 20',
      () async {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'owner',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'owner-token'});
    final adapter = _PagedBatchesAdapter(total: 401);
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final client = ApiClient(SessionStore(), dio: dio);
    addTearDown(client.dispose);

    final batches = await BatchRepository(client).listBatches(8);

    expect(batches, hasLength(401));
    expect(batches.first.id, 1);
    expect(batches.last.id, 401);
    expect(adapter.requestedPages, [1, 2, 3]);
    expect(adapter.requestedPageSizes, everyElement(200));
  });
}

class _PagedBatchesAdapter implements HttpClientAdapter {
  _PagedBatchesAdapter({required this.total});

  final int total;
  final requestedPages = <int>[];
  final requestedPageSizes = <int>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    expect(options.path, '/api/batches');
    expect(options.headers['Authorization'], 'Bearer owner-token');
    expect(options.headers['X-House-Id'], '8');
    final page = _asInt(options.queryParameters['page']);
    final pageSize = _asInt(options.queryParameters['pageSize']);
    requestedPages.add(page);
    requestedPageSizes.add(pageSize);

    final start = (page - 1) * pageSize;
    final remaining = total - start;
    final count = remaining <= 0
        ? 0
        : remaining < pageSize
            ? remaining
            : pageSize;
    final data = List.generate(count, (index) {
      final id = start + index + 1;
      return {
        'id': id,
        'houseId': 8,
        'batchCode': 'B-${id.toString().padLeft(4, '0')}',
        'status': id.isEven ? '进行中' : '计划中',
        'startDate': '2026-08-14T00:00:00',
        'endDate': null,
        'remark': '分页测试 Batch $id',
      };
    });

    return ResponseBody.fromString(
      jsonEncode({'code': 0, 'message': 'ok', 'data': data}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  int _asInt(Object? value) {
    if (value is num) {
      return value.toInt();
    }
    return int.parse(value.toString());
  }

  @override
  void close({bool force = false}) {}
}
