class UserProfile {
  const UserProfile({
    required this.userId,
    required this.userName,
    required this.openidBound,
    this.permissions = const <String>[],
    this.createTime,
    this.updateTime,
  });

  final int userId;
  final String userName;
  final bool openidBound;
  final List<String> permissions;
  final DateTime? createTime;
  final DateTime? updateTime;

  static UserProfile fromJson(Map<String, dynamic> json) {
    return UserProfile(
      userId: _intValue(json['userId']),
      userName: json['userName'] as String? ?? '',
      openidBound: json['openidBound'] == true,
      permissions: (json['permissions'] as List<dynamic>? ?? const <dynamic>[])
          .whereType<String>()
          .toList(growable: false),
      createTime: _dateValue(json['createTime']),
      updateTime: _dateValue(json['updateTime']),
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

  static DateTime? _dateValue(Object? value) {
    if (value is String && value.isNotEmpty) {
      return DateTime.tryParse(value);
    }
    if (value is int) {
      return DateTime.fromMillisecondsSinceEpoch(value);
    }
    if (value is num) {
      return DateTime.fromMillisecondsSinceEpoch(value.toInt());
    }
    return null;
  }
}
