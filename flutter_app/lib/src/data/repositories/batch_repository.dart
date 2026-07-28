import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/batch.dart';
import 'package:rabbit_flutter/src/domain/models/batch_rabbit.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

final batchRepositoryProvider = Provider<BatchRepository>((ref) {
  return BatchRepository(ref.watch(apiClientProvider));
});

final currentHouseBatchesProvider = FutureProvider<List<Batch>>((ref) async {
  final houseId = ref.watch(authControllerProvider).valueOrNull?.houseId ?? 0;
  if (houseId <= 0) {
    return const <Batch>[];
  }
  return ref.watch(batchRepositoryProvider).listBatches(houseId);
});

final houseBatchesProvider =
    FutureProvider.family<List<Batch>, int>((ref, houseId) async {
  if (houseId <= 0) {
    return const <Batch>[];
  }
  return ref.watch(batchRepositoryProvider).listBatches(houseId);
});

class BatchRepository {
  BatchRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  Future<List<Batch>> listBatches(int houseId) {
    return _api.get<List<Batch>>(
      '/api/batches',
      houseId: houseId,
      query: const {'page': 1, 'pageSize': 20},
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
  }) {
    return _api.post<Batch>(
      '/api/batches',
      houseId: houseId,
      body: {
        'batchCode': batchCode.trim(),
        'femaleRabbitIds': femaleRabbitIds,
        'remark': remark.trim(),
        'requestId': _uuid.v4(),
      },
      decode: (data) {
        if (data is! Map) {
          throw const ApiException('创建批次结果格式不正确');
        }
        return Batch.fromJson(Map<String, dynamic>.from(data));
      },
    );
  }

  Future<List<BatchRabbitItem>> listBatchRabbits({
    required int houseId,
    required int batchId,
    bool? active,
  }) {
    return _api.get<List<BatchRabbitItem>>(
      '/api/batches/$batchId/batch-rabbits',
      houseId: houseId,
      query: {
        if (active != null) 'active': active,
      },
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
    required DateTime weaningDate,
    required int weaningCount,
    int? maleCount,
    int? femaleCount,
    int? targetCageId,
    double? avgWeight,
    String remark = '',
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/weaning',
      houseId: houseId,
      body: {
        'rabbitId': rabbitId,
        'requestId': _uuid.v4(),
        'weaningDate': _formatDate(weaningDate),
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
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/mating',
      houseId: houseId,
      body: {
        'femaleRabbitId': femaleRabbitId,
        'maleRabbitId': maleRabbitId,
        'matingDate': _formatDate(matingDate),
        'requestId': _uuid.v4(),
      },
      decode: (_) {},
    );
  }

  Future<void> submitPregnancyCheck({
    required int houseId,
    required int batchId,
    required int rabbitId,
    required DateTime checkDate,
    required String result,
    String remark = '',
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/pregnancy-check',
      houseId: houseId,
      body: {
        'rabbitId': rabbitId,
        'checkDate': _formatDate(checkDate),
        'result': result,
        'requestId': _uuid.v4(),
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
      },
      decode: (_) {},
    );
  }

  Future<void> submitPrepartumFinish({
    required int houseId,
    required int batchId,
    required int rabbitId,
    required DateTime actionDate,
    String remark = '',
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/prepartum/finish',
      houseId: houseId,
      body: {
        'rabbitId': rabbitId,
        'actionDate': _formatDate(actionDate),
        'requestId': _uuid.v4(),
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
      },
      decode: (_) {},
    );
  }

  Future<void> submitParturition({
    required int houseId,
    required int batchId,
    required int rabbitId,
    required DateTime birthDate,
    required int totalKits,
    required int liveKits,
    bool failed = false,
    String remark = '',
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/parturition',
      houseId: houseId,
      body: {
        'rabbitId': rabbitId,
        'birthDate': _formatDate(birthDate),
        'totalKits': totalKits,
        'liveKits': liveKits,
        'failed': failed,
        'requestId': _uuid.v4(),
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
  }) {
    return _api.post<void>(
      '/api/batches/$batchId/sale',
      houseId: houseId,
      body: {
        'rabbitIds': rabbitIds,
        'saleDate': _formatDate(saleDate),
        'requestId': _uuid.v4(),
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
      },
      decode: (_) {},
    );
  }

  static String _formatDate(DateTime date) {
    final y = date.year.toString().padLeft(4, '0');
    final m = date.month.toString().padLeft(2, '0');
    final d = date.day.toString().padLeft(2, '0');
    return '$y-$m-$d';
  }
}
