import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/config/app.dart';
import 'package:rabbit_flutter/src/data/repositories/app_update/repository.dart';
import 'package:rabbit_flutter/src/data/services/app_update/installer.dart';
import 'package:rabbit_flutter/src/data/services/app_update/package.dart';
import 'package:rabbit_flutter/src/data/services/app_update/prefs.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/app_update/release.dart';

final appUpdateControllerProvider =
    StateNotifierProvider<AppUpdateController, AppUpdateState>((ref) {
  return AppUpdateController(
    repository: ref.watch(appUpdateRepositoryProvider),
    packageReader: ref.watch(appPackageReaderProvider),
    installer: ref.watch(appApkInstallerProvider),
    prefs: ref.watch(appUpdatePrefsProvider),
  );
});

enum AppUpdatePhase {
  idle,
  checking,
  available,
  downloading,
  ready,
  failed,
}

class AppUpdateState {
  const AppUpdateState({
    required this.phase,
    this.identity,
    this.update = AppUpdateCheck.none,
    this.progress = 0,
    this.localFile,
    this.error,
    this.manual = false,
  });

  final AppUpdatePhase phase;
  final AppPackageIdentity? identity;
  final AppUpdateCheck update;
  final double progress;
  final File? localFile;
  final String? error;
  final bool manual;

  bool get shouldPrompt {
    if (!update.hasUpdate) {
      return false;
    }
    return phase == AppUpdatePhase.available ||
        phase == AppUpdatePhase.downloading ||
        phase == AppUpdatePhase.ready ||
        (phase == AppUpdatePhase.failed && (manual || update.forceUpdate));
  }
}

class AppUpdateController extends StateNotifier<AppUpdateState> {
  AppUpdateController({
    required AppUpdateRepository repository,
    required AppPackageReader packageReader,
    required AppApkInstaller installer,
    required AppUpdatePrefs prefs,
  })  : _repository = repository,
        _packageReader = packageReader,
        _installer = installer,
        _prefs = prefs,
        super(const AppUpdateState(phase: AppUpdatePhase.idle));

  final AppUpdateRepository _repository;
  final AppPackageReader _packageReader;
  final AppApkInstaller _installer;
  final AppUpdatePrefs _prefs;
  CancelToken? _downloadToken;
  DateTime? _lastSilentCheck;

  Future<void> checkSilently() async {
    final lastCheck = _lastSilentCheck;
    if (lastCheck != null &&
        DateTime.now().difference(lastCheck) < const Duration(minutes: 15)) {
      return;
    }
    await check(manual: false);
  }

  Future<void> check({required bool manual}) async {
    if (state.phase == AppUpdatePhase.checking ||
        state.phase == AppUpdatePhase.downloading) {
      return;
    }
    state = AppUpdateState(
      phase: AppUpdatePhase.checking,
      identity: state.identity,
      manual: manual,
    );
    try {
      final identity = await _packageReader.current();
      if (identity.versionCode < 1) {
        throw const AppUpdateException('读不到当前内部版本号，无法检查更新');
      }
      if (identity.packageName !=
          AppConfig.expectedApplicationIdFor(identity.channel)) {
        throw AppUpdateException(
          '当前安装包与 ${identity.channel} 渠道不匹配，已停止检查更新',
        );
      }
      final update = await _repository.check(
        channel: identity.channel,
        versionCode: identity.versionCode,
      );
      if (!manual) {
        _lastSilentCheck = DateTime.now();
      }
      if (!update.hasUpdate) {
        state = AppUpdateState(
          phase: AppUpdatePhase.idle,
          identity: identity,
          manual: manual,
        );
        return;
      }
      if (!manual && !update.forceUpdate) {
        final skipped = await _prefs.readSkippedVersionCode();
        if (skipped != null && skipped == update.versionCode) {
          state = AppUpdateState(
            phase: AppUpdatePhase.idle,
            identity: identity,
            update: update,
          );
          return;
        }
      }
      state = AppUpdateState(
        phase: AppUpdatePhase.available,
        identity: identity,
        update: update,
        manual: manual,
      );
    } catch (error) {
      if (!manual) {
        state = AppUpdateState(
          phase: AppUpdatePhase.idle,
          identity: state.identity,
        );
        return;
      }
      state = AppUpdateState(
        phase: AppUpdatePhase.failed,
        identity: state.identity,
        error: _message(error),
        manual: true,
      );
    }
  }

  Future<void> startDownload() async {
    final update = state.update;
    if (!update.hasUpdate) {
      return;
    }
    _downloadToken?.cancel();
    final token = CancelToken();
    _downloadToken = token;
    state = AppUpdateState(
      phase: AppUpdatePhase.downloading,
      identity: state.identity,
      update: update,
      progress: 0,
      manual: state.manual,
    );
    try {
      final file = await _repository.download(
        update: update,
        cancelToken: token,
        onProgress: (progress) {
          if (!mounted) return;
          state = AppUpdateState(
            phase: AppUpdatePhase.downloading,
            identity: state.identity,
            update: update,
            progress: progress,
            manual: state.manual,
          );
        },
      );
      if (!mounted) return;
      state = AppUpdateState(
        phase: AppUpdatePhase.ready,
        identity: state.identity,
        update: update,
        progress: 1,
        localFile: file,
        manual: state.manual,
      );
    } catch (error) {
      if (token.isCancelled || !mounted) {
        return;
      }
      state = AppUpdateState(
        phase: AppUpdatePhase.failed,
        identity: state.identity,
        update: update,
        error: _message(error),
        manual: state.manual,
      );
    }
  }

  Future<void> install() async {
    final file = state.localFile;
    if (file == null) {
      return;
    }
    try {
      await _installer.install(file.path);
    } catch (error) {
      state = AppUpdateState(
        phase: AppUpdatePhase.failed,
        identity: state.identity,
        update: state.update,
        localFile: file,
        error: _message(error),
        manual: true,
      );
    }
  }

  Future<void> dismiss() async {
    if (state.update.forceUpdate) {
      return;
    }
    _downloadToken?.cancel();
    final versionCode = state.update.versionCode;
    if (versionCode != null) {
      await _prefs.skipVersionCode(versionCode);
    }
    state = AppUpdateState(
      phase: AppUpdatePhase.idle,
      identity: state.identity,
    );
  }

  @override
  void dispose() {
    _downloadToken?.cancel();
    super.dispose();
  }

  static String _message(Object error) {
    if (error is ApiException) {
      return error.message;
    }
    if (error is AppUpdateException) {
      return error.message;
    }
    if (error is PlatformException) {
      return error.message?.trim().isNotEmpty == true
          ? error.message!
          : error.code;
    }
    return error.toString();
  }
}
