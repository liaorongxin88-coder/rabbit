import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/response.dart';
import 'package:rabbit_flutter/src/domain/cages/transfer.dart';
import 'package:rabbit_flutter/src/domain/rabbits/batch_entry.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/rabbits/batch_membership.dart';
import 'package:rabbit_flutter/src/domain/rabbits/replacement.dart';
import 'package:rabbit_flutter/src/domain/rabbits/range_entry.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';

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
    return _listAllRabbits(
      houseId,
      rabbitType: '0',
      active: true,
      cancelToken: cancelToken,
    );
  }

  Future<List<Rabbit>> listAllBreedingRabbits(
    int houseId, {
    CancelToken? cancelToken,
  }) {
    return _listAllRabbits(
      houseId,
      rabbitType: '0',
      cancelToken: cancelToken,
    );
  }

  Future<List<Rabbit>> _listAllActiveRabbits(
    int houseId, {
    String? rabbitType,
    CancelToken? cancelToken,
  }) {
    return _listAllRabbits(
      houseId,
      rabbitType: rabbitType,
      active: true,
      cancelToken: cancelToken,
    );
  }

  Future<List<Rabbit>> _listAllRabbits(
    int houseId, {
    String? rabbitType,
    bool? active,
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
        active: active,
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
    bool? active = true,
    CancelToken? cancelToken,
  }) {
    return _api.get<List<Rabbit>>(
      '/api/rabbits',
      houseId: houseId,
      query: {
        if (active != null) 'active': active,
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

  Future<String> uploadImage({
    required int houseId,
    required String filePath,
    String? fileName,
  }) async {
    final form = FormData.fromMap({
      'file': await MultipartFile.fromFile(filePath, filename: fileName),
    });
    return _api.postMultipart<String>(
      '/api/business-files/images',
      houseId: houseId,
      body: form,
      decode: (data) {
        if (data is Map) {
          final fileId = data['fileId'];
          if (fileId is String && fileId.isNotEmpty) {
            return fileId;
          }
        }
        throw const FormatException('图片上传结果格式不正确');
      },
    );
  }

  Future<void> createAbnormalCondition({
    required int houseId,
    required int rabbitId,
    required String warningStatus,
    required String imageFileId,
    required String remark,
    required String requestId,
  }) {
    return _api.post<void>(
      '/api/abnormal',
      houseId: houseId,
      body: {
        'rabbitId': rabbitId,
        'warningStatus': warningStatus.trim(),
        'imageFileId': imageFileId,
        'remark': remark.trim(),
        'requestId': requestId,
      },
      decode: (_) {},
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
    String sourceSeller = '',
    int? motherId,
    required DateTime arrivalDate,
    required double? weight,
    String? growthStage,
    DateTime? growthStageEnteredAt,
    String? reproductiveStage,
    String? reproStage,
    int? batchId,
    DateTime? stageEnteredAt,
    DateTime? matingDate,
    DateTime? birthDate,
    int? totalKits,
    int? liveKits,
    int? keptKits,
    int? maleRabbitId,
    MatingMethod? matingMethod,
    String? requestId,
  }) {
    final body = <String, dynamic>{
      'cageId': cageId,
      'type': type,
      'gender': gender,
      'arrivalMethod': arrivalMethod,
      if (sourceSeller.trim().isNotEmpty) 'sourceSeller': sourceSeller.trim(),
      if (motherId != null && motherId > 0) 'motherId': motherId,
      'arrivalDate': DateFormat('yyyy-MM-dd').format(arrivalDate),
      'requestId': requestId ?? _uuid.v4(),
    };
    // 录入时直接入轨：建兔与开周期必须同事务，否则存栏里有这只母兔、
    // 待办里却没有，她会永远不被提醒。
    final trimmedReproStage = reproStage?.trim();
    if (trimmedReproStage != null && trimmedReproStage.isNotEmpty) {
      body['reproStage'] = trimmedReproStage;
      if (batchId != null && batchId > 0) {
        body['batchId'] = batchId;
      }
      if (stageEnteredAt != null) {
        body['stageEnteredAt'] =
            farmDateTimeToEpochMilliseconds(stageEnteredAt);
      }
      if (matingDate != null) {
        body['matingDate'] = farmDateTimeToEpochMilliseconds(matingDate);
      }
      if (birthDate != null) {
        body['birthDate'] = farmDateTimeToEpochMilliseconds(birthDate);
      }
      if (totalKits != null && totalKits >= 0) {
        body['totalKits'] = totalKits;
      }
      if (liveKits != null && liveKits >= 0) {
        body['liveKits'] = liveKits;
      }
      if (keptKits != null && keptKits >= 0) {
        body['keptKits'] = keptKits;
      }
      if (maleRabbitId != null && maleRabbitId > 0) {
        body['maleRabbitId'] = maleRabbitId;
      }
      if (matingMethod != null) {
        body['matingMethod'] = matingMethod.wire;
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
    if (growthStageEnteredAt != null) {
      body['growthStageEnteredAt'] =
          DateFormat('yyyy-MM-dd').format(growthStageEnteredAt);
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

  Future<BatchRabbitEntryResult> createRabbitBatch({
    required int houseId,
    required int cageId,
    required int quantity,
    required double totalWeight,
    required String type,
    required String gender,
    required String arrivalMethod,
    required DateTime arrivalDate,
    String breed = '',
    String sourceSeller = '',
    int? motherId,
    String? growthStage,
    DateTime? growthStageEnteredAt,
    String? reproductiveStage,
    String? requestId,
  }) {
    return _api.post<BatchRabbitEntryResult>(
      '/api/rabbits/batch-entry',
      houseId: houseId,
      body: {
        'cageId': cageId,
        'quantity': quantity,
        'totalWeight': totalWeight,
        'type': type,
        'gender': gender,
        'arrivalMethod': arrivalMethod,
        'arrivalDate': DateFormat('yyyy-MM-dd').format(arrivalDate),
        'requestId': requestId ?? _uuid.v4(),
        if (breed.trim().isNotEmpty) 'breed': breed.trim(),
        if (sourceSeller.trim().isNotEmpty) 'sourceSeller': sourceSeller.trim(),
        if (motherId != null && motherId > 0) 'motherId': motherId,
        if (growthStage?.trim().isNotEmpty ?? false)
          'growthStage': growthStage!.trim(),
        if (growthStageEnteredAt != null)
          'growthStageEnteredAt':
              DateFormat('yyyy-MM-dd').format(growthStageEnteredAt),
        if (reproductiveStage?.trim().isNotEmpty ?? false)
          'reproductiveStage': reproductiveStage!.trim(),
      },
      decode: (data) => BatchRabbitEntryResult.fromJson(
        requireJsonObject(data, message: '批量录入结果格式不正确'),
      ),
    );
  }

  Future<RangeRabbitEntryResult> createRabbitsInRange({
    required int houseId,
    required int rowStart,
    required int rowEnd,
    required int positionStart,
    required int positionEnd,
    required int layerStart,
    required int layerEnd,
    required int rabbitsPerCage,
    required String type,
    required String gender,
    required String arrivalMethod,
    required DateTime arrivalDate,
    String breed = '',
    double? weight,
    String? growthStage,
    String? reproductiveStage,
  }) {
    final body = <String, dynamic>{
      'rowStart': rowStart,
      'rowEnd': rowEnd,
      'positionStart': positionStart,
      'positionEnd': positionEnd,
      'layerStart': layerStart,
      'layerEnd': layerEnd,
      'rabbitsPerCage': rabbitsPerCage,
      'type': type,
      'gender': gender,
      'arrivalMethod': arrivalMethod,
      'arrivalDate': DateFormat('yyyy-MM-dd').format(arrivalDate),
      'requestId': _uuid.v4(),
    };
    if (breed.trim().isNotEmpty) body['breed'] = breed.trim();
    if (weight != null && weight > 0) body['weight'] = weight;
    if (growthStage?.trim().isNotEmpty ?? false) {
      body['growthStage'] = growthStage!.trim();
    }
    body['growthStageEnteredAt'] = DateFormat('yyyy-MM-dd').format(arrivalDate);
    if (reproductiveStage?.trim().isNotEmpty ?? false) {
      body['reproductiveStage'] = reproductiveStage!.trim();
    }
    return _api.post<RangeRabbitEntryResult>(
      '/api/rabbits/range-entry',
      houseId: houseId,
      body: body,
      decode: (data) => RangeRabbitEntryResult.fromJson(
        requireJsonObject(data, message: '范围入栏结果格式不正确'),
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
      body['arrivalDate'] = DateFormat('yyyy-MM-dd').format(arrivalDate);
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

  Future<List<ReplacementConversion>> convertToReplacement({
    required int houseId,
    required int rabbitId,
    required int? sourceBatchId,
    required double measuredTotalWeightKg,
    int? targetCageId,
    bool forceExitBatch = true,
    String? requestId,
  }) {
    if (rabbitId <= 0 || (sourceBatchId != null && sourceBatchId <= 0)) {
      throw ArgumentError('留后备兔来源信息不正确');
    }
    final allocation = ReplacementBatchAllocation(
      batchId: sourceBatchId,
      rabbitCount: 1,
      totalWeightKg: measuredTotalWeightKg,
    );
    final validation = allocation.validate();
    if (validation != null) throw ArgumentError(validation);
    return _api.post<List<ReplacementConversion>>(
      '/api/rabbits/replacement',
      houseId: houseId,
      body: {
        'rabbitIds': [rabbitId],
        'forceExitBatch': forceExitBatch,
        'batchAllocations': [allocation.toJson()],
        'requestId': requestId ?? _uuid.v4(),
        if (targetCageId != null && targetCageId > 0)
          'targetCageId': targetCageId,
      },
      decode: (data) {
        if (data is! Map || data['items'] is! List) {
          throw const FormatException('留后备兔结果格式不正确');
        }
        return (data['items'] as List)
            .whereType<Map>()
            .map((item) => ReplacementConversion.fromJson(
                  Map<String, dynamic>.from(item),
                ))
            .toList(growable: false);
      },
    );
  }

  Future<void> promoteReplacement({
    required int houseId,
    required int rabbitId,
    String reason = '',
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/rabbits/$rabbitId/promote-breeder',
      houseId: houseId,
      body: {
        'requestId': requestId ?? _uuid.v4(),
        if (reason.trim().isNotEmpty) 'reason': reason.trim(),
      },
      decode: (_) {},
    );
  }

  Future<void> createRabbitSale({
    required int houseId,
    required int rabbitId,
    required DateTime saleTime,
    required double totalWeight,
    double? unitPrice,
    String customer = '',
    String remark = '',
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/sales',
      houseId: houseId,
      body: {
        'rabbitIds': [rabbitId],
        'saleTime': saleTime.millisecondsSinceEpoch,
        'totalWeight': totalWeight,
        if (unitPrice != null) 'unitPrice': unitPrice,
        if (customer.trim().isNotEmpty) 'customer': customer.trim(),
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
        'requestId': requestId ?? _uuid.v4(),
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
