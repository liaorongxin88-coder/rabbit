import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/house_member.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

final houseRepositoryProvider = Provider<HouseRepository>((ref) {
  return HouseRepository(ref.watch(apiClientProvider));
});

final housesProvider = FutureProvider<List<RabbitHouse>>((ref) {
  final session = ref.watch(authControllerProvider).valueOrNull;
  if (session == null) {
    return Future.value(const <RabbitHouse>[]);
  }
  return ref.watch(houseRepositoryProvider).listHouses();
});

final housePermissionProvider =
    FutureProvider.family<HousePermission, int>((ref, houseId) {
  if (houseId <= 0) {
    return Future.value(const HousePermission(perms: 'view', isAdmin: false));
  }
  return ref.watch(houseRepositoryProvider).getMyPermission(houseId);
});

final houseMembersProvider =
    FutureProvider.family<List<HouseMember>, int>((ref, houseId) {
  return ref.watch(houseRepositoryProvider).listMembers(houseId);
});

class HouseRepository {
  HouseRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  Future<List<RabbitHouse>> listHouses() {
    return _api.get<List<RabbitHouse>>(
      '/api/houses',
      decode: (data) {
        if (data is! List) {
          throw const ApiException('兔舍列表格式不正确');
        }
        return data
            .whereType<Map>()
            .map(
                (item) => RabbitHouse.fromJson(Map<String, dynamic>.from(item)))
            .toList();
      },
    );
  }

  Future<RabbitHouse> createHouse({
    required String name,
    required int rows,
    required int cols,
    required int layers,
    String remark = '',
  }) {
    return _api.post<RabbitHouse>(
      '/api/houses',
      body: {
        'name': name,
        'layoutRows': rows,
        'layoutCols': cols,
        'layoutLayers': layers,
        'remark': remark,
        'requestId': _uuid.v4(),
      },
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('创建兔舍结果格式不正确');
        }
        return RabbitHouse.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }

  Future<HousePermission> getMyPermission(int houseId) {
    return _api.get<HousePermission>(
      '/api/houses/permission',
      houseId: houseId,
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('权限信息格式不正确');
        }
        return HousePermission.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }

  Future<List<HouseMember>> listMembers(int houseId) {
    return _api.get<List<HouseMember>>(
      '/api/house-members',
      houseId: houseId,
      decode: (data) {
        if (data is! List) {
          throw const ApiException('成员列表格式不正确');
        }
        return data
            .whereType<Map>()
            .map((item) => HouseMember.fromJson(Map<String, dynamic>.from(item)))
            .toList();
      },
    );
  }

  Future<List<UserSearchItem>> searchMemberCandidates({
    required int houseId,
    required String keyword,
  }) {
    return _api.get<List<UserSearchItem>>(
      '/api/house-members/search-users',
      houseId: houseId,
      query: {'q': keyword.trim()},
      decode: (data) {
        if (data is! List) {
          return const <UserSearchItem>[];
        }
        return data
            .whereType<Map>()
            .map((item) => UserSearchItem.fromJson(Map<String, dynamic>.from(item)))
            .toList();
      },
    );
  }

  Future<void> addMember({
    required int houseId,
    required String userName,
    required String perms,
  }) {
    return _api.post<void>(
      '/api/house-members',
      houseId: houseId,
      body: {
        'userName': userName.trim(),
        'perms': perms,
        'isAdmin': false,
        'requestId': _uuid.v4(),
      },
      decode: (_) {},
    );
  }

  Future<void> updateMember({
    required int houseId,
    required int memberUserId,
    String? perms,
    bool? isAdmin,
  }) {
    return _api.put<void>(
      '/api/house-members/$memberUserId',
      houseId: houseId,
      body: {
        if (perms != null) 'perms': perms,
        if (isAdmin != null) 'isAdmin': isAdmin,
        'requestId': _uuid.v4(),
      },
      decode: (_) {},
    );
  }

  Future<void> removeMember({
    required int houseId,
    required int memberUserId,
  }) {
    return _api.delete<void>(
      '/api/house-members/$memberUserId',
      houseId: houseId,
      query: {'requestId': _uuid.v4()},
      decode: (_) {},
    );
  }

  Future<void> leaveHouse({required int houseId}) {
    return _api.post<void>(
      '/api/house-members/leave',
      houseId: houseId,
      query: {'requestId': _uuid.v4()},
      decode: (_) {},
    );
  }
}
