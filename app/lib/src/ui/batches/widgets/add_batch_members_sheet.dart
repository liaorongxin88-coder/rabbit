import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_exception.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/batch_providers.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/batch_sheet_async_state.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

Future<bool> showAddBatchMembersSheet({
  required BuildContext context,
  required int houseId,
  required int batchId,
  required Set<int> currentMemberIds,
}) async {
  final result = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    useSafeArea: true,
    builder: (context) => _AddBatchMembersSheet(
      houseId: houseId,
      batchId: batchId,
      currentMemberIds: currentMemberIds,
    ),
  );
  return result == true;
}

class _AddBatchMembersSheet extends ConsumerStatefulWidget {
  const _AddBatchMembersSheet({
    required this.houseId,
    required this.batchId,
    required this.currentMemberIds,
  });

  final int houseId;
  final int batchId;
  final Set<int> currentMemberIds;

  @override
  ConsumerState<_AddBatchMembersSheet> createState() =>
      _AddBatchMembersSheetState();
}

class _AddBatchMembersSheetState extends ConsumerState<_AddBatchMembersSheet> {
  final _writeRequest = BatchWriteRequestController();
  final _searchController = TextEditingController();
  final _selectedRabbitIds = <int>{};
  var _query = '';
  var _saving = false;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  List<Rabbit> _availableRabbits(List<Rabbit> rabbits) {
    final result = rabbits
        .where(
          (rabbit) =>
              rabbit.isActive &&
              _batchPurpose(rabbit) != null &&
              !widget.currentMemberIds.contains(rabbit.id),
        )
        .toList();
    result.sort((left, right) => left.id.compareTo(right.id));
    return result;
  }

  List<Rabbit> _filteredRabbits(List<Rabbit> rabbits) {
    final query = _query.trim().toLowerCase();
    if (query.isEmpty) {
      return rabbits;
    }
    return rabbits.where((rabbit) {
      return rabbit.id.toString().contains(query) ||
          rabbit.cageId.toString().contains(query) ||
          rabbit.breed.toLowerCase().contains(query) ||
          rabbit.typeLabel.toLowerCase().contains(query);
    }).toList();
  }

  void _toggleFilteredSelection(List<Rabbit> rabbits) {
    final ids = rabbits.map((rabbit) => rabbit.id).toSet();
    final allSelected = ids.isNotEmpty &&
        ids.every((rabbitId) => _selectedRabbitIds.contains(rabbitId));
    setState(() {
      if (allSelected) {
        _selectedRabbitIds.removeAll(ids);
      } else {
        _selectedRabbitIds.addAll(ids);
      }
    });
  }

