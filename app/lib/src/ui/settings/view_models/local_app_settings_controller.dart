import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/storage/local_app_settings_store.dart';
import 'package:rabbit_flutter/src/domain/models/local_app_settings.dart';

final localAppSettingsControllerProvider = StateNotifierProvider<
    LocalAppSettingsController, AsyncValue<LocalAppSettings>>((ref) {
  return LocalAppSettingsController(
    ref.watch(localAppSettingsStoreProvider),
  );
});

class LocalAppSettingsController
    extends StateNotifier<AsyncValue<LocalAppSettings>> {
  LocalAppSettingsController(this._store) : super(const AsyncValue.loading());

  final LocalAppSettingsStore _store;

  Future<void> restore() async {
    try {
      state = AsyncValue.data(await _store.read());
    } catch (error, stackTrace) {
      state = AsyncValue.error(error, stackTrace);
    }
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    final current = state.valueOrNull ?? LocalAppSettings.defaultSettings;
    state = AsyncValue.data(current.copyWith(themeMode: mode));
    await _store.saveThemeMode(mode);
  }

  Future<void> setStartRoute(String route) async {
    final normalized = LocalAppSettings.normalizeStartRoute(route);
    final current = state.valueOrNull ?? LocalAppSettings.defaultSettings;
    state = AsyncValue.data(current.copyWith(startRoute: normalized));
    await _store.saveStartRoute(normalized);
  }

  Future<void> clearLocalPreferences() async {
    await _store.clearLocalPreferences();
    state = const AsyncValue.data(LocalAppSettings.defaultSettings);
  }
}
