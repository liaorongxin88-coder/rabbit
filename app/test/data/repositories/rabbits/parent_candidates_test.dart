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
  setUp(() {
    SharedPreferences.setMockInitialValues({'userId': 7});
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  test('parent candidate query includes active and inactive breeding rabbits',
      () async {
    final adapter = _RabbitListAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final client = ApiClient(SessionStore(), dio: dio);
    addTearDown(client.dispose);

    final rabbits = await RabbitRepository(client).listAllBreedingRabbits(8);

    expect(adapter.query['type'], '0');
    expect(adapter.query.containsKey('active'), isFalse);
    expect(rabbits.map((rabbit) => rabbit.isActive), [true, false]);
  });
}

class _RabbitListAdapter implements HttpClientAdapter {
  Map<String, dynamic> query = {};

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    query = Map<String, dynamic>.from(options.queryParameters);
    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': [
          {
            'id': 101,
            'houseId': 8,
            'cageId': 1,
            'type': '0',
            'gender': '0',
            'isActive': true,
          },
          {
            'id': 88,
            'houseId': 8,
            'cageId': 2,
            'type': '0',
            'gender': '1',
            'isActive': false,
          },
        ],
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
