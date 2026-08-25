import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/sheets/replacement.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  testWidgets('replacement submits once with target cage and exits batch',
      (tester) async {
    final adapter = _DelayedAdapter();
    final repository = _repository(adapter);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          rabbitRepositoryProvider.overrideWithValue(repository),
          houseCagesProvider(8).overrideWith((_) async => const [
                _targetCage,
                _occupiedCage,
                _crossHouseCage,
              ]),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: Scaffold(
            body: Builder(
              builder: (context) => FilledButton(
                key: const ValueKey('open-rabbit-replacement-sheet'),
                onPressed: () => showRabbitReplacementSheet(
                  context: context,
                  houseId: 8,
                  rabbit: _commodityRabbit,
                ),
                child: const Text('留种'),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(
      find.byKey(const ValueKey('open-rabbit-replacement-sheet')),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('rabbit-replacement-cage-21')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('rabbit-replacement-cage-22')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('rabbit-replacement-cage-23')),
      findsNothing,
    );

    await tester.tap(
      find.byKey(const ValueKey('rabbit-replacement-cage-21')),
    );
    await tester.pump();
    final submit = find.byKey(const ValueKey('rabbit-replacement-submit'));
    await tester.tap(submit);
    await tester.tap(submit);
    for (var attempt = 0; attempt < 20 && adapter.requests.isEmpty; attempt++) {
      await tester.pump(const Duration(milliseconds: 10));
    }

    expect(adapter.requests, hasLength(1));
    expect(tester.widget<ElevatedButton>(submit).onPressed, isNull);
    expect(adapter.requests.single.path, '/api/rabbits/replacement');
    expect(adapter.requests.single.body['rabbitIds'], [31]);
    expect(adapter.requests.single.body['targetCageId'], 21);
    expect(adapter.requests.single.body['forceExitBatch'], isTrue);
    expect(adapter.requests.single.body['requestId'], isNotEmpty);

    adapter.completeSuccess();
    await tester.pumpAndSettle();
    expect(find.text('商品兔 #31 已转入 B-01'), findsOneWidget);
  });
}

RabbitRepository _repository(_DelayedAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);
  return RabbitRepository(client);
}

class _CapturedRequest {
  const _CapturedRequest({required this.path, required this.body});

  final String path;
  final Map<String, dynamic> body;
}

class _DelayedAdapter implements HttpClientAdapter {
  final requests = <_CapturedRequest>[];
  final _response = Completer<ResponseBody>();

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) {
    requests.add(
      _CapturedRequest(
        path: options.path,
        body: Map<String, dynamic>.from(options.data as Map),
      ),
    );
    return _response.future;
  }

  void completeSuccess() {
    _response.complete(
      ResponseBody.fromString(
        jsonEncode({
          'code': 0,
          'message': 'ok',
          'data': {
            'items': [
              {
                'rabbitId': 31,
                'replacementRecordId': 901,
                'targetCageId': 21,
              },
            ],
          },
        }),
        200,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      ),
    );
  }

  @override
  void close({bool force = false}) {}
}

const _commodityRabbit = Rabbit(
  id: 31,
  houseId: 8,
  cageId: 12,
  motherId: null,
  type: '2',
  gender: '0',
  breed: '新西兰白兔',
  arrivalMethod: '0',
  arrivalDate: null,
  weight: 2.5,
  isActive: true,
);

const _targetCage = Cage(
  id: 21,
  houseId: 8,
  cageNumber: 'B-01',
  status: '2',
  rabbitCount: 0,
  isEnabled: true,
);

const _occupiedCage = Cage(
  id: 22,
  houseId: 8,
  cageNumber: 'B-02',
  status: '2',
  rabbitCount: 1,
  isEnabled: true,
);

const _crossHouseCage = Cage(
  id: 23,
  houseId: 9,
  cageNumber: 'B-03',
  status: '2',
  rabbitCount: 0,
  isEnabled: true,
);
