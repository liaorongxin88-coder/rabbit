import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart'
    show formatBatchWriteDate, formatBatchWriteDateTime;
import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/reproduction/event.dart';
import 'package:rabbit_flutter/src/domain/settings/production.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/notice.dart';
import 'package:rabbit_flutter/src/ui/reproduction/widgets/context.dart';
import 'package:rabbit_flutter/src/ui/reproduction/widgets/action_time.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/providers.dart';

Future<ReproActionResult?> showWeaningSheet({
  required BuildContext context,
  required EventItem event,
}) {
  final houseId = event.sourceHouseId;
  final rabbitId = event.rabbitId;
  final cycleId = event.recordId;
  if (houseId == null ||
      houseId <= 0 ||
      rabbitId == null ||
      rabbitId <= 0 ||
      cycleId <= 0) {
    return Future<ReproActionResult?>.value();
  }

  return showAppModalSheet<ReproActionResult>(
    context: context,
    builder: (context) => _WeaningSheet(
      houseId: houseId,
      batchId: event.batchId,
      rabbitId: rabbitId,
      breedingCycleId: cycleId,
      houseLabel: event.houseLabel,
    ),
  );
}

class _WeaningSheet extends ConsumerStatefulWidget {
  const _WeaningSheet({
    required this.houseId,
    required this.batchId,
    required this.rabbitId,
    required this.breedingCycleId,
    required this.houseLabel,
  });

  final int houseId;
  final int? batchId;
  final int rabbitId;
  final int breedingCycleId;
  final String houseLabel;

  @override
  ConsumerState<_WeaningSheet> createState() => _WeaningSheetState();
}

enum _NextReminderMode { houseSetting, custom }

class _WeaningSheetState extends ConsumerState<_WeaningSheet> {
  final _writeRequest = BatchWriteRequestController();
  final _countController = TextEditingController(text: '8');
  final _maleController = TextEditingController();
  final _femaleController = TextEditingController();
  final _weightController = TextEditingController();
  final _remarkController = TextEditingController();

