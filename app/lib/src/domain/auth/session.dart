class AuthSession {
  const AuthSession({
    required this.token,
    required this.userId,
    required this.userName,
    required this.houseId,
    this.phoneBound = false,
    this.maskedPhone = '',
    this.hasPassword = true,
    this.permissions = const <String>[],
  });

  final String token;
  final int userId;
  final String userName;
  final int houseId;
  final bool phoneBound;
  final String maskedPhone;
  final bool hasPassword;
  final List<String> permissions;

  AuthSession copyWith({
    String? token,
    int? userId,
    String? userName,
    int? houseId,
    bool? phoneBound,
    String? maskedPhone,
    bool? hasPassword,
    List<String>? permissions,
  }) {
    return AuthSession(
      token: token ?? this.token,
      userId: userId ?? this.userId,
      userName: userName ?? this.userName,
      houseId: houseId ?? this.houseId,
      phoneBound: phoneBound ?? this.phoneBound,
      maskedPhone: maskedPhone ?? this.maskedPhone,
      hasPassword: hasPassword ?? this.hasPassword,
      permissions: permissions ?? this.permissions,
    );
  }

  static AuthSession fromJson(Map<String, dynamic> json) {
    return AuthSession(
      token: json['token'] as String? ?? '',
      userId: _intValue(json['userId']),
      userName: json['userName'] as String? ?? '',
      houseId: 0,
      phoneBound: json['phoneBound'] == true,
      maskedPhone: json['maskedPhone'] as String? ?? '',
      hasPassword:
          json['hasPassword'] is bool ? json['hasPassword'] == true : true,
      permissions: (json['permissions'] as List<dynamic>? ?? const <dynamic>[])
          .whereType<String>()
          .toList(growable: false),
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
}
