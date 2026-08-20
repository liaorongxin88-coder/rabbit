import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/auth/session.dart';
import 'package:rabbit_flutter/src/data/repositories/houses/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/domain/auth/session.dart';
import 'package:rabbit_flutter/src/domain/houses/member.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';

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
    expect(repository.permissionCalls, 2);
  });

  test('house members reload when the authenticated user changes', () async {
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
    final first = await container.read(houseMembersProvider(8).future);
    expect(first.single.userName, 'member-for-view-user');

    await auth.logout();
    await auth.login('control-user', 'password');
    final second = await container.read(houseMembersProvider(8).future);

    expect(second.single.userName, 'member-for-control-user');
    expect(repository.memberCalls, 2);
  });

  test('reconciles zero, one and multiple available houses', () async {
    final store = _MemorySessionStore(initialHouseId: 9);
    final container = ProviderContainer(
      overrides: [
        authRepositoryProvider.overrideWithValue(_SwitchingAuthRepository()),
        sessionStoreProvider.overrideWithValue(store),
      ],
    );
    addTearDown(container.dispose);

    final auth = container.read(authControllerProvider.notifier);
    await auth.login('view-user', 'password');

    await auth.reconcileHouseIds(const [8, 9]);
    expect(container.read(authControllerProvider).valueOrNull?.houseId, 9);

    await auth.reconcileHouseIds(const [8]);
    expect(container.read(authControllerProvider).valueOrNull?.houseId, 8);

    await auth.reconcileHouseIds(const []);
    expect(container.read(authControllerProvider).valueOrNull?.houseId, 0);

    await auth.reconcileHouseIds(const [8, 9]);
    expect(container.read(authControllerProvider).valueOrNull?.houseId, 0);
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

  int permissionCalls = 0;
  int memberCalls = 0;

  @override
  Future<HousePermission> getMyPermission(int houseId) async {
    permissionCalls += 1;
    if (permissionCalls == 1) {
      return const HousePermission(perms: 'view', isAdmin: false);
    }
    return const HousePermission(perms: 'control', isAdmin: false);
  }

  @override
  Future<List<HouseMember>> listMembers(int houseId) async {
    memberCalls += 1;
    return [
      HouseMember(
        userId: memberCalls,
        userName: memberCalls == 1
            ? 'member-for-view-user'
            : 'member-for-control-user',
        perms: 'view',
        isAdmin: false,
      ),
    ];
  }
}

class _MemorySessionStore extends SessionStore {
  _MemorySessionStore({int initialHouseId = 0})
      : _snapshot = SessionSnapshot(
          token: null,
          userId: null,
          userName: null,
          houseId: initialHouseId,
        );

  SessionSnapshot _snapshot;

  @override
  Future<SessionSnapshot> readSession() async => _snapshot;

  @override
  Future<void> saveAuth({
    required String token,
    required int userId,
    required String userName,
  }) async {
    _snapshot = SessionSnapshot(
      token: token,
      userId: userId,
      userName: userName,
      houseId: _snapshot.houseId,
    );
  }

  @override
  Future<void> saveHouseId(int userId, int houseId) async {
    _snapshot = SessionSnapshot(
      token: _snapshot.token,
      userId: userId,
      userName: _snapshot.userName,
      houseId: houseId,
    );
  }

  @override
  Future<void> clear() async {
    _snapshot = SessionSnapshot.empty;
  }
}
