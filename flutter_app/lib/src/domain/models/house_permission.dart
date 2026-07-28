class HousePermission {
  const HousePermission({
    required this.perms,
    required this.isAdmin,
  });

  final String perms;
  final bool isAdmin;

  bool get canView => true;

  bool get canEdit => isAdmin || perms == 'edit' || perms == 'control';

  bool get canControl => isAdmin || perms == 'control';

  bool get canManageMembers => isAdmin;

  String get roleLabel {
    if (isAdmin) {
      return '管理员';
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
