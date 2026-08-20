import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/repro_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/event_item.dart';
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

  testWidgets('batch AI can submit without a male and omits maleRabbitId',
      (tester) async {
    final adapter = _MatingAdapter();
    final repository = _reproRepository(adapter);

    await tester.pumpWidget(
      _batchApp(
        repository: repository,
        rabbits: const [],
      ),
    );
    await _open(tester, const ValueKey('open-batch-mating'));

    await tester.tap(
      find.byKey(const ValueKey('batch-mating-method-AI')),
    );
    await tester.pump();

    expect(find.text('种公兔'), findsNothing);
    final submit = find.byKey(const ValueKey('batch-mating-confirm'));
    expect(tester.widget<ElevatedButton>(submit).onPressed, isNotNull);

    await tester.tap(submit);
    await tester.pumpAndSettle();

    expect(adapter.actionRequests, hasLength(1));
    final request = adapter.actionRequests.single;
    expect(request['action'], 'MATING');
    expect(request['matingMethod'], 'AI');
    expect(request.containsKey('maleRabbitId'), isFalse);
    expect(request['requestId'], isNotEmpty);
  });

  testWidgets('single mating clears selected male when switching to AI',
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

    expect(find.byKey(const ValueKey('mating-male-201')), findsNothing);
    await tester.tap(find.byKey(const ValueKey('production-event-submit')));
    await tester.pumpAndSettle();

    expect(adapter.actionRequests, hasLength(1));
    final request = adapter.actionRequests.single;
    expect(request['action'], 'MATING');
    expect(request['matingMethod'], 'AI');
    expect(request.containsKey('maleRabbitId'), isFalse);
    expect(request['requestId'], isNotEmpty);
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

  testWidgets('batch natural mating disables confirmation without a valid male',
      (tester) async {
    final adapter = _MatingAdapter();
    final repository = _reproRepository(adapter);

    await tester.pumpWidget(
      _batchApp(
        repository: repository,
        rabbits: const [],
      ),
    );
    await _open(tester, const ValueKey('open-batch-mating'));

    final submit = find.byKey(const ValueKey('batch-mating-confirm'));
    expect(tester.widget<ElevatedButton>(submit).onPressed, isNull);
    expect(adapter.actionRequests, isEmpty);
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

Widget _batchApp({
  required ReproRepository repository,
  required List<Rabbit> rabbits,
}) {
  return ProviderScope(
    overrides: [
      reproRepositoryProvider.overrideWithValue(repository),
      allActiveHouseRabbitsProvider(8).overrideWith((_) async => rabbits),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: Scaffold(
        body: Builder(
          builder: (context) => Center(
            child: ElevatedButton(
              key: const ValueKey('open-batch-mating'),
              onPressed: () => showBatchMatingSheet(
                context: context,
                houseId: 8,
                batchId: 9,
                rabbitIds: const [101],
                requestId: 'batch-ai-request',
              ),
              child: const Text('打开批量配种'),
            ),
          ),
        ),
      ),
    ),
  );
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
          (request) =>
              request['path'] == '/api/repro/tasks/bulk-actions' ||
              request['path'] == '/api/repro/cycles/301/actions',
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
    if (options.path.startsWith('/api/tasks')) {
      return _json({
        'total': 1,
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
        ],
      });
    }
    if (options.path == '/api/repro/tasks/bulk-actions') {
      return _json({
        'total': 1,
        'succeeded': 1,
        'failed': 0,
        'items': [],
      });
    }
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
