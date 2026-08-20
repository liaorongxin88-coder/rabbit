import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/data/services/storage/nfc.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/pending_sync.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/queue.dart';

class NfcWriteSetupScreen extends ConsumerStatefulWidget {
  const NfcWriteSetupScreen({super.key, required this.houseId});

  final int houseId;

  @override
  ConsumerState<NfcWriteSetupScreen> createState() =>
      _NfcWriteSetupScreenState();
}

class _NfcWriteSetupScreenState extends ConsumerState<NfcWriteSetupScreen> {
  var _scope = _WriteScope.unbound;
  var _reverse = false;
  final _selectedIds = <int>{};

  @override
  Widget build(BuildContext context) {
    final queue = ref.watch(nfcCageWriteQueueProvider(widget.houseId));
    final pendingSync = ref.watch(nfcPendingSyncControllerProvider);
    return AppPage(
      title: 'NFC标签写入',
      actions: [
        IconButton(
          tooltip: '刷新',
          onPressed: () => ref.invalidate(
            nfcCageWriteQueueProvider(widget.houseId),
          ),
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: queue.when(
        data: (items) => _buildContent(items, pendingSync),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(
            nfcCageWriteQueueProvider(widget.houseId),
          ),
        ),
      ),
    );
  }

  Widget _buildContent(
    List<NfcCageQueueItem> items,
    NfcPendingSyncState pendingSync,
  ) {
    final palette = AppPalette.of(context);
    final bound = items.where((item) => item.isBound).length;
    final conflicts = items.where((item) => item.hasConflict).length;
    final selected = _selectedItems(items);
    return Column(
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 14),
          decoration: BoxDecoration(
            color: palette.surface,
            border: Border(bottom: BorderSide(color: palette.line)),
          ),
          child: Row(
            children: [
              Icon(Icons.nfc_rounded, color: palette.primary, size: 30),
              const SizedBox(width: 12),
              Expanded(
                child: Wrap(
                  spacing: 16,
                  runSpacing: 6,
                  children: [
                    _StatusValue(label: '总数', value: items.length),
                    _StatusValue(label: '已绑定', value: bound),
                    _StatusValue(
                      label: '未绑定',
                      value: items.length - bound - conflicts,
                    ),
                    if (conflicts > 0)
                      _StatusValue(label: '异常', value: conflicts, danger: true),
                  ],
                ),
              ),
            ],
          ),
        ),
        if (pendingSync.items.isNotEmpty)
          _PendingSyncBand(
            state: pendingSync,
            onManage: () => _showPendingBindings(items),
            onSync: () =>
                ref.read(nfcPendingSyncControllerProvider.notifier).syncAll(),
          ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 110),
            children: [
              Text('写入范围', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 10),
              SegmentedButton<_WriteScope>(
                segments: const [
                  ButtonSegment(
                    value: _WriteScope.unbound,
                    label: Text('仅未绑定'),
                  ),
                  ButtonSegment(
                    value: _WriteScope.all,
                    label: Text('全部'),
                  ),
                  ButtonSegment(
                    value: _WriteScope.custom,
                    label: Text('自选'),
                  ),
                ],
                selected: {_scope},
                onSelectionChanged: (values) {
                  setState(() => _scope = values.first);
                },
              ),
              const SizedBox(height: 18),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      '写入顺序',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ),
                  SegmentedButton<bool>(
                    segments: const [
                      ButtonSegment(value: false, label: Text('正序')),
                      ButtonSegment(value: true, label: Text('倒序')),
                    ],
                    selected: {_reverse},
                    onSelectionChanged: (values) {
                      setState(() => _reverse = values.first);
                    },
                    showSelectedIcon: false,
                  ),
                ],
              ),
              const SizedBox(height: 18),
              Divider(color: palette.line),
              const SizedBox(height: 8),
              if (items.isEmpty)
                const EmptyState(
                  icon: Icons.grid_off_outlined,
                  title: '暂无可写笼位',
                  message: '当前兔舍没有启用的笼位。',
                )
              else
                for (final item in items)
                  _QueueTile(
                    item: item,
                    selectable: _scope == _WriteScope.custom,
                    selected: _selectedIds.contains(item.cageId),
                    onChanged: (checked) {
                      setState(() {
                        if (checked) {
                          _selectedIds.add(item.cageId);
                        } else {
                          _selectedIds.remove(item.cageId);
                        }
                      });
                    },
                  ),
            ],
          ),
        ),
        Container(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 18),
          decoration: BoxDecoration(
            color: palette.surface,
            border: Border(top: BorderSide(color: palette.line)),
          ),
          child: SafeArea(
            top: false,
            child: FilledButton.icon(
              onPressed: selected.isEmpty ? null : () => _start(selected),
              icon: const Icon(Icons.nfc),
              label: Text('开始写入 ${selected.length} 个笼位'),
            ),
          ),
        ),
      ],
    );
  }

  List<NfcCageQueueItem> _selectedItems(List<NfcCageQueueItem> items) {
    Iterable<NfcCageQueueItem> selected;
    switch (_scope) {
      case _WriteScope.unbound:
        selected = items.where((item) => !item.isBound);
      case _WriteScope.all:
        selected = items;
      case _WriteScope.custom:
        selected = items.where((item) => _selectedIds.contains(item.cageId));
    }
    final result = selected.toList();
    return _reverse ? result.reversed.toList() : result;
  }

  Future<void> _start(List<NfcCageQueueItem> selected) async {
    final session = NfcWriteSession(
      houseId: widget.houseId,
      items:
          selected.map((item) => NfcWriteSessionItem(queueItem: item)).toList(),
      currentIndex: 0,
      updatedAt: DateTime.now().millisecondsSinceEpoch,
    );
    await ref.read(nfcLocalStoreProvider).saveSession(session);
    if (mounted) {
      context.go('/houses/${widget.houseId}/nfc/write/session');
    }
  }

  Future<void> _showPendingBindings(List<NfcCageQueueItem> queue) {
    return showAppModalSheet<void>(
      context: context,
      useRootNavigator: false,
      builder: (context) => _PendingBindingsSheet(queue: queue),
    );
  }
}

