import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';

void main() {
  test('parses action permissions returned by the backend', () {
    final permission = HousePermission.fromJson(<String, dynamic>{
      'perms': 'control',
      'isAdmin': false,
      'role': 'MANAGER',
      'permissions': <String>[
        'rabbit:rabbits:list',
        'rabbit:rabbits:control',
        'rabbit:rabbits:add',
        'rabbit:batches:query',
        'rabbit:batches:edit',
      ],
    });

    expect(permission.role, 'MANAGER');
    expect(permission.hasPermission('rabbit:rabbits:list'), isTrue);
    expect(permission.hasPermission('rabbit:rabbits:control'), isTrue);
    expect(permission.hasPermission('rabbit:house-members:list'), isFalse);
    expect(permission.canAddRabbit, isTrue);
    expect(permission.canQueryBatches, isTrue);
    expect(permission.canEditBatches, isTrue);
    expect(permission.canManageMembers, isFalse);
  });

  test('keeps legacy admin compatibility when permissions are absent', () {
    final permission = HousePermission.fromJson(<String, dynamic>{
      'perms': 'control',
      'isAdmin': 1,
    });

    expect(permission.role, 'VIEWER');
    expect(permission.permissions, isEmpty);
    expect(permission.canAddRabbit, isTrue);
    expect(permission.canQueryBatches, isTrue);
    expect(permission.canEditBatches, isTrue);
    expect(permission.canManageMembers, isTrue);
  });

  test('legacy edit flag does not grant source-specific permissions', () {
    const permission = HousePermission(perms: 'edit', isAdmin: false);

    expect(permission.canEdit, isTrue);
    expect(permission.canAddRabbit, isFalse);
    expect(permission.canQueryBatches, isFalse);
    expect(permission.canEditBatches, isFalse);
  });
}
