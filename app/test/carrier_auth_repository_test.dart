import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/auth_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';
import 'package:rabbit_flutter/src/domain/models/auth_session.dart';
import 'package:rabbit_flutter/src/domain/models/carrier_auth.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
  });

  test('HTTPS one-tap login sends the provider token and idempotency key',
      () async {
    final adapter = _CapturingAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final repository = AuthRepository(ApiClient(SessionStore(), dio: dio));

    final session = await repository.loginWithCarrier(
      const CarrierAuthCredential(
        provider: 'test-carrier',
        accessToken: 'short-lived-token',
      ),
      requestId: 'one-tap-request-1',
    );

    expect(adapter.path, '/api/auth/phone-one-tap-login');
    expect(adapter.body, {
      'provider': 'test-carrier',
      'accessToken': 'short-lived-token',
      'requestId': 'one-tap-request-1',
    });
    expect(session.userId, 42);
    expect(session.houseId, 0);
  });

  test('HTTP one-tap login is rejected before sending the carrier token',
      () async {
    final adapter = _CapturingAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'http://rabbit.test'))
      ..httpClientAdapter = adapter;
    final repository = AuthRepository(ApiClient(SessionStore(), dio: dio));

    await expectLater(
      Future<AuthSession>.sync(
        () => repository.loginWithCarrier(
          const CarrierAuthCredential(
            provider: 'test-carrier',
            accessToken: 'must-not-leave-device',
          ),
          requestId: 'blocked-http-request',
        ),
      ),
      throwsA(
        isA<ApiException>().having(
          (error) => error.message,
          'message',
          '一键登录仅支持安全的 HTTPS 服务地址',
        ),
      ),
    );
    expect(adapter.calls, 0);
    expect(adapter.body, isNull);
  });
}

class _CapturingAdapter implements HttpClientAdapter {
  int calls = 0;
  String? path;
  Map<String, dynamic>? body;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    calls += 1;
    path = options.path;
    body = Map<String, dynamic>.from(options.data as Map);
    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': {
          'token': 'server-session-token',
          'userId': 42,
          'userName': 'phone_user',
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
