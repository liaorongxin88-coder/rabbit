import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/repositories/reproduction/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/reproduction/widgets/action_time.dart';
import 'package:rabbit_flutter/src/ui/reproduction/widgets/context.dart';
import 'package:rabbit_flutter/src/ui/reproduction/sheets/event.dart';

class _KeptKitsFormError extends StatelessWidget {
  const _KeptKitsFormError({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: colors.errorContainer,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Icon(Icons.error_outline, color: colors.error),
          const SizedBox(width: 8),
          Expanded(child: Text(message)),
        ],
      ),
    );
  }
}

Future<bool> showKeptKitsAdjustmentSheet({
  required BuildContext context,
  required int houseId,
  required int cycleId,
  required int motherRabbitId,
}) async {
  final result = await showAppModalSheet<bool>(
    context: context,
    builder: (context) => _KeptKitsAdjustmentSheet(
      houseId: houseId,
      cycleId: cycleId,
      motherRabbitId: motherRabbitId,
    ),
  );
  return result == true;
}

class _KeptKitsAdjustmentSheet extends ConsumerStatefulWidget {
  const _KeptKitsAdjustmentSheet({
    required this.houseId,
    required this.cycleId,
    required this.motherRabbitId,
  });

  final int houseId;
  final int cycleId;
  final int motherRabbitId;

  @override
  ConsumerState<_KeptKitsAdjustmentSheet> createState() =>
      _KeptKitsAdjustmentSheetState();
}

