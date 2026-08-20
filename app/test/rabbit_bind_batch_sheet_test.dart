import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/bind_batch.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  test('batch purpose follows rabbit production type', () {
    expect(rabbitBatchPurposeLabel(_commodityRabbit), '养育/售卖');
    expect(rabbitBatchPurposeLabel(_breedingDoe), '繁育');
    expect(rabbitBatchPurposeLabel(_breedingBuck), isNull);
  });

  testWidgets('commodity rabbit binds to an active batch from detail flow',
      (tester) async {
    final adapter = _CapturingAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = adapter;
    final client = ApiClient(SessionStore(), dio: dio);
    addTearDown(client.dispose);
    final repository = BatchRepository(client);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          batchRepositoryProvider.overrideWithValue(repository),
          houseBatchesProvider(8).overrideWith(
            (_) async => const [
              _activeBatch,
              _otherActiveBatch,
              _completedBatch,
            ],
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: FilledButton(
                  key: const ValueKey('open-bind-batch'),
                  onPressed: () => showRabbitBindBatchSheet(
                    context: context,
                    houseId: 8,
                    rabbit: _commodityRabbit,
                    excludedBatchIds: const {11},
                  ),
                  child: const Text('绑定批次'),
                ),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('open-bind-batch')));
    await tester.pumpAndSettle();

    expect(find.text('兔 #31 · 养育/售卖'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('rabbit-bind-batch-option-9')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-bind-batch-option-10')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-bind-batch-option-11')),
      findsNothing,
    );

    await tester.tap(
      find.byKey(const ValueKey('rabbit-bind-batch-option-9')),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('rabbit-bind-batch-submit')),
    );
    await tester.pumpAndSettle();

    expect(adapter.requests, hasLength(1));
    expect(adapter.requests.single.path, '/api/batches/9/members');
    expect(adapter.requests.single.body['rabbitIds'], [31]);
    expect(
      adapter.requests.single.body['requestId'],
      isA<String>().having((value) => value.isNotEmpty, 'non-empty', true),
    );
    expect(tester.takeException(), isNull);
  });
}

class _CapturedRequest {
  const _CapturedRequest({required this.path, required this.body});

  final String path;
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

const _activeBatch = Batch(
  id: 9,
  houseId: 8,
  batchCode: 'SALE-2026-08',
  status: '进行中',
  startDate: null,
  endDate: null,
  remark: '',
);

const _otherActiveBatch = Batch(
  id: 11,
  houseId: 8,
  batchCode: 'SALE-OTHER',
  status: '进行中',
  startDate: null,
  endDate: null,
  remark: '',
);

const _completedBatch = Batch(
  id: 10,
  houseId: 8,
  batchCode: 'SALE-CLOSED',
  status: '已完成',
  startDate: null,
  endDate: null,
  remark: '',
);

const _commodityRabbit = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 12,
  motherId: null,
  type: '2',
  gender: '1',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 2.5,
  isActive: true,
);

const _breedingDoe = Rabbit(
  id: 32,
  houseId: 8,
  cageId: 13,
  motherId: null,
  type: '0',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 4.1,
  isActive: true,
);

const _breedingBuck = Rabbit(
  id: 33,
  houseId: 8,
  cageId: 14,
  motherId: null,
  type: '0',
  gender: '1',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 4.3,
  isActive: true,
);
