import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class AppConfig {
  static const buildEnv = String.fromEnvironment(
    'RABBIT_BUILD_ENV',
    defaultValue: 'dev',
  );

  static const _definedBaseUrl = String.fromEnvironment(
    'RABBIT_API_BASE_URL',
    defaultValue: '',
  );

  static String? _envFileBaseUrl;

  static String get defaultBaseUrl {
    final definedBaseUrl = _definedBaseUrl.trim();
    if (definedBaseUrl.isNotEmpty) {
      return definedBaseUrl;
    }

    final envFileBaseUrl = _envFileBaseUrl?.trim();
    if (envFileBaseUrl != null && envFileBaseUrl.isNotEmpty) {
      return envFileBaseUrl;
    }

    return switch (buildEnv) {
      'release' => 'https://api.dzht.top',
      _ => 'http://10.0.2.2:8080',
    };
  }

  static Future<void> load() async {
    if (_definedBaseUrl.trim().isNotEmpty) {
      return;
    }

    final content = await _tryLoadEnvFile(_envAssetPath);
    if (content == null) {
      return;
    }

    _envFileBaseUrl = _readEnvValue(content, 'RABBIT_API_BASE_URL');
  }

  static String get _envAssetPath {
    return switch (buildEnv) {
      'test' => 'config/env/test.env',
      _ => 'config/env/dev.env',
    };
  }

  static Future<String?> _tryLoadEnvFile(String path) async {
    try {
      return await rootBundle.loadString(path);
    } on FlutterError {
      return null;
    }
  }

  static String? _readEnvValue(String content, String key) {
    for (final rawLine in content.split('\n')) {
      final line = rawLine.trim();
      if (line.isEmpty || line.startsWith('#')) {
        continue;
      }

      final separatorIndex = line.indexOf('=');
      if (separatorIndex <= 0) {
        continue;
      }

      final envKey = line.substring(0, separatorIndex).trim();
      if (envKey != key) {
        continue;
      }

      return line.substring(separatorIndex + 1).trim();
    }
    return null;
  }
}
