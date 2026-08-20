import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/profile/profile.dart';

final accountRepositoryProvider = Provider<AccountRepository>((ref) {
  return AccountRepository(ref.watch(apiClientProvider));
});

class AccountRepository {
  AccountRepository(this._api);

  final ApiClient _api;

  Future<UserProfile> loadProfile() {
    return _api.get<UserProfile>(
      '/api/auth/me',
      decode: (data) => UserProfile.fromJson(
        requireJsonObject(data, message: '账号资料格式不正确'),
      ),
    );
  }

  Future<UserProfile> updateUserName(String userName) {
    return _api.put<UserProfile>(
      '/api/auth/me',
      body: {'userName': userName},
      decode: (data) => UserProfile.fromJson(
        requireJsonObject(data, message: '账号资料格式不正确'),
      ),
    );
  }

  Future<void> updatePassword({
    required String oldPassword,
    required String newPassword,
  }) {
    return _api.put<void>(
      '/api/auth/password',
      body: {
        'oldPassword': oldPassword,
        'newPassword': newPassword,
      },
      decode: (_) {},
    );
  }

  Future<UserProfile> updatePhone({
    required String phone,
    required String code,
    String currentPassword = '',
    String currentPhone = '',
    String currentPhoneCode = '',
  }) {
    final body = <String, dynamic>{
      'phone': phone,
      'code': code,
    };
    if (currentPassword.isNotEmpty) {
      body['currentPassword'] = currentPassword;
    }
    if (currentPhone.isNotEmpty) {
      body['currentPhone'] = currentPhone;
    }
    if (currentPhoneCode.isNotEmpty) {
      body['currentPhoneCode'] = currentPhoneCode;
    }
    return _api.put<UserProfile>(
      '/api/auth/phone',
      body: body,
      decode: (data) => UserProfile.fromJson(
        requireJsonObject(data, message: '账号资料格式不正确'),
      ),
    );
  }
}
