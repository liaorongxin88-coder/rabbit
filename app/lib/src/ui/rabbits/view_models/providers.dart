import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/vaccinations/repository.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/rabbits/batch_membership.dart';
import 'package:rabbit_flutter/src/domain/rabbits/vaccination.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';

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
      .listAllActiveRabbits(houseId, cancelToken: cancelToken);
});

final houseBreedingRabbitsProvider =
    FutureProvider.autoDispose.family<List<Rabbit>, int>((ref, houseId) async {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return const <Rabbit>[];
  }
  final cancelToken = CancelToken();
  ref.onDispose(cancelToken.cancel);
  return ref
      .watch(rabbitRepositoryProvider)
      .listAllActiveBreedingRabbits(houseId, cancelToken: cancelToken);
});

final houseBreedingParentCandidatesProvider =
    FutureProvider.autoDispose.family<List<Rabbit>, int>((ref, houseId) async {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return const <Rabbit>[];
  }
  final cancelToken = CancelToken();
  ref.onDispose(cancelToken.cancel);
  final rabbits = await ref
      .watch(rabbitRepositoryProvider)
      .listAllBreedingRabbits(houseId, cancelToken: cancelToken);
  return rabbits
      .where((rabbit) => rabbit.houseId == houseId && rabbit.type == '0')
      .toList(growable: false);
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

class RabbitDetailRequest {
  const RabbitDetailRequest({
    required this.houseId,
    required this.rabbitId,
  });

  final int houseId;
  final int rabbitId;

  @override
  bool operator ==(Object other) {
    return other is RabbitDetailRequest &&
        other.houseId == houseId &&
        other.rabbitId == rabbitId;
  }

  @override
  int get hashCode => Object.hash(houseId, rabbitId);
}

final rabbitDetailProvider =
    FutureProvider.autoDispose.family<Rabbit, RabbitDetailRequest>(
  (ref, request) async {
    final userId = ref.watch(authenticatedUserIdProvider);
    if (userId <= 0) {
      throw StateError('登录状态已失效');
    }
    if (request.houseId <= 0 || request.rabbitId <= 0) {
      throw ArgumentError('兔只详情参数不正确');
    }
    final cancelToken = CancelToken();
    ref.onDispose(cancelToken.cancel);
    return ref.watch(rabbitRepositoryProvider).getRabbit(
          houseId: request.houseId,
          rabbitId: request.rabbitId,
          cancelToken: cancelToken,
        );
  },
);

class RabbitBatchMembershipRequest {
  const RabbitBatchMembershipRequest({
    required this.houseId,
    required this.rabbitId,
    this.active = true,
  });

  final int houseId;
  final int rabbitId;
  final bool active;

  @override
  bool operator ==(Object other) {
    return other is RabbitBatchMembershipRequest &&
        other.houseId == houseId &&
        other.rabbitId == rabbitId &&
        other.active == active;
  }

  @override
  int get hashCode => Object.hash(houseId, rabbitId, active);
}

final rabbitBatchMembershipsProvider = FutureProvider.autoDispose
    .family<List<RabbitBatchMembership>, RabbitBatchMembershipRequest>(
  (ref, request) async {
    final userId = ref.watch(authenticatedUserIdProvider);
    if (userId <= 0) {
      return const <RabbitBatchMembership>[];
    }
    if (request.houseId <= 0 || request.rabbitId <= 0) {
      throw ArgumentError('兔只批次关系参数不正确');
    }
    final cancelToken = CancelToken();
    ref.onDispose(cancelToken.cancel);
    return ref.watch(rabbitRepositoryProvider).listRabbitBatchMemberships(
          houseId: request.houseId,
          rabbitId: request.rabbitId,
          active: request.active,
          cancelToken: cancelToken,
        );
  },
);

/// 单只兔的接种历史。复用 [RabbitDetailRequest] 作为 family key，
/// 免得为同一对 (houseId, rabbitId) 再造一个等价的值对象。
final rabbitVaccinationsProvider = FutureProvider.autoDispose
    .family<List<VaccinationRecord>, RabbitDetailRequest>(
  (ref, request) async {
    final userId = ref.watch(authenticatedUserIdProvider);
    if (userId <= 0) {
      return const <VaccinationRecord>[];
    }
    if (request.houseId <= 0 || request.rabbitId <= 0) {
      throw ArgumentError('接种记录参数不正确');
    }
    final cancelToken = CancelToken();
    ref.onDispose(cancelToken.cancel);
    return ref.watch(vaccinationRepositoryProvider).listByRabbit(
          houseId: request.houseId,
          rabbitId: request.rabbitId,
          cancelToken: cancelToken,
        );
  },
);

/// 全舍待接种：已过 next_due_date 且尚未补种。
final houseDueVaccinationsProvider = FutureProvider.autoDispose
    .family<List<VaccinationRecord>, int>((ref, houseId) async {
  final userId = ref.watch(authenticatedUserIdProvider);
  if (userId <= 0 || houseId <= 0) {
    return const <VaccinationRecord>[];
  }
  final cancelToken = CancelToken();
  ref.onDispose(cancelToken.cancel);
  return ref
      .watch(vaccinationRepositoryProvider)
      .listDue(houseId: houseId, cancelToken: cancelToken);
});
