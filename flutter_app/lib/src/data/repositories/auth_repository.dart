import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/auth_session.dart';
import 'package:rabbit_flutter/src/domain/models/user_profile.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(ref.watch(apiClientProvider));
});

class AuthRepository {
  AuthRepository(this._api);

  final ApiClient _api;

  Stream<void> get unauthorizedEvents => _api.unauthorizedEvents;

  Future<AuthSession> login(String userName, String password) {
    return _authenticate('/api/auth/login', userName, password);
  }

  Future<AuthSession> register(String userName, String password) {
    return _authenticate('/api/auth/register', userName, password);
  }

  Future<AuthSession> validateSession(AuthSession localSession) async {
    final profile = await _api.get<UserProfile>(
      '/api/auth/me',
      decode: _decodeProfile,
    );
    return localSession.copyWith(
      userId: profile.userId,
      userName: profile.userName,
      houseId: profile.userId == localSession.userId ? localSession.houseId : 0,
    );
  }

  Future<AuthSession> _authenticate(
    String path,
    String userName,
    String password,
  ) {
    return _api.post<AuthSession>(
      path,
      body: {
        'userName': userName,
        'password': password,
      },
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('登录结果格式不正确');
        }
        final session = AuthSession.fromJson(Map<String, dynamic>.from(data));
        if (session.token.trim().isEmpty || session.userId <= 0) {
          throw const ApiException('登录结果格式不正确');
        }
        return session;
      },
    );
  }

  static UserProfile _decodeProfile(Object? data) {
    if (data is! Map) {
      throw const ApiException('账号资料格式不正确');
    }
    final profile = UserProfile.fromJson(Map<String, dynamic>.from(data));
    if (profile.userId <= 0 || profile.userName.trim().isEmpty) {
      throw const ApiException('账号资料格式不正确');
    }
    return profile;
  }
}
