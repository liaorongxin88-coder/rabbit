import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/domain/models/repro_task.dart';

final reproRepositoryProvider = Provider<ReproRepository>((ref) {
  return ReproRepository(ref.watch(apiClientProvider));
});

/// 阶段→可执行动作字典，全局取一次。
///
/// 这是业务常量，不随房舍或兔只变，所以不带参数。界面用它决定
/// 「流产」这类非计划入口该不该出现，而不是在客户端再拄一张规则表。
final reproStageActionsProvider =
    FutureProvider.family<Map<String, List<String>>, int>((ref, houseId) async {
  return ref.watch(reproRepositoryProvider).stageActions(houseId: houseId);
});

/// 生产流程（doe-breeding-v2）的客户端入口。
///
/// 与被它取代的 `BatchRepository` 那八个写方法相比，这里最大的不同是<b>只有一个
/// 写方法</b>：[applyAction]。旧客户端为六个动作各写了一个方法、各拼一套 body、
/// 各自校验，结果是同一条业务规则在六处漂移；服务端也正因为同样的原因做了这次重构。
/// 动作之间的差异现在只体现为参数是否传，而不是走不走另一条代码路径。
class ReproRepository {
  ReproRepository(this._api);

  final ApiClient _api;
  static const _uuid = Uuid();

  /// 待办清单。
  ///
  /// 首页今日待办、笼位 NFC 轻触、兔卡、批次详情全部走这一个接口，
  /// 只是过滤条件不同——旧实现里首页和笼位各查各的，两边给出的提醒并不一致。
  ///
  /// [dueBefore] 不传时服务端默认「今天及以前」，即今日待办 + 逾期。
  /// 要看未来的待办必须显式给一个将来的时间。
  Future<ReproTaskPage> listTasks({
    required int houseId,
    DateTime? dueBefore,
    String? taskType,
    int? batchId,
    int? cageId,
    int? rabbitId,
    int page = 1,
    int size = 50,
  }) {
    return _api.get<ReproTaskPage>(
      '/api/tasks',
      houseId: houseId,
      query: {
        if (dueBefore != null) 'dueBefore': dueBefore.millisecondsSinceEpoch,
        if (taskType != null && taskType.isNotEmpty) 'type': taskType,
        if (batchId != null) 'batchId': batchId,
        if (cageId != null) 'cageId': cageId,
        if (rabbitId != null) 'rabbitId': rabbitId,
        'page': page,
        'size': size,
      },
      decode: (data) => data is Map
          ? ReproTaskPage.fromJson(Map<String, dynamic>.from(data))
          : ReproTaskPage.empty,
    );
  }

  /// 阶段→可执行动作字典。
  ///
  /// 客户端不自己维护「哪个阶段能做什么」：那张表的真相在服务端的转换表里，
  /// 拄写一份就会漂移，用户会看到点下去必定 409 的按钮。
  Future<Map<String, List<String>>> stageActions({required int houseId}) {
    return _api.get<Map<String, List<String>>>(
      '/api/repro/stage-actions',
      houseId: houseId,
      decode: (data) {
        final result = <String, List<String>>{};
        for (final raw in (data as List? ?? const [])) {
          final row = Map<String, dynamic>.from(raw as Map);
          result[row['stage']?.toString() ?? ''] = [
            for (final a in (row['actions'] as List? ?? const []))
              Map<String, dynamic>.from(a as Map)['action']?.toString() ?? '',
          ];
        }
        return result;
      },
    );
  }

