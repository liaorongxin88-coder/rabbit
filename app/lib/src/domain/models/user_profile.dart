class UserProfile {
  const UserProfile({
    required this.userId,
    required this.userName,
    required this.openidBound,
    this.userCode = '',
    this.phoneBound = false,
    this.maskedPhone = '',
    this.hasPassword = true,
    this.permissions = const <String>[],
    this.createTime,
    this.updateTime,
  });

  final int userId;
  final String userName;

  /// 兔号：自己看得见、可以报给别人拉自己进兔舍的唯一标识。
  /// 老后端不返回这个字段时为空串，界面需要自己兼容。
  final String userCode;
  final bool openidBound;
  final bool phoneBound;
  final String maskedPhone;
  final bool hasPassword;
  final List<String> permissions;
  final DateTime? createTime;
  final DateTime? updateTime;

  static UserProfile fromJson(Map<String, dynamic> json) {
    return UserProfile(
      userId: _intValue(json['userId']),
      userName: json['userName'] as String? ?? '',
      userCode: json['userCode'] as String? ?? '',
      openidBound: json['openidBound'] == true,
      phoneBound: json['phoneBound'] == true,
      maskedPhone: json['maskedPhone'] as String? ?? '',
      hasPassword:
          json['hasPassword'] is bool ? json['hasPassword'] == true : true,
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
