import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';

void main() {
  test('parses action permissions returned by the backend', () {
    final permission = HousePermission.fromJson(<String, dynamic>{
      'perms': 'control',
      'isAdmin': false,
      'role': 'MANAGER',
      'permissions': <String>[
        'rabbit:rabbits:list',
        'rabbit:rabbits:control',
      ],
    });

    expect(permission.role, 'MANAGER');
    expect(permission.hasPermission('rabbit:rabbits:list'), isTrue);
    expect(permission.hasPermission('rabbit:rabbits:control'), isTrue);
    expect(permission.hasPermission('rabbit:house-members:list'), isFalse);
    expect(permission.canManageMembers, isFalse);
  });

  test('keeps legacy admin compatibility when permissions are absent', () {
    final permission = HousePermission.fromJson(<String, dynamic>{
      'perms': 'control',
      'isAdmin': 1,
    });

    expect(permission.role, 'VIEWER');
    expect(permission.permissions, isEmpty);
    expect(permission.canManageMembers, isTrue);
  });
}
