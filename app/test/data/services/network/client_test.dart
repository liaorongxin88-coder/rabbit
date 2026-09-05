import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
  });

  test('business code 401 emits an unauthorized event', () async {
    final client = _clientFor(
      statusCode: 200,
      body: {'code': 401, 'message': '未登录', 'data': null},
    );
    var unauthorizedEvents = 0;
    final subscription = client.unauthorizedEvents.listen(
      (_) => unauthorizedEvents += 1,
    );

    await expectLater(
      client.get<void>('/protected', decode: (_) {}),
      throwsA(
        isA<ApiException>().having(
          (error) => error.businessCode,
          'businessCode',
          401,
        ),
      ),
    );

    expect(unauthorizedEvents, 1);
    await subscription.cancel();
    client.dispose();
  });

  test('HTTP 401 emits an unauthorized event', () async {
    final client = _clientFor(
      statusCode: 401,
      body: {'code': 401, 'message': '登录已失效', 'data': null},
    );
    var unauthorizedEvents = 0;
    final subscription = client.unauthorizedEvents.listen(
      (_) => unauthorizedEvents += 1,
    );

    await expectLater(
      client.get<void>('/protected', decode: (_) {}),
      throwsA(
        isA<ApiException>().having(
          (error) => error.statusCode,
          'statusCode',
          401,
        ),
      ),
    );

    expect(unauthorizedEvents, 1);
    await subscription.cancel();
    client.dispose();
  });

  test('disabled account business response emits an unauthorized event',
      () async {
    final client = _clientFor(
      statusCode: 200,
      body: {'code': 403, 'message': '账号已停用', 'data': null},
    );
    var unauthorizedEvents = 0;
    final subscription = client.unauthorizedEvents.listen(
      (_) => unauthorizedEvents += 1,
    );

    await expectLater(
      client.get<void>('/protected', decode: (_) {}),
      throwsA(
        isA<ApiException>()
            .having(
              (error) => error.businessCode,
              'businessCode',
              403,
            )
            .having(
              (error) => error.invalidatesSession,
              'invalidatesSession',
              isTrue,
            ),
      ),
    );

    expect(unauthorizedEvents, 1);
    await subscription.cancel();
    client.dispose();
  });

  test('disabled account HTTP response emits an unauthorized event', () async {
    final client = _clientFor(
      statusCode: 403,
      body: {'code': 403, 'message': '账号已停用', 'data': null},
    );
    var unauthorizedEvents = 0;
    final subscription = client.unauthorizedEvents.listen(
      (_) => unauthorizedEvents += 1,
    );

    await expectLater(
      client.get<void>('/protected', decode: (_) {}),
      throwsA(
        isA<ApiException>().having(
          (error) => error.invalidatesSession,
          'invalidatesSession',
          isTrue,
        ),
      ),
    );

    expect(unauthorizedEvents, 1);
    await subscription.cancel();
    client.dispose();
  });

  test('ordinary forbidden response does not invalidate the session', () async {
    final client = _clientFor(
      statusCode: 200,
      body: {'code': 403, 'message': '无权访问该兔舍', 'data': null},
    );
    var unauthorizedEvents = 0;
    final subscription = client.unauthorizedEvents.listen(
      (_) => unauthorizedEvents += 1,
    );

    await expectLater(
      client.get<void>('/protected', decode: (_) {}),
      throwsA(
        isA<ApiException>().having(
          (error) => error.invalidatesSession,
          'invalidatesSession',
          isFalse,
        ),
      ),
    );

    expect(unauthorizedEvents, 0);
    await subscription.cancel();
    client.dispose();
  });

  test('retries app build lookup after a transient failure', () async {
    final adapter = _JsonResponseAdapter(
      statusCode: 200,
      body: {'code': 0, 'message': 'ok', 'data': null},
    );
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    var buildLoads = 0;
    final client = ApiClient(
      SessionStore(),
      dio: dio,
      appBuildLoader: () async {
        buildLoads++;
        if (buildLoads == 1) throw StateError('platform channel unavailable');
        return '4020';
      },
    );

    await client.get<void>('/first', decode: (_) {});
    await client.get<void>('/second', decode: (_) {});

    expect(buildLoads, 2);
    expect(adapter.requests.first.headers['X-App-Build'], 'UNKNOWN');
    expect(adapter.requests.last.headers['X-App-Build'], '4020');
    client.dispose();
  });

  test('GET forwards cancellation to Dio', () async {
    final client = _clientFor(
      statusCode: 200,
      body: {'code': 0, 'message': 'ok', 'data': null},
    );
    final cancelToken = CancelToken()..cancel('page disposed');

    await expectLater(
      client.get<void>(
        '/slow',
        cancelToken: cancelToken,
        decode: (_) {},
      ),
      throwsA(isA<ApiException>()),
    );

    client.dispose();
  });
}

ApiClient _clientFor({
  required int statusCode,
  required Map<String, Object?> body,
}) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'));
  dio.httpClientAdapter = _JsonResponseAdapter(
    statusCode: statusCode,
    body: body,
  );
  return ApiClient(SessionStore(), dio: dio);
}

class _JsonResponseAdapter implements HttpClientAdapter {
  _JsonResponseAdapter({
    required this.statusCode,
    required this.body,
  });

  final int statusCode;
  final Map<String, Object?> body;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode(body),
      statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
