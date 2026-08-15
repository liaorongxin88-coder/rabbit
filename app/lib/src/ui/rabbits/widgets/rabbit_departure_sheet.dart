import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/batch_providers.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/home_events_provider.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';

enum RabbitDepartureType { cull, death }

Future<void> showRabbitDepartureSheet({
  required BuildContext context,
  required int houseId,
  required int batchId,
  required int rabbitId,
  String? rabbitLabel,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    useSafeArea: true,
    builder: (context) => _RabbitDepartureSheet(
      houseId: houseId,
      batchId: batchId,
      rabbitId: rabbitId,
      rabbitLabel: rabbitLabel ?? '母兔 #$rabbitId',
    ),
  );
}

class _RabbitDepartureSheet extends ConsumerStatefulWidget {
  const _RabbitDepartureSheet({
    required this.houseId,
    required this.batchId,
    required this.rabbitId,
    required this.rabbitLabel,
  });

  final int houseId;
  final int batchId;
  final int rabbitId;
  final String rabbitLabel;

  @override
  ConsumerState<_RabbitDepartureSheet> createState() =>
      _RabbitDepartureSheetState();
}

class _RabbitDepartureSheetState extends ConsumerState<_RabbitDepartureSheet> {
  final _formKey = GlobalKey<FormState>();
  final _reasonController = TextEditingController();
  final _remarkController = TextEditingController();
  final _writeRequest = BatchWriteRequestController();

