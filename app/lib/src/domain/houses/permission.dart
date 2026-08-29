class HousePermission {
  const HousePermission({
    required this.perms,
    required this.isAdmin,
    this.role = 'VIEWER',
    this.permissions = const <String>[],
  });

  final String perms;
  final bool isAdmin;
  final String role;
  final List<String> permissions;

  bool hasPermission(String permission) => permissions.contains(permission);

  bool get canView => true;

  bool get canEdit => isAdmin || perms == 'edit' || perms == 'control';

  bool get canControl => isAdmin || perms == 'control';

  bool get canAddSales => isAdmin || hasPermission('rabbit:sales:add');

  bool get canAddRabbit => isAdmin || hasPermission('rabbit:rabbits:add');

  bool get canQueryBatches => isAdmin || hasPermission('rabbit:batches:query');

  bool get canEditBatches => isAdmin || hasPermission('rabbit:batches:edit');

  bool get canEditHouse => isAdmin || hasPermission('rabbit:houses:edit');

  bool get canManageMembers =>
      hasPermission('rabbit:house-members:list') || isAdmin;

  bool get canViewAudit => hasPermission('rabbit:audit:list') || isAdmin;

  String get roleLabel {
    if (isAdmin) {
      return '所有者';
    }
    if (perms == 'view') {
      return '游客';
    }
    if (perms == 'control') {
      return '设备管理员';
    }
    return '生产人员';
  }

  static HousePermission fromJson(Map<String, dynamic> json) {
    return HousePermission(
      perms: json['perms'] as String? ?? 'view',
      isAdmin: _boolValue(json['isAdmin']),
      role: json['role'] as String? ?? 'VIEWER',
      permissions: (json['permissions'] as List<dynamic>? ?? const <dynamic>[])
          .whereType<String>()
          .toList(growable: false),
    );
  }

  static bool _boolValue(Object? value) {
    if (value is bool) {
      return value;
    }
    if (value is num) {
      return value != 0;
    }
    if (value is String) {
      return value == 'true' || value == '1';
    }
    return false;
  }
}
