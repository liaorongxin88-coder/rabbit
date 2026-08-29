import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/data/repositories/app_update/repository.dart';
import 'package:rabbit_flutter/src/data/services/app_update/installer.dart';
import 'package:rabbit_flutter/src/domain/app_update/release.dart';
import 'package:rabbit_flutter/src/ui/app_update/view_models/controller.dart';

void main() {
  late Directory tempDir;

  setUp(() => tempDir = Directory.systemTemp.createTempSync('ota_test'));
  tearDown(() => tempDir.deleteSync(recursive: true));

  /// 造一个「已经完整下载好」的包，大小和版本清单对得上。
  AppRelease seedCachedApk() {
    final bytes = List<int>.filled(1024, 0);
    File('${tempDir.path}/rabbit-4012.apk').writeAsBytesSync(bytes);
    return AppRelease(
      buildNumber: 4012,
      versionName: '1.0.9',
      downloadUrl: Uri.parse('https://example.invalid/app.apk'),
      sha256: 'a' * 64,
      apkSizeBytes: bytes.length,
      releaseNotes: '',
      forceUpdate: false,
    );
  }

  test('安装界面被误触关掉后，重新拉起安装不会重新下载', () async {
    final release = seedCachedApk();
    final installer = _FakeInstaller(tempDir);
    final repository = _FakeRepository(release);
    final controller = AppUpdateController(
      repository: repository,
      installer: installer,
    );
    addTearDown(controller.dispose);

    await controller.check();
    expect(controller.state.phase, AppUpdatePhase.available);

    // 包已经在本地，这一步应该直接进安装环节，不再下载。
    await controller.startDownload();
    expect(controller.state.phase, AppUpdatePhase.installing);
    expect(installer.installCalls, 1);
    expect(repository.downloadCalls, 0);

    // 用户把系统安装器点掉了，再点一次「重新打开安装」。
    await controller.authorizeAndInstall();

    expect(installer.installCalls, 2, reason: '应该能再次拉起安装器');
    expect(repository.downloadCalls, 0, reason: '本地已有包，不该重新下载');
  });
}

class _FakeInstaller implements AppUpdateInstaller {
  _FakeInstaller(this._directory);

  final Directory _directory;
  var installCalls = 0;

  @override
  Future<AppVersion> currentVersion() async =>
      const AppVersion(versionName: '1.0.8', buildNumber: 4011);

  @override
  Future<Directory> updateDirectory() async => _directory;

  @override
  Future<bool> canInstallPackages() async => true;

  @override
  Future<void> openInstallPermissionSettings() async {}

  @override
  Future<AppInstallResult> installApk({
    required String path,
    required String sha256,
  }) async {
    installCalls += 1;
    return AppInstallResult.installerOpened;
  }
}

class _FakeRepository implements AppUpdateRepository {
  _FakeRepository(this._release);

  final AppRelease _release;
  var downloadCalls = 0;

  @override
  Future<AppUpdateCheck> check(int buildNumber) async =>
      AppUpdateCheck.available(currentBuild: buildNumber, release: _release);

  @override
  Future<File> download(
    AppRelease release,
    Directory directory, {
    required void Function(int received, int total) onReceiveProgress,
    required CancelToken cancelToken,
  }) async {
    downloadCalls += 1;
    return File('${directory.path}/rabbit-${release.buildNumber}.apk');
  }
}
