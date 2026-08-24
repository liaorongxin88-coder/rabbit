import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/app_update/release.dart';

void main() {
  test('parses a published update payload', () {
    final update = AppUpdateCheck.fromJson({
      'hasUpdate': true,
      'forceUpdate': true,
      'id': 'apk_1',
      'channel': 'prod',
      'versionName': '1.0.3',
      'versionCode': 4004,
      'releaseNotes': '修了登录',
      'sizeBytes': 12,
      'sha256': 'abc',
      'downloadPath': '/api/app/updates/apk_1/apk',
    });

    expect(update.hasUpdate, isTrue);
    expect(update.forceUpdate, isTrue);
    expect(update.versionName, '1.0.3');
    expect(update.versionCode, 4004);
    expect(update.downloadPath, '/api/app/updates/apk_1/apk');
  });

  test('treats missing or empty payloads as no update', () {
    expect(AppUpdateCheck.fromJson(null).hasUpdate, isFalse);
    expect(AppUpdateCheck.fromJson({'hasUpdate': false}).hasUpdate, isFalse);
  });
}