class _KeptKitsAdjustmentSheetState
    extends ConsumerState<_KeptKitsAdjustmentSheet> {
  final _formKey = GlobalKey<FormState>();
  final _keptController = TextEditingController();
  final _remarkController = TextEditingController();
  final _writeRequest = BatchWriteRequestController();

  ReproLitter? _litter;
  Object? _loadError;
  int? _sourceMotherId;
  String? _formError;
  late DateTime _occurredAt;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _occurredAt = DateTime.now();
    _load();
  }

  @override
  void dispose() {
    _keptController.dispose();
    _remarkController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final litter = await ref.read(reproRepositoryProvider).getCycleLitter(
            houseId: widget.houseId,
            cycleId: widget.cycleId,
          );
      if (!mounted) return;
      setState(() {
        _litter = litter;
        _loadError = null;
        _keptController.text = '${litter.keptKits}';
      });
    } catch (error) {
      if (mounted) setState(() => _loadError = error);
    }
  }

  Future<void> _pickTime() async {
    final picked = await pickActionTime(
      context: context,
      current: _occurredAt,
      helpText: '选择调整日期',
    );
    if (picked != null && mounted) {
      setState(() => _occurredAt = picked);
    }
  }

  Future<void> _submit(List<Rabbit> sourceMothers) async {
    if (_saving || !(_formKey.currentState?.validate() ?? false)) return;
    final litter = _litter;
    if (litter == null) return;
    final keptKits = int.parse(_keptController.text.trim());
    if (keptKits > litter.keptKits &&
        !sourceMothers.any((rabbit) => rabbit.id == _sourceMotherId)) {
      _showMessage('留崽数增加时请选择留崽来源母兔');
      return;
    }
    final remark = _remarkController.text.trim();
    final sourceMotherId = keptKits > litter.keptKits ? _sourceMotherId : null;
    setState(() {
      _saving = true;
      _formError = null;
    });
    try {
      await ref.read(reproRepositoryProvider).adjustKeptKits(
            houseId: widget.houseId,
            cycleId: widget.cycleId,
            occurredAt: _occurredAt,
            keptKits: keptKits,
            sourceMotherRabbitId: sourceMotherId,
            remark: remark,
            requestId: _writeRequest.requestIdFor(
              canonicalBatchWriteFingerprint({
                'action': 'adjustKeptKits',
                'houseId': widget.houseId,
                'cycleId': widget.cycleId,
                'occurredAt': formatBatchWriteDateTime(_occurredAt),
                'keptKits': keptKits,
                'sourceMotherRabbitId': sourceMotherId,
                'remark': remark,
              }),
            ),
          );
      if (!mounted) return;
      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.pop(context, true);
      messenger?.showSnackBar(
        SnackBar(content: Text('留崽数已调整为 $keptKits')),
      );
    } catch (error) {
      if (mounted) {
        _showMessage(error is ApiException ? error.message : error.toString());
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _showMessage(String message) {
    if (!mounted) {
      return;
    }
    setState(() => _formError = message);
  }

  @override
  Widget build(BuildContext context) {
    final litter = _litter;
    if (_loadError != null) {
      return SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('无法读取当前留崽数'),
              const SizedBox(height: 12),
              FilledButton.icon(
                onPressed: _load,
                icon: const Icon(Icons.refresh),
                label: const Text('重试'),
              ),
            ],
          ),
        ),
      );
    }
    if (litter == null) {
      return const SafeArea(
        top: false,
        child: Padding(
          padding: EdgeInsets.all(28),
          child: Center(child: CircularProgressIndicator()),
        ),
      );
    }

    final rabbits = ref.watch(allActiveHouseRabbitsProvider(widget.houseId));
    return rabbits.when(
      loading: () => const SafeArea(
        top: false,
        child: Padding(
          padding: EdgeInsets.all(28),
          child: Center(child: CircularProgressIndicator()),
        ),
      ),
      error: (error, _) => SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Text('无法加载来源母兔：$error'),
        ),
      ),
      data: (items) {
        final sourceMothers = items
            .where(
              (rabbit) =>
                  rabbit.id != widget.motherRabbitId &&
                  rabbit.isActive &&
                  rabbit.type == '0' &&
                  rabbit.gender == '0',
            )
            .toList()
          ..sort((left, right) => left.id.compareTo(right.id));
        final entered = int.tryParse(_keptController.text.trim());
        final increasing = entered != null && entered > litter.keptKits;
        return SafeArea(
          top: false,
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('调整留崽数', style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 4),
                  ProductionContextLine(
                    houseLabel: '当前留崽 ${litter.keptKits} 只',
                    rabbitId: widget.motherRabbitId,
                    batchId: litter.batchId,
                    cycleRecordId: widget.cycleId,
                  ),
                  const SizedBox(height: 12),
                  if (_formError != null) ...[
                    _KeptKitsFormError(message: _formError!),
                    const SizedBox(height: 12),
                  ],
                  ListTile(
                    key: const ValueKey('kept-kits-occurred-at'),
                    contentPadding: EdgeInsets.zero,
                    title: const Text('执行时间 *'),
                    subtitle: Text(formatActionTime(_occurredAt)),
                    trailing: const Icon(Icons.schedule),
                    onTap: _saving ? null : _pickTime,
                  ),
                  TextFormField(
                    key: const ValueKey('kept-kits-count'),
                    controller: _keptController,
                    enabled: !_saving,
                    keyboardType: TextInputType.number,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    decoration: const InputDecoration(labelText: '调整后留崽数量 *'),
                    onChanged: (_) => setState(() {
                      if ((int.tryParse(_keptController.text) ?? 0) <=
                          litter.keptKits) {
                        _sourceMotherId = null;
                      }
                    }),
                    validator: (value) {
                      final parsed = int.tryParse(value?.trim() ?? '');
                      return parsed == null || parsed < 0 ? '请输入非负整数' : null;
                    },
                  ),
                  if (increasing) ...[
                    const SizedBox(height: 12),
                    Text('留崽来源母兔 *',
                        style: Theme.of(context).textTheme.titleSmall),
                    if (sourceMothers.isEmpty)
                      const Text('当前兔舍没有其他可选种母兔')
                    else ...[
                      NfcRabbitPickerButton(
                        key: const ValueKey('kept-kits-source-nfc'),
                        houseId: widget.houseId,
                        candidates: sourceMothers,
                        idleLabel: '碰一下选择来源母兔',
                        waitingLabel: '请靠近来源母兔所在笼位的 NFC 标签',
                        enabled: !_saving,
                        onSelected: (matches) =>
                            setState(() => _sourceMotherId = matches.single.id),
                      ),
                      const SizedBox(height: 8),
                      ...sourceMothers.map(
                        (rabbit) => RadioListTile<int>(
                          key: ValueKey('kept-kits-source-${rabbit.id}'),
                          value: rabbit.id,
                          groupValue: _sourceMotherId,
                          onChanged: _saving
                              ? null
                              : (value) =>
                                  setState(() => _sourceMotherId = value),
                          title: Text(
                            '母兔 #${rabbit.id} · ${rabbit.breed.isEmpty ? '未填品种' : rabbit.breed}',
                          ),
                        ),
                      ),
                    ],
                  ],
                  const SizedBox(height: 12),
                  TextFormField(
                    key: const ValueKey('kept-kits-remark'),
                    controller: _remarkController,
                    enabled: !_saving,
                    maxLines: 2,
                    decoration: const InputDecoration(labelText: '备注（可选）'),
                  ),
                  const SizedBox(height: 16),
                  FilledButton(
                    key: const ValueKey('kept-kits-submit'),
                    onPressed: _saving ? null : () => _submit(sourceMothers),
                    child: _saving
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text('提交调整'),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}
