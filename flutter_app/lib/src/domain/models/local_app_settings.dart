import 'package:flutter/material.dart';

class LocalAppSettings {
  const LocalAppSettings({
    required this.themeMode,
    required this.startRoute,
  });

  final ThemeMode themeMode;
  final String startRoute;

  static const defaultSettings = LocalAppSettings(
    themeMode: ThemeMode.system,
    startRoute: '/',
  );

  LocalAppSettings copyWith({
    ThemeMode? themeMode,
    String? startRoute,
  }) {
    return LocalAppSettings(
      themeMode: themeMode ?? this.themeMode,
      startRoute: startRoute ?? this.startRoute,
    );
  }

  String get themeLabel {
    switch (themeMode) {
      case ThemeMode.light:
        return '浅色';
      case ThemeMode.dark:
        return '深色';
      case ThemeMode.system:
        return '跟随系统';
    }
  }

  String get startRouteLabel {
    switch (startRoute) {
      case '/houses':
        return '兔舍';
      case '/dashboard':
        return '数据面板';
      case '/profile':
        return '我的';
      case '/':
      default:
        return '首页';
    }
  }

  static ThemeMode themeModeFromName(String? value) {
    switch (value) {
      case 'light':
        return ThemeMode.light;
      case 'dark':
        return ThemeMode.dark;
      case 'system':
      default:
        return ThemeMode.system;
    }
  }

  static String themeModeName(ThemeMode mode) {
    switch (mode) {
      case ThemeMode.light:
        return 'light';
      case ThemeMode.dark:
        return 'dark';
      case ThemeMode.system:
        return 'system';
    }
  }

  static String normalizeStartRoute(String? value) {
    switch (value) {
      case '/houses':
      case '/dashboard':
      case '/profile':
        return value!;
      case '/':
      default:
        return '/';
    }
  }
}
