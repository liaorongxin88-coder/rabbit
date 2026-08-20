import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/ui/reproduction/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  test('rabbit repro task request is a stable house and rabbit family key', () {
    const first = RabbitReproTasksRequest(houseId: 8, rabbitId: 31);
    const same = RabbitReproTasksRequest(houseId: 8, rabbitId: 31);
    const otherRabbit = RabbitReproTasksRequest(houseId: 8, rabbitId: 32);

    expect(first, same);
    expect(first.hashCode, same.hashCode);
    expect(first, isNot(otherRabbit));
  });

  test('rabbit repro tasks load future pending tasks and keep stable ordering',
      () async {
    final adapter = _RabbitTasksAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final client = ApiClient(SessionStore(), dio: dio);
    final repository = ReproRepository(client);
    final container = ProviderContainer(
      overrides: [
        authenticatedUserIdProvider.overrideWithValue(7),
        reproRepositoryProvider.overrideWithValue(repository),
      ],
    );
    addTearDown(container.dispose);
    addTearDown(client.dispose);

    const request = RabbitReproTasksRequest(houseId: 8, rabbitId: 31);
    final subscription = container.listen(
      rabbitReproTasksProvider(request),
      (_, __) {},
    );
    addTearDown(subscription.close);

    final tasks =
        await container.read(rabbitReproTasksProvider(request).future);

    expect(tasks.map((task) => task.id), [10, 20, 30, 40]);
    expect(tasks, hasLength(4));
    expect(tasks.map((task) => task.status), everyElement('PENDING'));
    expect(adapter.query['includeFuture'], isTrue);
    expect(adapter.query['rabbitId'], 31);
    expect(adapter.query['size'], 500);
    expect(adapter.houseId, '8');
  });
}

class _RabbitTasksAdapter implements HttpClientAdapter {
  Map<String, dynamic> query = const {};
  String? houseId;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    expect(options.path, '/api/tasks');
    query = Map<String, dynamic>.from(options.queryParameters);
    houseId = options.headers['X-House-Id']?.toString();
    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': {
          'items': [
            _task(
              id: 40,
              dueDate: '2026-08-23',
              dueTime: '2026-08-23T09:00:00',
            ),
            _task(
              id: 30,
              dueDate: '2026-08-22',
              dueTime: '2026-08-22T09:00:00',
            ),
            _task(
              id: 20,
              dueDate: '2026-08-22',
              dueTime: '2026-08-22T09:00:00',
            ),
            _task(
              id: 10,
              dueDate: '2026-08-21',
              dueTime: '2026-08-22T09:00:00',
            ),
            _task(
              id: 1,
              dueDate: '2026-08-20',
              dueTime: '2026-08-20T09:00:00',
              status: 'COMPLETED',
            ),
          ],
          'total': 5,
          'page': 1,
          'size': 500,
        },
      }),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  Map<String, Object?> _task({
    required int id,
    required String dueDate,
    required String dueTime,
    String status = 'PENDING',
  }) {
    return {
      'id': id,
      'taskType': 'MATING',
      'taskLabel': '配种',
      'action': 'MATING',
      'cycleId': 701,
      'rabbitId': 31,
      'batchId': 61,
      'dueDate': dueDate,
      'dueTime': dueTime,
      'status': status,
    };
  }

  @override
  void close({bool force = false}) {}
}
