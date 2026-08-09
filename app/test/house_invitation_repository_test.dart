import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';

void main() {
  test('phone invitation sends scoped request without account discovery',
      () async {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'owner',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'owner-token'});
    final adapter = _CapturingAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final client = ApiClient(SessionStore(), dio: dio);
    addTearDown(client.dispose);

    await HouseRepository(client).inviteMember(
      houseId: 8,
      phone: '13800138000',
      role: 'STAFF',
    );

    final request = adapter.request;
    expect(request.path, '/api/house-invitations');
    expect(request.headers['Authorization'], 'Bearer owner-token');
    expect(request.headers['X-House-Id'], '8');
    final body = Map<String, dynamic>.from(request.data as Map);
    expect(body['phone'], '13800138000');
    expect(body['role'], 'STAFF');
    expect(
        body['requestId'],
        isA<String>().having(
          (value) => value.isNotEmpty,
          'non-empty UUID',
          isTrue,
        ));
    expect(body.keys, containsAll(<String>['phone', 'role', 'requestId']));
    expect(body.keys, hasLength(3));
  });
}

class _CapturingAdapter implements HttpClientAdapter {
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
        'data': {'status': 'PENDING', 'role': 'STAFF'},
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
