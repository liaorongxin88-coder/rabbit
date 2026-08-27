import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/app_update/repository.dart';
import 'package:rabbit_flutter/src/data/services/app_update/installer.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/app_update/release.dart';

final appUpdateControllerProvider =
    StateNotifierProvider<AppUpdateController, AppUpdateState>((ref) {
  return AppUpdateController(
    repository: ref.watch(appUpdateRepositoryProvider),
    installer: ref.watch(appUpdateInstallerProvider),
  );
});

enum AppUpdatePhase {
  idle,
  checking,
  upToDate,
  available,
  downloading,
  permissionRequired,
  installing,
  failed,
}

class AppUpdateState {
  const AppUpdateState({
    this.phase = AppUpdatePhase.idle,
    this.currentVersion,
    this.release,
    this.progress,
    this.message,
  });

  final AppUpdatePhase phase;
  final AppVersion? currentVersion;
  final AppRelease? release;
  final double? progress;
  final String? message;

  bool get isDownloading => phase == AppUpdatePhase.downloading;
  bool get isAvailable => release != null;

  AppUpdateState copyWith({
    AppUpdatePhase? phase,
    AppVersion? currentVersion,
    AppRelease? release,
    bool clearRelease = false,
    double? progress,
    bool clearProgress = false,
    String? message,
    bool clearMessage = false,
  }) {
    return AppUpdateState(
      phase: phase ?? this.phase,
      currentVersion: currentVersion ?? this.currentVersion,
      release: clearRelease ? null : release ?? this.release,
      progress: clearProgress ? null : progress ?? this.progress,
      message: clearMessage ? null : message ?? this.message,
    );
  }
}

class AppUpdateController extends StateNotifier<AppUpdateState> {
  AppUpdateController({
    required AppUpdateRepository repository,
    required AppUpdateInstaller installer,
  })  : _repository = repository,
        _installer = installer,
        super(const AppUpdateState());

  final AppUpdateRepository _repository;
  final AppUpdateInstaller _installer;
  CancelToken? _downloadCancelToken;
  File? _downloadedApk;
  Future<void>? _checkInFlight;

  Future<void> check() {
    if (_checkInFlight != null) {
      return _checkInFlight!;
    }
    final operation = _checkOnce();
    _checkInFlight = operation;
    return operation.whenComplete(() {
      if (identical(_checkInFlight, operation)) {
        _checkInFlight = null;
      }
    });
  }

  Future<void> _checkOnce() async {
    _emit(state.copyWith(
      phase: AppUpdatePhase.checking,
      clearRelease: true,
      clearProgress: true,
      clearMessage: true,
    ));
    try {
      final currentVersion = await _installer.currentVersion();
      final check = await _repository.check(currentVersion.buildNumber);
      if (!mounted) return;
      final release = check.release;
      _emit(AppUpdateState(
        phase: release == null
            ? AppUpdatePhase.upToDate
            : AppUpdatePhase.available,
        currentVersion: currentVersion,
        release: release,
      ));
    } catch (error) {
      if (!mounted) return;
      _emit(state.copyWith(
        phase: AppUpdatePhase.failed,
        clearRelease: true,
        clearProgress: true,
        message: _message(error),
      ));
    }
  }

  Future<void> startDownload() async {
    final release = state.release;
    if (release == null || state.isDownloading) return;
    // 同一个版本已经下过就直接装，不重下。去系统设置授权再返回会重新
    // check 一次，阶段回到 available，此时再点主按钮走的就是这里；
    // 进程重启后 _downloadedApk 为空但文件还在，所以按路径而不是按字段找。
    // 文件真假仍由原生层的 sha256 校验兜底，不通过会删掉并提示重新下载。
    final cached = await _cachedApk(release);
    if (cached != null) {
      _downloadedApk = cached;
      await _openInstaller(release, cached);
      return;
    }
    final cancelToken = CancelToken();
    _downloadCancelToken = cancelToken;
    _emit(state.copyWith(
      phase: AppUpdatePhase.downloading,
      progress: 0,
      clearMessage: true,
    ));
    try {
      final directory = await _installer.updateDirectory();
      await _removeOtherApks(directory, release.buildNumber);
      final file = await _repository.download(
        release,
        directory,
        cancelToken: cancelToken,
        onReceiveProgress: (received, total) {
          if (!mounted ||
              total <= 0 ||
              !identical(_downloadCancelToken, cancelToken)) {
            return;
          }
          _emit(state.copyWith(
            phase: AppUpdatePhase.downloading,
            progress: received / total,
          ));
        },
      );
      if (!mounted || !identical(_downloadCancelToken, cancelToken)) return;
      _downloadedApk = file;
      await _openInstaller(release, file);
    } catch (error) {
      if (!mounted || !identical(_downloadCancelToken, cancelToken)) return;
      if (cancelToken.isCancelled) {
        await _deleteDownloadedApk();
      }
      _emit(state.copyWith(
        phase: AppUpdatePhase.failed,
        clearProgress: true,
        message: cancelToken.isCancelled ? '下载已取消' : _message(error),
      ));
    } finally {
      if (identical(_downloadCancelToken, cancelToken)) {
        _downloadCancelToken = null;
      }
    }
  }

