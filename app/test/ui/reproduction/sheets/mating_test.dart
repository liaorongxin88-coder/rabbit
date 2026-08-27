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
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/reproduction/event.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
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
      'single AI keeps the buck selector visible but clears it by default',
      (tester) async {
    final adapter = _MatingAdapter();
    final repository = _reproRepository(adapter);

    await tester.pumpWidget(
      _singleApp(
        repository: repository,
        rabbits: [_male(201)],
      ),
    );
    await _open(tester, const ValueKey('open-single-mating'));

    final male = find.byKey(const ValueKey('mating-male-201'));
    expect(male, findsOneWidget);
    expect(tester.widget<RadioListTile<int>>(male).groupValue, 201);

    await tester.tap(find.byKey(const ValueKey('mating-method-AI')));
    await tester.pump();

    expect(find.byKey(const ValueKey('mating-male-201')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('production-event-submit')));
    await tester.pumpAndSettle();

    expect(adapter.actionRequests, hasLength(1));
    final request = adapter.actionRequests.single;
    expect(request['action'], 'MATING');
    expect(request['matingMethod'], 'AI');
    expect(request.containsKey('maleRabbitId'), isFalse);
    expect(request['requestId'], isNotEmpty);
  });

  testWidgets('single AI records an optionally selected buck', (tester) async {
    final adapter = _MatingAdapter();
    final repository = _reproRepository(adapter);

    await tester.pumpWidget(
      _singleApp(
        repository: repository,
        rabbits: [_male(201)],
      ),
    );
    await _open(tester, const ValueKey('open-single-mating'));
    await tester.tap(find.byKey(const ValueKey('mating-method-AI')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('mating-male-201')));
    await tester.tap(find.byKey(const ValueKey('production-event-submit')));
    await tester.pumpAndSettle();

    expect(adapter.actionRequests.single['matingMethod'], 'AI');
    expect(adapter.actionRequests.single['maleRabbitId'], 201);
  });

  testWidgets('single natural mating rejects a missing valid male',
      (tester) async {
    final adapter = _MatingAdapter();
    final repository = _reproRepository(adapter);

    await tester.pumpWidget(
      _singleApp(
        repository: repository,
        rabbits: const [],
      ),
    );
    await _open(tester, const ValueKey('open-single-mating'));

    expect(find.text('暂无可用种公兔，请先在笼位录入。'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('production-event-submit')));
    await tester.pumpAndSettle();

    expect(adapter.actionRequests, isEmpty);
    expect(find.text('请选择种公兔'), findsOneWidget);
  });
}

Future<void> _open(WidgetTester tester, Key key) async {
  await tester.tap(find.byKey(key));
  await tester.pumpAndSettle();
}

ReproRepository _reproRepository(_MatingAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(SessionStore(), dio: dio);
  // The provider keeps the repository alive for the duration of the test.
  // Disposing the client after the test avoids leaking its Dio resources.
  addTearDown(client.dispose);
  return ReproRepository(client);
}

Widget _singleApp({
  required ReproRepository repository,
  required List<Rabbit> rabbits,
}) {
  return ProviderScope(
    overrides: [
      reproRepositoryProvider.overrideWithValue(repository),
      allActiveHouseRabbitsProvider(8).overrideWith((_) async => rabbits),
      houseCagesProvider(8).overrideWith((_) async => const <Cage>[]),
      houseBatchesProvider(8).overrideWith((_) async => const [_activeBatch]),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: Scaffold(
        body: Builder(
          builder: (context) => Center(
            child: ElevatedButton(
              key: const ValueKey('open-single-mating'),
              onPressed: () => showProductionEventSheet(
                context: context,
                event: _matingEvent,
              ),
              child: const Text('打开单只配种'),
            ),
          ),
        ),
      ),
    ),
  );
}

Rabbit _male(int id) {
  return Rabbit(
    id: id,
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
  );
}

const _activeBatch = Batch(
  id: 9,
  houseId: 8,
  batchCode: 'B-009',
  status: '进行中',
  startDate: null,
  endDate: null,
  remark: '',
);

const _matingEvent = EventItem(
  recordId: 301,
  category: '生产周期',
  eventType: '配种',
  eventDate: null,
  batchId: 9,
  rabbitId: 101,
  status: 'due',
  sourceHouseId: 8,
  sourceHouseName: '测试兔舍',
);

class _MatingAdapter implements HttpClientAdapter {
  final requests = <Map<String, dynamic>>[];

  List<Map<String, dynamic>> get actionRequests {
    return requests
        .where(
          (request) => request['path'] == '/api/repro/cycles/301/actions',
        )
        .map((request) => request['body'] as Map<String, dynamic>)
        .toList();
  }

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add({
      'path': options.path,
      'body': options.data is Map
          ? Map<String, dynamic>.from(options.data as Map)
          : <String, dynamic>{},
    });
    return _json({'cycleId': 301});
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
