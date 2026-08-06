import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/auth_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';
import 'package:rabbit_flutter/src/domain/models/auth_session.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

void main() {
  test('house permission reloads when the authenticated user changes',
      () async {
    final repository = _SequenceHouseRepository();
    final container = ProviderContainer(
      overrides: [
        authRepositoryProvider.overrideWithValue(_SwitchingAuthRepository()),
        sessionStoreProvider.overrideWithValue(_MemorySessionStore()),
        houseRepositoryProvider.overrideWithValue(repository),
      ],
    );
    addTearDown(container.dispose);

    final auth = container.read(authControllerProvider.notifier);
    await auth.login('view-user', 'password');
    final viewPermission = await container.read(
      housePermissionProvider(8).future,
    );
    expect(viewPermission.canEdit, isFalse);

    await auth.logout();
    await auth.login('control-user', 'password');
    final controlPermission = await container.read(
      housePermissionProvider(8).future,
    );

    expect(controlPermission.canControl, isTrue);
    expect(repository.calls, 2);
  });
}

class _SwitchingAuthRepository extends AuthRepository {
  _SwitchingAuthRepository() : super(ApiClient(_MemorySessionStore()));

  @override
  Future<AuthSession> login(String userName, String password) async {
    return AuthSession(
      token: 'token-$userName',
      userId: userName == 'view-user' ? 1 : 2,
      userName: userName,
      houseId: 0,
    );
  }
}

class _SequenceHouseRepository extends HouseRepository {
  _SequenceHouseRepository() : super(ApiClient(_MemorySessionStore()));

  int calls = 0;

  @override
  Future<HousePermission> getMyPermission(int houseId) async {
    calls += 1;
    if (calls == 1) {
      return const HousePermission(perms: 'view', isAdmin: false);
    }
    return const HousePermission(perms: 'control', isAdmin: false);
  }
}

class _MemorySessionStore extends SessionStore {
  @override
  Future<void> saveAuth({
    required String token,
    required int userId,
    required String userName,
  }) async {}

  @override
  Future<void> clear() async {}
}
