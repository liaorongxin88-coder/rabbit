import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

final appUpdatePrefsProvider = Provider<AppUpdatePrefs>(
  (_) => const AppUpdatePrefs(),
);

class AppUpdatePrefs {
  const AppUpdatePrefs();

  static const skippedVersionCodeKey = 'app_update_skipped_version_code';

  Future<int?> readSkippedVersionCode() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getInt(skippedVersionCodeKey);
  }

  Future<void> skipVersionCode(int versionCode) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(skippedVersionCodeKey, versionCode);
  }
}
