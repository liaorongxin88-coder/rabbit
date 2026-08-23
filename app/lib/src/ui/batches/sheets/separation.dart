import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/batches/weaning.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';

Future<bool?> showBatchWeaningSeparationSheet({
  required BuildContext context,
  required int houseId,
  required int batchId,
  required PendingWeaningRecord record,
}) {
  return showAppModalSheet<bool>(
    context: context,
    builder: (_) => _BatchWeaningSeparationSheet(
      houseId: houseId,
      batchId: batchId,
      record: record,
    ),
  );
}

class _BatchWeaningSeparationSheet extends ConsumerStatefulWidget {
  const _BatchWeaningSeparationSheet({
    required this.houseId,
    required this.batchId,
    required this.record,
  });

  final int houseId;
  final int batchId;
  final PendingWeaningRecord record;

  @override
  ConsumerState<_BatchWeaningSeparationSheet> createState() =>
      _BatchWeaningSeparationSheetState();
}

class _BatchWeaningSeparationSheetState
    extends ConsumerState<_BatchWeaningSeparationSheet> {
  final _countController = TextEditingController();
  final _request = BatchWriteRequestController();
  int? _cageId;
  var _saving = false;

  @override
  void initState() {
    super.initState();
    _countController.text = '${widget.record.waitingCount}';
  }

  @override
  void dispose() {
    _countController.dispose();
    super.dispose();
  }

  Future<void> _submit(List<Cage> cages) async {
    final count = int.tryParse(_countController.text.trim()) ?? 0;
    final cageId = _cageId;
    if (cageId == null || cageId <= 0) {
      _showMessage('请选择商品兔笼位');
      return;
    }
    if (count <= 0 || count > widget.record.waitingCount) {
      _showMessage('分笼数量需在 1 到 ${widget.record.waitingCount} 之间');
      return;
    }
    final cage = cages.where((item) => item.id == cageId).firstOrNull;
    if (cage == null || !cage.canAcceptCommodityCount(count)) {
      _showMessage('所选笼位剩余容量不足');
      return;
    }

    final requestId = _request.requestIdFor(canonicalBatchWriteFingerprint({
      'action': 'separateWeaning',
      'houseId': widget.houseId,
      'batchId': widget.batchId,
      'weaningRecordId': widget.record.id,
      'cageId': cageId,
      'count': count,
    }));
    setState(() => _saving = true);
    try {
      await ref.read(batchRepositoryProvider).separatePendingWeaning(
            houseId: widget.houseId,
            batchId: widget.batchId,
            weaningRecordId: widget.record.id,
            cageId: cageId,
            count: count,
            requestId: requestId,
          );
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) {
        _showMessage(error is ApiException ? error.message : error.toString());
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final cages = ref.watch(houseCagesProvider(widget.houseId));
    return SafeArea(
      top: false,
      child: cages.when(
        loading: () => const Padding(
          padding: EdgeInsets.all(32),
          child: Center(child: CircularProgressIndicator()),
        ),
        error: (error, _) => Padding(
          padding: const EdgeInsets.all(24),
          child: Text('无法加载商品兔笼位：$error'),
        ),
        data: (allCages) {
          final commodityCages = allCages
              .where((cage) => cage.isCommodityCage)
              .toList()
            ..sort(
                (left, right) => left.cageNumber.compareTo(right.cageNumber));
          return Padding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('分笼', style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 6),
                Text(
                    '母兔 #${widget.record.rabbitId} · 待分笼 ${widget.record.waitingCount} 只'),
                const SizedBox(height: 16),
                DropdownButtonFormField<int>(
                  key: const ValueKey('pending-weaning-cage'),
                  value: _cageId,
                  isExpanded: true,
                  decoration: const InputDecoration(labelText: '商品兔笼位'),
                  items: commodityCages
                      .map(
                        (cage) => DropdownMenuItem(
                          value: cage.id,
                          child: Text(
                            '${cage.cageNumber} · 还可放 ${cage.commodityRemainingCapacity} 只',
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      )
                      .toList(),
                  onChanged: _saving
                      ? null
                      : (value) => setState(() => _cageId = value),
                ),
                const SizedBox(height: 12),
                TextField(
                  key: const ValueKey('pending-weaning-count'),
                  controller: _countController,
                  enabled: !_saving,
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  decoration: InputDecoration(
                    labelText: '本次分笼数量',
                    helperText: '剩余 ${widget.record.waitingCount} 只，可分批完成',
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed:
                            _saving ? null : () => Navigator.of(context).pop(),
                        child: const Text('取消'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: ElevatedButton(
                        key: const ValueKey('pending-weaning-submit'),
                        onPressed:
                            _saving ? null : () => _submit(commodityCages),
                        child: _saving
                            ? const SizedBox.square(
                                dimension: 20,
                                child:
                                    CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Text('确认分笼'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
