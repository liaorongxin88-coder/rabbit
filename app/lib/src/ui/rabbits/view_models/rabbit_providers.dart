import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';

final houseCagesProvider =
    FutureProvider.autoDispose.family<List<Cage>, int>((ref, houseId) async {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return const <Cage>[];
  }
  final cancelToken = CancelToken();
  ref.onDispose(cancelToken.cancel);
  return ref
      .watch(rabbitRepositoryProvider)
      .listCages(houseId, cancelToken: cancelToken);
});

final houseRabbitsProvider =
    FutureProvider.autoDispose.family<List<Rabbit>, int>((ref, houseId) async {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return const <Rabbit>[];
  }
  final cancelToken = CancelToken();
  ref.onDispose(cancelToken.cancel);
  return ref
      .watch(rabbitRepositoryProvider)
      .listRabbits(houseId, cancelToken: cancelToken);
});

final allActiveHouseRabbitsProvider =
    FutureProvider.autoDispose.family<List<Rabbit>, int>((ref, houseId) async {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return const <Rabbit>[];
  }
  final cancelToken = CancelToken();
  ref.onDispose(cancelToken.cancel);
  return ref
      .watch(rabbitRepositoryProvider)
      .listAllActiveRabbits(houseId, cancelToken: cancelToken);
});
