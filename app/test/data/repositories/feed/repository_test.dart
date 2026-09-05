import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/feed/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/feed/log.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({'userId': 7, 'userName': 'feed'});
    FlutterSecureStorage.setMockInitialValues({'token': 'feed-token'});
  });

  test('preview sorts rabbit ids and save sends exact allocation payload',
      () async {
    final adapter = _FeedAdapter();
    final client = ApiClient(
      SessionStore(),
      dio: Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
        ..httpClientAdapter = adapter,
      appBuildLoader: () async => '4020',
    );
    addTearDown(client.dispose);
    final repository = FeedRepository(client);
    final feedTime = DateTime(2026, 9, 5, 8);

    final preview = await repository.previewAllocations(
      houseId: 8,
      rabbitIds: const [3, 1, 3, 2],
      feedTime: feedTime,
    );
    await repository.addFeedLog(
      houseId: 8,
      draft: FeedLogDraft(
        rabbitIds: const [1, 2, 3],
        feedTime: feedTime,
        requestId: 'feed-request-1',
        amount: 2.5,
        allocations: const [
          FeedBatchAllocation(
            batchId: 101,
            phase: FeedAllocationPhase.fattening,
            amountKg: 2.5,
          ),
        ],
      ),
    );

    expect(preview.groups.single.batchId, 101);
    expect(adapter.requests[0].data, {
      'rabbitIds': [1, 2, 3],
      'feedTime': DateTime.utc(2026, 9, 5).millisecondsSinceEpoch,
    });
    expect(
      adapter.requests[1].data['feedTime'],
      '2026-09-05T00:00:00.000Z',
    );
    expect(adapter.requests[1].data['allocations'], [
      {'batchId': 101, 'phase': 'FATTENING', 'amountKg': 2.5},
    ]);
    for (final request in adapter.requests) {
      expect(request.headers['Authorization'], 'Bearer feed-token');
      expect(request.headers['X-House-Id'], '8');
      expect(request.headers['X-App-Build'], '4020');
    }
  });
}

class _FeedAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final data = options.path.endsWith('/allocation-preview')
        ? {
            'groups': [
              {'batchId': 101, 'phase': 'FATTENING', 'rabbitCount': 3},
            ],
          }
        : null;
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
