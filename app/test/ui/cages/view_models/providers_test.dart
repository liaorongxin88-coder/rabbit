import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/weaning.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';

void main() {
  test('pending allocation count ignores completed batches', () async {
    final repository = _RecordingBatchRepository();
    final container = ProviderContainer(
      overrides: [
        authenticatedUserIdProvider.overrideWithValue(7),
        batchRepositoryProvider.overrideWithValue(repository),
      ],
    );
    addTearDown(container.dispose);
    addTearDown(repository.dispose);

    final count = await container.read(
      pendingCommodityAllocationCountProvider(8).future,
    );

    expect(count, 3);
    expect(repository.requestedBatchIds, [20]);
  });
}

class _RecordingBatchRepository extends BatchRepository {
  factory _RecordingBatchRepository() {
    final client = ApiClient(SessionStore());
    return _RecordingBatchRepository._(client);
  }

  _RecordingBatchRepository._(this.client) : super(client);

  final ApiClient client;
  final requestedBatchIds = <int>[];

  void dispose() {
    client.dispose();
  }

  @override
  Future<List<Batch>> listBatches(
    int houseId, {
    CancelToken? cancelToken,
  }) async {
    return const [
      Batch(
        id: 20,
        houseId: 8,
        batchCode: 'OPEN',
        status: '进行中',
        startDate: null,
        endDate: null,
        remark: '',
      ),
      Batch(
        id: 14,
        houseId: 8,
        batchCode: 'DONE',
        status: '已完成',
        startDate: null,
        endDate: null,
        remark: '',
      ),
    ];
  }

  @override
  Future<List<PendingWeaningRecord>> listPendingWeaningRecords({
    required int houseId,
    required int batchId,
    CancelToken? cancelToken,
  }) async {
    requestedBatchIds.add(batchId);
    return [
      PendingWeaningRecord(
        id: 1,
        batchId: batchId,
        rabbitId: 101,
        weaningCount: 3,
        waitingCount: 3,
      ),
    ];
  }
}