  late DateTime _weaningDate;
  DateTime? _postponeDate;
  DateTime? _customNextReminderDate;
  var _nextReminderMode = _NextReminderMode.houseSetting;
  var _postponed = false;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _weaningDate = DateTime.now();
  }

  @override
  void dispose() {
    _countController.dispose();
    _maleController.dispose();
    _femaleController.dispose();
    _weightController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  int? _parseOptionalInt(TextEditingController controller) {
    final text = controller.text.trim();
    if (text.isEmpty) {
      return null;
    }
    return int.tryParse(text);
  }

  double? _parseOptionalDouble(TextEditingController controller) {
    final text = controller.text.trim();
    if (text.isEmpty) {
      return null;
    }
    return double.tryParse(text);
  }

  Future<void> _pickDate() async {
    final picked = await pickActionTime(
      context: context,
      current: _weaningDate,
      helpText: '选择分笼日期',
    );
    if (picked != null && mounted) {
      setState(() => _weaningDate = picked);
    }
  }

  Future<void> _pickPostponeDate() async {
    final today = farmToday();
    final setting =
        ref.read(houseSettingProvider(widget.houseId)).valueOrNull?.setting ??
            GlobalSetting.defaults();
    final suggested = suggestedReminderDate(
      stage: ReproStage.awaitWeaning,
      setting: setting,
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

  GlobalSetting get _reminderSetting =>
      ref.read(houseSettingProvider(widget.houseId)).valueOrNull?.setting ??
      GlobalSetting.defaults();

  String get _nextReminderDateLabel =>
      reminderDateLabelForStage(ReproStage.awaitEstrus);

  DateTime get _suggestedNextReminderDate {
    final today = farmToday();
    return reminderInitialDate(
      suggested: suggestedReminderDate(
        stage: ReproStage.awaitEstrus,
        setting: _reminderSetting,
        from: _weaningDate,
      ),
      now: today,
      latest: today.add(const Duration(days: 3650)),
    );
  }

  DateTime? get _ordinaryNextRemindAt =>
      _nextReminderMode == _NextReminderMode.houseSetting
          ? null
          : _customNextReminderDate ?? _suggestedNextReminderDate;

  Future<void> _pickCustomNextReminderDate() async {
    final today = farmToday();
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

  void _invalidateBatchProviders() {
    final batchId = widget.batchId;
    if (batchId == null || batchId <= 0) {
      return;
    }
    ref.invalidate(houseBatchesProvider(widget.houseId));
    final detailRequest = BatchDetailRequest(
      houseId: widget.houseId,
      batchId: batchId,
    );
    ref.invalidate(batchDetailProvider(detailRequest));
    ref.invalidate(batchMembersProvider(detailRequest));
    ref.invalidate(pendingWeaningRecordsProvider(detailRequest));
  }

  Future<void> _submit() async {
    if (_postponed) {
      final cycleId = widget.breedingCycleId;
      final postponeDate = _postponeDate;
      if (cycleId <= 0) {
        _showMessage('未找到对应的生产周期，请刷新后重试');
        return;
      }
      if (postponeDate == null) {
        _showMessage('请选择下次提醒日期');
        return;
      }
      setState(() => _saving = true);
      try {
        final requestId = _writeRequest.requestIdFor(
          canonicalBatchWriteFingerprint({
            'action': 'weaningPostpone',
            'houseId': widget.houseId,
            'batchId': widget.batchId,
            'rabbitId': widget.rabbitId,
            'breedingCycleId': cycleId,
            'postponeDate': formatBatchWriteDate(postponeDate),
          }),
        );
        final result = await ref.read(reproRepositoryProvider).applyAction(
              houseId: widget.houseId,
              cycleId: cycleId,
              action: ReproAction.postpone,
              occurredAt: _weaningDate,
              nextRemindAt: postponeDate,
              requestId: requestId,
            );
        if (!mounted) return;
        ref.invalidate(homeEventsProvider);
        ref.invalidate(houseRabbitsProvider(widget.houseId));
        _invalidateBatchProviders();
        final messenger = ScaffoldMessenger.maybeOf(context);
        Navigator.of(context).pop(result);
        messenger?.showSnackBar(
          const SnackBar(content: Text('已推迟断奶提醒')),
        );
      } catch (error) {
        if (mounted) {
          _showMessage(
              error is ApiException ? error.message : error.toString());
        }
      } finally {
        if (mounted) setState(() => _saving = false);
      }
      return;
    }
    final count = int.tryParse(_countController.text.trim()) ?? -1;
    if (count < 0) {
      _showMessage('请输入有效的断奶数量');
      return;
    }

    final male = _parseOptionalInt(_maleController);
    final female = _parseOptionalInt(_femaleController);
    if (male != null && male < 0) {
      _showMessage('公兔数量不能小于 0');
      return;
    }
    if (female != null && female < 0) {
      _showMessage('母兔数量不能小于 0');
      return;
    }
    if (male != null && female != null && male + female != count) {
      _showMessage('公母数量之和需等于断奶数量');
      return;
    }

    setState(() => _saving = true);
    try {
      final avgWeight = _parseOptionalDouble(_weightController);
      final remark = _remarkController.text.trim();
      final nextRemindAt = _ordinaryNextRemindAt;
      final requestId = _writeRequest.requestIdFor(
        canonicalBatchWriteFingerprint({
          'action': 'weaning',
          'houseId': widget.houseId,
          'batchId': widget.batchId,
          'rabbitId': widget.rabbitId,
          'breedingCycleId': widget.breedingCycleId,
          'weaningDate': formatBatchWriteDateTime(_weaningDate),
          'weaningCount': count,
          'maleCount': male,
          'femaleCount': female,
          'avgWeight': avgWeight,
          'remark': remark,
          if (nextRemindAt != null)
            'nextRemindAt': formatBatchWriteDate(nextRemindAt),
        }),
      );
      // 断奶只推进周期并保留待分笼记录；选定目标笼位后才会创建商品兔。
      final cycleId = widget.breedingCycleId;
      if (cycleId <= 0) {
        _showMessage('未找到对应的生产周期，请刷新后重试');
        return;
      }
      final result = await ref.read(reproRepositoryProvider).applyAction(
            houseId: widget.houseId,
            cycleId: cycleId,
            action: ReproAction.weaning,
            occurredAt: _weaningDate,
            nextRemindAt: nextRemindAt,
            weanedCount: count,
            maleCount: male,
            femaleCount: female,
            avgWeaningWeight: avgWeight,
            remark: remark,
            requestId: requestId,
          );
      if (!mounted) {
        return;
      }

      ref.invalidate(homeEventsProvider);
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      _invalidateBatchProviders();
      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop(result);
      messenger?.showSnackBar(
        SnackBar(
          content: Text(
            '母兔 #${widget.rabbitId} 断奶完成（$count 只待分笼），请前往笼位详情选择场内生产。',
          ),
        ),
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

  void _showMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    ref.watch(houseSettingProvider(widget.houseId));
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    final availableHeight = mediaQuery.size.height - keyboardInset;
    final dateLabel = formatActionTime(_weaningDate);

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
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Flexible(
                child: ListView(
                  key: const ValueKey('weaning-form-list'),
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
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  '记录断奶',
                                  style: Theme.of(context).textTheme.titleLarge,
                                ),
                                const SizedBox(height: 4),
                                ProductionContextLine(
                                  houseLabel: widget.houseLabel,
                                  rabbitId: widget.rabbitId,
                                  batchId: widget.batchId,
                                  cycleRecordId: widget.breedingCycleId,
                                ),
                              ],
                            ),
                          ),
                          IconButton(
                            onPressed:
                                _saving ? null : () => Navigator.pop(context),
                            icon: const Icon(Icons.close),
                          ),
                        ],
                      ),
                    ),
                    const InfoNotice(
                      text: '断奶仅记录待分笼数量。请在批次详情选择商品兔笼位后完成分笼；数量填 0 表示全部损失。',
                    ),
                    const SizedBox(height: 14),
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('执行时间 *'),
                      subtitle: Text(dateLabel),
                      trailing: const Icon(Icons.calendar_today_outlined),
                      onTap: _saving ? null : _pickDate,
                    ),
                    SwitchListTile(
                      key: const ValueKey('weaning-postpone-switch'),
                      contentPadding: EdgeInsets.zero,
                      title: const Text('本次未执行，改期提醒'),
                      subtitle: const Text('不推进断奶状态，只调整下一次提醒日期'),
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
                        key: const ValueKey('weaning-postpone-date'),
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
                    if (!_postponed) ...[
                      const SizedBox(height: 12),
                      Text(
                        _nextReminderDateLabel,
                        key: const ValueKey(
                          'weaning-next-reminder-stage-label',
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
                                'weaning-next-reminder-house-setting',
                              ),
                            ),
                          ),
                          ButtonSegment(
                            value: _NextReminderMode.custom,
                            label: Text(
                              '自定义日期',
                              key: ValueKey(
                                'weaning-next-reminder-custom',
                              ),
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
                        _nextReminderMode == _NextReminderMode.houseSetting
                            ? '建议 ${formatBatchWriteDate(_suggestedNextReminderDate)}，由兔场规则计算'
                            : '覆盖断奶后生成的$_nextReminderDateLabel',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      if (_nextReminderMode == _NextReminderMode.custom)
                        ListTile(
                          key: const ValueKey(
                            'weaning-next-reminder-custom-date',
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
                          onTap: _saving ? null : _pickCustomNextReminderDate,
                        ),
                      const SizedBox(height: 8),
                    ],
                    TextField(
                      key: const ValueKey('weaning-count'),
                      controller: _countController,
                      enabled: !_saving,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      onChanged: (_) => setState(() {}),
                      decoration: const InputDecoration(
                        labelText: '断奶数量',
                        hintText: '本次断奶的仔兔数量',
                      ),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            key: const ValueKey('weaning-male-count'),
                            controller: _maleController,
                            enabled: !_saving,
                            keyboardType: TextInputType.number,
                            inputFormatters: [
                              FilteringTextInputFormatter.digitsOnly,
                            ],
                            decoration: const InputDecoration(
                              labelText: '公兔数（可选）',
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: TextField(
                            key: const ValueKey('weaning-female-count'),
                            controller: _femaleController,
                            enabled: !_saving,
                            keyboardType: TextInputType.number,
                            inputFormatters: [
                              FilteringTextInputFormatter.digitsOnly,
                            ],
                            decoration: const InputDecoration(
                              labelText: '母兔数（可选）',
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      key: const ValueKey('weaning-average-weight'),
                      controller: _weightController,
                      enabled: !_saving,
                      keyboardType: const TextInputType.numberWithOptions(
                        decimal: true,
                      ),
                      decoration: const InputDecoration(
                        labelText: '平均体重 kg（可选）',
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      key: const ValueKey('weaning-remark'),
                      controller: _remarkController,
                      enabled: !_saving,
                      maxLines: 2,
                      decoration: const InputDecoration(
                        labelText: '备注（可选）',
                      ),
                    ),
                    const SizedBox(height: 16),
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
                          onPressed:
                              _saving ? null : () => Navigator.pop(context),
                          child: const Text('取消'),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ElevatedButton(
                          key: const ValueKey('weaning-submit'),
                          onPressed: _saving ? null : _submit,
                          child: _saving
                              ? const SizedBox.square(
                                  dimension: 20,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : const Text('确认断奶'),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
