import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';

class LitterRepositoryHarness {
  LitterRepositoryHarness({
    required int cycleId,
    required int motherRabbitId,
    int currentNursing = 8,
  }) {
    SharedPreferences.setMockInitialValues({
      'userId': 7,
      'userName': 'operator',
    });
    FlutterSecureStorage.setMockInitialValues({'token': 'operator-token'});
    final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
      ..httpClientAdapter = _LitterAdapter(
        cycleId: cycleId,
        motherRabbitId: motherRabbitId,
        currentNursing: currentNursing,
      );
    client = ApiClient(
      SessionStore(),
      dio: dio,
      appBuildLoader: () async => '4020',
    );
    repository = ReproRepository(client);
  }

  late final ApiClient client;
  late final ReproRepository repository;

  void dispose() => client.dispose();
}

class _LitterAdapter implements HttpClientAdapter {
  const _LitterAdapter({
    required this.cycleId,
    required this.motherRabbitId,
    required this.currentNursing,
  });

  final int cycleId;
  final int motherRabbitId;
  final int currentNursing;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final expectedPath = '/api/repro/cycles/$cycleId/litter';
    if (options.method != 'GET' || options.path != expectedPath) {
      throw StateError(
        'Unexpected litter request: ${options.method} ${options.path}',
      );
    }
    return ResponseBody.fromString(
      jsonEncode({
        'code': 0,
        'message': 'ok',
        'data': {
          'id': 81,
          'cycleId': cycleId,
          'motherRabbitId': motherRabbitId,
          'keptKits': currentNursing,
          'currentNursing': currentNursing,
          'status': 'NURSING',
        },
      }),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
