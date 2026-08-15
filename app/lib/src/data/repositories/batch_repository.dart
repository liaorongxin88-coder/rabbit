import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/batch.dart';
import 'package:rabbit_flutter/src/domain/models/batch_rabbit.dart';

final batchRepositoryProvider = Provider<BatchRepository>((ref) {
  return BatchRepository(ref.watch(apiClientProvider));
});

class BatchRepository {
  BatchRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  Future<List<Batch>> listBatches(
    int houseId, {
    CancelToken? cancelToken,
  }) async {
    const pageSize = 200;
    final batches = <Batch>[];
    var page = 1;

    while (true) {
      final items = await listBatchesPage(
        houseId: houseId,
        page: page,
        pageSize: pageSize,
        cancelToken: cancelToken,
      );
      batches.addAll(items);
      if (items.length < pageSize) {
        return batches;
      }
      page += 1;
    }
  }

  Future<List<Batch>> listBatchesPage({
    required int houseId,
    required int page,
    required int pageSize,
    CancelToken? cancelToken,
  }) {
    return _api.get<List<Batch>>(
      '/api/batches',
      houseId: houseId,
      query: {'page': page, 'pageSize': pageSize},
      cancelToken: cancelToken,
      decode: (data) {
        if (data is! List) {
          throw const ApiException('批次列表格式不正确');
        }
        return data
            .whereType<Map>()
            .map((item) => Batch.fromJson(Map<String, dynamic>.from(item)))
            .toList();
      },
    );
  }

