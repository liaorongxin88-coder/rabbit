import 'dart:io';

import 'package:integration_test/integration_test_driver_extended.dart';

Future<void> main() async {
  final artifactDirectory =
      Platform.environment['RABBIT_ANDROID_E2E_ARTIFACT_DIR'] ??
          'build/android-e2e/latest';
  final directory = Directory(artifactDirectory);
  await directory.create(recursive: true);

  await integrationDriver(
    onScreenshot: (name, bytes, [args]) async {
      await File('${directory.path}/$name.png').writeAsBytes(bytes);
      return true;
    },
    responseDataCallback: (data) => writeResponseData(
      data,
      testOutputFilename: 'android_e2e_result',
      destinationDirectory: directory.path,
    ),
    writeResponseOnFailure: true,
  );
}
