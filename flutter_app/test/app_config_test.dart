import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/config/app_config.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('loads backend url from dart define or bundled dev env asset', () async {
    const definedBaseUrl = String.fromEnvironment(
      'RABBIT_API_BASE_URL',
      defaultValue: '',
    );

    await AppConfig.load();

    if (definedBaseUrl.trim().isNotEmpty) {
      expect(AppConfig.defaultBaseUrl, definedBaseUrl);
      return;
    }

    final content = await rootBundle.loadString('config/env/dev.env');
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
