import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  test('stage and breeding-cage fields decode from the server contract', () {
    final rabbit = Rabbit.fromJson({
      ..._rabbitJson,
      'growthStage': ' MATURE ',
      'reproductiveStage': ' PREGNANT ',
    });
    final doeCage = Cage.fromJson({
      ..._cageJson,
      'breedingOccupantGender': '0',
    });
    final emptyBreedingCage = Cage.fromJson({
      ..._cageJson,
      'breedingOccupantGender': ' ',
    });

    expect(rabbit.growthStage, 'MATURE');
    expect(rabbit.reproductiveStage, 'PREGNANT');
    expect(doeCage.isDoeBreedingCage, isTrue);
    expect(doeCage.isBuckBreedingCage, isFalse);
    expect(doeCage.usageLabel, '种母兔');
    expect(emptyBreedingCage.isDoeBreedingCage, isFalse);
    expect(emptyBreedingCage.isBuckBreedingCage, isFalse);
    expect(emptyBreedingCage.usageLabel, '种兔');
  });

  test('repository sends stage snapshots and retains them during a cage move',
      () async {
    final adapter = _CapturingAdapter();
    final repository = _repository(adapter);

    await repository.createRabbit(
      houseId: 8,
      cageId: 11,
      type: '0',
      gender: '0',
      breed: ' 新西兰白兔 ',
      arrivalMethod: '0',
      weight: 4.2,
      growthStage: ' MATURE ',
      reproductiveStage: ' PREGNANT ',
    );
    await repository.updateRabbit(
      houseId: 8,
      rabbitId: 801,
      cageId: 12,
      motherId: null,
      breed: '新西兰白兔',
      arrivalMethod: '0',
      arrivalDate: null,
      weight: 4.3,
      growthStage: ' GROWING ',
      reproductiveStage: ' ',
    );
    await repository.transferRabbitCage(
      houseId: 8,
      rabbitId: 801,
      targetCageId: 13,
    );

    expect(adapter.requests, hasLength(3));

    final create = adapter.requests[0];
    expect(create.path, '/api/rabbits');
    expect(create.method, 'POST');
    expect(create.headers['X-House-Id'], '8');
    expect(create.body['breed'], '新西兰白兔');
    expect(create.body['growthStage'], 'MATURE');
    expect(create.body['reproductiveStage'], 'PREGNANT');
    expect(create.body['requestId'], isNotEmpty);

    final update = adapter.requests[1];
    expect(update.path, '/api/rabbits/801');
    expect(update.method, 'PUT');
    expect(update.body['growthStage'], 'GROWING');
    expect(update.body.containsKey('reproductiveStage'), isFalse);

    // 换笼走专用端点，而不是把整行资料重发一遍。两个原因：
    // 一是后端已拒收种母兔手录的 reproductiveStage，重发就是 400；
    // 二是只有专用端点能表达“目标笼已有种兔时两笼对调”。
    final move = adapter.requests[2];
    expect(move.path, '/api/rabbits/801/cage-transfer');
    expect(move.method, 'POST');
    expect(move.body['targetCageId'], 13);
    expect(move.body['requestId'], isNotEmpty);
    expect(move.body.containsKey('growthStage'), isFalse);
    expect(move.body.containsKey('reproductiveStage'), isFalse);
  });
}

RabbitRepository _repository(_CapturingAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);
  return RabbitRepository(client);
}

class _CapturedRequest {
  const _CapturedRequest({
    required this.method,
    required this.path,
    required this.headers,
    required this.body,
  });

  final String method;
  final String path;
  final Map<String, dynamic> headers;
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
        method: options.method,
        path: options.path,
        headers: Map<String, dynamic>.from(options.headers),
        body: Map<String, dynamic>.from(options.data as Map),
      ),
    );
    return ResponseBody.fromString(
      jsonEncode({'code': 0, 'message': 'ok', 'data': _rabbitJson}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

const _rabbitJson = <String, dynamic>{
  'id': 801,
  'houseId': 8,
  'cageId': 11,
  'motherId': null,
  'type': '0',
  'gender': '0',
  'breed': '新西兰白兔',
  'arrivalMethod': '0',
  'arrivalDate': null,
  'weight': 4.2,
  'isActive': true,
};

const _cageJson = <String, dynamic>{
  'id': 11,
  'houseId': 8,
  'cageNumber': 'A-01',
  'status': '1',
  'rabbitCount': 1,
  'isEnabled': true,
};
