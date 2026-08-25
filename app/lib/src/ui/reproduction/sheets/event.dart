import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbits/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/reproduction/event.dart';
import 'package:rabbit_flutter/src/domain/settings/production.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/reproduction/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet_states.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/notice.dart';
import 'package:rabbit_flutter/src/ui/reproduction/widgets/context.dart';
import 'package:rabbit_flutter/src/ui/reproduction/widgets/action_time.dart';
import 'package:rabbit_flutter/src/ui/reproduction/widgets/required_images.dart';
import 'package:rabbit_flutter/src/ui/reproduction/sheets/weaning.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/providers.dart';

enum ProductionKind {
  estrus,
  mating,
  pregnancyCheck,
  prepartum,
  parturition,
  weaning,
  sale,
  replacement,
}

ProductionKind? productionKindFromEvent(EventItem event) {
  if (event.isReplacement) {
    return ProductionKind.replacement;
  }
  final type = event.eventType;
  if (type.contains('配种')) {
    return ProductionKind.mating;
  }
  if (type.contains('摸胎')) {
    return ProductionKind.pregnancyCheck;
  }
  if (type.contains('备产')) {
    return ProductionKind.prepartum;
  }
  if (type.contains('分娩') || type.contains('生产')) {
    return ProductionKind.parturition;
  }
  if (type.contains('断奶')) {
    return ProductionKind.weaning;
  }
  if (type.contains('出售')) {
    return ProductionKind.sale;
  }
  return null;
}

bool eventIsActionable(EventItem event) =>
    productionKindFromEvent(event) != null;

ProductionKind? productionKindFromTask(ReproTask task) {
  return switch (task.action) {
    ReproAction.estrus => ProductionKind.estrus,
    ReproAction.mating => ProductionKind.mating,
    ReproAction.palpation => ProductionKind.pregnancyCheck,
    ReproAction.prepartum => ProductionKind.prepartum,
    ReproAction.delivery => ProductionKind.parturition,
    ReproAction.weaning => ProductionKind.weaning,
    _ => null,
  };
}

bool reproTaskIsActionable(ReproTask task) =>
    task.actionable && productionKindFromTask(task) != null;

String reproTaskActionHint(ReproTask task) {
  return switch (task.action) {
    ReproAction.estrus => '完成催情',
    ReproAction.mating => '记录配种',
    ReproAction.palpation => '记录摸胎结果',
    ReproAction.prepartum => '完成备产',
    ReproAction.delivery => '记录分娩',
    ReproAction.weaning => '断奶并放入笼位',
    _ => '',
  };
}

String productionActionHint(EventItem event) {
  switch (productionKindFromEvent(event)) {
    case ProductionKind.estrus:
      return '完成催情';
    case ProductionKind.mating:
      return '记录配种';
    case ProductionKind.pregnancyCheck:
      return '记录摸胎结果';
    case ProductionKind.prepartum:
      return '完成备产';
    case ProductionKind.parturition:
      return '记录分娩';
    case ProductionKind.weaning:
      return '断奶并放入笼位';
    case ProductionKind.sale:
      return '记录出售';
    case ProductionKind.replacement:
      return '转为种兔';
    case null:
      return '';
  }
}

List<Rabbit> _availableBreedingMales(
  Iterable<Rabbit> rabbits, {
  required int houseId,
}) {
  return rabbits
      .where(
        (rabbit) =>
            rabbit.id > 0 &&
            rabbit.houseId == houseId &&
            rabbit.isActive &&
            rabbit.type == '0' &&
            rabbit.gender == '1',
      )
      .toList()
    ..sort((left, right) => left.id.compareTo(right.id));
}

bool _containsMaleId(Iterable<Rabbit> males, int? maleId) {
  return maleId != null && males.any((male) => male.id == maleId);
}

Future<void> showProductionEventSheet({
  required BuildContext context,
  required EventItem event,
}) async {
  final kind = productionKindFromEvent(event);
  if (kind == null) {
    return;
  }
  if (kind == ProductionKind.weaning) {
    await showWeaningSheet(context: context, event: event);
    return;
  }

  final houseId = event.sourceHouseId;
  final rabbitId = event.rabbitId;
  if (houseId == null || houseId <= 0 || rabbitId == null || rabbitId <= 0) {
    return;
  }

  await _showProductionActionSheet(
    context: context,
    input: _ProductionSheetInput(
      kind: kind,
      houseId: houseId,
      rabbitId: rabbitId,
      batchId: event.batchId,
      cycleId: event.isBreedingCycle ? event.recordId : null,
      initialDate: event.eventDate,
      contextLabel: event.houseLabel,
    ),
  );
}

