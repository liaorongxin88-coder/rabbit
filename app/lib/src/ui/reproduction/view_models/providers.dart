import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/domain/reproduction/entry_point.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';

/// 阶段到可执行动作的服务端字典，界面据此决定可展示的操作入口。
final reproStageActionsProvider =
    FutureProvider.family<Map<String, List<String>>, int>((ref, houseId) async {
  return ref.watch(reproRepositoryProvider).stageActions(houseId: houseId);
});

/// 母兔入轨表单使用的阶段与字段定义。
final reproEntryPointsProvider =
    FutureProvider.family<List<ReproEntryPoint>, int>((ref, houseId) async {
  return ref.watch(reproRepositoryProvider).entryPoints(houseId: houseId);
});

/// 一只兔子的繁育待办查询键。
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
