import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/batches/carcass_yield.dart';

import '../../../domain/batches/statistics_fixture.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({'userId': 7, 'userName': 'owner'});
    FlutterSecureStorage.setMockInitialValues({'token': 'owner-token'});
  });

  test('statistics request sends auth, house, and actual app build headers',
      () async {
    final adapter = _BatchAdapter();
    final client = _client(adapter);
    addTearDown(client.dispose);

    final statistics = await BatchRepository(client).getBatchStatistics(
      houseId: 8,
      batchId: 11,
    );

    expect(statistics.metrics, hasLength(28));
    expect(statistics.metrics.first.code, 'MATING_DATE');
    expect(statistics.metrics.last.code, 'CARCASS_YIELD_RATE');
    final request = adapter.requests.single;
    expect(request.path, '/api/batches/11/statistics');
    _expectBusinessHeaders(request);
  });

  test('protected XLSX download uses business headers and temporary file',
      () async {
    final adapter = _BatchAdapter();
    final client = _client(adapter);
    final directory = await Directory.systemTemp.createTemp('rabbit-xlsx-');
    addTearDown(client.dispose);
    addTearDown(() => directory.delete(recursive: true));

    final file = await BatchRepository(client).downloadBatchStatistics(
      houseId: 8,
      batchId: 11,
      directory: directory,
    );

    expect(file.path, endsWith('批次-STATS-11-统计.xlsx'));
    expect(await file.readAsBytes(), [80, 75, 3, 4]);
    final request = adapter.requests.single;
    expect(request.path, '/api/reports/batches/11/statistics.xlsx');
    _expectBusinessHeaders(request);
  });

  test('protected XLSX falls back when the response has no filename', () async {
    final adapter = _BatchAdapter(downloadMode: _DownloadMode.noFilename);
    final client = _client(adapter);
    final directory = await Directory.systemTemp.createTemp('rabbit-xlsx-');
    addTearDown(client.dispose);
    addTearDown(() => directory.delete(recursive: true));

    final file = await BatchRepository(client).downloadBatchStatistics(
      houseId: 8,
      batchId: 11,
      directory: directory,
    );

    expect(file.path, endsWith('batch-11-statistics.xlsx'));
  });

  test('protected XLSX keeps hostile filenames inside the target directory',
      () async {
    final adapter = _BatchAdapter(downloadMode: _DownloadMode.hostileFilename);
    final client = _client(adapter);
    final directory = await Directory.systemTemp.createTemp('rabbit-xlsx-');
    addTearDown(client.dispose);
    addTearDown(() => directory.delete(recursive: true));

    final file = await BatchRepository(client).downloadBatchStatistics(
      houseId: 8,
      batchId: 11,
      directory: directory,
    );

    expect(file.parent.path, directory.path);
    expect(file.path, endsWith('outside.xlsx'));
  });

  for (final mode in [
    _DownloadMode.invalidMime,
    _DownloadMode.empty,
    _DownloadMode.businessError,
  ]) {
    test('$mode rejects invalid downloads and removes partial files', () async {
      final adapter = _BatchAdapter(downloadMode: mode);
      final client = _client(adapter);
      final directory = await Directory.systemTemp.createTemp('rabbit-xlsx-');
      addTearDown(client.dispose);
      addTearDown(() => directory.delete(recursive: true));

      await expectLater(
        BatchRepository(client).downloadBatchStatistics(
          houseId: 8,
          batchId: 11,
          directory: directory,
        ),
        throwsA(isA<ApiException>()),
      );
      expect(directory.listSync(), isEmpty);
    });
  }

  test('protected XLSX 401 emits the shared unauthorized event', () async {
    final adapter = _BatchAdapter(downloadMode: _DownloadMode.unauthorized);
    final client = _client(adapter);
    final directory = await Directory.systemTemp.createTemp('rabbit-xlsx-');
    addTearDown(client.dispose);
    addTearDown(() => directory.delete(recursive: true));
    final unauthorized = expectLater(client.unauthorizedEvents, emits(null));

    await expectLater(
      BatchRepository(client).downloadBatchStatistics(
        houseId: 8,
        batchId: 11,
        directory: directory,
      ),
      throwsA(isA<ApiException>()),
    );
    await unauthorized;
    expect(directory.listSync(), isEmpty);
  });

  test('carcass yield create and paginated history follow backend contract',
      () async {
    final adapter = _BatchAdapter();
    final client = _client(adapter);
    final repository = BatchRepository(client);
    addTearDown(client.dispose);

    final created = await repository.createCarcassYield(
      houseId: 8,
      batchId: 11,
      draft: BatchCarcassYieldDraft(
        yieldRate: 0.56,
        sourceUnit: '测试屠宰场',
        measuredDate: DateTime(2024, 8, 1),
        reportNumber: 'REPORT-1',
        evidenceFileId: 'file-1',
        remark: '首份报告',
        changeReason: '首次录入',
        requestId: 'yield-request-1',
      ),
    );
    final page = await repository.listCarcassYields(
      houseId: 8,
      batchId: 11,
      page: 2,
      pageSize: 10,
    );

    expect(created.yieldRate, 0.56);
    expect(page.items.single.id, 91);
    expect(page.page, 2);
    final post = adapter.requests[0];
    expect(post.data, containsPair('requestId', 'yield-request-1'));
    expect(post.data, containsPair('yieldRate', 0.56));
    final get = adapter.requests[1];
    expect(get.queryParameters, {'page': 2, 'pageSize': 10});
    _expectBusinessHeaders(post);
    _expectBusinessHeaders(get);
  });
}