/// 从服务端待办打开单兔生产动作。动作、周期和日期均来自 [task]，不再从
/// 事件中文名推断；首页和批次页仍可继续使用 [showProductionEventSheet]。
Future<ReproActionResult?> showReproTaskActionSheet({
  required BuildContext context,
  required int houseId,
  required ReproTask task,
}) {
  final kind = productionKindFromTask(task);
  final rabbitId = task.rabbitId;
  if (kind == null ||
      houseId <= 0 ||
      rabbitId == null ||
      rabbitId <= 0 ||
      task.cycleId == null ||
      task.cycleId! <= 0) {
    return Future<ReproActionResult?>.value();
  }
  if (kind == ProductionKind.weaning) {
    final event = EventItem(
      recordId: task.cycleId!,
      category: '生产周期',
      eventType: task.taskLabel,
      eventDate: task.dueTime,
      batchId: task.batchId,
      rabbitId: rabbitId,
      status: task.overdue ? 'overdue' : 'due',
      sourceHouseId: houseId,
    );
    return showWeaningSheet(context: context, event: event);
  }
  return _showProductionActionSheet(
    context: context,
    input: _ProductionSheetInput(
      kind: kind,
      houseId: houseId,
      rabbitId: rabbitId,
      batchId: task.batchId,
      cycleId: task.cycleId,
      initialDate: null,
      contextLabel: '生产待办 #${task.id}',
    ),
  );
}

Future<ReproActionResult?> _showProductionActionSheet({
  required BuildContext context,
  required _ProductionSheetInput input,
}) {
  return showAppModalSheet<ReproActionResult>(
    context: context,
    builder: (context) => _ProductionEventSheet(input: input),
  );
}

class _ProductionSheetInput {
  const _ProductionSheetInput({
    required this.kind,
    required this.houseId,
    required this.rabbitId,
    required this.contextLabel,
    this.batchId,
    this.cycleId,
    this.initialDate,
  });

  final ProductionKind kind;
  final int houseId;
  final int rabbitId;
  final String contextLabel;
  final int? batchId;
  final int? cycleId;
  final DateTime? initialDate;
}

class _ProductionEventSheet extends ConsumerStatefulWidget {
  const _ProductionEventSheet({required this.input});

  final _ProductionSheetInput input;

  ProductionKind get kind => input.kind;
  int get houseId => input.houseId;
  int get rabbitId => input.rabbitId;
  int? get batchId => input.batchId;
  int? get cycleId => input.cycleId;
  DateTime? get initialDate => input.initialDate;
  String get contextLabel => input.contextLabel;

  @override
  ConsumerState<_ProductionEventSheet> createState() =>
      _ProductionEventSheetState();
}

enum _NextReminderMode { houseSetting, custom }

class _ProductionEventSheetState extends ConsumerState<_ProductionEventSheet> {
  final _writeRequest = BatchWriteRequestController();
  late DateTime _actionDate;
  final _remarkController = TextEditingController();
  final _totalKitsController = TextEditingController(text: '8');
  final _liveKitsController = TextEditingController(text: '8');
  final _keptKitsController = TextEditingController(text: '8');
  var _palpationResult = PalpationResult.pregnant;
  var _matingMethod = MatingMethod.natural;

