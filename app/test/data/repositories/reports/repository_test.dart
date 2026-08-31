import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/reports/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'owner',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'owner-token'});
  });

  test('dashboard summary sends house header and complete report scope',
      () async {
    final adapter = _DashboardAdapter();
    final client = _client(adapter);
    addTearDown(client.dispose);

    final summary = await ReportRepository(client).loadDashboardSummary(
      houseId: 8,
      batchId: 11,
      year: 2026,
    );

    expect(summary.selectedHouseId, 8);
    expect(summary.selectedBatchId, 11);
    expect(summary.year, 2026);
    expect(adapter.requests, 1);
  });

  test('dashboard summary exposes backend business errors', () async {
    final client = _client(_DashboardAdapter(error: true));
    addTearDown(client.dispose);

    await expectLater(
      ReportRepository(client).loadDashboardSummary(
        houseId: 8,
        batchId: 99,
        year: 2026,
      ),
      throwsA(
        isA<ApiException>()
            .having((error) => error.businessCode, 'businessCode', 400)
            .having(
              (error) => error.message,
              'message',
              '批次不属于当前兔舍',
            ),
      ),
    );
  });
}

ApiClient _client(HttpClientAdapter adapter) {
  return ApiClient(
    SessionStore(),
    dio: Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter,
  );
}

class _DashboardAdapter implements HttpClientAdapter {
  _DashboardAdapter({this.error = false});

  final bool error;
  int requests = 0;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests += 1;
    expect(options.method, 'GET');
    expect(options.path, '/api/reports/dashboard');
    expect(options.headers['Authorization'], 'Bearer owner-token');
    expect(options.headers['X-House-Id'], '8');
    expect(options.queryParameters, {
      'houseId': 8,
      'batchId': error ? 99 : 11,
      'year': 2026,
    });

    final body = error
        ? {
            'code': 400,
            'message': '批次不属于当前兔舍',
            'data': null,
          }
        : {
            'code': 0,
            'message': 'ok',
            'data': {
              'selectedHouseId': 8,
              'selectedBatchId': 11,
              'houseCount': 1,
              'year': 2026,
              'totalRabbits': 12,
              'monthlyBirths': <int>[],
              'monthlyWeaned': <int>[],
            },
          };
    return ResponseBody.fromString(
      jsonEncode(body),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
