import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/houses/invitation.dart';
import 'package:rabbit_flutter/src/domain/houses/member.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/domain/profile/code.dart';

final houseRepositoryProvider = Provider<HouseRepository>((ref) {
  return HouseRepository(ref.watch(apiClientProvider));
});

class HouseRepository {
  HouseRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  Future<List<RabbitHouse>> listHouses() {
    return _api.get<List<RabbitHouse>>(
      '/api/houses',
      decode: (data) => requireJsonObjectList(
        data,
        message: '兔舍列表格式不正确',
      ).map(RabbitHouse.fromJson).toList(),
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
      decode: (data) => RabbitHouse.fromJson(
        requireJsonObject(data, message: '创建兔舍结果格式不正确'),
      ),
    );
  }

  Future<RabbitHouse> updateHouse({
    required int houseId,
    required String name,
    required String remark,
  }) {
    return _api.put<RabbitHouse>(
      '/api/houses/$houseId',
      houseId: houseId,
      body: {
        'name': name,
        'remark': remark,
      },
      decode: (data) => RabbitHouse.fromJson(
        requireJsonObject(data, message: '更新兔舍结果格式不正确'),
      ),
    );
  }

  Future<HousePermission> getMyPermission(int houseId) {
    return _api.get<HousePermission>(
      '/api/houses/permission',
      houseId: houseId,
      decode: (data) => HousePermission.fromJson(
        requireJsonObject(data, message: '权限信息格式不正确'),
      ),
    );
  }

  Future<List<HouseMember>> listMembers(int houseId) {
    return _api.get<List<HouseMember>>(
      '/api/house-members',
      houseId: houseId,
      decode: (data) => requireJsonObjectList(
        data,
        message: '成员列表格式不正确',
      ).map(HouseMember.fromJson).toList(),
    );
  }

  /// [identifier] 可以是手机号，也可以是对方的账号，服务端自己认。
  /// phone 字段同时也发：新客户端碰上老后端时，手机号那条路还能走通。
  Future<HouseInvitationResult> inviteMember({
    required int houseId,
    required String identifier,
    required String role,
  }) {
    final trimmed = identifier.trim();
    return _api.post<HouseInvitationResult>(
      '/api/house-invitations',
      houseId: houseId,
      body: {
        'identifier': trimmed,
        if (UserCode.looksLikeMobile(trimmed)) 'phone': trimmed,
        'role': role,
        'requestId': _uuid.v4(),
      },
      decode: (data) => HouseInvitationResult.fromJson(
        (data as Map?)?.cast<String, dynamic>() ?? const <String, dynamic>{},
      ),
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