  late DateTime _actionDate;
  RabbitDepartureType _departureType = RabbitDepartureType.cull;
  var _confirmed = false;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _actionDate = _dateOnly(DateTime.now());
  }

  @override
  void dispose() {
    _reasonController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  String get _eventType =>
      _departureType == RabbitDepartureType.cull ? 'cull' : 'death';

  String get _eventLabel =>
      _departureType == RabbitDepartureType.cull ? '淘汰' : '死亡';

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _actionDate,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 1)),
      helpText: '选择离场日期',
      cancelText: '取消',
      confirmText: '确定',
    );
    if (picked != null && mounted) {
      setState(() => _actionDate = _dateOnly(picked));
    }
  }

  Future<void> _submit() async {
    if (_saving) {
      return;
    }
    final reason = _reasonController.text.trim();
    if (reason.isEmpty) {
      _formKey.currentState?.validate();
      _showMessage('请填写离场原因');
      return;
    }
    if (!(_formKey.currentState?.validate() ?? false)) {
      return;
    }
    if (!_confirmed) {
      _showMessage('请确认已了解离场后不可恢复批次关系');
      return;
    }

    setState(() => _saving = true);
    try {
      final remark = _remarkController.text.trim();
      final requestId = _writeRequest.requestIdFor(
        canonicalBatchWriteFingerprint({
          'action': 'rabbitDeparture',
          'houseId': widget.houseId,
          'batchId': widget.batchId,
          'rabbitId': widget.rabbitId,
          'eventType': _eventType,
          'actionDate': _actionDate.millisecondsSinceEpoch,
          'reason': reason,
          'remark': remark,
          'forceExitBatch': true,
        }),
      );
      await ref.read(rabbitRepositoryProvider).submitRabbitEvent(
            houseId: widget.houseId,
            rabbitId: widget.rabbitId,
            eventType: _eventType,
            actionDate: _actionDate,
            reason: reason,
            remark: remark,
            forceExitBatch: true,
            requestId: requestId,
          );
      if (!mounted) {
        return;
      }

      // Keep all views that cache active rabbits or Batch membership coherent
      // before closing the sheet. The server has already closed the relation.
      ref.invalidate(homeEventsProvider);
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(allActiveHouseRabbitsProvider(widget.houseId));
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(houseBatchesProvider(widget.houseId));
      final detailRequest = BatchDetailRequest(
        houseId: widget.houseId,
        batchId: widget.batchId,
      );
      ref.invalidate(batchDetailProvider(detailRequest));
      ref.invalidate(batchMembersProvider(detailRequest));

      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop();
      messenger?.showSnackBar(
        SnackBar(content: Text('${widget.rabbitLabel}已记录$_eventLabel离场')),
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
    final dateLabel = DateFormat('yyyy-MM-dd').format(_actionDate);
    final palette = AppPalette.of(context);
    final availableHeight =
        mediaQuery.size.height - mediaQuery.viewInsets.bottom;

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: mediaQuery.viewInsets.bottom),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: availableHeight * .92),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 18, 12, 10),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '母兔离场',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.titleLarge,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '${widget.rabbitLabel} · Batch #${widget.batchId}',
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      tooltip: '关闭',
                      onPressed:
                          _saving ? null : () => Navigator.of(context).pop(),
                      icon: const Icon(Icons.close),
                    ),
                  ],
                ),
              ),
              Flexible(
                child: Form(
                  key: _formKey,
                  child: ListView(
                    keyboardDismissBehavior:
                        ScrollViewKeyboardDismissBehavior.onDrag,
                    padding: const EdgeInsets.fromLTRB(20, 0, 20, 84),
                    children: [
                      Text(
                        '离场类型',
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                      const SizedBox(height: 4),
                      RadioListTile<RabbitDepartureType>(
                        key: const ValueKey('rabbit-departure-event-cull'),
                        contentPadding: EdgeInsets.zero,
                        value: RabbitDepartureType.cull,
                        groupValue: _departureType,
                        onChanged: _saving
                            ? null
                            : (value) {
                                if (value != null) {
                                  setState(() => _departureType = value);
                                }
                              },
                        title: const Text('淘汰'),
                        subtitle: const Text('不再进入后续繁殖安排'),
                      ),
                      RadioListTile<RabbitDepartureType>(
                        key: const ValueKey('rabbit-departure-event-death'),
                        contentPadding: EdgeInsets.zero,
                        value: RabbitDepartureType.death,
                        groupValue: _departureType,
                        onChanged: _saving
                            ? null
                            : (value) {
                                if (value != null) {
                                  setState(() => _departureType = value);
                                }
                              },
                        title: const Text('死亡'),
                        subtitle: const Text('记录死亡并退出在栏状态'),
                      ),
                      const SizedBox(height: 4),
                      ListTile(
                        key: const ValueKey('rabbit-departure-date'),
                        contentPadding: EdgeInsets.zero,
                        title: const Text('离场日期'),
                        subtitle: Text(dateLabel),
                        trailing: const Icon(Icons.calendar_today_outlined),
                        onTap: _saving ? null : _pickDate,
                      ),
                      const SizedBox(height: 8),
                      TextFormField(
                        key: const ValueKey('rabbit-departure-reason'),
                        controller: _reasonController,
                        enabled: !_saving,
                        maxLength: 120,
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: '离场原因 *',
                          hintText: '请填写可追溯的业务原因',
                        ),
                        validator: (value) {
                          if (value == null || value.trim().isEmpty) {
                            return '请填写离场原因';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 8),
                      TextFormField(
                        key: const ValueKey('rabbit-departure-remark'),
                        controller: _remarkController,
                        enabled: !_saving,
                        maxLines: 3,
                        maxLength: 240,
                        decoration: const InputDecoration(
                          labelText: '备注（可选）',
                          hintText: '可记录检查结果、处理人或其他说明',
                        ),
                      ),
                      const SizedBox(height: 8),
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: palette.dangerSoft,
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(
                            color: palette.danger.withAlpha(90),
                          ),
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(Icons.warning_amber_rounded,
                                color: palette.danger),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Text(
                                '提交后将立即退出该母兔的所有活跃 Batch 关系（包括当前 Batch），并关闭未完成的繁殖周期；离场记录不可撤销。',
                                style: TextStyle(color: palette.text),
                              ),
                            ),
                          ],
                        ),
                      ),
                      CheckboxListTile(
                        key: const ValueKey('rabbit-departure-confirm-risk'),
                        contentPadding: EdgeInsets.zero,
                        value: _confirmed,
                        onChanged: _saving
                            ? null
                            : (value) =>
                                setState(() => _confirmed = value == true),
                        controlAffinity: ListTileControlAffinity.leading,
                        title: const Text('我确认离场信息无误，并了解该操作不可撤销'),
                      ),
                    ],
                  ),
                ),
              ),
              DecoratedBox(
                decoration: BoxDecoration(
                  border: Border(top: BorderSide(color: palette.line)),
                ),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      OutlinedButton(
                        onPressed:
                            _saving ? null : () => Navigator.of(context).pop(),
                        child: const Text('取消'),
                      ),
                      const SizedBox(height: 8),
                      FilledButton.icon(
                        key: const ValueKey('rabbit-departure-submit'),
                        onPressed: _saving || !_confirmed ? null : _submit,
                        icon: _saving
                            ? const SizedBox.square(
                                dimension: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Icon(Icons.exit_to_app_outlined),
                        label: Text('确认$_eventLabel离场'),
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

DateTime _dateOnly(DateTime value) =>
    DateTime(value.year, value.month, value.day);

String _errorMessage(Object error) {
  if (error is ApiException) {
    return error.message;
  }
  return '提交失败，请检查网络后重试';
}
