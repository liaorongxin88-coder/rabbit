import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final appUpdateInstallerProvider = Provider<AppUpdateInstaller>(
  (_) => const MethodChannelAppUpdateInstaller(),
);

class AppVersion {
  const AppVersion({
    required this.versionName,
    required this.buildNumber,
  });

  final String versionName;
  final int buildNumber;

  String get label => '$versionName+$buildNumber';
}

enum AppInstallResult {
  installerOpened,
  permissionRequired,
  hashMismatch,
  invalidApk,
}

abstract class AppUpdateInstaller {
  Future<AppVersion> currentVersion();

  Future<Directory> updateDirectory();

  Future<bool> canInstallPackages();

  Future<void> openInstallPermissionSettings();

  Future<AppInstallResult> installApk({
    required String path,
    required String sha256,
  });
}

class MethodChannelAppUpdateInstaller implements AppUpdateInstaller {
  const MethodChannelAppUpdateInstaller({
    MethodChannel channel = const MethodChannel(_channelName),
  }) : _channel = channel;

  static const _channelName = 'com.rabbit.app.flutter/app_update';

  final MethodChannel _channel;

  @override
  Future<AppVersion> currentVersion() async {
    try {
      final result = await _channel.invokeMapMethod<String, dynamic>('appInfo');
      final versionName = result?['versionName'] as String?;
      final buildNumber = _positiveInt(result?['buildNumber']);
      if (versionName == null ||
          versionName.trim().isEmpty ||
          buildNumber == null) {
        throw const AppUpdateInstallException('当前安装包版本信息无效');
      }
      return AppVersion(
        versionName: versionName.trim(),
        buildNumber: buildNumber,
      );
    } on MissingPluginException {
      throw const AppUpdateInstallException('当前设备不支持应用更新');
    } on PlatformException {
      throw const AppUpdateInstallException('无法读取当前安装包版本');
    }
  }

  @override
  Future<Directory> updateDirectory() async {
    try {
      final path = await _channel.invokeMethod<String>('updateDirectory');
      if (path == null || path.trim().isEmpty) {
        throw const AppUpdateInstallException('无法创建升级下载目录');
      }
      return Directory(path);
    } on MissingPluginException {
      throw const AppUpdateInstallException('当前设备不支持应用更新');
    } on PlatformException {
      throw const AppUpdateInstallException('无法创建升级下载目录');
    }
  }

  @override
  Future<bool> canInstallPackages() async {
    try {
      return await _channel.invokeMethod<bool>('canInstallPackages') ?? false;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }

  @override
  Future<void> openInstallPermissionSettings() async {
    try {
      await _channel.invokeMethod<void>('openInstallPermissionSettings');
    } on MissingPluginException {
      throw const AppUpdateInstallException('当前设备不支持安装授权');
    } on PlatformException {
      throw const AppUpdateInstallException('无法打开安装授权页面');
    }
  }

  @override
  Future<AppInstallResult> installApk({
    required String path,
    required String sha256,
  }) async {
    try {
      final result = await _channel.invokeMethod<String>('installApk', {
        'path': path,
        'sha256': sha256,
      });
      return switch (result) {
        'INSTALLER_OPENED' => AppInstallResult.installerOpened,
        'PERMISSION_REQUIRED' => AppInstallResult.permissionRequired,
        'HASH_MISMATCH' => AppInstallResult.hashMismatch,
        _ => AppInstallResult.invalidApk,
      };
    } on MissingPluginException {
      throw const AppUpdateInstallException('当前设备不支持应用更新');
    } on PlatformException {
      throw const AppUpdateInstallException('无法启动系统安装器');
    }
  }
}

class AppUpdateInstallException implements Exception {
  const AppUpdateInstallException(this.message);

  final String message;

  @override
  String toString() => message;
}

int? _positiveInt(Object? value) {
  if (value is int && value > 0) {
    return value;
  }
  if (value is num && value > 0 && value == value.roundToDouble()) {
    return value.toInt();
  }
  if (value is String) {
    final parsed = int.tryParse(value);
    return parsed != null && parsed > 0 ? parsed : null;
  }
  return null;
}
