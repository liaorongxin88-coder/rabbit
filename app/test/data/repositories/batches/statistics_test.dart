import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('getBatchStatistics requests the fixed batch statistics contract',
      () async {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'owner',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'owner-token'});
    final adapter = _BatchStatisticsAdapter();
    final client = ApiClient(
      SessionStore(),
      dio: Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
        ..httpClientAdapter = adapter,
    );
    addTearDown(client.dispose);

    final statistics = await BatchRepository(client).getBatchStatistics(
      houseId: 8,
      batchId: 11,
    );

    expect(statistics.totalLitters, 3);
    expect(statistics.totalKits, 28);
    expect(statistics.totalLiveKits, 26);
    expect(statistics.totalWeaned, 22);
  });
}

class _BatchStatisticsAdapter implements HttpClientAdapter {
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    expect(options.method, 'GET');
    expect(options.path, '/api/batches/11/statistics');
    expect(options.headers['Authorization'], 'Bearer owner-token');
    expect(options.headers['X-House-Id'], '8');

    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': {
          'totalLitters': 3,
          'totalKits': 28,
          'totalLiveKits': 26,
          'totalWeaned': 22,
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