ApiClient _client(HttpClientAdapter adapter) {
  return ApiClient(
    SessionStore(),
    dio: Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter,
    appBuildLoader: () async => '4020',
  );
}

void _expectBusinessHeaders(RequestOptions request) {
  expect(request.headers['Authorization'], 'Bearer owner-token');
  expect(request.headers['X-House-Id'], '8');
  expect(request.headers['X-App-Build'], '4020');
}

enum _DownloadMode {
  success,
  noFilename,
  hostileFilename,
  invalidMime,
  empty,
  businessError,
  unauthorized,
}

class _BatchAdapter implements HttpClientAdapter {
  _BatchAdapter({this.downloadMode = _DownloadMode.success});

  final _DownloadMode downloadMode;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    if (options.path.endsWith('.xlsx')) {
      if (downloadMode == _DownloadMode.businessError) {
        return ResponseBody.fromString(
          jsonEncode({'code': 403, 'message': '无报表导出权限'}),
          200,
          headers: {
            Headers.contentTypeHeader: [Headers.jsonContentType],
          },
        );
      }
      if (downloadMode == _DownloadMode.unauthorized) {
        return ResponseBody.fromString(
          jsonEncode({'message': '登录已失效，请重新登录'}),
          401,
          headers: {
            Headers.contentTypeHeader: [Headers.jsonContentType],
          },
        );
      }
      final contentType = downloadMode == _DownloadMode.invalidMime
          ? 'text/plain'
          : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
      final headers = <String, List<String>>{
        Headers.contentTypeHeader: [contentType],
      };
      if (downloadMode == _DownloadMode.success) {
        headers['content-disposition'] = [
          'attachment; filename="batch-STATS-11-statistics.xlsx"; '
              "filename*=UTF-8''%E6%89%B9%E6%AC%A1-STATS-11-%E7%BB%9F%E8%AE%A1.xlsx",
        ];
      } else if (downloadMode == _DownloadMode.hostileFilename) {
        headers['content-disposition'] = [
          "attachment; filename*=UTF-8''..%2F..%2Foutside.xlsx",
        ];
      }
      return ResponseBody.fromBytes(
        downloadMode == _DownloadMode.empty ? [] : [80, 75, 3, 4],
        200,
        headers: headers,
      );
    }
    if (options.path.endsWith('/carcass-yields')) {
      if (options.method == 'POST') {
        return _json(_carcassRecord());
      }
      return _json({
        'items': [_carcassRecord()],
        'total': 11,
        'page': 2,
        'pageSize': 10,
      });
    }
    return _json(_statisticsPayload());
  }

  ResponseBody _json(Object data) => ResponseBody.fromString(
        jsonEncode({'code': 0, 'message': 'ok', 'data': data}),
        200,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );

  Map<String, Object?> _carcassRecord() => {
        'id': 91,
        'houseId': 8,
        'batchId': 11,
        'yieldRate': 0.56,
        'sourceUnit': '测试屠宰场',
        'measuredDate': '2024-08-01',
        'reportNumber': 'REPORT-1',
        'evidenceFileId': 'file-1',
        'remark': '首份报告',
        'changeReason': '首次录入',
        'requestId': 'yield-request-1',
        'createdBy': 7,
        'createdByName': '操作员',
        'createdAt': '2026-09-05T08:30:00Z',
      };

  @override
  void close({bool force = false}) {}
}

Map<String, Object?> _statisticsPayload() => testStatisticsPayload();
