import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/domain/auth/session.dart';
import 'package:rabbit_flutter/src/domain/auth/carrier.dart';

final authControllerProvider =
    StateNotifierProvider<AuthController, AsyncValue<AuthSession?>>((ref) {
  return AuthController(
    ref.watch(authRepositoryProvider),
    ref.watch(sessionStoreProvider),
  );
});

final authenticatedUserIdProvider = Provider<int>((ref) {
  return ref.watch(
    authControllerProvider.select((state) => state.valueOrNull?.userId ?? 0),
  );
});

class AuthController extends StateNotifier<AsyncValue<AuthSession?>> {
  AuthController(this._repository, this._sessionStore)
      : super(const AsyncValue.loading()) {
    _unauthorizedSubscription =
        _repository.unauthorizedEvents.listen((_) => _invalidateSession());
  }

  final AuthRepository _repository;
  final SessionStore _sessionStore;
  late final StreamSubscription<void> _unauthorizedSubscription;

  Future<void> restore() async {
    state = const AsyncValue.loading();
    try {
      final snapshot = await _sessionStore.readSession();
      if (!snapshot.isAuthenticated) {
        state = const AsyncValue.data(null);
        return;
      }
      final localSession = AuthSession(
        token: snapshot.token!,
        userId: snapshot.userId ?? 0,
        userName: snapshot.userName ?? '',
        houseId: snapshot.houseId,
      );
      final validatedSession = await _repository.validateSession(localSession);
      if (validatedSession.userId != localSession.userId ||
          validatedSession.userName != localSession.userName ||
          validatedSession.token != localSession.token) {
        await _sessionStore.saveAuth(
          token: validatedSession.token,
          userId: validatedSession.userId,
          userName: validatedSession.userName,
        );
      }
      state = AsyncValue.data(validatedSession);
    } on ApiException catch (error, stackTrace) {
      if (error.invalidatesSession) {
        await _invalidateSession();
        return;
      }
      state = AsyncValue.error(error, stackTrace);
    } catch (error, stackTrace) {
      state = AsyncValue.error(error, stackTrace);
    }
  }

  Future<void> login(
    String userName,
    String password, {
    required String captchaId,
    required String captchaCode,
  }) {
    return _authenticate(
      () => _repository.login(
        userName,
        password,
        captchaId: captchaId,
        captchaCode: captchaCode,
      ),
    );
  }

  Future<void> register(String userName, String password) {
    return _authenticate(() => _repository.register(userName, password));
  }

  Future<void> loginWithPhone(String phone, String code) {
    return _authenticate(() => _repository.loginWithPhone(phone, code));
  }

  Future<void> loginWithCarrier(CarrierAuthCredential credential) {
    return _authenticate(() => _repository.loginWithCarrier(credential));
  }

  Future<void> setHouseId(int houseId) async {
    final current = state.valueOrNull;
    if (current == null) {
      return;
    }
    await _sessionStore.saveHouseId(current.userId, houseId);
    state = AsyncValue.data(current.copyWith(houseId: houseId));
  }

  Future<void> reconcileHouseIds(Iterable<int> availableHouseIds) async {
    final current = state.valueOrNull;
    if (current == null) {
      return;
    }

    final ids = availableHouseIds.where((id) => id > 0).toSet();
    final snapshot = await _sessionStore.readSession();
    if (state.valueOrNull?.userId != current.userId) {
      return;
    }

    final cachedHouseId =
        snapshot.userId == current.userId ? snapshot.houseId : current.houseId;
    final candidateHouseId =
        current.houseId > 0 ? current.houseId : cachedHouseId;
    final int nextHouseId;
    if (ids.isEmpty) {
      nextHouseId = 0;
    } else if (ids.length == 1) {
      nextHouseId = ids.first;
    } else if (ids.contains(candidateHouseId)) {
      nextHouseId = candidateHouseId;
    } else {
      nextHouseId = 0;
    }

    if (current.houseId == nextHouseId && cachedHouseId == nextHouseId) {
      return;
    }
    await _sessionStore.saveHouseId(current.userId, nextHouseId);
    final latest = state.valueOrNull;
    if (mounted && latest?.userId == current.userId) {
      state = AsyncValue.data(latest!.copyWith(houseId: nextHouseId));
    }
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
    await _invalidateSession();
  }

  Future<void> _authenticate(
      Future<AuthSession> Function() authenticate) async {
    final previous = state.valueOrNull;
    state = const AsyncValue.loading();
    try {
      final session = await authenticate();
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

  Future<void> _invalidateSession() async {
    await _sessionStore.clear();
    if (mounted) {
      state = const AsyncValue.data(null);
    }
  }

  @override
  void dispose() {
    _unauthorizedSubscription.cancel();
    super.dispose();
  }
}
