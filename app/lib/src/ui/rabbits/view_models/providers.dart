import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/rabbits/batch_membership.dart';
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
