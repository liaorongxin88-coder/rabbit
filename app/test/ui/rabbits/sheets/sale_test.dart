import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  test('createRabbitSale sends one rabbit through the sales API', () async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);
    final saleTime = DateTime(2026, 8, 14);

    await repository.createRabbitSale(
      houseId: 8,
      rabbitId: 801,
      saleTime: saleTime,
      totalWeight: 3.25,
      unitPrice: 22.5,
      customer: '  本地采购商  ',
      remark: '  种兔更新  ',
      requestId: 'sale-request-1',
    );

    expect(adapter.requests, hasLength(1));
    final request = adapter.requests.single;
    expect(request.path, '/api/sales');
    expect(request.headers['Authorization'], 'Bearer operator-token');
    expect(request.headers['X-House-Id'], '8');
    expect(request.body, {
      'rabbitIds': [801],
      'saleTime': saleTime.millisecondsSinceEpoch,
      'totalWeight': 3.25,
      'unitPrice': 22.5,
      'customer': '本地采购商',
      'remark': '种兔更新',
      'requestId': 'sale-request-1',
    });
  });
}

RabbitRepository _repository(_CapturingAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);
  return RabbitRepository(client);
}

class _CapturedRequest {
  const _CapturedRequest({
    required this.path,
    required this.headers,
    required this.body,
  });

  final String path;
  final Map<String, dynamic> headers;
  final Map<String, dynamic> body;
}

class _CapturingAdapter implements HttpClientAdapter {
  final requests = <_CapturedRequest>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(
      _CapturedRequest(
        path: options.path,
        headers: Map<String, dynamic>.from(options.headers),
        body: Map<String, dynamic>.from(options.data as Map),
      ),
    );
    return ResponseBody.fromString(
      jsonEncode({'code': 0, 'message': 'ok', 'data': null}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