  /// 母兔入轨：为一头还没有生产周期的母兔开一轮。
  ///
  /// 用于存量录入、建场初始化、后备兔转种兔。日常流程不需要它——
  /// 每轮结束时服务端会自动接续下一轮。
  Future<ReproActionResult> openCycle({
    required int houseId,
    required int motherRabbitId,
    int? batchId,
    ReproStage stage = ReproStage.awaitEstrus,
    DateTime? occurredAt,
    DateTime? matingDate,
    DateTime? expectedBirthDate,
    DateTime? birthDate,
    int? totalKits,
    int? liveKits,
    int? maleRabbitId,
    MatingMethod? matingMethod,
    DateTime? firstDueAt,
    String remark = '',
    String? requestId,
  }) {
    return _api.post<ReproActionResult>(
      '/api/repro/cycles',
      houseId: houseId,
      body: {
        'motherRabbitId': motherRabbitId,
        if (batchId != null) 'batchId': batchId,
        'stage': stage.wire,
        if (occurredAt != null) 'occurredAt': occurredAt.millisecondsSinceEpoch,
        if (matingDate != null) 'matingDate': matingDate.millisecondsSinceEpoch,
        if (expectedBirthDate != null)
          'expectedBirthDate': expectedBirthDate.millisecondsSinceEpoch,
        if (birthDate != null) 'birthDate': birthDate.millisecondsSinceEpoch,
        if (totalKits != null) 'totalKits': totalKits,
        if (liveKits != null) 'liveKits': liveKits,
        if (maleRabbitId != null) 'maleRabbitId': maleRabbitId,
        if (matingMethod != null) 'matingMethod': matingMethod.wire,
        if (firstDueAt != null) 'firstDueAt': firstDueAt.millisecondsSinceEpoch,
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: _decodeAction,
    );
  }

  /// 单只母兔的一次状态推进——六个动作表单共用的唯一写入口。
  ///
  /// 是否合法由服务端的转换表判定，客户端不再自己判断「当前阶段能不能做这个动作」。
  /// 非法组合会返回 409 并附带可直接展示的中文原因。
  Future<ReproActionResult> applyAction({
    required int houseId,
    required int cycleId,
    required ReproAction action,
    DateTime? occurredAt,
    // 接产：BORN / FAILED
    String? outcome,
    // 配种
    int? maleRabbitId,
    MatingMethod? matingMethod,
    // 摸胎
    PalpationResult? palpationResult,
    // 推迟，以及摸胎「不确定」时的复查日期
    DateTime? nextRemindAt,
    // 接产
    int? totalKits,
    int? liveKits,
    int? keptKits,
    // 分笼
    int? weanedCount,
    double? avgWeaningWeight,
    int? targetCageId,
    int? maleCount,
    int? femaleCount,
    // 流产
    int? stillbirthCount,
    // 流产 / 离场
    String reason = '',
    String remark = '',
    List<String> attachmentFileIds = const [],
    String? requestId,
  }) {
    return _api.post<ReproActionResult>(
      '/api/repro/cycles/$cycleId/actions',
      houseId: houseId,
      body: {
        'action': action.wire,
        if (outcome != null) 'outcome': outcome,
        if (occurredAt != null) 'occurredAt': occurredAt.millisecondsSinceEpoch,
        if (maleRabbitId != null) 'maleRabbitId': maleRabbitId,
        if (matingMethod != null) 'matingMethod': matingMethod.wire,
        if (palpationResult != null) 'palpationResult': palpationResult.wire,
        if (nextRemindAt != null)
          'nextRemindAt': nextRemindAt.millisecondsSinceEpoch,
        if (totalKits != null) 'totalKits': totalKits,
        if (liveKits != null) 'liveKits': liveKits,
        if (keptKits != null) 'keptKits': keptKits,
        if (weanedCount != null) 'weanedCount': weanedCount,
        if (avgWeaningWeight != null) 'avgWeaningWeight': avgWeaningWeight,
        if (targetCageId != null && targetCageId > 0)
          'targetCageId': targetCageId,
        if (maleCount != null) 'maleCount': maleCount,
        if (femaleCount != null) 'femaleCount': femaleCount,
        if (stillbirthCount != null) 'stillbirthCount': stillbirthCount,
        if (reason.trim().isNotEmpty) 'reason': reason.trim(),
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
        if (attachmentFileIds.isNotEmpty)
          'attachmentFileIds': attachmentFileIds,
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: _decodeAction,
    );
  }

  /// 批量推进待办。
  ///
  /// 目标二选一：显式 [taskIds]，或按 [batchId] / [taskType] / [cageId] 过滤。
  /// 两者性质不同——过滤形式解析的是「此刻仍待办的」，整轮做完再发一次自然什么也不做；
  /// 显式 id 则会重新命中同一批，靠 requestId 走幂等回放。
  ///
  /// 接产与分笼不支持批量：每头母兔的仔数、去向笼位都不一样，共用一份数据没有意义。
  ///
  /// 返回逐项结果。<b>部分成功是常态</b>：一百头里有一头被别人推进过，
  /// 不应该让另外九十九头白做，所以整体仍是 HTTP 200，失败项在 [ReproBulkResult.failures] 里。
  Future<ReproBulkResult> bulkApply({
    required int houseId,
    required ReproAction action,
    DateTime? occurredAt,
    List<int>? taskIds,
    int? batchId,
    String? taskType,
    int? cageId,
    String? outcome,
    int? maleRabbitId,
    MatingMethod? matingMethod,
    PalpationResult? palpationResult,
    DateTime? nextRemindAt,
    String reason = '',
    String remark = '',
    String? requestId,
  }) {
    final hasIds = taskIds != null && taskIds.isNotEmpty;
    final hasFilter = batchId != null || taskType != null || cageId != null;
    assert(
      hasIds != hasFilter,
      '批量目标必须二选一：taskIds 或 filter',
    );

    return _api.post<ReproBulkResult>(
      '/api/repro/tasks/bulk-actions',
      houseId: houseId,
      body: {
        'action': action.wire,
        if (outcome != null) 'outcome': outcome,
        if (occurredAt != null) 'occurredAt': occurredAt.millisecondsSinceEpoch,
        if (maleRabbitId != null) 'maleRabbitId': maleRabbitId,
        if (matingMethod != null) 'matingMethod': matingMethod.wire,
        if (palpationResult != null) 'palpationResult': palpationResult.wire,
        if (nextRemindAt != null)
          'nextRemindAt': nextRemindAt.millisecondsSinceEpoch,
        if (reason.trim().isNotEmpty) 'reason': reason.trim(),
        if (remark.trim().isNotEmpty) 'remark': remark.trim(),
        if (hasIds) 'taskIds': _sortedUnique(taskIds),
        if (hasFilter)
          'filter': {
            if (batchId != null) 'batchId': batchId,
            if (taskType != null && taskType.isNotEmpty) 'taskType': taskType,
            if (cageId != null) 'cageId': cageId,
          },
        'requestId': requestId ?? _uuid.v4(),
      },
      decode: (data) => data is Map
          ? ReproBulkResult.fromJson(Map<String, dynamic>.from(data))
          : const ReproBulkResult(total: 0, succeeded: 0, failed: 0, items: []),
    );
  }

  /// 对「选中的这几只母兔」批量执行一个动作。
  ///
  /// 界面上用户选的是兔子，而批量接口收的是待办 id，中间这层解析放在这里而不是
  /// 摄入每个页面：否则每个调用方都要自己写一遍「取待办→按兔子过滤→拿 id」，
  /// 那正是旧实现里同一条规则在多处漂移的起点。
  ///
  /// 选中但当下没有对应待办的兔子会被自然跳过（比如别人已经做过了），
  /// 这不算错误；一个都没解析到时返回 total 为 0 的空结果，由调用方提示用户。
  Future<ReproBulkResult> bulkApplyForRabbits({
    required int houseId,
    required int batchId,
    required String taskType,
    required ReproAction action,
    required List<int> rabbitIds,
    DateTime? occurredAt,
    int? maleRabbitId,
    MatingMethod? matingMethod,
    PalpationResult? palpationResult,
    DateTime? nextRemindAt,
    String remark = '',
    String? requestId,
  }) async {
    final wanted = rabbitIds.toSet();
    // 拉到足够远的将来，否则默认只能看到「今日及逆期」，
    // 提前做的批次（比如已安排到两天后的配种）会惄无声息地漏掉。
    final page = await listTasks(
      houseId: houseId,
      batchId: batchId,
      taskType: taskType,
      dueBefore: DateTime.now().add(const Duration(days: 3650)),
      size: 500,
    );
    final taskIds = page.items
        .where((task) => task.rabbitId != null && wanted.contains(task.rabbitId))
        .map((task) => task.id)
        .toList();
    if (taskIds.isEmpty) {
      return const ReproBulkResult(
        total: 0,
        succeeded: 0,
        failed: 0,
        items: [],
      );
    }
    return bulkApply(
      houseId: houseId,
      action: action,
      taskIds: taskIds,
      occurredAt: occurredAt,
      maleRabbitId: maleRabbitId,
      matingMethod: matingMethod,
      palpationResult: palpationResult,
      nextRemindAt: nextRemindAt,
      remark: remark,
      requestId: requestId,
    );
  }

  /// 批量配种，同时处理两类母兔。
  ///
  /// 这两类在新模型里的处境并不一样，合并在这里是为了不把差异泄露给界面：
  ///
  /// - [matableRabbitIds]：已处于待配种，有现成的配种待办，直接走批量推进。
  /// - [nursingRabbitIds]：还在哺乳（血配）。哺乳周期不占用流水线，所以她根本
  ///   没有配种待办，必须先另开一个处于待配种的新周期，再对新周期配种——
  ///   一头母兔同时持有哺乳周期与新怀孕周期，正是血配在新模型里的形态。
  ///
  /// 血配分两步而不是一次性以「待摸胎」入轨，是为了走完整的配种校验（公兔资格、
  /// 二次配种日不得早于上一窝产仔日）；入轨接口故意不做这些校验，因为它的本职
  /// 是补录历史事实（公兔可能早已离场）。若第二步失败，母兔停在待配种，
  /// 是个可从界面继续推进的合法中间态，不会脏数据。
  Future<ReproBulkResult> bulkMate({
    required int houseId,
    required int batchId,
    required List<int> matableRabbitIds,
    required List<int> nursingRabbitIds,
    required int maleRabbitId,
    required DateTime matingDate,
    MatingMethod matingMethod = MatingMethod.natural,
    String? requestId,
  }) async {
    final seed = requestId ?? _uuid.v4();

    // 血配：逐头开新周期。失败不中断其他母兔。
    final bloodMated = <int>[];
    final failures = <ReproBulkItem>[];
    for (final rabbitId in nursingRabbitIds) {
      try {
        final opened = await openCycle(
          houseId: houseId,
          motherRabbitId: rabbitId,
          batchId: batchId,
          stage: ReproStage.awaitMating,
          occurredAt: matingDate,
          firstDueAt: matingDate,
          requestId: '$seed-open-$rabbitId',
        );
        await applyAction(
          houseId: houseId,
          cycleId: opened.cycleId,
          action: ReproAction.mating,
          occurredAt: matingDate,
          maleRabbitId: matingMethod == MatingMethod.ai ? null : maleRabbitId,
          matingMethod: matingMethod,
          requestId: '$seed-mate-$rabbitId',
        );
        bloodMated.add(rabbitId);
      } on Object catch (error) {
        failures.add(
          ReproBulkItem(
            ok: false,
            rabbitId: rabbitId,
            message: _messageOf(error),
          ),
        );
      }
    }

    ReproBulkResult byTasks = const ReproBulkResult(
      total: 0,
      succeeded: 0,
      failed: 0,
      items: [],
    );
    if (matableRabbitIds.isNotEmpty) {
      byTasks = await bulkApplyForRabbits(
        houseId: houseId,
        batchId: batchId,
        taskType: 'MATING',
        action: ReproAction.mating,
        rabbitIds: matableRabbitIds,
        occurredAt: matingDate,
        maleRabbitId: matingMethod == MatingMethod.ai ? null : maleRabbitId,
        matingMethod: matingMethod,
        requestId: seed,
      );
    }

    return ReproBulkResult(
      total: byTasks.total + nursingRabbitIds.length,
      succeeded: byTasks.succeeded + bloodMated.length,
      failed: byTasks.failed + failures.length,
      items: [...byTasks.items, ...failures],
    );
  }

  static String _messageOf(Object error) {
    final message = error.toString();
    // ApiException.toString() 已是可读中文；其余异常就原样呈现。
    return message.isEmpty ? '提交失败' : message;
  }

  static ReproActionResult _decodeAction(Object? data) {
    if (data is Map) {
      return ReproActionResult.fromJson(Map<String, dynamic>.from(data));
    }
    return const ReproActionResult(cycleId: 0);
  }

  static List<int> _sortedUnique(List<int> ids) {
    final unique = ids.where((id) => id > 0).toSet().toList()..sort();
    return unique;
  }
}
