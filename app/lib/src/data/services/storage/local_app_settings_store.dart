import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/domain/models/local_app_settings.dart';

final localAppSettingsStoreProvider =
    Provider<LocalAppSettingsStore>((ref) => LocalAppSettingsStore());

class LocalAppSettingsStore {
  static const _themeModeKey = 'app.themeMode';
  static const _startRouteKey = 'app.startRoute';

  Future<LocalAppSettings> read() async {
    final prefs = await SharedPreferences.getInstance();
    return LocalAppSettings(
      themeMode:
          LocalAppSettings.themeModeFromName(prefs.getString(_themeModeKey)),
      startRoute:
          LocalAppSettings.normalizeStartRoute(prefs.getString(_startRouteKey)),
    );
  }

  Future<void> saveThemeMode(ThemeMode mode) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _themeModeKey,
      LocalAppSettings.themeModeName(mode),
    );
  }

  Future<void> saveStartRoute(String route) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _startRouteKey,
      LocalAppSettings.normalizeStartRoute(route),
    );
  }

  Future<void> clearLocalPreferences() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_themeModeKey);
    await prefs.remove(_startRouteKey);
  }
}
