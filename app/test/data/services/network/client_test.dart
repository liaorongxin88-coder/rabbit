import 'dart:convert';
import 'dart:io';
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

  test('downloadFile rejects a JSON error payload', () async {
    final client = _clientFor(
      statusCode: 200,
      body: {'code': 404, 'message': '安装包不存在或尚未发布', 'data': null},
    );
    final file = File('${Directory.systemTemp.path}/rabbit-download-test.apk');
    addTearDown(() {
      if (file.existsSync()) {
        file.deleteSync();
      }
    });

    await expectLater(
      client.downloadFile('/api/app/updates/missing/apk', savePath: file.path),
      throwsA(isA<ApiException>()),
    );

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
  const _JsonResponseAdapter({
    required this.statusCode,
    required this.body,
  });

  final int statusCode;
  final Map<String, Object?> body;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
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
