import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

String _posixPath(String path) => path.replaceAll(r'\', '/');

void main() {
  test('unit and widget tests follow source ownership', () {
    const topLevelOwners = {
      'app',
      'architecture',
      'config',
      'data',
      'domain',
      'routing',
      'ui',
    };
    const dataTypes = {'repositories', 'services'};
    const uiTypes = {'screens', 'sheets', 'widgets', 'view_models'};
    final violations = <String>[];

    for (final entity in Directory('test').listSync(recursive: true)) {
      if (entity is! File || !entity.path.endsWith('_test.dart')) {
        continue;
      }

      final segments = _posixPath(entity.path).split('/');
      final owner = segments.length > 1 ? segments[1] : null;
      if (owner == null || !topLevelOwners.contains(owner)) {
        violations.add(entity.path);
        continue;
      }

      if (owner == 'data') {
        final dataType = segments.length > 2 ? segments[2] : null;
        if (!dataTypes.contains(dataType) || segments.length < 5) {
          violations.add(entity.path);
        }
        continue;
      }

      if (owner == 'domain' && segments.length < 4) {
        violations.add(entity.path);
        continue;
      }

      if (owner == 'ui') {
        final feature = segments.length > 2 ? segments[2] : null;
        final interfaceType = segments.length > 3 ? segments[3] : null;
        if (feature == 'core') {
          final isCoreFile = interfaceType == 'widgets' ||
              (interfaceType?.endsWith('_test.dart') ?? false);
          if (!isCoreFile) {
            violations.add(entity.path);
          }
        } else if (feature == null ||
            !uiTypes.contains(interfaceType) ||
            segments.length < 5) {
          violations.add(entity.path);
        }
      }
    }

    expect(
      violations,
      isEmpty,
      reason: 'Tests must use the same owner and interface types as lib/src: '
          '${violations.join(', ')}',
    );
  });

  test('device tests are grouped by business workflow', () {
    const businessOwners = {
      'auth',
      'batches',
      'cages',
      'houses',
      'nfc',
      'outbound',
      'profile',
      'rabbits',
      'reports',
      'reproduction',
      'settings',
    };
    final violations = <String>[];

    for (final entity
        in Directory('integration_test').listSync(recursive: true)) {
      if (entity is! File || !entity.path.endsWith('_test.dart')) {
        continue;
      }

      final segments = _posixPath(entity.path).split('/');
      final owner = segments.length > 1 ? segments[1] : null;
      if (segments.length < 3 || !businessOwners.contains(owner)) {
        violations.add(entity.path);
      }
    }

    expect(
      violations,
      isEmpty,
      reason: 'Device tests must live under a business workflow: '
          '${violations.join(', ')}',
    );
  });

  test('test support code keeps an explicit owner', () {
    const forbiddenDirectories = [
      'test/shared',
      'test/common',
      'test/utils',
      'integration_test/shared',
      'integration_test/common',
      'integration_test/utils',
    ];
    final violations = forbiddenDirectories
        .where((path) => Directory(path).existsSync())
        .toList();

    expect(
      violations,
      isEmpty,
      reason: 'Test support belongs to the nearest layer or business owner: '
          '${violations.join(', ')}',
    );
  });

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

  test('repositories and domain code are grouped by business', () {
    const groupedRoots = [
      'lib/src/data/repositories',
      'lib/src/domain',
    ];
    final violations = <String>[];

    for (final root in groupedRoots) {
      final directory = Directory(root);
      for (final entity in directory.listSync()) {
        if (entity is File && entity.path.endsWith('.dart')) {
          violations.add(entity.path);
        }
      }
    }

    expect(
      violations,
      isEmpty,
      reason: 'Business code must not sit at a grouped layer root: '
          '${violations.join(', ')}',
    );
  });

  test('lower-layer namespaces describe business capabilities', () {
    const forbiddenDirectories = [
      'lib/src/domain/models',
      'lib/src/domain/home',
      'lib/src/domain/dashboard',
      'lib/src/data/repositories/home',
      'lib/src/data/repositories/dashboard',
    ];
    final violations = forbiddenDirectories
        .where((path) => Directory(path).existsSync())
        .toList();

    expect(
      violations,
      isEmpty,
      reason: 'Lower layers use business names, not generic wrappers or UI '
          'surface names: ${violations.join(', ')}',
    );
  });

  test('shared code keeps an explicit owner', () {
    const forbiddenDirectories = [
      'lib/src/shared',
      'lib/src/ui/shared',
      'lib/src/data/shared',
      'lib/src/domain/shared',
    ];
    final violations = forbiddenDirectories
        .where((path) => Directory(path).existsSync())
        .toList();

    expect(
      violations,
      isEmpty,
      reason: 'Shared code belongs to an owning capability or ui/core: '
          '${violations.join(', ')}',
    );
  });

  test('feature UI separates screens, sheets, widgets, and view models', () {
    const featureDirectories = {
      'screens',
      'sheets',
      'widgets',
      'view_models',
    };
    const coreDirectories = {'theme.dart', 'widgets'};
    final violations = <String>[];
    final uiDirectory = Directory('lib/src/ui');

    for (final entity in uiDirectory.listSync(recursive: true)) {
      if (entity is! File || !entity.path.endsWith('.dart')) {
        continue;
      }
      final segments = _posixPath(entity.path).split('/');
      final uiIndex = segments.indexOf('ui');
      final hasFeatureAndType = uiIndex >= 0 && segments.length > uiIndex + 2;
      final feature = hasFeatureAndType ? segments[uiIndex + 1] : null;
      final interfaceType = hasFeatureAndType ? segments[uiIndex + 2] : null;
      final allowed = feature == 'core' ? coreDirectories : featureDirectories;

      if (feature == null ||
          interfaceType == null ||
          !allowed.contains(interfaceType)) {
        violations.add(entity.path);
        continue;
      }
    }

    expect(
      violations,
      isEmpty,
      reason: 'Feature UI files must match their interface type: '
          '${violations.join(', ')}',
    );
  });

  test('file names do not repeat directory namespaces', () {
    final rules = <String, RegExp>{
      'lib/src/config': RegExp(r'_config\.dart$'),
      'lib/src/data/repositories': RegExp(r'_repository\.dart$'),
      'lib/src/data/services': RegExp(r'_(service|store)\.dart$'),
      'lib/src/domain': RegExp(r'_models?\.dart$'),
      'lib/src/ui': RegExp(
        r'_(screen|sheet|widget|provider|providers|controller|view_model|theme)\.dart$',
      ),
    };
    final violations = <String>[];

    for (final entry in rules.entries) {
      final directory = Directory(entry.key);
      for (final entity in directory.listSync(recursive: true)) {
        if (entity is File && entry.value.hasMatch(entity.path)) {
          violations.add(entity.path);
        }
      }
    }

    expect(
      violations,
      isEmpty,
      reason: 'Parent directories already provide these namespaces: '
          '${violations.join(', ')}',
    );
  });
}
