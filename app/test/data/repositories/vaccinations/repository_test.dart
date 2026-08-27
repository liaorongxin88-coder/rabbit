import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/vaccinations/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
  });

  test('createVaccination posts the whole cage in one batched request',
      () async {
    final adapter = _CapturingAdapter(data: {'created': 3, 'records': []});
    final repository = _repository(adapter);
    final vaccinatedAt = DateTime(2026, 8, 14);
    final nextDueDate = DateTime(2026, 9, 4);

    final created = await repository.createVaccination(
      houseId: 8,
      rabbitIds: const [801, 802, 803],
      vaccineName: '  兔瘟疫苗  ',
      vaccinatedAt: vaccinatedAt,
      vaccineBatchNo: '  B20260301  ',
      dose: '  1ml  ',
      route: '皮下注射',
      nextDueDate: nextDueDate,
      remark: '  整笼接种  ',
      requestId: 'vaccination-request-1',
    );

    expect(created, 3);
    expect(adapter.requests, hasLength(1));
    final request = adapter.requests.single;
    expect(request.path, '/api/vaccinations');
    expect(request.headers['Authorization'], 'Bearer operator-token');
    expect(request.headers['X-House-Id'], '8');
    expect(request.body, {
      'rabbitIds': [801, 802, 803],
      'vaccineName': '兔瘟疫苗',
      'vaccineBatchNo': 'B20260301',
      'dose': '1ml',
      'route': '皮下注射',
      'vaccinatedAt': vaccinatedAt.millisecondsSinceEpoch,
      'nextDueDate': nextDueDate.millisecondsSinceEpoch,
      'remark': '整笼接种',
      'requestId': 'vaccination-request-1',
    });
  });

  test('createVaccination omits every optional field left blank', () async {
    final adapter = _CapturingAdapter(data: {'created': 1, 'records': []});
    final repository = _repository(adapter);
    final vaccinatedAt = DateTime(2026, 8, 14);

    await repository.createVaccination(
      houseId: 8,
      rabbitIds: const [801],
      vaccineName: '巴氏杆菌苗',
      vaccinatedAt: vaccinatedAt,
      vaccineBatchNo: '   ',
      dose: '',
      requestId: 'vaccination-request-2',
    );

    expect(adapter.requests.single.body, {
      'rabbitIds': [801],
      'vaccineName': '巴氏杆菌苗',
      'vaccinatedAt': vaccinatedAt.millisecondsSinceEpoch,
      'requestId': 'vaccination-request-2',
    });
  });

  test('listByRabbit parses records and flags the ones still owing a dose',
      () async {
    final adapter = _CapturingAdapter(data: [
      {
        'id': 5,
        'rabbitId': 801,
        'vaccineName': '兔瘟疫苗',
        'vaccinatedAt': '2026-08-14T00:00:00.000Z',
        'nextDueDate': '2026-09-04T00:00:00.000Z',
        'status': 'SCHEDULED',
        'vaccineBatchNo': 'B20260301',
        'dose': '1ml',
        'route': '皮下注射',
        'remark': '整笼接种',
      },
      {
        'id': 4,
        'rabbitId': 801,
        'vaccineName': '巴氏杆菌苗',
        'vaccinatedAt': '2026-07-01T00:00:00.000Z',
        'status': 'DONE',
      },
    ]);
    final repository = _repository(adapter);

    final records = await repository.listByRabbit(houseId: 8, rabbitId: 801);

    expect(adapter.requests.single.path, '/api/vaccinations');
    expect(records, hasLength(2));
    expect(records.first.vaccineName, '兔瘟疫苗');
    expect(records.first.vaccineBatchNo, 'B20260301');
    expect(records.first.awaitsNextDose, isTrue);
    expect(records.first.statusLabel, '待补种');
    expect(records.last.awaitsNextDose, isFalse);
    expect(records.last.statusLabel, '已完成');
    // 没下发的可选字段解析成 null，而不是空串
    expect(records.last.dose, isNull);
    expect(records.last.nextDueDate, isNull);
  });

  test('listDue reads the house-wide outstanding vaccinations', () async {
    final adapter = _CapturingAdapter(data: const []);
    final repository = _repository(adapter);

    final records = await repository.listDue(houseId: 8);

    expect(records, isEmpty);
    expect(adapter.requests.single.path, '/api/vaccinations/due');
    expect(adapter.requests.single.headers['X-House-Id'], '8');
  });
}

VaccinationRepository _repository(_CapturingAdapter adapter) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  final client = ApiClient(SessionStore(), dio: dio);
  addTearDown(client.dispose);
  return VaccinationRepository(client);
}

class _CapturedRequest {
  const _CapturedRequest({
    required this.path,
    required this.headers,
    required this.body,
  });

  final String path;
  final Map<String, dynamic> headers;
  final Map<String, dynamic> body;
}

class _CapturingAdapter implements HttpClientAdapter {
  _CapturingAdapter({this.data});

  final Object? data;
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
        headers: Map<String, dynamic>.from(options.headers),
        body: options.data is Map
            ? Map<String, dynamic>.from(options.data as Map)
            : <String, dynamic>{},
      ),
    );
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
