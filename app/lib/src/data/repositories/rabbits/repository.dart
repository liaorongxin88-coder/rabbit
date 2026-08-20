import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/cages/transfer.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/rabbits/batch_membership.dart';

final rabbitRepositoryProvider = Provider<RabbitRepository>((ref) {
  return RabbitRepository(ref.watch(apiClientProvider));
});

class RabbitRepository {
  RabbitRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  Future<List<Rabbit>> listRabbits(int houseId, {CancelToken? cancelToken}) {
    return listAllActiveRabbits(houseId, cancelToken: cancelToken);
  }

  Future<List<Rabbit>> listAllActiveRabbits(
    int houseId, {
    CancelToken? cancelToken,
  }) {
    return _listAllActiveRabbits(houseId, cancelToken: cancelToken);
  }

  Future<List<Rabbit>> listAllActiveBreedingRabbits(
    int houseId, {
    CancelToken? cancelToken,
  }) {
    return _listAllActiveRabbits(
      houseId,
      rabbitType: '0',
      cancelToken: cancelToken,
    );
  }

  Future<List<Rabbit>> _listAllActiveRabbits(
    int houseId, {
    String? rabbitType,
    CancelToken? cancelToken,
  }) async {
    const pageSize = 200;
    final rabbits = <Rabbit>[];
    var page = 1;

    while (true) {
      final batch = await listRabbitsPage(
        houseId: houseId,
        page: page,
        pageSize: pageSize,
        rabbitType: rabbitType,
        cancelToken: cancelToken,
      );
      rabbits.addAll(batch);
      if (batch.length < pageSize) {
        return rabbits;
      }
      page += 1;
    }
  }

  Future<List<Rabbit>> listRabbitsPage({
    required int houseId,
    required int page,
    required int pageSize,
    String? rabbitType,
    CancelToken? cancelToken,
  }) {
    return _api.get<List<Rabbit>>(
      '/api/rabbits',
      houseId: houseId,
      query: {
        'active': true,
        if (rabbitType != null) 'type': rabbitType,
        'page': page,
        'pageSize': pageSize,
      },
      cancelToken: cancelToken,
      decode: (data) => requireJsonObjectList(
        data,
        message: '兔只列表格式不正确',
      ).map(Rabbit.fromJson).toList(),
    );
  }

  Future<List<Rabbit>> listRabbitsForCage({
    required int houseId,
    required int cageId,
  }) {
    return _api.get<List<Rabbit>>(
      '/api/rabbits',
      houseId: houseId,
      query: {
        'cageId': cageId,
        'active': true,
        'page': 1,
        'pageSize': 200,
      },
      decode: (data) => requireJsonObjectList(
        data,
        message: '笼位兔只列表格式不正确',
      ).map(Rabbit.fromJson).toList(),
    );
  }

  Future<Rabbit> getRabbit({
    required int houseId,
    required int rabbitId,
    CancelToken? cancelToken,
  }) {
    return _api.get<Rabbit>(
      '/api/rabbits/$rabbitId',
      houseId: houseId,
      cancelToken: cancelToken,
      decode: (data) => Rabbit.fromJson(
        requireJsonObject(data, message: '兔只详情格式不正确'),
      ),
    );
  }

  Future<List<RabbitBatchMembership>> listRabbitBatchMemberships({
    required int houseId,
    required int rabbitId,
    bool active = true,
    CancelToken? cancelToken,
  }) {
    return _api.get<List<RabbitBatchMembership>>(
      '/api/rabbits/$rabbitId/batch-memberships',
      houseId: houseId,
      query: {'active': active},
      cancelToken: cancelToken,
      decode: (data) {
        return requireJsonObjectList(data, message: '兔只批次关系格式不正确')
            .map(RabbitBatchMembership.fromJson)
            .where(
              (membership) => membership.batchId > 0 && membership.rabbitId > 0,
            )
            .toList();
      },
    );
  }

  Future<Rabbit> createRabbit({
    required int houseId,
    required int cageId,
    required String type,
    required String gender,
    required String breed,
    required String arrivalMethod,
    required double? weight,
    String? growthStage,
    String? reproductiveStage,
    String? reproStage,
    DateTime? stageEnteredAt,
    DateTime? matingDate,
    DateTime? birthDate,
    int? liveKits,
  }) {
    final body = <String, dynamic>{
      'cageId': cageId,
      'type': type,
      'gender': gender,
      'arrivalMethod': arrivalMethod,
      'arrivalDate': DateTime.now().millisecondsSinceEpoch,
      'requestId': _uuid.v4(),
    };
    // 录入时直接入轨：建兔与开周期必须同事务，否则存栏里有这只母兔、
    // 待办里却没有，她会永远不被提醒。
    final trimmedReproStage = reproStage?.trim();
    if (trimmedReproStage != null && trimmedReproStage.isNotEmpty) {
      body['reproStage'] = trimmedReproStage;
      if (stageEnteredAt != null) {
        body['stageEnteredAt'] = stageEnteredAt.millisecondsSinceEpoch;
      }
      if (matingDate != null) {
        body['matingDate'] = matingDate.millisecondsSinceEpoch;
      }
      if (birthDate != null) {
        body['birthDate'] = birthDate.millisecondsSinceEpoch;
      }
      if (liveKits != null && liveKits >= 0) {
        body['liveKits'] = liveKits;
      }
    }
    final trimmedBreed = breed.trim();
    if (trimmedBreed.isNotEmpty) {
      body['breed'] = trimmedBreed;
    }
    if (weight != null && weight > 0) {
      body['weight'] = weight;
    }
    final trimmedGrowthStage = growthStage?.trim();
    if (trimmedGrowthStage != null && trimmedGrowthStage.isNotEmpty) {
      body['growthStage'] = trimmedGrowthStage;
    }
    final trimmedReproductiveStage = reproductiveStage?.trim();
    if (trimmedReproductiveStage != null &&
        trimmedReproductiveStage.isNotEmpty) {
      body['reproductiveStage'] = trimmedReproductiveStage;
    }

    return _api.post<Rabbit>(
      '/api/rabbits',
      houseId: houseId,
      body: body,
      decode: (data) => Rabbit.fromJson(
        requireJsonObject(data, message: '录入兔只结果格式不正确'),
      ),
    );
  }

