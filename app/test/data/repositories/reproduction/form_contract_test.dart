import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({'userId': 7});
    FlutterSecureStorage.setMockInitialValues({'token': 'test-token'});
  });

  test('action always sends execution time and kept adjustment keeps source',
      () async {
    final adapter = _FormContractAdapter();
    final repository = _repository(adapter);

    await repository.applyAction(
      houseId: 8,
      cycleId: 9,
      action: ReproAction.estrus,
      requestId: 'action-time',
    );
    await repository.applyAction(
      houseId: 8,
      cycleId: 10,
      action: ReproAction.mating,
      batchId: 61,
      maleRabbitId: 22,
      matingMethod: MatingMethod.natural,
      requestId: 'mating-batch',
    );
    final action = adapter.jsonRequests.firstWhere(
      (request) => request.path == '/api/repro/cycles/9/actions',
    );
    expect(action.body['occurredAt'], isA<int>());
    final mating = adapter.jsonRequests.firstWhere(
      (request) => request.path == '/api/repro/cycles/10/actions',
    );
    expect(mating.body['batchId'], 61);
    expect(mating.body['maleRabbitId'], 22);
    expect(mating.body['matingMethod'], 'NATURAL');

    final result = await repository.adjustKeptKits(
      houseId: 8,
      cycleId: 9,
      occurredAt: DateTime(2026, 8, 21, 14, 30),
      keptKits: 7,
      sourceMotherRabbitId: 22,
      remark: '寄养转入',
      requestId: 'adjust-kept',
    );
    expect(result.keptKits, 7);
    final adjustment = adapter.jsonRequests.firstWhere(
      (request) => request.path == '/api/repro/cycles/9/kept-kits-adjustments',
    );
    expect(adjustment.body['sourceMotherRabbitId'], 22);
    expect(
      adjustment.body['occurredAt'],
      DateTime.utc(2026, 8, 21, 6, 30).millisecondsSinceEpoch,
    );
  });

  test('weaning sends immutable total weight without client average', () async {
    final adapter = _FormContractAdapter();
    final repository = _repository(adapter);

    await repository.applyAction(
      houseId: 8,
      cycleId: 9,
      action: ReproAction.weaning,
      occurredAt: DateTime(2026, 9, 5, 8),
      weanedCount: 6,
      maleCount: 3,
      femaleCount: 3,
      weaningTotalWeightKg: 4.41,
      requestId: 'weaning-total-1',
    );

    final request = adapter.jsonRequests.single;
    expect(request.body['weaningTotalWeightKg'], 4.41);
    expect(request.body.containsKey('avgWeaningWeight'), isFalse);
    expect(request.body['weanedCount'], 6);
    expect(request.body['requestId'], 'weaning-total-1');
  });

  test('open cycle sends its selected batch and stable request id', () async {
    final adapter = _FormContractAdapter();
    final repository = _repository(adapter);

    await repository.openCycle(
      houseId: 8,
      motherRabbitId: 31,
      batchId: 61,
      stage: ReproStage.awaitPalpation,
      occurredAt: DateTime(2026, 8, 21),
      maleRabbitId: 22,
      matingMethod: MatingMethod.natural,
      requestId: 'open-cycle-61',
    );

    final request = adapter.jsonRequests.singleWhere(
      (item) => item.path == '/api/repro/cycles',
    );
    expect(request.body['motherRabbitId'], 31);
    expect(request.body['batchId'], 61);
    expect(request.body['stage'], 'AWAIT_PALPATION');
    expect(
      request.body['occurredAt'],
      DateTime.utc(2026, 8, 20, 16).millisecondsSinceEpoch,
    );
    expect(request.body['maleRabbitId'], 22);
    expect(request.body['matingMethod'], 'NATURAL');
    expect(request.body['requestId'], 'open-cycle-61');
  });

  test('image upload uses multipart and returns the server file id', () async {
    final adapter = _FormContractAdapter();
    final repository = _repository(adapter);
    final directory =
        await Directory.systemTemp.createTemp('rabbit-image-test-');
    addTearDown(() => directory.delete(recursive: true));
    final file = File('${directory.path}/abortion.png');
    await file.writeAsBytes(const [0x89, 0x50, 0x4e, 0x47]);

    final fileId = await repository.uploadImage(
      houseId: 8,
      filePath: file.path,
      fileName: 'abortion.png',
    );

    expect(fileId, 'file_test_image');
    expect(adapter.multipartPath, '/api/business-files/images');
    expect(adapter.multipartFileName, 'abortion.png');
  });
}

ReproRepository _repository(_FormContractAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(
    SessionStore(),
    dio: dio,
    appBuildLoader: () async => '4020',
  );
  addTearDown(client.dispose);
  return ReproRepository(client);
}

class _JsonRequest {
  const _JsonRequest(this.path, this.body);

  final String path;
  final Map<String, dynamic> body;
}

class _FormContractAdapter implements HttpClientAdapter {
  final jsonRequests = <_JsonRequest>[];
  String? multipartPath;
  String? multipartFileName;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.data is FormData) {
      final form = options.data as FormData;
      multipartPath = options.path;
      multipartFileName = form.files.single.value.filename;
      return _json({
        'fileId': 'file_test_image',
        'fileName': multipartFileName,
        'contentType': 'image/png',
        'sizeBytes': 4,
      });
    }
    jsonRequests.add(
      _JsonRequest(
        options.path,
        options.data is Map
            ? Map<String, dynamic>.from(options.data as Map)
            : <String, dynamic>{},
      ),
    );
    if (options.path.endsWith('/kept-kits-adjustments')) {
      return _json({
        'cycleId': 9,
        'litterId': 11,
        'eventId': 12,
        'previousKeptKits': 6,
        'keptKits': 7,
        'sourceMotherRabbitId': 22,
        'replayed': false,
      });
    }
    return _json({'cycleId': 9});
  }

  static ResponseBody _json(Object? data) => ResponseBody.fromString(
        jsonEncode({'code': 0, 'message': 'ok', 'data': data}),
        200,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );

  @override
  void close({bool force = false}) {}
}
