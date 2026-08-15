import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/production_event_sheet.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

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
          batchRepositoryProvider.overrideWithValue(BatchRepository(client)),
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
    requests.add(Map<String, dynamic>.from(options.data as Map));
    if (requests.length <= failuresBeforeSuccess) {
      throw DioException.connectionError(
        requestOptions: options,
        reason: 'fixture connection loss',
      );
    }
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
