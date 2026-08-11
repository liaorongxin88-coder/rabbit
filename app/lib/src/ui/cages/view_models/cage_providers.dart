import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/domain/models/cage_summary.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

typedef CageDetailKey = ({int houseId, int cageId});

final cageSummaryProvider =
    FutureProvider.autoDispose.family<CageSummary, CageDetailKey>((ref, key) {
  ref.watch(authenticatedUserIdProvider);
  return ref
      .watch(rabbitRepositoryProvider)
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
