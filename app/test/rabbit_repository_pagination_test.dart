import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('listRabbits loads every page instead of silently stopping at 50',
      () async {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'owner',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'owner-token'});
    final adapter = _PagedRabbitsAdapter(total: 401);
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final client = ApiClient(SessionStore(), dio: dio);
    addTearDown(client.dispose);

    final rabbits = await RabbitRepository(client).listRabbits(8);

    expect(rabbits, hasLength(401));
    expect(rabbits.first.id, 1);
    expect(rabbits.last.id, 401);
    expect(adapter.requestedPages, [1, 2, 3]);
    expect(adapter.requestedPageSizes, everyElement(200));
  });

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

    final cages = await RabbitRepository(client).listCages(8);

    // 停用笼位以前在仓库层被默默丢掉，分层地图于是凭空少一个位置。
    expect(cages.map((cage) => cage.id), [1, 2]);
    expect(cages.last.isEnabled, isFalse);
    // id 异常的脏数据仍然要挡掉。
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
          'cageNumber': 'R1-C1-L1',
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
          'cageNumber': 'R1-C2-L1',
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

class _PagedRabbitsAdapter implements HttpClientAdapter {
  _PagedRabbitsAdapter({required this.total});

  final int total;
  final requestedPages = <int>[];
  final requestedPageSizes = <int>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    expect(options.path, '/api/rabbits');
    expect(options.queryParameters['active'], isTrue);
    final page = _asInt(options.queryParameters['page']);
    final pageSize = _asInt(options.queryParameters['pageSize']);
    requestedPages.add(page);
    requestedPageSizes.add(pageSize);

    final start = (page - 1) * pageSize;
    final remaining = total - start;
    final count = remaining <= 0
        ? 0
        : remaining < pageSize
            ? remaining
            : pageSize;
    final data = List.generate(count, (index) {
      final id = start + index + 1;
      return {
        'id': id,
        'houseId': 8,
        'cageId': id,
        'motherId': null,
        'type': '2',
        'gender': id.isEven ? '0' : '1',
        'breed': '新西兰白兔',
        'arrivalMethod': 'SELF_BRED',
        'arrivalDate': 1751328000000,
        'weight': 2.5,
        'isActive': true,
      };
    });

    return ResponseBody.fromString(
      jsonEncode({'code': 0, 'message': 'ok', 'data': data}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  int _asInt(Object? value) {
    if (value is num) {
      return value.toInt();
    }
    return int.parse(value.toString());
  }

  @override
  void close({bool force = false}) {}
}
