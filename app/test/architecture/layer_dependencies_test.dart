import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('lower layers do not import UI code', () {
    const lowerLayers = ['config', 'data', 'domain'];
    final violations = <String>[];

    for (final layer in lowerLayers) {
      final directory = Directory('lib/src/$layer');
      for (final entity in directory.listSync(recursive: true)) {
        if (entity is! File || !entity.path.endsWith('.dart')) {
          continue;
        }
        if (entity.readAsStringSync().contains(
              'package:rabbit_flutter/src/ui/',
            )) {
          violations.add(entity.path);
        }
      }
    }

    expect(
      violations,
      isEmpty,
      reason: 'Lower layers must not depend on UI: ${violations.join(', ')}',
    );
  });
}
