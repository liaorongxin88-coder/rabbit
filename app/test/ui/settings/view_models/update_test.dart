import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:dio/dio.dart';
import 'package:rabbit_flutter/src/data/repositories/app_update/repository.dart';
import 'package:rabbit_flutter/src/data/services/app_update/installer.dart';
import 'package:rabbit_flutter/src/data/services/app_update/package.dart';
import 'package:rabbit_flutter/src/data/services/app_update/prefs.dart';
import 'package:rabbit_flutter/src/domain/app_update/release.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/update.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  test('refuses to check when the package name does not match the channel',
      () async {
    final controller = _controller(
      identity: const AppPackageIdentity(
        versionName: '1.0.2',
        versionCode: 4003,
        channel: 'test',
        packageName: 'com.rabbit.app.flutter.dev',
      ),
    );

    await controller.check(manual: true);

    expect(controller.state.phase, AppUpdatePhase.failed);
    expect(controller.state.error, contains('渠道不匹配'));
  });

  test('keeps a skipped optional update hidden during silent checks', () async {
    final update = _update(forceUpdate: false);
    final controller = _controller(update: update);

    await controller.check(manual: false);
    expect(controller.state.phase, AppUpdatePhase.available);
    await controller.dismiss();
    expect(controller.state.phase, AppUpdatePhase.idle);

    await controller.check(manual: false);
    expect(controller.state.phase, AppUpdatePhase.idle);

    await controller.check(manual: true);
    expect(controller.state.phase, AppUpdatePhase.available);
  });

  test('does not dismiss a forced update', () async {
    final controller = _controller(update: _update(forceUpdate: true));

    await controller.check(manual: false);
    await controller.dismiss();

    expect(controller.state.phase, AppUpdatePhase.available);
    expect(controller.state.update.forceUpdate, isTrue);
    expect(controller.state.shouldPrompt, isTrue);
  });

  test('keeps the downloaded file when install permission is denied', () async {
    final file = File('${Directory.systemTemp.path}/rabbit-ota-ready.apk');
    await file.writeAsString('apk');
    addTearDown(() {
      if (file.existsSync()) {
        file.deleteSync();
      }
    });
    final installer = _FakeInstaller()
      ..error = PlatformException(
        code: 'PERMISSION',
        message: '请先允许安装未知应用，然后再点立即安装',
      );
    final controller = _controller(
      update: _update(),
      file: file,
      installer: installer,
    );

    await controller.check(manual: true);
    await controller.startDownload();
    await controller.install();

    expect(controller.state.phase, AppUpdatePhase.failed);
    expect(controller.state.localFile?.path, file.path);
    expect(controller.state.error, '请先允许安装未知应用，然后再点立即安装');
  });
}

AppUpdateController _controller({
  AppPackageIdentity identity = const AppPackageIdentity(
    versionName: '1.0.2',
    versionCode: 4003,
    channel: 'dev',
    packageName: 'com.rabbit.app.flutter.dev',
  ),
  AppUpdateCheck update = AppUpdateCheck.none,
  File? file,
  _FakeInstaller? installer,
}) {
  return AppUpdateController(
    repository: _FakeRepository(update: update, file: file),
    packageReader: _FakeReader(identity),
    installer: installer ?? _FakeInstaller(),
    prefs: const AppUpdatePrefs(),
  );
}

AppUpdateCheck _update({bool forceUpdate = false}) {
  return AppUpdateCheck(
    hasUpdate: true,
    forceUpdate: forceUpdate,
    id: 'apk_1',
    versionName: '1.0.3',
    versionCode: 4004,
    sha256: 'abc',
    downloadPath: '/api/app/updates/apk_1/apk',
  );
}

class _FakeReader implements AppPackageReader {
  const _FakeReader(this.identity);

  final AppPackageIdentity identity;

  @override
  Future<AppPackageIdentity> current() async => identity;
}

class _FakeRepository implements AppUpdateRepository {
  _FakeRepository({required this.update, this.file});

  final AppUpdateCheck update;
  final File? file;

  @override
  Directory Function()? get cacheDirectory => null;

  @override
  Future<AppUpdateCheck> check({
    required String channel,
    required int versionCode,
  }) async {
    return update;
  }

  @override
  Future<File> download({
    required AppUpdateCheck update,
    required void Function(double progress) onProgress,
    CancelToken? cancelToken,
  }) async {
    return file ?? File('${Directory.systemTemp.path}/missing.apk');
  }
}

class _FakeInstaller implements AppApkInstaller {
  Object? error;

  @override
  Future<void> install(String filePath) async {
    final thrown = error;
    if (thrown != null) {
      throw thrown;
    }
  }
}
