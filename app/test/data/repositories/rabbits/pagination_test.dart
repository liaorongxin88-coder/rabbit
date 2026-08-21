import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';

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

  test('breeding rabbit pages keep the server-side type filter', () async {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'owner',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'owner-token'});
    final adapter = _PagedRabbitsAdapter(total: 201);
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final client = ApiClient(SessionStore(), dio: dio);
    addTearDown(client.dispose);

    final rabbits =
        await RabbitRepository(client).listAllActiveBreedingRabbits(8);

    expect(rabbits, hasLength(201));
    expect(adapter.requestedPages, [1, 2]);
    expect(adapter.requestedTypes, everyElement('0'));
  });

  test('rabbit detail loads by id with the selected house context', () async {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'owner',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'owner-token'});
    final adapter = _RabbitDetailAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final client = ApiClient(SessionStore(), dio: dio);
    addTearDown(client.dispose);

    final rabbit = await RabbitRepository(client).getRabbit(
      houseId: 8,
      rabbitId: 31,
    );

    expect(adapter.requestedHouseId, '8');
    expect(rabbit.id, 31);
    expect(rabbit.isActive, isFalse);
    expect(rabbit.cageId, 12);
  });

  test('rabbit batch memberships send house context and active filter',
      () async {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'owner',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'owner-token'});
    final adapter = _RabbitMembershipsAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final client = ApiClient(SessionStore(), dio: dio);
    addTearDown(client.dispose);
    final repository = RabbitRepository(client);

    final active = await repository.listRabbitBatchMemberships(
      houseId: 8,
      rabbitId: 31,
    );
    final inactive = await repository.listRabbitBatchMemberships(
      houseId: 8,
      rabbitId: 31,
      active: false,
    );

    expect(adapter.requestedActive, [true, false]);
    expect(adapter.requestedHouseIds, ['8', '8']);
    expect(active, hasLength(1));
    expect(active.single.batchId, 61);
    expect(active.single.rabbitId, 31);
    expect(active.single.isActive, isTrue);
    expect(active.single.currentStage, 'AWAIT_PALPATION');
    expect(active.single.currentCycleId, 701);
    expect(active.single.batchRole, 'breeding');
    expect(active.single.joinDate, DateTime(2025, 8, 1));
    expect(active.single.nextEventDate, DateTime(2025, 8, 20));
    expect(active.single.nextEventType, '摸胎');
    expect(inactive.single.isActive, isFalse);
    expect(inactive.single.exitDate, DateTime(2025, 8, 18));
  });
}

class _RabbitDetailAdapter implements HttpClientAdapter {
  String? requestedHouseId;

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    expect(options.path, '/api/rabbits/31');
    expect(options.method, 'GET');
    requestedHouseId = options.headers['X-House-Id']?.toString();
    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': {
          'id': 31,
          'houseId': 8,
          'cageId': 12,
          'motherId': null,
          'type': '2',
          'gender': '0',
          'breed': '新西兰白兔',
          'arrivalMethod': '0',
          'arrivalDate': 1751328000000,
          'weight': 2.5,
          'isActive': false,
        },
      }),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }
}

class _RabbitMembershipsAdapter implements HttpClientAdapter {
  final requestedActive = <bool>[];
  final requestedHouseIds = <String>[];

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    expect(options.path, '/api/rabbits/31/batch-memberships');
    final active = options.queryParameters['active'] == true;
    requestedActive.add(active);
    requestedHouseIds.add(options.headers['X-House-Id'].toString());

    final data = active
        ? [
            {
              'batchId': 61,
              'rabbitId': 31,
              'isActive': true,
              'joinDate': '2025-08-01',
              'exitDate': null,
              'currentStage': 'AWAIT_PALPATION',
              'currentCycleId': 701,
              'batchRole': 'breeding',
              'nextEventDate': '2025-08-20',
              'nextEventType': '摸胎',
            },
            {
              'batchId': 0,
              'rabbitId': 31,
              'isActive': true,
            },
          ]
        : [
            {
              'batchId': 60,
              'rabbitId': 31,
              'isActive': false,
              'joinDate': '2025-07-01',
              'exitDate': '2025-08-18',
              'currentStage': 'RETIRED',
              'currentCycleId': null,
              'batchRole': 'breeding',
              'nextEventDate': null,
              'nextEventType': null,
            },
          ];
    return ResponseBody.fromString(
      jsonEncode({'code': 0, 'message': 'ok', 'data': data}),
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
  final requestedTypes = <String?>[];

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
    requestedTypes.add(options.queryParameters['type']?.toString());

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
