import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/app_update/release.dart';

final appUpdateRepositoryProvider = Provider<AppUpdateRepository>((ref) {
  return AppUpdateRepository(ref.watch(apiClientProvider));
});

class AppUpdateRepository {
  AppUpdateRepository(this._api);

  final ApiClient _api;

  Future<AppUpdateCheck> check(int buildNumber) {
    return _api.get<AppUpdateCheck>(
      '/api/app-updates/check',
      query: {'buildNumber': buildNumber},
      decode: (data) => AppUpdateCheck.fromJson(
        requireJsonObject(data, message: '升级检查响应格式不正确'),
      ),
    );
  }

  Future<File> download(
    AppRelease release,
    Directory directory, {
    required void Function(int received, int total) onReceiveProgress,
    required CancelToken cancelToken,
  }) async {
    await directory.create(recursive: true);
    final file = File('${directory.path}/rabbit-${release.buildNumber}.apk');
    if (await file.exists()) {
      await file.delete();
    }
    await _api.download(
      release.downloadUrl,
      file.path,
      onReceiveProgress: onReceiveProgress,
      cancelToken: cancelToken,
    );
    final actualSize = await file.length();
    if (actualSize != release.apkSizeBytes) {
      await file.delete();
      throw const AppUpdateException('下载文件大小与版本清单不一致');
    }
    return file;
  }
}

class AppUpdateException implements Exception {
  const AppUpdateException(this.message);

  final String message;

  @override
  String toString() => message;
}
