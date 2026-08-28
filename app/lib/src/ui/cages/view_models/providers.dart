import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/cages/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/cages/summary.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';

typedef CageDetailKey = ({int houseId, int cageId});

final houseCagesProvider =
    FutureProvider.autoDispose.family<List<Cage>, int>((ref, houseId) async {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return const <Cage>[];
  }
  final cancelToken = CancelToken();
  ref.onDispose(cancelToken.cancel);
  return ref
      .watch(cageRepositoryProvider)
      .listCages(houseId, cancelToken: cancelToken);
});

/// 断奶后尚未分笼的商品兔总数。
///
/// 待分笼记录按批次归属，笼位页没有新的聚合接口时在客户端并发汇总。
final pendingCommodityAllocationCountProvider =
    FutureProvider.autoDispose.family<int, int>((ref, houseId) async {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return 0;
  }
  final cancelToken = CancelToken();
  ref.onDispose(cancelToken.cancel);
  final repository = ref.watch(batchRepositoryProvider);
  final batches = await repository.listBatches(
    houseId,
    cancelToken: cancelToken,
  );
  final records = await Future.wait(
    batches.map(
      (batch) => repository.listPendingWeaningRecords(
        houseId: houseId,
        batchId: batch.id,
        cancelToken: cancelToken,
      ),
    ),
  );
  return records.expand((items) => items).fold<int>(
        0,
        (total, record) => total + record.waitingCount,
      );
});

final cageSummaryProvider =
    FutureProvider.autoDispose.family<CageSummary, CageDetailKey>((ref, key) {
  ref.watch(authenticatedUserIdProvider);
  return ref
      .watch(cageRepositoryProvider)
      .getCageSummary(houseId: key.houseId, cageId: key.cageId);
});

final cageRabbitsProvider =
    FutureProvider.autoDispose.family<List<Rabbit>, CageDetailKey>((ref, key) {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0) {
    return Future.value(const <Rabbit>[]);
  }
  return ref
      .watch(rabbitRepositoryProvider)
      .listRabbitsForCage(houseId: key.houseId, cageId: key.cageId);
});
