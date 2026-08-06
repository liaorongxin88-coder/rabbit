class AuthSession {
  const AuthSession({
    required this.token,
    required this.userId,
    required this.userName,
    required this.houseId,
    this.permissions = const <String>[],
  });

  final String token;
  final int userId;
  final String userName;
  final int houseId;
  final List<String> permissions;

  AuthSession copyWith({
    String? token,
    int? userId,
    String? userName,
    int? houseId,
    List<String>? permissions,
  }) {
    return AuthSession(
      token: token ?? this.token,
      userId: userId ?? this.userId,
      userName: userName ?? this.userName,
      houseId: houseId ?? this.houseId,
      permissions: permissions ?? this.permissions,
    );
  }

  static AuthSession fromJson(Map<String, dynamic> json) {
    return AuthSession(
      token: json['token'] as String? ?? '',
      userId: _intValue(json['userId']),
      userName: json['userName'] as String? ?? '',
      houseId: 0,
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
