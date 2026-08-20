/// 生产待办与生产动作的领域模型（doe-breeding-v2）。
///
/// 这里刻意不再出现「批次成员状态」这类旧词汇。旧客户端自己维护了一张
/// 「待办类型 → 可执行动作」的映射表，服务端也有一张，两张表各自演化后就对不上了。
/// 现在动作由服务端在 TaskView 上直接给出（[ReproTask.action]），客户端不再猜。
library;

/// 生产动作。取值与后端 `ReproAction` 一一对应。
enum ReproAction {
  startCycle('START_CYCLE', '入轨'),
  estrus('ESTRUS', '催情'),
  mating('MATING', '配种'),
  palpation('PALPATION', '摸胎'),
  prepartum('PREPARTUM', '备产'),
  delivery('DELIVERY', '接产'),
  weaning('WEANING', '分笼'),
  abortion('ABORTION', '流产'),
  postpone('POSTPONE', '推迟'),
  retire('RETIRE', '离场');

  const ReproAction(this.wire, this.label);

  /// 传给后端的枚举名。
  final String wire;

  /// 界面上显示的中文。
  final String label;

  static ReproAction? tryParse(String? value) {
    final normalized = value?.trim().toUpperCase();
    if (normalized == null || normalized.isEmpty) {
      return null;
    }
    for (final action in ReproAction.values) {
      if (action.wire == normalized) {
        return action;
      }
    }
    return null;
  }
}

/// 摸胎结论。
enum PalpationResult {
  pregnant('PREGNANT', '怀孕'),
  empty('EMPTY', '空怀'),
  unsure('UNSURE', '不确定');

  const PalpationResult(this.wire, this.label);

  final String wire;
  final String label;
}

/// 配种方式。
enum MatingMethod {
  natural('NATURAL', '体配'),
  ai('AI', '人工授精');

  const MatingMethod(this.wire, this.label);

  final String wire;
  final String label;
}

/// 生产阶段。仅用于展示与判断表单形态。
enum ReproStage {
  ready('READY', '准备'),
  awaitEstrus('AWAIT_ESTRUS', '待催情'),
  awaitMating('AWAIT_MATING', '待配种'),
  awaitPalpation('AWAIT_PALPATION', '待摸胎'),
  awaitPrepartum('AWAIT_PREPARTUM', '待备产'),
  awaitDelivery('AWAIT_DELIVERY', '待分娩'),
  awaitWeaning('AWAIT_WEANING', '待分笼'),
  suspended('SUSPENDED', '暂停'),
  retired('RETIRED', '离场');

  const ReproStage(this.wire, this.label);

  final String wire;
  final String label;

  static ReproStage? tryParse(String? value) {
    final normalized = value?.trim().toUpperCase();
    if (normalized == null || normalized.isEmpty) {
      return null;
    }
    for (final stage in ReproStage.values) {
      if (stage.wire == normalized) {
        return stage;
      }
    }
    return null;
  }
}

/// 一条生产待办。对应后端 `TaskView`。
class ReproTask {
  const ReproTask({
    required this.id,
    required this.taskType,
    required this.taskLabel,
    this.action,
    this.subjectType,
    this.subjectId,
    this.cycleId,
    this.rabbitId,
    this.batchId,
    this.cageId,
    this.dueDate,
    this.dueTime,
    this.status,
    this.overdue = false,
    this.snoozeCount = 0,
  });

  final int id;
  final String taskType;

  /// 服务端给的中文标签，客户端不再自己拼。
  final String taskLabel;

  /// 该待办对应的自然动作；为空表示这条待办不能直接推进生产流程。
  final ReproAction? action;

  /// CYCLE / LITTER / RABBIT / CAGE。分笼待办挂在窝上，其余挂在周期上。
  final String? subjectType;
  final int? subjectId;

  final int? cycleId;
  final int? rabbitId;
  final int? batchId;
  final int? cageId;
  final DateTime? dueDate;
  final DateTime? dueTime;
  final String? status;

  /// 是否已过期。由服务端判定，避免客户端时区与服务端不一致。
  final bool overdue;

  /// 被推迟过几次。用来把长期拖延的待办显性化。
  final int snoozeCount;

  bool get actionable => action != null && cycleId != null;

  static ReproTask fromJson(Map<String, dynamic> json) {
    return ReproTask(
      id: _int(json['id']) ?? 0,
      taskType: _str(json['taskType']),
      taskLabel: _str(json['taskLabel']),
      action: ReproAction.tryParse(json['action'] as String?),
      subjectType: json['subjectType'] as String?,
      subjectId: _int(json['subjectId']),
      cycleId: _int(json['cycleId']),
      rabbitId: _int(json['rabbitId']),
      batchId: _int(json['batchId']),
      cageId: _int(json['cageId']),
      dueDate: _date(json['dueDate']),
      dueTime: _date(json['dueTime']),
      status: json['status'] as String?,
      overdue: json['overdue'] == true,
      snoozeCount: _int(json['snoozeCount']) ?? 0,
    );
  }
}

