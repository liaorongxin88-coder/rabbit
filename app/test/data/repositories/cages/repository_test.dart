import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/cages/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('listCages keeps disabled cages so the map matches the real rack',
      () async {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'owner',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'owner-token'});
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = _CagesAdapter();
    final client = ApiClient(SessionStore(), dio: dio);
    addTearDown(client.dispose);

    final cages = await CageRepository(client).listCages(8);

    expect(cages.map((cage) => cage.id), [1, 2]);
    expect(cages.last.isEnabled, isFalse);
    expect(cages.where((cage) => cage.id <= 0), isEmpty);
  });
}

class _CagesAdapter implements HttpClientAdapter {
  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final payload = jsonEncode({
      'code': 0,
      'message': 'ok',
      'data': [
        {
          'id': 1,
          'houseId': 8,
          'cageNumber': '1-1-1',
          'rowCode': 'R1',
          'layerIndex': 1,
          'positionIndex': 1,
          'status': '0',
          'rabbitCount': 0,
          'isEnabled': true,
          'isFed': true,
        },
        {
          'id': 2,
          'houseId': 8,
          'cageNumber': '1-2-1',
          'rowCode': 'R1',
          'layerIndex': 1,
          'positionIndex': 2,
          'status': '0',
          'rabbitCount': 0,
          'isEnabled': false,
          'isFed': true,
        },
        {
          'id': 0,
          'houseId': 8,
          'cageNumber': '脏数据',
          'status': '0',
          'rabbitCount': 0,
          'isEnabled': true,
        },
      ],
    });
    return ResponseBody.fromString(
      payload,
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }
}
