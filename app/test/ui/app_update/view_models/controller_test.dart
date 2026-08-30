import 'dart:async';
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

  AppRelease release() => AppRelease(
        buildNumber: 4012,
        versionName: '1.0.9',
        downloadUrl: Uri.parse('https://example.invalid/app.apk'),
        sha256: 'a' * 64,
        apkSizeBytes: 1024,
        releaseNotes: '',
        forceUpdate: false,
      );

  AppRelease seedCachedApk() {
    final candidate = release();
    File('${tempDir.path}/rabbit-${candidate.buildNumber}.apk')
        .writeAsBytesSync(List<int>.filled(candidate.apkSizeBytes, 0));
    return candidate;
  }

  AppUpdateController controllerFor(
    AppRelease release, {
    required _FakeInstaller installer,
    required _FakeRepository repository,
  }) {
    final controller = AppUpdateController(
      repository: repository,
      installer: installer,
    );
    addTearDown(controller.dispose);
    return controller;
  }

  test('下载前拒绝权限时立即打开授权页且不下载', () async {
    final candidate = release();
    final installer = _FakeInstaller(tempDir)..permissionGranted = false;
    final repository = _FakeRepository(candidate);
    final controller = controllerFor(
      candidate,
      installer: installer,
      repository: repository,
    );

    await controller.check();
    await controller.startDownload();

    expect(controller.state.phase, AppUpdatePhase.permissionRequired);
    expect(controller.state.message, contains('下载更新包前'));
    expect(installer.settingsCalls, 1);
    expect(repository.downloadCalls, 0);

    await controller.resumeAfterInstallPermission();

    expect(controller.state.phase, AppUpdatePhase.permissionRequired);
    expect(controller.state.message, contains('升级不会继续'));
    expect(repository.downloadCalls, 0, reason: '用户取消授权后不得下载');
  });

  test('授权页返回且权限已授予时自动开始下载', () async {
    final candidate = release();
    final installer = _FakeInstaller(tempDir)..permissionGranted = false;
    final repository = _FakeRepository(candidate);
    final controller = controllerFor(
      candidate,
      installer: installer,
      repository: repository,
    );

    await controller.check();
    await controller.startDownload();
    installer.permissionGranted = true;

    await controller.resumeAfterInstallPermission();

    expect(repository.downloadCalls, 1);
    expect(installer.installCalls, 1);
    expect(controller.state.phase, AppUpdatePhase.installing);
  });

  test('授权页返回事件重复到达时只续接一次下载', () async {
    final candidate = release();
    final installer = _FakeInstaller(tempDir)..permissionGranted = false;
    final repository = _FakeRepository(candidate);
    final controller = controllerFor(
      candidate,
      installer: installer,
      repository: repository,
    );

    await controller.check();
    await controller.startDownload();
    installer.permissionGranted = true;

    await Future.wait([
      controller.resumeAfterInstallPermission(),
      controller.resumeAfterInstallPermission(),
    ]);

    expect(repository.downloadCalls, 1);
    expect(installer.installCalls, 1);
  });

  test('下载期间撤销权限时安装前复核并重新打开授权页', () async {
    final candidate = release();
    final installer = _FakeInstaller(tempDir)
      ..permissionResponses.addAll([true, false]);
    final repository = _FakeRepository(candidate);
    final controller = controllerFor(
      candidate,
      installer: installer,
      repository: repository,
    );

    await controller.check();
    await controller.startDownload();

    expect(repository.downloadCalls, 1);
    expect(installer.permissionChecks, 2);
    expect(installer.installCalls, 0);
    expect(installer.settingsCalls, 1);
    expect(controller.state.phase, AppUpdatePhase.permissionRequired);
    expect(controller.state.message, contains('安装更新包前'));
  });

  test('原生安装前兜底发现权限撤销时回到同一授权状态', () async {
    final candidate = release();
    final installer = _FakeInstaller(tempDir)
      ..installResult = AppInstallResult.permissionRequired;
    final repository = _FakeRepository(candidate);
    final controller = controllerFor(
      candidate,
      installer: installer,
      repository: repository,
    );

    await controller.check();
    await controller.startDownload();

    expect(installer.installCalls, 1);
    expect(installer.settingsCalls, 1);
    expect(controller.state.phase, AppUpdatePhase.permissionRequired);
    expect(controller.state.message, contains('权限已被撤销'));
  });

  test('重复升级请求共用同一个在途操作', () async {
    final candidate = release();
    final permissionGate = Completer<bool>();
    final installer = _FakeInstaller(tempDir)
      ..permissionCheckGate = permissionGate;
    final repository = _FakeRepository(candidate);
    final controller = controllerFor(
      candidate,
      installer: installer,
      repository: repository,
    );

    await controller.check();
    final first = controller.startDownload();
    final second = controller.startDownload();

    expect(identical(first, second), isTrue);
    permissionGate.complete(true);
    await Future.wait([first, second]);

    expect(repository.downloadCalls, 1);
    expect(installer.installCalls, 1);
  });

  test('安装界面被关掉后复用已有安装包且不重新下载', () async {
    final candidate = seedCachedApk();
    final installer = _FakeInstaller(tempDir);
    final repository = _FakeRepository(candidate);
    final controller = controllerFor(
      candidate,
      installer: installer,
      repository: repository,
    );

    await controller.check();
    await controller.startDownload();
    await controller.authorizeAndInstall();

    expect(installer.installCalls, 2);
    expect(repository.downloadCalls, 0);
  });

  test('用户取消在途下载后进入失败态且不会安装', () async {
    final candidate = release();
    final downloadGate = Completer<void>();
    final repository = _FakeRepository(candidate)..downloadGate = downloadGate;
    final installer = _FakeInstaller(tempDir);
    final controller = controllerFor(
      candidate,
      installer: installer,
      repository: repository,
    );

    await controller.check();
    final operation = controller.startDownload();
    await repository.downloadStarted.future;
    controller.cancelDownload();
    await operation;

    expect(controller.state.phase, AppUpdatePhase.failed);
    expect(controller.state.message, '下载已取消');
    expect(installer.installCalls, 0);
  });

  test('没有待续接授权时生命周期恢复不会重复请求', () async {
    final candidate = release();
    final installer = _FakeInstaller(tempDir);
    final repository = _FakeRepository(candidate);
    final controller = controllerFor(
      candidate,
      installer: installer,
      repository: repository,
    );

    await controller.check();
    await controller.resumeAfterInstallPermission();
    await controller.resumeAfterInstallPermission();

    expect(installer.permissionChecks, 0);
    expect(repository.downloadCalls, 0);
  });
}

