import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/app_update/release.dart';

void main() {
  test('uses Android build numbers instead of semantic version text', () {
    expect(isNewerBuild(4005, 4006), isTrue);
    expect(isNewerBuild(4005, 4005), isFalse);
    expect(isNewerBuild(4005, 4004), isFalse);
    expect(isNewerBuild(0, 4006), isFalse);
  });

  test('accepts a higher build even when the display version is lower', () {
    final check = AppUpdateCheck.fromJson(_available(
      currentBuild: 4005,
      buildNumber: 4006,
      versionName: '0.9.9',
    ));

    expect(check.updateAvailable, isTrue);
    expect(check.release!.versionName, '0.9.9');
    expect(check.release!.buildNumber, 4006);
  });

  test('rejects an available response with an equal or older build', () {
    for (final buildNumber in [4005, 4004]) {
      expect(
        () => AppUpdateCheck.fromJson(_available(
          currentBuild: 4005,
          buildNumber: buildNumber,
          versionName: '9.9.9',
        )),
        throwsFormatException,
      );
    }
  });

  test('rejects missing or unsafe release fields', () {
    final invalid = _available(
      currentBuild: 4005,
      buildNumber: 4006,
      versionName: '1.0.5',
    )..remove('sha256');
    expect(() => AppUpdateCheck.fromJson(invalid), throwsFormatException);

    final insecure = _available(
      currentBuild: 4005,
      buildNumber: 4006,
      versionName: '1.0.5',
    )..['downloadUrl'] = 'http://downloads.example.test/rabbit.apk';
    expect(() => AppUpdateCheck.fromJson(insecure), throwsFormatException);
  });

  test('does not require release fields when the app is already current', () {
    final check = AppUpdateCheck.fromJson({
      'currentBuild': 4005,
      'updateAvailable': false,
    });

    expect(check.updateAvailable, isFalse);
    expect(check.release, isNull);
  });
}

Map<String, dynamic> _available({
  required int currentBuild,
  required int buildNumber,
  required String versionName,
}) {
  return {
    'currentBuild': currentBuild,
    'updateAvailable': true,
    'buildNumber': buildNumber,
    'versionName': versionName,
    'downloadUrl': 'https://downloads.example.test/rabbit.apk',
    'sha256':
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    'apkSizeBytes': 123456,
    'releaseNotes': '修复现场升级流程',
    'forceUpdate': false,
  };
}
