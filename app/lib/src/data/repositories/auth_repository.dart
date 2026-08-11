import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/auth_session.dart';
import 'package:rabbit_flutter/src/domain/models/carrier_auth.dart';
import 'package:rabbit_flutter/src/domain/models/sms_code_delivery.dart';
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

  Future<SmsCodeDelivery> sendSmsCode(String phone) {
    return sendSmsCodeForPurpose(phone, 'LOGIN_OR_REGISTER');
  }

  Future<SmsCodeDelivery> sendSmsCodeForPurpose(
    String phone,
    String purpose,
  ) {
    return _api.post<SmsCodeDelivery>(
      '/api/auth/sms/code',
      body: {'phone': phone, 'purpose': purpose},
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('验证码发送结果格式不正确');
        }
        final delivery = SmsCodeDelivery.fromJson(
          Map<String, dynamic>.from(data),
        );
        if (delivery.expiresInSeconds <= 0 || delivery.retryAfterSeconds <= 0) {
          throw const ApiException('验证码发送结果格式不正确');
        }
        return delivery;
      },
    );
  }

  Future<void> resetPasswordBySms({
    required String phone,
    required String code,
    required String newPassword,
  }) {
    return _api.post<void>(
      '/api/auth/sms/reset-password',
      body: {
        'phone': phone,
        'code': code,
        'newPassword': newPassword,
      },
      decode: (_) {},
    );
  }

  Future<AuthSession> loginWithPhone(String phone, String code) {
    return _api.post<AuthSession>(
      '/api/auth/sms/login',
      body: {
        'phone': phone,
        'code': code,
        'purpose': 'LOGIN_OR_REGISTER',
      },
      decode: _decodeSession,
    );
  }

  Future<AuthSession> loginWithCarrier(
    CarrierAuthCredential credential, {
    String? requestId,
  }) {
    if (!_api.usesSecureTransport) {
      throw const ApiException('一键登录仅支持安全的 HTTPS 服务地址');
    }
    final provider = credential.provider.trim();
    final accessToken = credential.accessToken.trim();
    if (provider.isEmpty || accessToken.isEmpty) {
      throw const ApiException('运营商认证凭证无效');
    }
    return _api.post<AuthSession>(
      '/api/auth/phone-one-tap-login',
      body: {
        'provider': provider,
        'accessToken': accessToken,
        'requestId': requestId ?? const Uuid().v4(),
      },
      decode: _decodeSession,
    );
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
      phoneBound: profile.phoneBound,
      maskedPhone: profile.maskedPhone,
      hasPassword: profile.hasPassword,
      permissions: profile.permissions,
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
      decode: _decodeSession,
    );
  }

  static AuthSession _decodeSession(Object? data) {
    if (data is! Map) {
      throw const ApiException('登录结果格式不正确');
    }
    final session = AuthSession.fromJson(Map<String, dynamic>.from(data));
    if (session.token.trim().isEmpty || session.userId <= 0) {
      throw const ApiException('登录结果格式不正确');
    }
    return session;
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