  /// 换笼位。
  ///
  /// 不再用 `PUT /api/rabbits/{id}` 顺手改 cageId：那条路会把整行资料重新提交，
  /// 包括种母兔已被后端拒收的 reproductiveStage，也无法表达两笼对调。
  Future<CageTransferResult> transferRabbitCage({
    required int houseId,
    required int rabbitId,
    required int targetCageId,
    String? requestId,
  }) {
    return _api.post<CageTransferResult>(
      '/api/rabbits/$rabbitId/cage-transfer',
      houseId: houseId,
      body: {
        'targetCageId': targetCageId,
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (data) => CageTransferResult.fromJson(
        requireJsonObject(data, message: '换笼位结果格式不正确'),
      ),
    );
  }

  Future<Rabbit> updateRabbit({
    required int houseId,
    required int rabbitId,
    required int cageId,
    required int? motherId,
    required String breed,
    required String arrivalMethod,
    required DateTime? arrivalDate,
    required double? weight,
    String? growthStage,
    String? reproductiveStage,
  }) {
    final body = <String, dynamic>{
      'cageId': cageId,
      'arrivalMethod': arrivalMethod,
      'requestId': _uuid.v4(),
    };
    if (motherId != null && motherId > 0) {
      body['motherId'] = motherId;
    }
    if (arrivalDate != null) {
      body['arrivalDate'] = arrivalDate.millisecondsSinceEpoch;
    }
    final trimmedBreed = breed.trim();
    if (trimmedBreed.isNotEmpty) {
      body['breed'] = trimmedBreed;
    }
    if (weight != null && weight > 0) {
      body['weight'] = weight;
    }
    final trimmedGrowthStage = growthStage?.trim();
    if (trimmedGrowthStage != null && trimmedGrowthStage.isNotEmpty) {
      body['growthStage'] = trimmedGrowthStage;
    }
    final trimmedReproductiveStage = reproductiveStage?.trim();
    if (trimmedReproductiveStage != null &&
        trimmedReproductiveStage.isNotEmpty) {
      body['reproductiveStage'] = trimmedReproductiveStage;
    }

    return _api.put<Rabbit>(
      '/api/rabbits/$rabbitId',
      houseId: houseId,
      body: body,
      decode: (data) => Rabbit.fromJson(
        requireJsonObject(data, message: '编辑兔只结果格式不正确'),
      ),
    );
  }

  Future<void> convertToReplacement({
    required int houseId,
    required List<int> rabbitIds,
    int? targetCageId,
    bool forceExitBatch = true,
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/rabbits/replacement',
      houseId: houseId,
      body: {
        'rabbitIds': _sortedUniqueIds(rabbitIds),
        'forceExitBatch': forceExitBatch,
        'requestId': requestId ?? _uuid.v4(),
        if (targetCageId != null && targetCageId > 0)
          'targetCageId': targetCageId,
      },
      decode: (_) {},
    );
  }

  /// Records a terminal rabbit event and, when requested, exits every active
  /// Batch relationship for the rabbit in the same server-side transaction.
  ///
  /// The API accepts a JSON date value. Epoch milliseconds are used here to
  /// preserve the selected local date without relying on a server timezone or
  /// a particular Jackson textual date format.
  Future<void> submitRabbitEvent({
    required int houseId,
    required int rabbitId,
    required String eventType,
    required DateTime actionDate,
    required String reason,
    String remark = '',
    bool forceExitBatch = true,
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/rabbits/events',
      houseId: houseId,
      body: {
        'rabbitId': rabbitId,
        'eventType': eventType.trim().toLowerCase(),
        'actionDate': actionDate.millisecondsSinceEpoch,
        'reason': reason.trim(),
        'remark': remark.trim(),
        'forceExitBatch': forceExitBatch,
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (_) {},
    );
  }
}

List<int> _sortedUniqueIds(Iterable<int> ids) {
  return ids.toSet().toList()..sort();
}
