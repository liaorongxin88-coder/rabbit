import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/domain/models/batch.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

final currentHouseBatchesProvider = FutureProvider<List<Batch>>((ref) async {
  final houseId = ref.watch(authControllerProvider).valueOrNull?.houseId ?? 0;
  if (houseId <= 0) {
    return const <Batch>[];
  }
  return ref.watch(batchRepositoryProvider).listBatches(houseId);
});

final houseBatchesProvider =
    FutureProvider.family<List<Batch>, int>((ref, houseId) async {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return const <Batch>[];
  }
  return ref.watch(batchRepositoryProvider).listBatches(houseId);
});
