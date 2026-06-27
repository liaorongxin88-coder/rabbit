import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';
import 'package:rabbit_flutter/src/domain/models/auth_session.dart';

final authControllerProvider =
    StateNotifierProvider<AuthController, AsyncValue<AuthSession?>>((ref) {
  return AuthController(
      ref.watch(apiClientProvider), ref.watch(sessionStoreProvider));
});

class AuthController extends StateNotifier<AsyncValue<AuthSession?>> {
  AuthController(this._api, this._sessionStore)
      : super(const AsyncValue.loading());

  final ApiClient _api;
  final SessionStore _sessionStore;

  Future<void> restore() async {
    state = const AsyncValue.loading();
    try {
      final snapshot = await _sessionStore.readSession();
      if (!snapshot.isAuthenticated) {
        state = const AsyncValue.data(null);
        return;
      }
      state = AsyncValue.data(
        AuthSession(
          token: snapshot.token!,
          userId: snapshot.userId ?? 0,
          userName: snapshot.userName ?? '',
          houseId: snapshot.houseId,
        ),
      );
    } catch (error, stackTrace) {
      state = AsyncValue.error(error, stackTrace);
    }
  }

  Future<void> login(String userName, String password) {
    return _authenticate('/api/auth/login', userName, password);
  }

  Future<void> register(String userName, String password) {
    return _authenticate('/api/auth/register', userName, password);
  }

  Future<void> setHouseId(int houseId) async {
    final current = state.valueOrNull;
    if (current == null) {
      return;
    }
    await _sessionStore.saveHouseId(current.userId, houseId);
    state = AsyncValue.data(current.copyWith(houseId: houseId));
  }

  Future<void> setUserName(String userName) async {
    final current = state.valueOrNull;
    if (current == null) {
      return;
    }
    await _sessionStore.saveUserName(userName);
    state = AsyncValue.data(current.copyWith(userName: userName));
  }

  Future<void> logout() async {
    await _sessionStore.clear();
    state = const AsyncValue.data(null);
  }

  Future<void> _authenticate(
    String path,
    String userName,
    String password,
  ) async {
    final previous = state.valueOrNull;
    state = const AsyncValue.loading();
    try {
      final session = await _api.post<AuthSession>(
        path,
        body: {
          'userName': userName,
          'password': password,
        },
        decode: (data) {
          if (data is! Map) {
            throw const ApiException('登录结果格式不正确');
          }
          return AuthSession.fromJson(Map<String, dynamic>.from(data));
        },
      );
      await _sessionStore.saveAuth(
        token: session.token,
        userId: session.userId,
        userName: session.userName,
      );
      state = AsyncValue.data(session);
    } catch (error, stackTrace) {
      state = AsyncValue.error(error, stackTrace);
      if (previous != null) {
        state = AsyncValue.data(previous);
      }
      rethrow;
    }
  }
}