class _FakeInstaller implements AppUpdateInstaller {
  _FakeInstaller(this._directory);

  final Directory _directory;
  final List<bool> permissionResponses = [];
  bool permissionGranted = true;
  Completer<bool>? permissionCheckGate;
  var permissionChecks = 0;
  var settingsCalls = 0;
  var installCalls = 0;
  AppInstallResult installResult = AppInstallResult.installerOpened;

  @override
  Future<AppVersion> currentVersion() async =>
      const AppVersion(versionName: '1.0.8', buildNumber: 4011);

  @override
  Future<Directory> updateDirectory() async => _directory;

  @override
  Future<bool> canInstallPackages() async {
    permissionChecks += 1;
    final gate = permissionCheckGate;
    if (gate != null) {
      permissionCheckGate = null;
      return gate.future;
    }
    if (permissionResponses.isNotEmpty) {
      return permissionResponses.removeAt(0);
    }
    return permissionGranted;
  }

  @override
  Future<void> openInstallPermissionSettings() async {
    settingsCalls += 1;
  }

  @override
  Future<AppInstallResult> installApk({
    required String path,
    required String sha256,
  }) async {
    installCalls += 1;
    return installResult;
  }
}

class _FakeRepository implements AppUpdateRepository {
  _FakeRepository(this._release);

  final AppRelease _release;
  final Completer<void> downloadStarted = Completer<void>();
  Completer<void>? downloadGate;
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
    if (!downloadStarted.isCompleted) downloadStarted.complete();
    final gate = downloadGate;
    if (gate != null) {
      await Future.any<void>([gate.future, cancelToken.whenCancel]);
    }
    if (cancelToken.isCancelled) {
      throw DioException(
        requestOptions: RequestOptions(path: release.downloadUrl.toString()),
        type: DioExceptionType.cancel,
      );
    }
    onReceiveProgress(release.apkSizeBytes, release.apkSizeBytes);
    return File('${directory.path}/rabbit-${release.buildNumber}.apk')
        .writeAsBytes(List<int>.filled(release.apkSizeBytes, 0));
  }
}
