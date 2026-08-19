import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/repro_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/repro_task.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/batch_providers.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/home_events_provider.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

/// 记录流产。
///
/// 与六个流程动作不同，流产是<b>非计划事件</b>：它不对应任何待办，不能从今日清单
/// 进入，只能对着一头具体的母兔记录。所以它和「登记离场」一样单列入口，
/// 而不是塞进推进流程的表单里。
///
/// 入口的显隐由服务端的阶段字典决定（见 `ReproRepository.stageActions`）：
/// 流产只在待摸胎/待备产/待分娩三个孕期阶段成立——还没配上或已经生完时
/// 出现「流产」按钮都是荒谬的，点下去也必定被转换表拒绝。
Future<bool> showAbortionSheet({
  required BuildContext context,
  required int houseId,
  required int cycleId,
  required int rabbitId,
  int? batchId,
  String? rabbitLabel,
  String? stageLabel,
}) async {
  final recorded = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    useSafeArea: true,
    builder: (context) => _AbortionSheet(
      houseId: houseId,
      cycleId: cycleId,
      rabbitId: rabbitId,
      batchId: batchId,
      rabbitLabel: rabbitLabel ?? '母兔 #$rabbitId',
      stageLabel: stageLabel,
    ),
  );
  return recorded ?? false;
}

class _AbortionSheet extends ConsumerStatefulWidget {
  const _AbortionSheet({
    required this.houseId,
    required this.cycleId,
    required this.rabbitId,
    required this.batchId,
    required this.rabbitLabel,
    required this.stageLabel,
  });

  final int houseId;
  final int cycleId;
  final int rabbitId;
  final int? batchId;
  final String rabbitLabel;
  final String? stageLabel;

  @override
  ConsumerState<_AbortionSheet> createState() => _AbortionSheetState();
}

class _AbortionSheetState extends ConsumerState<_AbortionSheet> {
  final _formKey = GlobalKey<FormState>();
  final _stillbirthController = TextEditingController();
  final _remarkController = TextEditingController();
  final _writeRequest = BatchWriteRequestController();

  late DateTime _occurredAt;
  var _confirmed = false;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _occurredAt = _dateOnly(DateTime.now());
  }

  @override
  void dispose() {
    _stillbirthController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _occurredAt,
      firstDate: DateTime(2020),
      // 允许补录过去，但不能记到明天：流产是已经发生的事。
      lastDate: DateTime.now().add(const Duration(days: 1)),
      helpText: '选择流产日期',
      cancelText: '取消',
      confirmText: '确定',
    );
    if (picked != null && mounted) {
      setState(() => _occurredAt = _dateOnly(picked));
    }
  }

  Future<void> _submit() async {
    if (_saving) {
      return;
    }
    if (!(_formKey.currentState?.validate() ?? false)) {
      return;
    }
    if (!_confirmed) {
      _showMessage('请确认本轮妊娠已终止');
      return;
    }

    final stillbirth = int.tryParse(_stillbirthController.text.trim());
    final remark = _remarkController.text.trim();
    setState(() => _saving = true);
    try {
      final requestId = _writeRequest.requestIdFor(
        canonicalBatchWriteFingerprint({
          'action': 'abortion',
          'houseId': widget.houseId,
          'cycleId': widget.cycleId,
          'occurredAt': _occurredAt.millisecondsSinceEpoch,
          'stillbirthCount': stillbirth,
          'remark': remark,
        }),
      );
      await ref.read(reproRepositoryProvider).applyAction(
            houseId: widget.houseId,
            cycleId: widget.cycleId,
            action: ReproAction.abortion,
            occurredAt: _occurredAt,
            stillbirthCount: stillbirth,
            remark: remark,
            requestId: requestId,
          );
      if (!mounted) {
        return;
      }

      // 流产会关掉本轮并接续新一轮，母兔阶段、待办、批次视图全都变了。
      ref.invalidate(homeEventsProvider);
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(houseBatchesProvider(widget.houseId));
      if (widget.batchId != null) {
        final detailRequest = BatchDetailRequest(
          houseId: widget.houseId,
          batchId: widget.batchId!,
        );
        ref.invalidate(batchDetailProvider(detailRequest));
        ref.invalidate(batchMembersProvider(detailRequest));
      }

      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop(true);
      messenger?.showSnackBar(
        SnackBar(content: Text('${widget.rabbitLabel}已记录流产，将重新进入待催情')),
      );
    } catch (error) {
      if (mounted) {
        _showMessage(_errorMessage(error));
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final mediaQuery = MediaQuery.of(context);
    final palette = AppPalette.of(context);
    final dateLabel = DateFormat('yyyy-MM-dd').format(_occurredAt);

    return AnimatedPadding(
      duration: const Duration(milliseconds: 120),
      padding: EdgeInsets.only(bottom: mediaQuery.viewInsets.bottom),
      child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Icon(Icons.report_problem_outlined, color: palette.danger),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        '记录流产',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  widget.stageLabel == null
                      ? widget.rabbitLabel
                      : '${widget.rabbitLabel} · 当前${widget.stageLabel}',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 16),
                ListTile(
                  key: const ValueKey('abortion-date'),
                  contentPadding: EdgeInsets.zero,
                  title: const Text('流产日期'),
                  subtitle: Text(dateLabel),
                  trailing: const Icon(Icons.calendar_today_outlined),
                  onTap: _saving ? null : _pickDate,
                ),
                TextFormField(
                  key: const ValueKey('abortion-stillbirth-count'),
                  controller: _stillbirthController,
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  enabled: !_saving,
                  decoration: const InputDecoration(
                    labelText: '死胎数 *',
                    hintText: '请填写本轮流产确认的死胎数量',
                  ),
                  validator: (value) {
                    final text = value?.trim() ?? '';
                    if (text.isEmpty) {
                      return '请填写流产死胎数';
                    }
                    final parsed = int.tryParse(text);
                    if (parsed == null || parsed < 0) {
                      return '死胎数需为非负整数';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  key: const ValueKey('abortion-remark'),
                  controller: _remarkController,
                  enabled: !_saving,
                  maxLines: 2,
                  decoration: const InputDecoration(
                    labelText: '备注',
                    hintText: '可记录原因、处理方式等',
                  ),
                ),
                const SizedBox(height: 8),
                CheckboxListTile(
                  key: const ValueKey('abortion-confirm'),
                  contentPadding: EdgeInsets.zero,
                  value: _confirmed,
                  onChanged: _saving
                      ? null
                      : (value) => setState(() => _confirmed = value ?? false),
                  title: const Text('确认本轮妊娠已终止'),
                  subtitle: const Text('本轮周期将结束，母兔复旧后重新进入待催情'),
                ),
                const SizedBox(height: 8),
                FilledButton(
                  key: const ValueKey('abortion-submit'),
                  onPressed: _saving ? null : _submit,
                  child: _saving
                      ? const SizedBox(
                          height: 18,
                          width: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('提交'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

DateTime _dateOnly(DateTime value) =>
    DateTime(value.year, value.month, value.day);

String _errorMessage(Object error) {
  if (error is ApiException) {
    return error.message;
  }
  return '记录流产失败，请稍后重试';
}
