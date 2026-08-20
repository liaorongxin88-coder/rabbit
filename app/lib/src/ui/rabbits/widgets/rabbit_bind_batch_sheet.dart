import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/batch.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/batch_providers.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/batch_sheet_async_state.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';

String? rabbitBatchPurposeLabel(Rabbit rabbit) {
  if (rabbit.type == '2') {
    return '养育/售卖';
  }
  if (rabbit.gender == '0' && (rabbit.type == '0' || rabbit.type == '1')) {
    return '繁育';
  }
  return null;
}

Future<bool> showRabbitBindBatchSheet({
  required BuildContext context,
  required int houseId,
  required Rabbit rabbit,
  Set<int> excludedBatchIds = const <int>{},
}) async {
  if (houseId <= 0 ||
      rabbit.id <= 0 ||
      rabbitBatchPurposeLabel(rabbit) == null) {
    return false;
  }
  final result = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    useSafeArea: true,
    builder: (context) => _RabbitBindBatchSheet(
      houseId: houseId,
      rabbit: rabbit,
      excludedBatchIds: excludedBatchIds,
    ),
  );
  return result == true;
}

class _RabbitBindBatchSheet extends ConsumerStatefulWidget {
  const _RabbitBindBatchSheet({
    required this.houseId,
    required this.rabbit,
    required this.excludedBatchIds,
  });

  final int houseId;
  final Rabbit rabbit;
  final Set<int> excludedBatchIds;

  @override
  ConsumerState<_RabbitBindBatchSheet> createState() =>
      _RabbitBindBatchSheetState();
}

class _RabbitBindBatchSheetState extends ConsumerState<_RabbitBindBatchSheet> {
  final _writeRequest = BatchWriteRequestController();
  int? _selectedBatchId;
  var _saving = false;

  List<Batch> _availableBatches(List<Batch> batches) {
    final result = batches
        .where(
          (batch) =>
              batch.status.trim() != '已完成' &&
              !widget.excludedBatchIds.contains(batch.id),
        )
        .toList();
    result.sort((left, right) {
      final leftDate = left.startDate?.millisecondsSinceEpoch ?? 0;
      final rightDate = right.startDate?.millisecondsSinceEpoch ?? 0;
      final byDate = rightDate.compareTo(leftDate);
      return byDate != 0 ? byDate : right.id.compareTo(left.id);
    });
    return result;
  }

  Future<void> _submit() async {
    final batchId = _selectedBatchId;
    if (_saving || batchId == null) {
      return;
    }
    final requestId = _writeRequest.requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': 'bindRabbitToBatch',
        'houseId': widget.houseId,
        'batchId': batchId,
        'rabbitIds': [widget.rabbit.id],
      }),
    );
    setState(() => _saving = true);
    try {
      await ref.read(batchRepositoryProvider).addBatchRabbits(
            houseId: widget.houseId,
            batchId: batchId,
            rabbitIds: [widget.rabbit.id],
            requestId: requestId,
          );
      if (mounted) {
        Navigator.of(context).pop(true);
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              error is ApiException ? error.message : '绑定失败，请检查网络后重试',
            ),
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final batches = ref.watch(houseBatchesProvider(widget.houseId));
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    final maxHeight = (mediaQuery.size.height - keyboardInset)
        .clamp(360.0, mediaQuery.size.height);

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardInset),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: maxHeight),
          child: batches.when(
            skipLoadingOnRefresh: false,
            loading: () => BatchSheetLoadingState(
              sheetTitle: '绑定批次',
              message: '正在加载进行中的批次',
              onClose: () => Navigator.pop(context),
            ),
            error: (error, _) => BatchSheetErrorState(
              sheetTitle: '绑定批次',
              error: error,
              fallbackMessage: '无法加载批次，请检查网络后重试。',
              onRetry: () => ref.invalidate(
                houseBatchesProvider(widget.houseId),
              ),
              onClose: () => Navigator.pop(context),
            ),
            data: (items) {
              final available = _availableBatches(items);
              final purpose = rabbitBatchPurposeLabel(widget.rabbit)!;
              return Column(
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
                                '绑定批次',
                                style: Theme.of(context).textTheme.titleLarge,
                              ),
                              const SizedBox(height: 4),
                              Text(
                                '兔 #${widget.rabbit.id} · $purpose',
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ],
                          ),
                        ),
                        IconButton(
                          tooltip: '关闭',
                          onPressed: _saving
                              ? null
                              : () => Navigator.of(context).pop(),
                          icon: const Icon(Icons.close),
                        ),
                      ],
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
                    child: Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: AppPalette.of(context).surfaceSubtle,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: AppPalette.of(context).line,
                        ),
                      ),
                      child: Text('批次按标签管理；绑定后，该兔会增加“$purpose”用途标签。'),
                    ),
                  ),
                  Flexible(
                    child: available.isEmpty
                        ? const Center(
                            child: Padding(
                              padding: EdgeInsets.all(24),
                              child: Text(
                                '没有其他可绑定的进行中批次。可先创建空批次，再返回添加标签。',
                                textAlign: TextAlign.center,
                              ),
                            ),
                          )
                        : ListView.builder(
                            key: const ValueKey('rabbit-bind-batch-list'),
                            padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
                            itemCount: available.length,
                            itemBuilder: (context, index) {
                              final batch = available[index];
                              return RadioListTile<int>(
                                key: ValueKey(
                                  'rabbit-bind-batch-option-${batch.id}',
                                ),
                                value: batch.id,
                                groupValue: _selectedBatchId,
                                onChanged: _saving
                                    ? null
                                    : (value) => setState(
                                          () => _selectedBatchId = value,
                                        ),
                                title: Text(
                                  batch.title,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                subtitle: Text(
                                  '${batch.status.isEmpty ? '进行中' : batch.status} · ${batch.dateLabel}',
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                contentPadding: EdgeInsets.zero,
                              );
                            },
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
                                  : () => Navigator.of(context).pop(),
                              child: const Text('取消'),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: FilledButton.icon(
                              key: const ValueKey('rabbit-bind-batch-submit'),
                              onPressed: _saving || _selectedBatchId == null
                                  ? null
                                  : _submit,
                              icon: _saving
                                  ? const SizedBox.square(
                                      dimension: 18,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                        color: Colors.white,
                                      ),
                                    )
                                  : const Icon(Icons.link),
                              label: Text(_saving ? '绑定中' : '确认绑定'),
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
    );
  }
}