  Future<Batch> createBatch({
    required int houseId,
    required String batchCode,
    required List<int> femaleRabbitIds,
    String remark = '',
    String? requestId,
  }) {
    return _api.post<Batch>(
      '/api/batches',
      houseId: houseId,
      body: {
        'batchCode': batchCode.trim(),
        'femaleRabbitIds': _sortedUniqueIds(femaleRabbitIds),
        'remark': remark.trim(),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('创建批次结果格式不正确');
        }
        return Batch.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }

  Future<Batch> getBatch({
    required int houseId,
    required int batchId,
    CancelToken? cancelToken,
  }) {
    return _api.get<Batch>(
      '/api/batches/$batchId',
      houseId: houseId,
      cancelToken: cancelToken,
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('批次详情格式不正确');
        }
        return Batch.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }

  Future<List<BatchRabbitItem>> listBatchRabbits({
    required int houseId,
    required int batchId,
    String? role,
    bool? active,
    CancelToken? cancelToken,
  }) {
    return _api.get<List<BatchRabbitItem>>(
      '/api/batches/$batchId/batch-rabbits',
      houseId: houseId,
      query: {
        if (role != null && role.trim().isNotEmpty) 'role': role.trim(),
        if (active != null) 'active': active,
      },
      cancelToken: cancelToken,
      decode: (data) {
        if (data is! List) {
          throw const ApiException('批次兔子列表格式不正确');
        }
        return data
            .whereType<Map>()
            .map(
              (item) => BatchRabbitItem.fromJson(
                Map<String, dynamic>.from(item),
              ),
            )
            .toList();
      },
    );
  }

  Future<void> submitWeaning({
    required int houseId,
    required int batchId,
    required int rabbitId,
    int? breedingCycleId,
    required DateTime weaningDate,
    required int weaningCount,
    int? maleCount,
    int? femaleCount,
    int? targetCageId,
    double? avgWeight,
    String remark = '',
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/weaning',
      houseId: houseId,
      body: {
        'rabbitId': rabbitId,
        if (breedingCycleId != null) 'breedingCycleId': breedingCycleId,
        'requestId': requestId ?? _uuid.v4(),
        'weaningDate': formatBatchWriteDate(weaningDate),
        'weaningCount': weaningCount,
        if (maleCount != null) 'maleCount': maleCount,
        if (femaleCount != null) 'femaleCount': femaleCount,
        if (targetCageId != null && targetCageId > 0)
          'targetCageId': targetCageId,
        if (avgWeight != null) 'avgWeight': avgWeight,
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
      },
      decode: (_) {},
    );
  }

  Future<void> submitMating({
    required int houseId,
    required int batchId,
    required int femaleRabbitId,
    required int maleRabbitId,
    required DateTime matingDate,
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/mating',
      houseId: houseId,
      body: {
        'femaleRabbitId': femaleRabbitId,
        'maleRabbitId': maleRabbitId,
        'matingDate': formatBatchWriteDate(matingDate),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (_) {},
    );
  }

  /// Submits one shared mating operation for up to 1,000 mothers.
  ///
  /// The server validates the whole set before writing it, so a retry with the
  /// same request id remains idempotent and cannot leave a partially mated
  /// selection behind.
  Future<void> submitMatingBulk({
    required int houseId,
    required int batchId,
    required List<int> rabbitIds,
    required int maleRabbitId,
    required DateTime matingDate,
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/mating/bulk',
      houseId: houseId,
      body: {
        'femaleRabbitIds': _sortedUniqueIds(rabbitIds),
        'maleRabbitId': maleRabbitId,
        'matingDate': formatBatchWriteDate(matingDate),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (_) {},
    );
  }

  Future<void> startAphrodisiac({
    required int houseId,
    required int batchId,
    required List<int> rabbitIds,
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/aphrodisiac/start',
      houseId: houseId,
      body: {
        'rabbitIds': _sortedUniqueIds(rabbitIds),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (_) {},
    );
  }

  Future<void> finishAphrodisiac({
    required int houseId,
    required int batchId,
    required List<int> rabbitIds,
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/aphrodisiac/finish',
      houseId: houseId,
      body: {
        'rabbitIds': _sortedUniqueIds(rabbitIds),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (_) {},
    );
  }

  Future<void> completeBatch({
    required int houseId,
    required int batchId,
    required DateTime endDate,
    required bool force,
    String remark = '',
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/complete',
      houseId: houseId,
      body: {
        'endDate': formatBatchWriteDate(endDate),
        'force': force,
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (_) {},
    );
  }

  Future<void> submitPregnancyCheck({
    required int houseId,
    required int batchId,
    required int rabbitId,
    int? breedingCycleId,
    required DateTime checkDate,
    required String result,
    String remark = '',
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/pregnancy-check',
      houseId: houseId,
      body: {
        'rabbitId': rabbitId,
        if (breedingCycleId != null) 'breedingCycleId': breedingCycleId,
        'checkDate': formatBatchWriteDate(checkDate),
        'result': result.trim(),
        'requestId': requestId ?? _uuid.v4(),
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
      },
      decode: (_) {},
    );
  }

  Future<void> submitPrepartumFinish({
    required int houseId,
    required int batchId,
    required int rabbitId,
    int? breedingCycleId,
    required DateTime actionDate,
    String remark = '',
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/prepartum/finish',
      houseId: houseId,
      body: {
        'rabbitId': rabbitId,
        if (breedingCycleId != null) 'breedingCycleId': breedingCycleId,
        'actionDate': formatBatchWriteDate(actionDate),
        'requestId': requestId ?? _uuid.v4(),
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
      },
      decode: (_) {},
    );
  }

  Future<void> submitParturition({
    required int houseId,
    required int batchId,
    required int rabbitId,
    int? breedingCycleId,
    required DateTime birthDate,
    required int totalKits,
    required int liveKits,
    bool failed = false,
    String remark = '',
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/parturition',
      houseId: houseId,
      body: {
        'rabbitId': rabbitId,
        if (breedingCycleId != null) 'breedingCycleId': breedingCycleId,
        'birthDate': formatBatchWriteDate(birthDate),
        'totalKits': totalKits,
        'liveKits': liveKits,
        'failed': failed,
        'requestId': requestId ?? _uuid.v4(),
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
      },
      decode: (_) {},
    );
  }

  Future<void> submitSale({
    required int houseId,
    required int batchId,
    required List<int> rabbitIds,
    required DateTime saleDate,
    String remark = '',
    String? requestId,
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/sale',
      houseId: houseId,
      body: {
        'rabbitIds': _sortedUniqueIds(rabbitIds),
        'saleDate': formatBatchWriteDate(saleDate),
        'requestId': requestId ?? _uuid.v4(),
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
      },
      decode: (_) {},
    );
  }
}

String formatBatchWriteDate(DateTime date) {
  final y = date.year.toString().padLeft(4, '0');
  final m = date.month.toString().padLeft(2, '0');
  final d = date.day.toString().padLeft(2, '0');
  return '$y-$m-$d';
}

List<int> _sortedUniqueIds(Iterable<int> ids) {
  return ids.toSet().toList()..sort();
}
