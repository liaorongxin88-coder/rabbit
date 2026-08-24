import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/app_update/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/app_update/release.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
  });

  test('check decodes a newer published release', () async {
    final repository = _repository(
      adapter: _UpdateAdapter(
        checkBody: {
          'code': 0,
          'message': 'ok',
          'data': {
            'hasUpdate': true,
            'forceUpdate': false,
            'id': 'apk_1',
            'versionName': '1.0.3',
            'versionCode': 4004,
            'sha256': 'abc',
            'downloadPath': '/api/app/updates/apk_1/apk',
          },
        },
      ),
    );

    final update = await repository.check(channel: 'prod', versionCode: 4003);

    expect(update.hasUpdate, isTrue);
    expect(update.versionCode, 4004);
  });

  test('download rejects a package whose hash does not match', () async {
    final bytes = Uint8List.fromList('apk-bytes'.codeUnits);
    final directory = await Directory.systemTemp.createTemp('rabbit-update-');
    addTearDown(() => directory.delete(recursive: true));
    final repository = _repository(
      adapter: _UpdateAdapter(apkBytes: bytes),
      cacheDirectory: directory,
    );

    await expectLater(
      repository.download(
        update: const AppUpdateCheck(
          hasUpdate: true,
          forceUpdate: false,
          id: 'apk_1',
          sha256: 'deadbeef',
          downloadPath: '/api/app/updates/apk_1/apk',
        ),
        onProgress: (_) {},
      ),
      throwsA(isA<ApiException>()),
    );
  });

  test('download keeps a package when the hash matches', () async {
    final bytes = Uint8List.fromList('apk-bytes'.codeUnits);
    final digest = sha256.convert(bytes).toString();
    final directory = await Directory.systemTemp.createTemp('rabbit-update-');
    addTearDown(() => directory.delete(recursive: true));
    final repository = _repository(
      adapter: _UpdateAdapter(apkBytes: bytes),
      cacheDirectory: directory,
    );

    final file = await repository.download(
      update: AppUpdateCheck(
        hasUpdate: true,
        forceUpdate: false,
        id: 'apk_1',
        sha256: digest,
        downloadPath: '/api/app/updates/apk_1/apk',
      ),
      onProgress: (_) {},
    );

    expect(file.existsSync(), isTrue);
    expect(file.readAsBytesSync(), bytes);
  });
}

AppUpdateRepository _repository({
  required HttpClientAdapter adapter,
  Directory? cacheDirectory,
}) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'))
    ..httpClientAdapter = adapter;
  return AppUpdateRepository(
    ApiClient(SessionStore(), dio: dio),
    cacheDirectory: cacheDirectory == null ? null : () => cacheDirectory,
  );
}

class _UpdateAdapter implements HttpClientAdapter {
  _UpdateAdapter({
    this.checkBody,
    this.apkBytes,
  });

  final Map<String, Object?>? checkBody;
  final Uint8List? apkBytes;

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    if (options.path.contains('/check')) {
      return ResponseBody.fromString(
        jsonEncode(checkBody ?? {'code': 0, 'message': 'ok', 'data': {}}),
        200,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );
    }
    return ResponseBody.fromBytes(
      apkBytes ?? Uint8List(0),
      200,
      headers: {
        Headers.contentTypeHeader: ['application/vnd.android.package-archive'],
      },
    );
  }
}