  void cancelDownload() {
    _downloadCancelToken?.cancel('user cancelled OTA download');
  }

  Future<void> openInstallPermissionSettings() async {
    try {
      await _installer.openInstallPermissionSettings();
      if (mounted) {
        _emit(state.copyWith(
          phase: AppUpdatePhase.permissionRequired,
          message: '请在系统页面允许此应用安装未知来源应用，再返回继续安装',
        ));
      }
    } catch (error) {
      if (mounted) {
        _emit(state.copyWith(
          phase: AppUpdatePhase.permissionRequired,
          message: _message(error),
        ));
      }
    }
  }

  /// 授权和安装合成一个动作。授没授过权 App 自己查得到，不该让用户先
  /// 判断「我到底授权了没」再从两个长得差不多的按钮里挑。
  /// 包已经在本地时这里不会重新下载。
  Future<void> authorizeAndInstall() async {
    final release = state.release;
    if (release == null) return;
    final file = _downloadedApk ?? await _cachedApk(release);
    if (file == null) {
      await startDownload();
      return;
    }
    _downloadedApk = file;
    if (await _installer.canInstallPackages()) {
      await _openInstaller(release, file);
      return;
    }
    await openInstallPermissionSettings();
  }

  Future<void> _openInstaller(AppRelease release, File file) async {
    if (!await _installer.canInstallPackages()) {
      _emit(state.copyWith(
        phase: AppUpdatePhase.permissionRequired,
        clearProgress: true,
        message: '需要允许此应用安装未知来源应用',
      ));
      return;
    }
    _emit(state.copyWith(
      phase: AppUpdatePhase.installing,
      clearProgress: true,
      clearMessage: true,
    ));
    try {
      final result = await _installer.installApk(
        path: file.path,
        sha256: release.sha256,
      );
      if (!mounted) return;
      switch (result) {
        case AppInstallResult.installerOpened:
          _emit(state.copyWith(
            phase: AppUpdatePhase.installing,
            message: '系统安装器已打开',
          ));
        case AppInstallResult.permissionRequired:
          _emit(state.copyWith(
            phase: AppUpdatePhase.permissionRequired,
            message: '需要允许此应用安装未知来源应用',
          ));
        case AppInstallResult.hashMismatch:
          await _deleteDownloadedApk();
          _emit(state.copyWith(
            phase: AppUpdatePhase.failed,
            message: '下载文件校验失败，请重新下载',
          ));
        case AppInstallResult.invalidApk:
          await _deleteDownloadedApk();
          _emit(state.copyWith(
            phase: AppUpdatePhase.failed,
            message: '下载文件无法安装，请重新下载',
          ));
      }
    } catch (error) {
      if (mounted) {
        _emit(state.copyWith(
          phase: AppUpdatePhase.failed,
          message: _message(error),
        ));
      }
    }
  }

  /// 返回已经完整下载好的同版本 APK，没有则返回 null。
  /// 体积对不上就当没有，这能挡掉下到一半被杀进程留下的残文件。
  Future<File?> _cachedApk(AppRelease release) async {
    try {
      final directory = await _installer.updateDirectory();
      final file = File('${directory.path}/rabbit-${release.buildNumber}.apk');
      if (await file.exists() && await file.length() == release.apkSizeBytes) {
        return file;
      }
    } catch (_) {
      // 拿不到目录就当没缓存，走正常下载。
    }
    return null;
  }

  /// 清掉其他版本的残留 APK。一个包就是几十 MB，不清会一直堆在场区设备上。
  Future<void> _removeOtherApks(Directory directory, int keepBuild) async {
    try {
      if (!await directory.exists()) return;
      final keep = 'rabbit-$keepBuild.apk';
      await for (final entity in directory.list()) {
        if (entity is! File) continue;
        final name = entity.uri.pathSegments.last;
        if (name.startsWith('rabbit-') &&
            name.endsWith('.apk') &&
            name != keep) {
          await entity.delete();
        }
      }
    } catch (_) {
      // 清理失败不能阻断升级。
    }
  }

  Future<void> _deleteDownloadedApk() async {
    final file = _downloadedApk;
    _downloadedApk = null;
    if (file != null && await file.exists()) {
      await file.delete();
    }
  }

  String _message(Object error) {
    return switch (error) {
      AppUpdateException() => error.message,
      AppUpdateInstallException() => error.message,
      ApiException() => error.message,
      _ => '升级操作未完成，请稍后重试',
    };
  }

  void _emit(AppUpdateState next) {
    if (mounted) state = next;
  }

  @override
  void dispose() {
    _downloadCancelToken?.cancel('controller disposed');
    super.dispose();
  }
}
