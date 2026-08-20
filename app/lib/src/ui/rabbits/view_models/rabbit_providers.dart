import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/repro_repository.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_batch_membership.dart';
import 'package:rabbit_flutter/src/domain/models/repro_task.dart';
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

/// A stable family key for a rabbit's breeding tasks.
///
/// Reuse this value when invalidating [rabbitReproTasksProvider] after an action.
class RabbitReproTasksRequest {
  const RabbitReproTasksRequest({
    required this.houseId,
    required this.rabbitId,
  });

  final int houseId;
  final int rabbitId;

  @override
  bool operator ==(Object other) {
    return other is RabbitReproTasksRequest &&
        other.houseId == houseId &&
        other.rabbitId == rabbitId;
  }

  @override
  int get hashCode => Object.hash(houseId, rabbitId);
}

final rabbitReproTasksProvider =
    FutureProvider.autoDispose.family<List<ReproTask>, RabbitReproTasksRequest>(
  (ref, request) async {
    final userId = ref.watch(authenticatedUserIdProvider);
    if (userId <= 0) {
      return const <ReproTask>[];
    }
    if (request.houseId <= 0 || request.rabbitId <= 0) {
      throw ArgumentError('兔只繁育待办参数不正确');
    }

    final page = await ref.watch(reproRepositoryProvider).listTasks(
          houseId: request.houseId,
          rabbitId: request.rabbitId,
          includeFuture: true,
          size: 500,
        );
    final tasks = page.items
        .where((task) => task.status?.trim().toUpperCase() == 'PENDING')
        .toList(growable: false)
      ..sort(_compareRabbitReproTasks);
    return List<ReproTask>.unmodifiable(tasks);
  },
);

int _compareRabbitReproTasks(ReproTask left, ReproTask right) {
  var result = _compareNullableDateTimes(left.dueTime, right.dueTime);
  if (result != 0) {
    return result;
  }
  result = _compareNullableDateTimes(left.dueDate, right.dueDate);
  if (result != 0) {
    return result;
  }
  return left.id.compareTo(right.id);
}

int _compareNullableDateTimes(DateTime? left, DateTime? right) {
  if (left == null) {
    return right == null ? 0 : 1;
  }
  if (right == null) {
    return -1;
  }
  return left.compareTo(right);
}
