import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/app_update/release.dart';

final appUpdateRepositoryProvider = Provider<AppUpdateRepository>((ref) {
  return AppUpdateRepository(ref.watch(apiClientProvider));
});

class AppUpdateRepository {
  AppUpdateRepository(this._api, {this.cacheDirectory});

  final ApiClient _api;
  final Directory Function()? cacheDirectory;

  Future<AppUpdateCheck> check({
    required String channel,
    required int versionCode,
  }) {
    return _api.get<AppUpdateCheck>(
      '/api/app/updates/check',
      query: {
        'channel': channel,
        'versionCode': versionCode,
      },
      decode: AppUpdateCheck.fromJson,
    );
  }

  Future<File> download({
    required AppUpdateCheck update,
    required void Function(double progress) onProgress,
    CancelToken? cancelToken,
  }) async {
    final downloadPath = update.downloadPath;
    final sha256Value = update.sha256;
    final id = update.id;
    if (downloadPath == null || sha256Value == null || id == null) {
      throw const ApiException('更新信息不完整');
    }

    final root = cacheDirectory?.call() ?? await getTemporaryDirectory();
    final directory = Directory('${root.path}/updates');
    if (!directory.existsSync()) {
      await directory.create(recursive: true);
    }
    final file = File('${directory.path}/$id.apk');
    await _api.downloadFile(
      downloadPath,
      savePath: file.path,
      cancelToken: cancelToken,
      onReceiveProgress: (received, total) {
        if (total > 0) {
          onProgress(received / total);
        }
      },
    );
    final digest = (await sha256.bind(file.openRead()).last).toString();
    if (digest.toLowerCase() != sha256Value.toLowerCase()) {
      await file.delete();
      throw const ApiException('安装包校验失败，请重新下载');
    }
    return file;
  }
}