/// 一页待办。`total` 让角标数字和列表用同一个过滤条件，不会各算各的。
class ReproTaskPage {
  const ReproTaskPage({
    required this.items,
    required this.total,
    this.page = 1,
    this.size = 50,
  });

  final List<ReproTask> items;
  final int total;
  final int page;
  final int size;

  static const ReproTaskPage empty = ReproTaskPage(items: [], total: 0);

  static ReproTaskPage fromJson(Map<String, dynamic> json) {
    final raw = json['items'];
    final items = raw is List
        ? raw
            .whereType<Map>()
            .map((e) => ReproTask.fromJson(Map<String, dynamic>.from(e)))
            .toList()
        : <ReproTask>[];
    return ReproTaskPage(
      items: items,
      total: _int(json['total']) ?? items.length,
      page: _int(json['page']) ?? 1,
      size: _int(json['size']) ?? items.length,
    );
  }
}

/// 一次动作提交后的结果。
class ReproActionResult {
  const ReproActionResult({
    required this.cycleId,
    this.currentCycleId,
    this.eventId,
    this.litterId,
    this.nextTaskId,
    this.stage,
    this.lifecycle,
    this.nextDueTime,
    this.followUpCycleId,
    this.replayed = false,
  });

  final int cycleId;

  /// 事务结束后 rabbits 权威投影中的活动周期，可能为空。
  final int? currentCycleId;

  final int? eventId;
  final int? litterId;
  final int? nextTaskId;
  final ReproStage? stage;
  final String? lifecycle;
  final DateTime? nextDueTime;

  /// 关周期并自动接续时，新开出来的那个周期。
  final int? followUpCycleId;

  /// 命中幂等回放：本次没有产生新的状态变更。
  final bool replayed;

  static ReproActionResult fromJson(Map<String, dynamic> json) {
    return ReproActionResult(
      cycleId: _int(json['cycleId']) ?? 0,
      currentCycleId: _int(json['currentCycleId']),
      eventId: _int(json['eventId']),
      litterId: _int(json['litterId']),
      nextTaskId: _int(json['nextTaskId']),
      stage: ReproStage.tryParse(json['stage'] as String?),
      lifecycle: json['lifecycle'] as String?,
      nextDueTime: _date(json['nextDueTime']),
      followUpCycleId: _int(json['followUpCycleId']),
      replayed: json['replayed'] == true,
    );
  }
}

/// 批量操作的逐项结果。部分成功是常态，不是异常。
class ReproBulkResult {
  const ReproBulkResult({
    required this.total,
    required this.succeeded,
    required this.failed,
    required this.items,
  });

  final int total;
  final int succeeded;
  final int failed;
  final List<ReproBulkItem> items;

  bool get allSucceeded => failed == 0 && total > 0;

  /// 失败项的可读原因，用于一次性提示用户哪些没成功。
  List<ReproBulkItem> get failures =>
      items.where((item) => !item.ok).toList(growable: false);

  static ReproBulkResult fromJson(Map<String, dynamic> json) {
    final raw = json['items'];
    return ReproBulkResult(
      total: _int(json['total']) ?? 0,
      succeeded: _int(json['succeeded']) ?? 0,
      failed: _int(json['failed']) ?? 0,
      items: raw is List
          ? raw
              .whereType<Map>()
              .map((e) => ReproBulkItem.fromJson(Map<String, dynamic>.from(e)))
              .toList()
          : <ReproBulkItem>[],
    );
  }
}

class ReproBulkItem {
  const ReproBulkItem({
    required this.ok,
    this.taskId,
    this.cycleId,
    this.rabbitId,
    this.code,
    this.message,
    this.replayed = false,
  });

  final bool ok;
  final int? taskId;
  final int? cycleId;
  final int? rabbitId;
  final int? code;
  final String? message;
  final bool replayed;

  static ReproBulkItem fromJson(Map<String, dynamic> json) {
    return ReproBulkItem(
      ok: json['ok'] == true,
      taskId: _int(json['taskId']),
      cycleId: _int(json['cycleId']),
      rabbitId: _int(json['rabbitId']),
      code: _int(json['code']),
      message: json['message'] as String?,
      replayed: json['replayed'] == true,
    );
  }
}

int? _int(Object? value) {
  if (value is int) {
    return value;
  }
  if (value is num) {
    return value.toInt();
  }
  if (value is String && value.isNotEmpty) {
    return int.tryParse(value);
  }
  return null;
}

String _str(Object? value) => value == null ? '' : value.toString();

DateTime? _date(Object? value) {
  if (value is int) {
    return DateTime.fromMillisecondsSinceEpoch(value);
  }
  if (value is num) {
    return DateTime.fromMillisecondsSinceEpoch(value.toInt());
  }
  if (value is String && value.isNotEmpty) {
    return DateTime.tryParse(value);
  }
  return null;
}
