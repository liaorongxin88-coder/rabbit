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

  static const _definedCarrierAuthEnabled = String.fromEnvironment(
    'RABBIT_CARRIER_AUTH_ENABLED',
    defaultValue: '',
  );

  static String? _envFileBaseUrl;
  static bool? _envFileCarrierAuthEnabled;

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
      'prod' || 'release' => 'https://api.dzht.top',
      _ => 'http://10.0.2.2:8080',
    };
  }

  static bool get carrierAuthEnabled {
    final defined = _boolValue(_definedCarrierAuthEnabled);
    if (defined != null) {
      return defined;
    }
    return _envFileCarrierAuthEnabled ?? false;
  }

  static Future<void> load() async {
    final envAssetPath = _envAssetPath;
    if (envAssetPath == null) {
      return;
    }
    final content = await _tryLoadEnvFile(envAssetPath);
    if (content == null) {
      return;
    }

    if (_definedBaseUrl.trim().isEmpty) {
      _envFileBaseUrl = _readEnvValue(content, 'RABBIT_API_BASE_URL');
    }
    if (_definedCarrierAuthEnabled.trim().isEmpty) {
      _envFileCarrierAuthEnabled = _boolValue(
        _readEnvValue(content, 'RABBIT_CARRIER_AUTH_ENABLED'),
      );
    }
  }

  static String? get _envAssetPath {
    return switch (buildEnv) {
      'dev' => 'config/env/dev.env',
      'test' => 'config/env/test.env',
      _ => null,
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

  static bool? _boolValue(String? value) {
    return switch (value?.trim().toLowerCase()) {
      'true' || '1' || 'yes' || 'on' => true,
      'false' || '0' || 'no' || 'off' => false,
      _ => null,
    };
  }
}
