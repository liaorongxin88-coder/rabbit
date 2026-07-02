import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/batch.dart';
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
}