enum _WriteScope { unbound, all, custom }

class _StatusValue extends StatelessWidget {
  const _StatusValue({
    required this.label,
    required this.value,
    this.danger = false,
  });

  final String label;
  final int value;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Text(
      '$label $value',
      style: TextStyle(
        color: danger ? palette.danger : palette.text,
        fontWeight: FontWeight.w800,
      ),
    );
  }
}

class _PendingSyncBand extends StatelessWidget {
  const _PendingSyncBand({
    required this.state,
    required this.onManage,
    required this.onSync,
  });

  final NfcPendingSyncState state;
  final VoidCallback onManage;
  final VoidCallback onSync;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final hasActionRequired = state.conflictCount + state.failedCount > 0;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(20, 10, 12, 10),
      color: hasActionRequired ? palette.warningSoft : palette.primarySoft,
      child: Row(
        children: [
          Icon(
            hasActionRequired ? Icons.sync_problem : Icons.cloud_sync_outlined,
            color: hasActionRequired ? palette.warning : palette.primary,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              hasActionRequired
                  ? '待同步 ${state.items.length} · 需处理 ${state.conflictCount + state.failedCount}'
                  : '待同步 ${state.items.length}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
          ),
          IconButton(
            tooltip: '立即同步',
            onPressed: state.syncing ? null : onSync,
            icon: state.syncing
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.sync),
          ),
          IconButton(
            tooltip: '管理待同步项',
            onPressed: onManage,
            icon: const Icon(Icons.manage_search),
          ),
        ],
      ),
    );
  }
}

class _PendingBindingsSheet extends ConsumerWidget {
  const _PendingBindingsSheet({required this.queue});

  final List<NfcCageQueueItem> queue;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(nfcPendingSyncControllerProvider);
    final controller = ref.read(nfcPendingSyncControllerProvider.notifier);
    return FractionallySizedBox(
      heightFactor: 0.82,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 8, 10),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    '待同步标签',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                IconButton(
                  tooltip: '关闭',
                  onPressed: () => Navigator.pop(context),
                  icon: const Icon(Icons.close),
                ),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: state.items.isEmpty
                ? const EmptyState(
                    icon: Icons.cloud_done_outlined,
                    title: '已全部同步',
                    message: '当前没有待处理的标签绑定。',
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(20, 10, 12, 20),
                    itemCount: state.items.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final item = state.items[index];
                      return _PendingBindingTile(
                        item: item,
                        cageNumber: _cageNumber(item.cageId),
                        onRetry: () => controller.retry(item),
                        onReplace: () => controller.forceReplace(item),
                        onDiscard: () => controller.discard(item),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  String _cageNumber(int cageId) {
    for (final item in queue) {
      if (item.cageId == cageId) {
        return item.cageNumber.isEmpty ? '#$cageId' : item.cageNumber;
      }
    }
    return '#$cageId';
  }
}

class _PendingBindingTile extends StatelessWidget {
  const _PendingBindingTile({
    required this.item,
    required this.cageNumber,
    required this.onRetry,
    required this.onReplace,
    required this.onDiscard,
  });

  final NfcPendingBinding item;
  final String cageNumber;
  final VoidCallback onRetry;
  final VoidCallback onReplace;
  final VoidCallback onDiscard;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final isConflict = item.status == NfcPendingBindingStatus.conflict;
    final isFailed = item.status == NfcPendingBindingStatus.failed;
    final statusText = isConflict
        ? '绑定冲突'
        : isFailed
            ? '同步失败'
            : item.errorMessage == null
                ? '等待同步'
                : '网络不可用';
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(
        isConflict || isFailed ? Icons.error_outline : Icons.cloud_upload,
        color: isConflict || isFailed ? palette.danger : palette.primary,
      ),
      title: Text(cageNumber),
      subtitle: Text(
        item.errorMessage == null
            ? statusText
            : '$statusText · ${item.errorMessage}',
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (isConflict)
            IconButton(
              tooltip: '强制重新绑定',
              onPressed: onReplace,
              icon: const Icon(Icons.link),
            )
          else
            IconButton(
              tooltip: '重试',
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
            ),
          IconButton(
            tooltip: '删除待同步项',
            onPressed: onDiscard,
            icon: const Icon(Icons.delete_outline),
          ),
        ],
      ),
    );
  }
}

class _QueueTile extends StatelessWidget {
  const _QueueTile({
    required this.item,
    required this.selectable,
    required this.selected,
    required this.onChanged,
  });

  final NfcCageQueueItem item;
  final bool selectable;
  final bool selected;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final icon = item.hasConflict
        ? Icons.error_outline
        : item.isBound
            ? Icons.nfc
            : Icons.nfc_outlined;
    final color = item.hasConflict
        ? palette.danger
        : item.isBound
            ? palette.success
            : palette.muted;
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: selectable
          ? Checkbox(
              value: selected,
              onChanged: (value) => onChanged(value ?? false),
            )
          : Icon(icon, color: color),
      title:
          Text(item.cageNumber.isEmpty ? '#${item.cageId}' : item.cageNumber),
      subtitle: Text(
        item.hasConflict
            ? '绑定异常'
            : item.isBound
                ? '已绑定 ${item.tagUid ?? ''}'
                : '未绑定',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      onTap: selectable ? () => onChanged(!selected) : null,
    );
  }
}