  Future<void> _submit() async {
    if (_saving || _selectedRabbitIds.isEmpty) {
      return;
    }
    final selectedIds = _selectedRabbitIds.toList()..sort();
    final requestId = _writeRequest.requestIdFor(
      canonicalBatchWriteFingerprint({
        'action': 'addBatchMembers',
        'houseId': widget.houseId,
        'batchId': widget.batchId,
        'rabbitIds': selectedIds,
      }),
    );
    setState(() => _saving = true);
    try {
      await ref.read(batchRepositoryProvider).addBatchRabbits(
            houseId: widget.houseId,
            batchId: widget.batchId,
            rabbitIds: selectedIds,
            requestId: requestId,
          );
      if (!mounted) {
        return;
      }
      Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) {
        _showMessage(
          error is ApiException ? error.message : '添加失败，请检查网络后重试',
        );
      }
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
    final rabbitsAsync =
        ref.watch(allActiveHouseRabbitsProvider(widget.houseId));
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    final availableHeight = (mediaQuery.size.height - keyboardInset)
        .clamp(320.0, mediaQuery.size.height);

    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardInset),
      child: SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: availableHeight),
          child: rabbitsAsync.when(
            skipLoadingOnRefresh: false,
            loading: () => BatchSheetLoadingState(
              sheetTitle: '添加批次标签',
              message: '正在加载可添加的兔只',
              onClose: () => Navigator.pop(context),
            ),
            error: (error, _) => BatchSheetErrorState(
              sheetTitle: '添加批次标签',
              error: error,
              fallbackMessage: '无法加载兔只信息，请检查网络后重试。',
              onRetry: () => ref.invalidate(
                allActiveHouseRabbitsProvider(widget.houseId),
              ),
              onClose: () => Navigator.pop(context),
            ),
            data: (rabbits) {
              final available = _availableRabbits(rabbits);
              final filtered = _filteredRabbits(available);
              final allFilteredSelected = filtered.isNotEmpty &&
                  filtered.every(
                    (rabbit) => _selectedRabbitIds.contains(rabbit.id),
                  );
              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Flexible(
                    child: CustomScrollView(
                      key: const ValueKey('batch-add-members-list'),
                      keyboardDismissBehavior:
                          ScrollViewKeyboardDismissBehavior.onDrag,
                      slivers: [
                        SliverPadding(
                          padding: const EdgeInsets.fromLTRB(20, 0, 20, 0),
                          sliver: SliverList(
                            delegate: SliverChildListDelegate.fixed([
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
                                            '添加批次标签',
                                            style: Theme.of(context)
                                                .textTheme
                                                .titleLarge,
                                          ),
                                          const SizedBox(height: 4),
                                          Text(
                                            '当前批次已有 ${widget.currentMemberIds.length} 只成员',
                                            maxLines: 2,
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ],
                                      ),
                                    ),
                                    IconButton(
                                      key: const ValueKey(
                                        'batch-add-members-close',
                                      ),
                                      onPressed: _saving
                                          ? null
                                          : () => Navigator.pop(context),
                                      tooltip: '关闭',
                                      icon: const Icon(Icons.close),
                                    ),
                                  ],
                                ),
                              ),
                              Text(
                                '可添加种母兔、后备母兔和商品兔；已绑定本批次的兔只不会重复显示。',
                                style: Theme.of(context).textTheme.bodyMedium,
                              ),
                              const SizedBox(height: 14),
                              TextField(
                                key: const ValueKey('batch-add-members-search'),
                                controller: _searchController,
                                enabled: !_saving && available.isNotEmpty,
                                onChanged: (value) =>
                                    setState(() => _query = value),
                                decoration: InputDecoration(
                                  labelText: '搜索兔只',
                                  hintText: '搜索兔号、笼位、品种或类型',
                                  prefixIcon: const Icon(Icons.search),
                                  suffixIcon: _query.isEmpty
                                      ? null
                                      : IconButton(
                                          key: const ValueKey(
                                            'batch-add-members-clear-search',
                                          ),
                                          tooltip: '清空搜索',
                                          onPressed: () {
                                            _searchController.clear();
                                            setState(() => _query = '');
                                          },
                                          icon: const Icon(Icons.close),
                                        ),
                                ),
                              ),
                              const SizedBox(height: 8),
                              _AddMembersSelectionBar(
                                total: available.length,
                                filtered: filtered.length,
                                selected: _selectedRabbitIds.length,
                                allFilteredSelected: allFilteredSelected,
                                enabled: !_saving && filtered.isNotEmpty,
                                onToggleFiltered: () =>
                                    _toggleFilteredSelection(filtered),
                                onClear: _selectedRabbitIds.isEmpty || _saving
                                    ? null
                                    : () => setState(
                                          _selectedRabbitIds.clear,
                                        ),
                              ),
                              const SizedBox(height: 6),
                            ]),
                          ),
                        ),
                        if (available.isEmpty)
                          const SliverPadding(
                            padding: EdgeInsets.fromLTRB(20, 8, 20, 20),
                            sliver: SliverToBoxAdapter(
                              child: _AddMembersEmptyState(
                                title: '暂无可添加的兔只',
                                message: '当前兔舍没有符合条件的在栏兔只，或它们已绑定本批次。',
                              ),
                            ),
                          )
                        else if (filtered.isEmpty)
                          const SliverPadding(
                            padding: EdgeInsets.fromLTRB(20, 8, 20, 20),
                            sliver: SliverToBoxAdapter(
                              child: _AddMembersEmptyState(
                                title: '没有符合搜索条件的兔只',
                                message: '请更换搜索词后重试。',
                              ),
                            ),
                          )
                        else
                          SliverPadding(
                            padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
                            sliver: SliverList(
                              delegate: SliverChildBuilderDelegate(
                                (context, index) {
                                  final rabbit = filtered[index];
                                  final selected =
                                      _selectedRabbitIds.contains(rabbit.id);
                                  return CheckboxListTile(
                                    key: ValueKey(
                                      'batch-add-member-option-${rabbit.id}',
                                    ),
                                    value: selected,
                                    onChanged: _saving
                                        ? null
                                        : (value) {
                                            setState(() {
                                              if (value == true) {
                                                _selectedRabbitIds
                                                    .add(rabbit.id);
                                              } else {
                                                _selectedRabbitIds
                                                    .remove(rabbit.id);
                                              }
                                            });
                                          },
                                    controlAffinity:
                                        ListTileControlAffinity.leading,
                                    title: Text(
                                      '兔 #${rabbit.id} · ${rabbit.typeLabel} · ${_batchPurpose(rabbit)}',
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                    subtitle: Text(
                                      '笼位 #${rabbit.cageId} · '
                                      '${rabbit.breed.isEmpty ? '未填品种' : rabbit.breed}',
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                    contentPadding: EdgeInsets.zero,
                                  );
                                },
                                childCount: filtered.length,
                              ),
                            ),
                          ),
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
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Text(
                            _selectedRabbitIds.isEmpty
                                ? '尚未选择兔只'
                                : '为 ${_selectedRabbitIds.length} 只兔添加该批次标签',
                            key: const ValueKey(
                              'batch-add-members-selection-summary',
                            ),
                            textAlign: TextAlign.center,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 8),
                          Row(
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
                                child: FilledButton(
                                  key: const ValueKey(
                                    'batch-add-members-submit',
                                  ),
                                  onPressed:
                                      _saving || _selectedRabbitIds.isEmpty
                                          ? null
                                          : _submit,
                                  child: _saving
                                      ? const SizedBox.square(
                                          dimension: 20,
                                          child: CircularProgressIndicator(
                                            strokeWidth: 2,
                                            color: Colors.white,
                                          ),
                                        )
                                      : const Text('添加到批次'),
                                ),
                              ),
                            ],
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

String? _batchPurpose(Rabbit rabbit) {
  if (rabbit.type.trim() == '2') {
    return '养育/售卖';
  }
  if (rabbit.gender.trim() == '0' &&
      (rabbit.type.trim() == '0' || rabbit.type.trim() == '1')) {
    return '繁育';
  }
  return null;
}

class _AddMembersSelectionBar extends StatelessWidget {
  const _AddMembersSelectionBar({
    required this.total,
    required this.filtered,
    required this.selected,
    required this.allFilteredSelected,
    required this.enabled,
    required this.onToggleFiltered,
    required this.onClear,
  });

  final int total;
  final int filtered;
  final int selected;
  final bool allFilteredSelected;
  final bool enabled;
  final VoidCallback onToggleFiltered;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 8, 4, 8),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              filtered == total ? '共 $total 只' : '结果 $filtered / $total 只',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          TextButton.icon(
            key: const ValueKey('batch-add-members-select-filtered'),
            onPressed: enabled ? onToggleFiltered : null,
            icon: Icon(
              allFilteredSelected
                  ? Icons.remove_done_rounded
                  : Icons.done_all_rounded,
              size: 19,
            ),
            label: Text(allFilteredSelected ? '取消结果' : '全选结果'),
          ),
          IconButton(
            key: const ValueKey('batch-add-members-clear-selection'),
            tooltip: selected == 0 ? '没有已选兔只' : '清空已选 $selected 只',
            onPressed: onClear,
            icon: const Icon(Icons.clear_all_rounded),
          ),
        ],
      ),
    );
  }
}

class _AddMembersEmptyState extends StatelessWidget {
  const _AddMembersEmptyState({required this.title, required this.message});

  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 24),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        children: [
          Icon(Icons.pets_outlined, color: palette.muted, size: 30),
          const SizedBox(height: 10),
          Text(
            title,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 6),
          Text(
            message,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}
