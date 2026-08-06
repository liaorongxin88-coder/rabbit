class HouseMember {
  const HouseMember({
    required this.userId,
    required this.userName,
    required this.perms,
    required this.isAdmin,
    this.joinTime,
  });

  final int userId;
  final String userName;
  final String perms;
  final bool isAdmin;
  final DateTime? joinTime;

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

  String get permLabel {
    switch (perms) {
      case 'view':
        return '只读';
      case 'edit':
        return '生产操作';
      case 'control':
        return '设备管理';
      default:
        return perms;
    }
  }

  static HouseMember fromJson(Map<String, dynamic> json) {
    return HouseMember(
      userId: _intValue(json['userId']),
      userName: json['userName'] as String? ?? '',
      perms: json['perms'] as String? ?? 'view',
      isAdmin: _boolValue(json['isAdmin']),
      joinTime: _dateValue(json['joinTime']),
    );
  }

  static int _intValue(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    if (value is String) {
      return int.tryParse(value) ?? 0;
    }
    return 0;
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

  static DateTime? _dateValue(Object? value) {
    if (value is String && value.isNotEmpty) {
      return DateTime.tryParse(value);
    }
    return null;
  }
}

class UserSearchItem {
  const UserSearchItem({
    required this.userId,
    required this.userName,
  });

  final int userId;
  final String userName;

  static UserSearchItem fromJson(Map<String, dynamic> json) {
    return UserSearchItem(
      userId: HouseMember._intValue(json['userId']),
      userName: json['userName'] as String? ?? '',
    );
  }
}
