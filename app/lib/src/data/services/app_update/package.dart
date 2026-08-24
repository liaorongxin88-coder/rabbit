import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';

import 'package:rabbit_flutter/src/config/app.dart';
import 'package:rabbit_flutter/src/domain/app_update/release.dart';

final appPackageReaderProvider = Provider<AppPackageReader>(
  (_) => const DeviceAppPackageReader(),
);

final appPackageIdentityProvider = FutureProvider<AppPackageIdentity>((ref) {
  return ref.watch(appPackageReaderProvider).current();
});

abstract class AppPackageReader {
  Future<AppPackageIdentity> current();
}

class DeviceAppPackageReader implements AppPackageReader {
  const DeviceAppPackageReader();

  @override
  Future<AppPackageIdentity> current() async {
    final info = await PackageInfo.fromPlatform();
    return AppPackageIdentity(
      versionName: info.version,
      versionCode: int.tryParse(info.buildNumber) ?? 0,
      channel: AppConfig.updateChannel,
      packageName: info.packageName,
    );
  }
}
