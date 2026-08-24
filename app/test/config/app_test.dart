import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/config/app.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('loads the backend url for the selected build environment', () async {
    const definedBaseUrl = String.fromEnvironment(
      'RABBIT_API_BASE_URL',
      defaultValue: '',
    );

    await AppConfig.load();

    expect(AppConfig.carrierAuthEnabled, isFalse);
    expect(
      AppConfig.updateChannel,
      AppConfig.buildEnv == 'prod' || AppConfig.buildEnv == 'release'
          ? 'prod'
          : AppConfig.buildEnv == 'test'
              ? 'test'
              : 'dev',
    );
    expect(
      AppConfig.expectedApplicationIdFor('dev'),
      'com.rabbit.app.flutter.dev',
    );
    expect(
      AppConfig.expectedApplicationIdFor('test'),
      'com.rabbit.app.flutter.test',
    );
    expect(
      AppConfig.expectedApplicationIdFor('prod'),
      'com.rabbit.app.flutter',
    );

    if (definedBaseUrl.trim().isNotEmpty) {
      expect(AppConfig.defaultBaseUrl, definedBaseUrl);
      return;
    }

    if (AppConfig.buildEnv == 'prod' || AppConfig.buildEnv == 'release') {
      expect(AppConfig.defaultBaseUrl, 'https://api.dzht.top');
      return;
    }

    const assetPath = AppConfig.buildEnv == 'test'
        ? 'config/env/test.env'
        : 'config/env/dev.env';
    final content = await rootBundle.loadString(assetPath);
    expect(AppConfig.defaultBaseUrl, _readEnvValue(content));
  });
}

String _readEnvValue(String content) {
  for (final rawLine in content.split('\n')) {
    final line = rawLine.trim();
    if (line.isEmpty || line.startsWith('#')) {
      continue;
    }

    final separatorIndex = line.indexOf('=');
    if (separatorIndex <= 0) {
      continue;
    }

    final key = line.substring(0, separatorIndex).trim();
    if (key == 'RABBIT_API_BASE_URL') {
      return line.substring(separatorIndex + 1).trim();
    }
  }
  return '';
}
