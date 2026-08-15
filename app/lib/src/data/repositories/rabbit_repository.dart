import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/cage_summary.dart';
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
        return data
            .whereType<Map>()
            .map((item) => Cage.fromJson(Map<String, dynamic>.from(item)))
            .where((cage) => cage.id > 0 && cage.isEnabled)
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

  Future<Cage> createCage({
    required int houseId,
    required String cageNumber,
    String? rowCode,
    int? layerIndex,
    int? positionIndex,
    String? remark,
  }) {
    return _api.post<Cage>(
      '/api/cages',
      houseId: houseId,
      body: {
        'cageNumber': cageNumber,
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
  }) {
    final body = <String, dynamic>{
      'cageId': cageId,
      'type': type,
      'gender': gender,
      'arrivalMethod': arrivalMethod,
      'arrivalDate': DateTime.now().millisecondsSinceEpoch,
      'requestId': _uuid.v4(),
    };
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

  Future<Rabbit> moveRabbitToCage({
    required int houseId,
    required Rabbit rabbit,
    required int targetCageId,
  }) {
    return updateRabbit(
      houseId: houseId,
      rabbitId: rabbit.id,
      cageId: targetCageId,
      motherId: rabbit.motherId,
      breed: rabbit.breed,
      arrivalMethod: rabbit.arrivalMethod,
      arrivalDate: rabbit.arrivalDate,
      weight: rabbit.weight,
      growthStage: rabbit.growthStage,
      reproductiveStage: rabbit.reproductiveStage,
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
