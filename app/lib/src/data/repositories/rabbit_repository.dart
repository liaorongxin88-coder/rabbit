import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/cage_summary.dart';
import 'package:rabbit_flutter/src/domain/models/cage_transfer_result.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';

final rabbitRepositoryProvider = Provider<RabbitRepository>((ref) {
  return RabbitRepository(ref.watch(apiClientProvider));
});

class RabbitRepository {
  RabbitRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  Future<List<Cage>> listCages(int houseId, {CancelToken? cancelToken}) {
    return _api.get<List<Cage>>(
      '/api/cages',
      houseId: houseId,
      cancelToken: cancelToken,
      decode: (data) {
        if (data is! List) {
          throw const ApiException('笼位列表格式不正确');
        }
        // 不过滤停用笼位：停用的笼子在货架上是真存在的，丢掉它会让分层地图
        // 凭空少一个位置，用户对着实物数不上。能不能放兔由 `Cage.acceptsMoreRabbits` /
        // `canAcceptRabbit` 在各个选择入口把关，而不是靠列表里看不见。
        return data
            .whereType<Map>()
            .map((item) => Cage.fromJson(Map<String, dynamic>.from(item)))
            .where((cage) => cage.id > 0)
            .toList();
      },
    );
  }

  Future<List<Rabbit>> listRabbits(int houseId, {CancelToken? cancelToken}) {
    return listAllActiveRabbits(houseId, cancelToken: cancelToken);
  }

  Future<List<Rabbit>> listAllActiveRabbits(
    int houseId, {
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
    CancelToken? cancelToken,
  }) {
    return _api.get<List<Rabbit>>(
      '/api/rabbits',
      houseId: houseId,
      query: {'active': true, 'page': page, 'pageSize': pageSize},
      cancelToken: cancelToken,
      decode: (data) {
        if (data is! List) {
          throw const ApiException('兔只列表格式不正确');
        }
        return data
            .whereType<Map>()
            .map((item) => Rabbit.fromJson(Map<String, dynamic>.from(item)))
            .toList();
      },
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
      decode: (data) {
        if (data is! List) {
          throw const ApiException('笼位兔只列表格式不正确');
        }
        return data
            .whereType<Map>()
            .map((item) => Rabbit.fromJson(Map<String, dynamic>.from(item)))
            .toList();
      },
    );
  }

  Future<CageSummary> getCageSummary({
    required int houseId,
    required int cageId,
  }) {
    return _api.get<CageSummary>(
      '/api/cages/$cageId/summary',
      houseId: houseId,
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('笼位摘要格式不正确');
        }
        return CageSummary.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }

  /// [cageNumber] 留空时由后端按「排-位-层」生成（见后端 CageNumbers）。
  /// 客户端别自己拼编号：以前 App 拼 `2(下)1`、后端建舍拼 `2-1-1`，
  /// 同一个兔舍里两套写法，工人拿笼上的签对不上系统。
  Future<Cage> createCage({
    required int houseId,
    String? cageNumber,
    String? rowCode,
    int? layerIndex,
    int? positionIndex,
    String? remark,
  }) {
    return _api.post<Cage>(
      '/api/cages',
      houseId: houseId,
      body: {
        if (cageNumber != null && cageNumber.trim().isNotEmpty)
          'cageNumber': cageNumber.trim(),
        if (rowCode != null && rowCode.trim().isNotEmpty)
          'rowCode': rowCode.trim(),
        if (layerIndex != null) 'layerIndex': layerIndex,
        if (positionIndex != null) 'positionIndex': positionIndex,
        'isEnabled': true,
        'remark': remark ?? '',
      },
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('新增笼位结果格式不正确');
        }
        return Cage.fromJson(Map<String, dynamic>.from(data));
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
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('录入兔只结果格式不正确');
        }
        return Rabbit.fromJson(Map<String, dynamic>.from(data));
      },
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
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('换笼位结果格式不正确');
        }
        return CageTransferResult.fromJson(Map<String, dynamic>.from(data));
      },
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
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('编辑兔只结果格式不正确');
        }
        return Rabbit.fromJson(Map<String, dynamic>.from(data));
      },
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
