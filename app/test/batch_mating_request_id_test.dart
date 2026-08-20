import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/reproduction/sheets/event.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  testWidgets(
      'bulk mating keeps the requestId for retry and rotates it after edits',
      (tester) async {
    final adapter = _BulkMatingAdapter(failuresBeforeSuccess: 2);
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final client = ApiClient(SessionStore(), dio: dio);
    addTearDown(client.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          reproRepositoryProvider.overrideWithValue(ReproRepository(client)),
          allActiveHouseRabbitsProvider(8).overrideWith(
            (_) async => const [
              Rabbit(
                id: 201,
                houseId: 8,
                cageId: 1,
                motherId: null,
                type: '0',
                gender: '1',
                breed: '新西兰白',
                arrivalMethod: 'self_bred',
                arrivalDate: null,
                weight: null,
                isActive: true,
              ),
              Rabbit(
                id: 202,
                houseId: 8,
                cageId: 2,
                motherId: null,
                type: '0',
                gender: '1',
                breed: '加州',
                arrivalMethod: 'self_bred',
                arrivalDate: null,
                weight: null,
                isActive: true,
              ),
            ],
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: ElevatedButton(
                  key: const ValueKey('open-bulk-mating'),
                  onPressed: () => showBatchMatingSheet(
                    context: context,
                    houseId: 8,
                    batchId: 9,
                    rabbitIds: const [101, 102],
                    requestId: 'initial-bulk-request',
                  ),
                  child: const Text('打开批量配种'),
                ),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('open-bulk-mating')));
    await tester.pumpAndSettle();

    final submit = find.byKey(const ValueKey('batch-mating-confirm'));
    await tester.tap(submit);
    await tester.pumpAndSettle();
    await tester.tap(submit);
    await tester.pumpAndSettle();

    final secondMale = find.byKey(const ValueKey('batch-mating-male-202'));
    await tester.ensureVisible(secondMale);
    await tester.tap(secondMale);
    await tester.pump();
    await tester.tap(submit);
    await tester.pumpAndSettle();

    expect(adapter.requests, hasLength(3));
    final firstId = adapter.requests[0]['requestId'];
    expect(firstId, 'initial-bulk-request');
    expect(adapter.requests[1]['requestId'], firstId);
    expect(adapter.requests[2]['requestId'], isNot(firstId));
    expect(
        adapter.requests.map((body) => body['maleRabbitId']), [201, 201, 202]);
    expect(find.text('批量配种'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}

/// 批量配种现在是两步：先拉取该批次的待配种任务，再按任务 id 批量提交。
/// [requests] 只记录真正的写请求，因为本用例要盯的是写请求的 requestId 行为。
class _BulkMatingAdapter implements HttpClientAdapter {
  _BulkMatingAdapter({required this.failuresBeforeSuccess});

  final int failuresBeforeSuccess;
  final requests = <Map<String, dynamic>>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.path.startsWith('/api/tasks')) {
      return _json({
        'total': 2,
        'page': 1,
        'size': 50,
        'items': [
          {
            'id': 701,
            'taskType': 'MATING',
            'taskLabel': '待配种',
            'action': 'MATING',
            'cycleId': 301,
            'rabbitId': 101,
          },
          {
            'id': 702,
            'taskType': 'MATING',
            'taskLabel': '待配种',
            'action': 'MATING',
            'cycleId': 302,
            'rabbitId': 102,
          },
        ],
      });
    }

    requests.add(Map<String, dynamic>.from(options.data as Map));
    if (requests.length <= failuresBeforeSuccess) {
      throw DioException.connectionError(
        requestOptions: options,
        reason: 'fixture connection loss',
      );
    }
    return _json({'total': 2, 'succeeded': 2, 'failed': 0, 'items': []});
  }

  static ResponseBody _json(Object? data) {
    return ResponseBody.fromString(
      jsonEncode({'code': 0, 'message': 'ok', 'data': data}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