  /// 摸胎「不确定」时的复查日期。旧实现没有这个字段，结果是结论为不确定的母兔
  /// 停在待摸胎且再也收不到提醒，从流程里惄无声息地消失。
  DateTime? _recheckDate;
  DateTime? _postponeDate;
  DateTime? _customNextReminderDate;
  var _nextReminderMode = _NextReminderMode.houseSetting;
  var _postponed = false;
  var _parturitionFailed = false;
  int? _selectedMaleId;
  List<XFile> _images = const [];
  var _saving = false;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    final initial = widget.initialDate;
    _actionDate = initial == null || initial.isAfter(now) ? now : initial;
  }

  @override
  void dispose() {
    _remarkController.dispose();
    _totalKitsController.dispose();
    _liveKitsController.dispose();
    _keptKitsController.dispose();
    super.dispose();
  }

  String get _title {
    switch (widget.kind) {
      case ProductionKind.estrus:
        return '完成催情';
      case ProductionKind.mating:
        return '记录配种';
      case ProductionKind.pregnancyCheck:
        return '记录摸胎';
      case ProductionKind.prepartum:
        return '完成备产';
      case ProductionKind.parturition:
        return '记录分娩';
      case ProductionKind.sale:
        return '记录出售';
      case ProductionKind.replacement:
        return '转为种兔';
      case ProductionKind.weaning:
        return '断奶';
    }
  }

  Future<void> _pickDate() async {
    final picked = await pickActionTime(
      context: context,
      current: _actionDate,
      helpText: '选择$_title日期',
    );
    if (picked != null && mounted) {
      setState(() => _actionDate = picked);
    }
  }

  /// 复查日期按兔场的配种至摸胎时长预填，并且只允许今天及以后。
  Future<void> _pickRecheckDate() async {
    final today = localDateOnly(DateTime.now());
    final suggested = suggestedReminderDate(
      stage: ReproStage.awaitPalpation,
      setting: _reminderSetting,
      from: today,
    );
    final lastDate = today.add(const Duration(days: 3650));
    final picked = await showDatePicker(
      context: context,
      initialDate: reminderInitialDate(
        suggested: suggested,
        selected: _recheckDate,
        now: today,
        latest: lastDate,
      ),
      firstDate: today,
      lastDate: lastDate,
      helpText: '选择复查日期',
      cancelText: '取消',
      confirmText: '确定',
    );
    if (picked != null && mounted) {
      setState(() => _recheckDate = picked);
    }
  }

  Future<void> _pickPostponeDate() async {
    final today = localDateOnly(DateTime.now());
    final suggested = suggestedReminderDate(
      stage: _currentReminderStage,
      setting: _reminderSetting,
      from: today,
    );
    final lastDate = today.add(const Duration(days: 3650));
    final picked = await showDatePicker(
      context: context,
      initialDate: reminderInitialDate(
        suggested: suggested,
        selected: _postponeDate,
        now: today,
        latest: lastDate,
      ),
      firstDate: today,
      lastDate: lastDate,
      helpText: '选择下次提醒日期',
      cancelText: '取消',
      confirmText: '确定',
    );
    if (picked != null && mounted) {
      setState(() => _postponeDate = picked);
    }
  }

  Future<void> _pickCustomNextReminderDate() async {
    final today = localDateOnly(DateTime.now());
    final lastDate = today.add(const Duration(days: 3650));
    final picked = await showDatePicker(
      context: context,
      initialDate: reminderInitialDate(
        suggested: _suggestedNextReminderDate,
        selected: _customNextReminderDate,
        now: today,
        latest: lastDate,
      ),
      firstDate: today,
      lastDate: lastDate,
      helpText: '选择$_nextReminderDateLabel',
      cancelText: '取消',
      confirmText: '确定',
    );
    if (picked != null && mounted) {
      setState(() => _customNextReminderDate = localDateOnly(picked));
    }
  }

  GlobalSetting get _reminderSetting =>
      ref.read(houseSettingProvider(widget.houseId)).valueOrNull?.setting ??
      GlobalSetting.defaults();

  ReproStage get _currentReminderStage => switch (widget.kind) {
        ProductionKind.estrus => ReproStage.awaitEstrus,
        ProductionKind.mating => ReproStage.awaitMating,
        ProductionKind.pregnancyCheck => ReproStage.awaitPalpation,
        ProductionKind.prepartum => ReproStage.awaitPrepartum,
        ProductionKind.parturition => ReproStage.awaitDelivery,
        ProductionKind.weaning => ReproStage.awaitWeaning,
        ProductionKind.sale || ProductionKind.replacement => ReproStage.ready,
      };

  ReproStage? get _nextReminderStage => switch (widget.kind) {
        ProductionKind.estrus => ReproStage.awaitMating,
        ProductionKind.mating => ReproStage.awaitPalpation,
        ProductionKind.pregnancyCheck => switch (_palpationResult) {
            PalpationResult.pregnant => ReproStage.awaitPrepartum,
            PalpationResult.empty => ReproStage.awaitEstrus,
            PalpationResult.unsure => ReproStage.awaitPalpation,
          },
        ProductionKind.prepartum => ReproStage.awaitDelivery,
        ProductionKind.parturition =>
          _parturitionFailed ? ReproStage.awaitEstrus : ReproStage.awaitWeaning,
        ProductionKind.weaning ||
        ProductionKind.sale ||
        ProductionKind.replacement =>
          null,
      };

  String get _nextReminderDateLabel =>
      reminderDateLabelForStage(_nextReminderStage!);

  bool get _canCustomizeNextReminder {
    if (_postponed ||
        (widget.kind == ProductionKind.pregnancyCheck &&
            _palpationResult == PalpationResult.unsure)) {
      return false;
    }
    return _nextReminderStage != null;
  }

  DateTime get _suggestedNextReminderDate {
    final today = localDateOnly(DateTime.now());
    return reminderInitialDate(
      suggested: suggestedReminderDate(
        stage: _nextReminderStage!,
        setting: _reminderSetting,
        from: _actionDate,
      ),
      now: today,
      latest: today.add(const Duration(days: 3650)),
    );
  }

  DateTime? get _ordinaryNextRemindAt {
    if (!_canCustomizeNextReminder ||
        _nextReminderMode == _NextReminderMode.houseSetting) {
      return null;
    }
    return _customNextReminderDate ?? _suggestedNextReminderDate;
  }

  void _selectMale(int? value) {
    final selected = value == null || value <= 0 ? null : value;
    if (selected == _selectedMaleId) return;
    setState(() => _selectedMaleId = selected);
  }

  void _selectMatingMethod(MatingMethod method) {
    setState(() {
      _matingMethod = method;
      if (method == MatingMethod.ai) {
        _selectedMaleId = null;
      }
    });
  }

  bool get _canPostpone =>
      widget.kind == ProductionKind.estrus ||
      widget.kind == ProductionKind.mating ||
      widget.kind == ProductionKind.pregnancyCheck ||
      widget.kind == ProductionKind.prepartum ||
      widget.kind == ProductionKind.parturition;

  int? get _breedingCycleId => widget.cycleId;

  String _requestIdFor(Map<String, Object?> fields) {
    return _writeRequest.requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': widget.kind.name,
        'houseId': widget.houseId,
        'batchId': widget.batchId,
        'rabbitId': widget.rabbitId,
        'breedingCycleId': _breedingCycleId,
        ...fields,
      }),
    );
  }

  Future<void> _submit({
    required List<Rabbit> rabbits,
    required List<Cage> cages,
  }) async {
    final batchId = widget.batchId;
    if (widget.kind == ProductionKind.sale &&
        (batchId == null || batchId <= 0)) {
      _showMessage('批次信息缺失，请刷新后重试');
      return;
    }

    setState(() => _saving = true);
    try {
      final repo = ref.read(batchRepositoryProvider);
      final rabbitRepo = ref.read(rabbitRepositoryProvider);
      final reproRepo = ref.read(reproRepositoryProvider);
      ReproActionResult? actionResult;

      final cycleId = _breedingCycleId;
      final needsCycle = widget.kind == ProductionKind.estrus ||
          widget.kind == ProductionKind.mating ||
          widget.kind == ProductionKind.pregnancyCheck ||
          widget.kind == ProductionKind.prepartum ||
          widget.kind == ProductionKind.parturition;
      if (needsCycle && (cycleId == null || cycleId <= 0)) {
        _showMessage('未找到对应的生产周期，请刷新后重试');
        return;
      }

      if (_postponed) {
        final postponeDate = _postponeDate;
        if (postponeDate == null) {
          _showMessage('请选择下次提醒日期');
          return;
        }
        actionResult = await reproRepo.applyAction(
          houseId: widget.houseId,
          cycleId: cycleId!,
          action: ReproAction.postpone,
          occurredAt: _actionDate,
          nextRemindAt: postponeDate,
          requestId: _requestIdFor({
            'postponeDate': formatBatchWriteDate(postponeDate),
          }),
        );
      } else if (widget.kind == ProductionKind.estrus) {
        final nextRemindAt = _ordinaryNextRemindAt;
        final remark = _remarkController.text.trim();
        actionResult = await reproRepo.applyAction(
          houseId: widget.houseId,
          cycleId: cycleId!,
          action: ReproAction.estrus,
          occurredAt: _actionDate,
          nextRemindAt: nextRemindAt,
          remark: remark,
          requestId: _requestIdFor({
            'actionDate': formatBatchWriteDateTime(_actionDate),
            'remark': remark,
            if (nextRemindAt != null)
              'nextRemindAt': formatBatchWriteDate(nextRemindAt),
          }),
        );
      } else if (widget.kind == ProductionKind.mating) {
        final males = _availableBreedingMales(
          rabbits,
          houseId: widget.houseId,
        );
        final maleId = _selectedMaleId;
        if (_matingMethod == MatingMethod.natural &&
            !_containsMaleId(males, maleId)) {
          _showMessage('请选择种公兔');
          return;
        }
        final nextRemindAt = _ordinaryNextRemindAt;
        actionResult = await reproRepo.applyAction(
          houseId: widget.houseId,
          cycleId: cycleId!,
          action: ReproAction.mating,
          occurredAt: _actionDate,
          maleRabbitId: maleId,
          matingMethod: _matingMethod,
          nextRemindAt: nextRemindAt,
          requestId: _requestIdFor({
            'maleRabbitId': maleId,
            'matingMethod': _matingMethod.wire,
            'matingDate': formatBatchWriteDateTime(_actionDate),
            if (nextRemindAt != null)
              'nextRemindAt': formatBatchWriteDate(nextRemindAt),
          }),
        );
      } else if (widget.kind == ProductionKind.pregnancyCheck) {
        final remark = _remarkController.text.trim();
        final result = _palpationResult;
        if (result == PalpationResult.unsure && _recheckDate == null) {
          _showMessage('摸胎结论为不确定时，请选择复查日期');
          return;
        }
        final nextRemindAt = result == PalpationResult.unsure
            ? _recheckDate
            : _ordinaryNextRemindAt;
        actionResult = await reproRepo.applyAction(
          houseId: widget.houseId,
          cycleId: cycleId!,
          action: ReproAction.palpation,
          occurredAt: _actionDate,
          palpationResult: result,
          nextRemindAt: nextRemindAt,
          remark: remark,
          requestId: _requestIdFor({
            'checkDate': formatBatchWriteDateTime(_actionDate),
            'result': result.wire,
            'remark': remark,
            if (nextRemindAt != null)
              'nextRemindAt': formatBatchWriteDate(nextRemindAt),
          }),
        );
      } else if (widget.kind == ProductionKind.prepartum) {
        final remark = _remarkController.text.trim();
        final nextRemindAt = _ordinaryNextRemindAt;
        actionResult = await reproRepo.applyAction(
          houseId: widget.houseId,
          cycleId: cycleId!,
          action: ReproAction.prepartum,
          occurredAt: _actionDate,
          nextRemindAt: nextRemindAt,
          remark: remark,
          requestId: _requestIdFor({
            'actionDate': formatBatchWriteDateTime(_actionDate),
            'remark': remark,
            if (nextRemindAt != null)
              'nextRemindAt': formatBatchWriteDate(nextRemindAt),
          }),
        );
      } else if (widget.kind == ProductionKind.parturition) {
        final total = int.tryParse(_totalKitsController.text.trim()) ?? -1;
        final live = int.tryParse(_liveKitsController.text.trim()) ?? -1;
        final kept = int.tryParse(_keptKitsController.text.trim()) ?? -1;
        if (total < 0 || live < 0 || kept < 0) {
          _showMessage('请输入有效的总产仔数、活仔数和留仔数');
          return;
        }
        if (live > total) {
          _showMessage('活仔数不能大于总产仔数');
          return;
        }
        if (kept > live) {
          _showMessage('留仔数不能大于活仔数');
          return;
        }
        final remark = _remarkController.text.trim();
        if (_parturitionFailed && remark.isEmpty) {
          _showMessage('请填写难产详情');
          return;
        }
        if (_parturitionFailed && _images.isEmpty) {
          _showMessage('请至少上传一张难产相关图片');
          return;
        }
        final attachmentFileIds = <String>[];
        for (final image in _parturitionFailed ? _images : const <XFile>[]) {
          attachmentFileIds.add(
            await reproRepo.uploadImage(
              houseId: widget.houseId,
              filePath: image.path,
              fileName: image.name,
            ),
          );
        }
        final nextRemindAt = _ordinaryNextRemindAt;
        actionResult = await reproRepo.applyAction(
          houseId: widget.houseId,
          cycleId: cycleId!,
          action: ReproAction.delivery,
          outcome: _parturitionFailed ? 'FAILED' : 'BORN',
          occurredAt: _actionDate,
          nextRemindAt: nextRemindAt,
          totalKits: total,
          liveKits: live,
          keptKits: kept,
          remark: remark,
          attachmentFileIds: attachmentFileIds,
          requestId: _requestIdFor({
            'birthDate': formatBatchWriteDateTime(_actionDate),
            'totalKits': total,
            'liveKits': live,
            'keptKits': kept,
            'failed': _parturitionFailed,
            'remark': remark,
            'imageNames': _images.map((image) => image.name).toList(),
            if (nextRemindAt != null)
              'nextRemindAt': formatBatchWriteDate(nextRemindAt),
          }),
        );
      } else if (widget.kind == ProductionKind.sale) {
        final remark = _remarkController.text.trim();
        await repo.submitSale(
          houseId: widget.houseId,
          batchId: batchId!,
          rabbitIds: [widget.rabbitId],
          saleDate: _actionDate,
          remark: remark,
          requestId: _requestIdFor({
            'rabbitIds': [widget.rabbitId],
            'saleDate': formatBatchWriteDateTime(_actionDate),
            'remark': remark,
          }),
        );
      } else if (widget.kind == ProductionKind.replacement) {
        await rabbitRepo.promoteReplacement(
          houseId: widget.houseId,
          rabbitId: widget.rabbitId,
          requestId: _requestIdFor({
            'rabbitId': widget.rabbitId,
            'action': 'promote-breeder',
          }),
        );
      }

      if (!mounted) {
        return;
      }
      ref.invalidate(homeEventsProvider);
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(
        rabbitReproTasksProvider(
          RabbitReproTasksRequest(
            houseId: widget.houseId,
            rabbitId: widget.rabbitId,
          ),
        ),
      );
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(houseBatchesProvider(widget.houseId));
      if (batchId != null && batchId > 0) {
        final detailRequest = BatchDetailRequest(
          houseId: widget.houseId,
          batchId: batchId,
        );
        ref.invalidate(batchDetailProvider(detailRequest));
        ref.invalidate(batchMembersProvider(detailRequest));
      }
      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop(actionResult);
      messenger?.showSnackBar(
        SnackBar(content: Text(_successMessage(actionResult))),
      );
    } catch (error) {
      if (!mounted) {
        return;
      }
      final message = error is ApiException ? error.message : error.toString();
      _showMessage(message);
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  String _successMessage(ReproActionResult? result) {
    final parts = <String>['$_title 已完成'];
    final stage = result?.stage;
    if (stage != null) {
      parts.add('下一阶段：${stage.label}');
    }
    final nextDueTime = result?.nextDueTime;
    if (nextDueTime != null) {
      final reminderTitle =
          stage == null ? '下次提醒' : reminderTitleForStage(stage);
      parts.add(
        '$reminderTitle：${DateFormat('yyyy-MM-dd').format(farmLocalDateTime(nextDueTime))}',
      );
    }
    return parts.join(' · ');
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(houseSettingProvider(widget.houseId));
    final rabbitsAsync =
        ref.watch(allActiveHouseRabbitsProvider(widget.houseId));
    final cagesAsync = ref.watch(houseCagesProvider(widget.houseId));
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    final availableHeight = mediaQuery.size.height - keyboardInset;
    final dateLabel = formatActionTime(_actionDate);

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardInset),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: availableHeight,
          ),
          child: rabbitsAsync.when(
            skipLoadingOnRefresh: false,
            loading: () => SheetLoadingState(
              sheetTitle: _title,
              message: '正在加载兔只信息',
              onClose: () => Navigator.pop(context),
            ),
            error: (error, _) => SheetErrorState(
              sheetTitle: _title,
              error: error,
              fallbackMessage: '无法加载兔只信息，请检查网络后重试。',
              onRetry: () => ref.invalidate(
                allActiveHouseRabbitsProvider(widget.houseId),
              ),
              onClose: () => Navigator.pop(context),
            ),
            data: (rabbits) => cagesAsync.when(
              skipLoadingOnRefresh: false,
              loading: () => SheetLoadingState(
                sheetTitle: _title,
                message: '正在加载笼位信息',
                onClose: () => Navigator.pop(context),
              ),
              error: (error, _) => SheetErrorState(
                sheetTitle: _title,
                error: error,
                fallbackMessage: '无法加载笼位信息，请检查网络后重试。',
                onRetry: () =>
                    ref.invalidate(houseCagesProvider(widget.houseId)),
                onClose: () => Navigator.pop(context),
              ),
              data: (cages) {
                final males = _availableBreedingMales(
                  rabbits,
                  houseId: widget.houseId,
                );
                if (widget.kind == ProductionKind.mating &&
                    !_postponed &&
                    _matingMethod == MatingMethod.natural &&
                    !_containsMaleId(males, _selectedMaleId) &&
                    males.isNotEmpty) {
                  WidgetsBinding.instance.addPostFrameCallback((_) {
                    if (mounted &&
                        !_postponed &&
                        _matingMethod == MatingMethod.natural &&
                        !_containsMaleId(males, _selectedMaleId)) {
                      setState(() => _selectedMaleId = males.first.id);
                    }
                  });
                }

                return Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Flexible(
                      child: ListView(
                        key: const ValueKey('production-event-form-list'),
                        keyboardDismissBehavior:
                            ScrollViewKeyboardDismissBehavior.onDrag,
                        padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
                        children: [
                          Padding(
                            padding: const EdgeInsets.fromLTRB(0, 18, 0, 8),
                            child: Row(
                              children: [
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        _title,
                                        style: Theme.of(context)
                                            .textTheme
                                            .titleLarge,
                                      ),
                                      const SizedBox(height: 4),
                                      ProductionContextLine(
                                        houseLabel: widget.contextLabel,
                                        rabbitId: widget.rabbitId,
                                        batchId: widget.batchId,
                                        cycleRecordId: widget.cycleId,
                                      ),
                                    ],
                                  ),
                                ),
                                IconButton(
                                  onPressed: _saving
                                      ? null
                                      : () => Navigator.pop(context),
                                  icon: const Icon(Icons.close),
                                ),
                              ],
                            ),
                          ),
                          ListTile(
                            contentPadding: EdgeInsets.zero,
                            title: const Text('执行时间 *'),
                            subtitle: Text(dateLabel),
                            trailing: const Icon(Icons.calendar_today_outlined),
                            onTap: _saving ? null : _pickDate,
                          ),
                          if (_canPostpone) ...[
                            SwitchListTile(
                              key: const ValueKey('production-postpone-switch'),
                              contentPadding: EdgeInsets.zero,
                              title: const Text('本次未执行，改期提醒'),
                              subtitle: const Text('不推进当前状态，只调整下一次提醒日期'),
                              value: _postponed,
                              onChanged: _saving
                                  ? null
                                  : (value) => setState(() {
                                        _postponed = value;
                                        if (!value) _postponeDate = null;
                                      }),
                            ),
                            if (_postponed)
                              ListTile(
                                key: const ValueKey('production-postpone-date'),
                                contentPadding: EdgeInsets.zero,
                                title: const Text('下次提醒日期 *'),
                                subtitle: Text(
                                  _postponeDate == null
                                      ? '请选择日期'
                                      : formatBatchWriteDate(_postponeDate!),
                                ),
                                trailing: const Icon(Icons.event),
                                onTap: _saving ? null : _pickPostponeDate,
                              ),
                          ],
                          if (!_postponed &&
                              widget.kind == ProductionKind.mating) ...[
                            const SizedBox(height: 8),
                            Text(
                              '配种方式',
                              style: Theme.of(context).textTheme.titleSmall,
                            ),
                            for (final method in MatingMethod.values)
                              RadioListTile<MatingMethod>(
                                key: ValueKey('mating-method-${method.wire}'),
                                value: method,
                                groupValue: _matingMethod,
                                onChanged: _saving
                                    ? null
                                    : (value) => _selectMatingMethod(
                                          value ?? method,
                                        ),
                                title: Text(method.label),
                              ),
                            const SizedBox(height: 8),
                            Text(
                              _matingMethod == MatingMethod.natural
                                  ? '种公兔 *'
                                  : '种公兔（可选）',
                              style: Theme.of(context).textTheme.titleSmall,
                            ),
                            if (_matingMethod == MatingMethod.ai)
                              RadioListTile<int>(
                                key: const ValueKey('mating-male-none'),
                                value: 0,
                                groupValue: _selectedMaleId ?? 0,
                                onChanged: _saving ? null : _selectMale,
                                title: const Text('不关联具体公兔'),
                                subtitle: const Text('混精或外购精源'),
                              ),
                            if (males.isEmpty)
                              const Padding(
                                padding: EdgeInsets.only(top: 8),
                                child: Text('暂无可用种公兔，请先在笼位录入。'),
                              )
                            else
                              ...males.map(
                                (male) => RadioListTile<int>(
                                  key: ValueKey('mating-male-${male.id}'),
                                  value: male.id,
                                  groupValue: _selectedMaleId,
                                  onChanged: _saving ? null : _selectMale,
                                  title: Text(
                                    '兔 #${male.id} · ${male.breed.isEmpty ? '未填品种' : male.breed}',
                                  ),
                                ),
                              ),
                          ],
                          if (!_postponed &&
                              widget.kind == ProductionKind.pregnancyCheck) ...[
                            const SizedBox(height: 8),
                            Text(
                              '摸胎结果',
                              style: Theme.of(context).textTheme.titleSmall,
                            ),
                            for (final result in PalpationResult.values)
                              RadioListTile<PalpationResult>(
                                key:
                                    ValueKey('pregnancy-result-${result.wire}'),
                                value: result,
                                groupValue: _palpationResult,
                                onChanged: _saving
                                    ? null
                                    : (value) => setState(
                                          () => _palpationResult =
                                              value ?? result,
                                        ),
                                title: Text(result.label),
                              ),
                            if (_palpationResult == PalpationResult.unsure)
                              ListTile(
                                key: const ValueKey('palpation-recheck-date'),
                                contentPadding: EdgeInsets.zero,
                                title: const Text('复查日期'),
                                subtitle: Text(
                                  _recheckDate == null
                                      ? '必填：不选则这只母兔不会再收到提醒'
                                      : formatBatchWriteDate(_recheckDate!),
                                ),
                                trailing: const Icon(Icons.event),
                                onTap: _saving ? null : _pickRecheckDate,
                              ),
                          ],
                          if (!_postponed &&
                              widget.kind == ProductionKind.parturition) ...[
                            const SizedBox(height: 8),
                            TextField(
                              key: const ValueKey('parturition-total-kits'),
                              controller: _totalKitsController,
                              enabled: !_saving && !_parturitionFailed,
                              keyboardType: TextInputType.number,
                              inputFormatters: [
                                FilteringTextInputFormatter.digitsOnly,
                              ],
                              decoration: const InputDecoration(
                                labelText: '总产仔数',
                              ),
                            ),
                            const SizedBox(height: 12),
                            TextField(
                              key: const ValueKey('parturition-live-kits'),
                              controller: _liveKitsController,
                              enabled: !_saving && !_parturitionFailed,
                              keyboardType: TextInputType.number,
                              inputFormatters: [
                                FilteringTextInputFormatter.digitsOnly,
                              ],
                              decoration: const InputDecoration(
                                labelText: '活仔数',
                              ),
                            ),
                            const SizedBox(height: 12),
                            TextField(
                              key: const ValueKey('parturition-kept-kits'),
                              controller: _keptKitsController,
                              enabled: !_saving && !_parturitionFailed,
                              keyboardType: TextInputType.number,
                              inputFormatters: [
                                FilteringTextInputFormatter.digitsOnly,
                              ],
                              decoration: const InputDecoration(
                                labelText: '留仔数',
                                helperText: '实际进入哺乳窝的活仔数',
                              ),
                            ),
                            SwitchListTile(
                              key: const ValueKey('parturition-failed-switch'),
                              contentPadding: EdgeInsets.zero,
                              title: const Text('判定为失败产'),
                              value: _parturitionFailed,
                              onChanged: _saving
                                  ? null
                                  : (value) => setState(() {
                                        _parturitionFailed = value;
                                        if (value) {
                                          _totalKitsController.text = '0';
                                          _liveKitsController.text = '0';
                                          _keptKitsController.text = '0';
                                        }
                                      }),
                            ),
                            if (_parturitionFailed) ...[
                              Text(
                                '难产的总产仔数、活仔数和留仔数均固定为 0。',
                                style: Theme.of(context).textTheme.bodyMedium,
                              ),
                              const SizedBox(height: 12),
                              RequiredImagesField(
                                files: _images,
                                enabled: !_saving,
                                onChanged: (files) =>
                                    setState(() => _images = files),
                              ),
                            ],
                          ],
                          if (!_postponed &&
                              widget.kind == ProductionKind.replacement) ...[
                            const SizedBox(height: 8),
                            const InfoNotice(
                              text: '成熟后备兔会在当前笼位转为种兔；种母兔同时进入待催情流程。',
                            ),
                          ],
                          if (_canCustomizeNextReminder) ...[
                            const SizedBox(height: 12),
                            Text(
                              _nextReminderDateLabel,
                              key: const ValueKey(
                                'next-reminder-stage-label',
                              ),
                              style: Theme.of(context).textTheme.titleSmall,
                            ),
                            const SizedBox(height: 8),
                            SegmentedButton<_NextReminderMode>(
                              segments: const [
                                ButtonSegment(
                                  value: _NextReminderMode.houseSetting,
                                  label: Text(
                                    '按兔场设置',
                                    key: ValueKey(
                                      'next-reminder-house-setting',
                                    ),
                                  ),
                                ),
                                ButtonSegment(
                                  value: _NextReminderMode.custom,
                                  label: Text(
                                    '自定义日期',
                                    key: ValueKey('next-reminder-custom'),
                                  ),
                                ),
                              ],
                              selected: {_nextReminderMode},
                              showSelectedIcon: false,
                              onSelectionChanged: _saving
                                  ? null
                                  : (selection) => setState(() {
                                        _nextReminderMode = selection.first;
                                        if (_nextReminderMode ==
                                            _NextReminderMode.custom) {
                                          _customNextReminderDate ??=
                                              _suggestedNextReminderDate;
                                        }
                                      }),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              _nextReminderMode ==
                                      _NextReminderMode.houseSetting
                                  ? '建议 ${formatBatchWriteDate(_suggestedNextReminderDate)}，由兔场规则计算'
                                  : '覆盖本次推进后生成的$_nextReminderDateLabel',
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                            if (_nextReminderMode == _NextReminderMode.custom)
                              ListTile(
                                key: const ValueKey(
                                  'next-reminder-custom-date',
                                ),
                                contentPadding: EdgeInsets.zero,
                                title: Text(_nextReminderDateLabel),
                                subtitle: Text(
                                  formatBatchWriteDate(
                                    _customNextReminderDate ??
                                        _suggestedNextReminderDate,
                                  ),
                                ),
                                trailing: const Icon(Icons.event_outlined),
                                onTap: _saving
                                    ? null
                                    : _pickCustomNextReminderDate,
                              ),
                          ],
                          if (!_postponed &&
                              widget.kind != ProductionKind.mating &&
                              widget.kind != ProductionKind.replacement) ...[
                            const SizedBox(height: 12),
                            TextField(
                              key: const ValueKey('production-event-remark'),
                              controller: _remarkController,
                              enabled: !_saving,
                              maxLines: 2,
                              decoration: InputDecoration(
                                labelText:
                                    _parturitionFailed ? '难产详情 *' : '备注（可选）',
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                    DecoratedBox(
                      decoration: BoxDecoration(
                        border: Border(
                          top: BorderSide(color: AppPalette.of(context).line),
                        ),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
                        child: Row(
                          children: [
                            Expanded(
                              child: OutlinedButton(
                                onPressed: _saving
                                    ? null
                                    : () => Navigator.pop(context),
                                child: const Text('取消'),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: ElevatedButton(
                                key: const ValueKey('production-event-submit'),
                                onPressed: _saving
                                    ? null
                                    : () => _submit(
                                          rabbits: rabbits,
                                          cages: cages,
                                        ),
                                child: _saving
                                    ? const SizedBox.square(
                                        dimension: 20,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                          color: Colors.white,
                                        ),
                                      )
                                    : const Text('确认'),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        ),
      ),
    );
  }
}
