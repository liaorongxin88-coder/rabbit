import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final appApkInstallerProvider = Provider<AppApkInstaller>(
  (_) => const DeviceAppApkInstaller(),
);

abstract class AppApkInstaller {
  Future<void> install(String filePath);
}

class DeviceAppApkInstaller implements AppApkInstaller {
  const DeviceAppApkInstaller();

  static const _channel = MethodChannel('com.rabbit.app.flutter/apk_installer');

  @override
  Future<void> install(String filePath) async {
    await _channel.invokeMethod<void>('install', {'path': filePath});
  }
}
