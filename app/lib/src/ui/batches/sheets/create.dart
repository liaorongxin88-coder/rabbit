import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/domain/batches/batch_code.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet_states.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/notice.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

Future<void> showCreateBatchSheet({
  required BuildContext context,
  required int houseId,
  required String houseName,
  DateTime Function()? now,
}) {
  return showAppModalSheet<void>(
    context: context,
    builder: (context) => _CreateBatchSheet(
      houseId: houseId,
      houseName: houseName,
      now: now ?? DateTime.now,
    ),
  );
}

class _CreateBatchSheet extends ConsumerStatefulWidget {
  const _CreateBatchSheet({
    required this.houseId,
    required this.houseName,
    required this.now,
  });

  final int houseId;
  final String houseName;
  final DateTime Function() now;

  @override
  ConsumerState<_CreateBatchSheet> createState() => _CreateBatchSheetState();
}

class _CreateBatchSheetState extends ConsumerState<_CreateBatchSheet> {
  final _writeRequest = BatchWriteRequestController();
  final _codeController = TextEditingController();
  final _remarkController = TextEditingController();
  final _searchController = TextEditingController();
  final _selectedFemaleIds = <int>{};
  var _saving = false;
  var _keyword = '';

  @override
  void initState() {
    super.initState();
    _codeController.text = defaultBatchCode(widget.houseName, widget.now());
  }

  @override
  void dispose() {
    _codeController.dispose();
    _remarkController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  List<Rabbit> _femaleBreeders(List<Rabbit> rabbits) {
    return rabbits.where((r) => r.type == '0' && r.gender == '0').toList()
      ..sort((a, b) => a.id.compareTo(b.id));
  }

  List<Rabbit> _filteredFemales(List<Rabbit> females) {
    final keyword = _keyword.trim().toLowerCase();
    if (keyword.isEmpty) {
      return females;
    }
    return females.where((rabbit) {
      return rabbit.id.toString().contains(keyword) ||
          rabbit.cageId.toString().contains(keyword) ||
          rabbit.breed.toLowerCase().contains(keyword);
    }).toList();
  }

  void _toggleFilteredSelection(List<Rabbit> filtered) {
    final ids = filtered.map((rabbit) => rabbit.id);
    final allSelected = filtered.isNotEmpty &&
        filtered.every((rabbit) => _selectedFemaleIds.contains(rabbit.id));
    setState(() {
      if (allSelected) {
        _selectedFemaleIds.removeAll(ids);
      } else {
        _selectedFemaleIds.addAll(ids);
      }
    });
  }

  Future<void> _submit() async {
    final code = _codeController.text.trim();
    if (code.isEmpty) {
      _showMessage('请输入批次编号');
      return;
    }
    if (_selectedFemaleIds.length >= 100) {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('确认创建大批次'),
          content: Text(
            '将 ${_selectedFemaleIds.length} 只种母兔加入批次 $code。'
            '创建后会为每只母兔生成独立生产任务。',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('返回核对'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('确认创建'),
            ),
          ],
        ),
      );
      if (confirmed != true || !mounted) {
        return;
      }
    }

    setState(() => _saving = true);
    try {
      final remark = _remarkController.text.trim();
      final requestId = _writeRequest.requestIdFor(
        canonicalBatchWriteFingerprint({
          'action': 'createBatch',
          'houseId': widget.houseId,
          'batchCode': code,
          'femaleRabbitIds': _selectedFemaleIds,
          'remark': remark,
        }),
      );
      await ref.read(batchRepositoryProvider).createBatch(
            houseId: widget.houseId,
            batchCode: code,
            femaleRabbitIds: _selectedFemaleIds.toList(),
            remark: remark,
            requestId: requestId,
          );
      if (!mounted) {
        return;
      }
      ref.invalidate(houseBatchesProvider(widget.houseId));
      ref.invalidate(homeEventsProvider);
      final messenger = ScaffoldMessenger.maybeOf(context);
      Navigator.of(context).pop();
      messenger?.showSnackBar(
        SnackBar(
          content: Text(
            _selectedFemaleIds.isEmpty
                ? '批次 $code 已创建'
                : '批次 $code 已创建（${_selectedFemaleIds.length} 只母兔）',
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
    final rabbitsAsync =
        ref.watch(allActiveHouseRabbitsProvider(widget.houseId));
    final mediaQuery = MediaQuery.of(context);
    final keyboardInset = mediaQuery.viewInsets.bottom;
    final availableHeight = mediaQuery.size.height - keyboardInset;

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
              sheetTitle: '创建批次',
              message: '正在加载可选种母兔',
              onClose: () => Navigator.pop(context),
            ),
            error: (error, _) => SheetErrorState(
              sheetTitle: '创建批次',
              error: error,
              fallbackMessage: '无法加载可选兔只，请检查网络后重试。',
              onRetry: () => ref.invalidate(
                allActiveHouseRabbitsProvider(widget.houseId),
              ),
              onClose: () => Navigator.pop(context),
            ),
            data: (rabbits) {
              final females = _femaleBreeders(rabbits);
              final filteredFemales = _filteredFemales(females);
              final allFilteredSelected = filteredFemales.isNotEmpty &&
                  filteredFemales.every(
                    (rabbit) => _selectedFemaleIds.contains(rabbit.id),
                  );
              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Flexible(
                    child: CustomScrollView(
                      key: const ValueKey('batch-mother-list'),
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
                                            '创建批次',
                                            style: Theme.of(context)
                                                .textTheme
                                                .titleLarge,
                                          ),
                                          const SizedBox(height: 4),
                                          Text(
                                            widget.houseName,
                                            style: Theme.of(context)
                                                .textTheme
                                                .bodyMedium,
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
                              const InfoNotice(
                                text: '批次可用于母兔繁育，也可用于商品兔养育与售卖。'
                                    '可先创建空批次，再从兔只详情绑定成员。',
                              ),
                              const SizedBox(height: 14),
                              TextField(
                                key: const ValueKey('batch-code-field'),
                                controller: _codeController,
                                enabled: !_saving,
                                maxLength: maxBatchCodeLength,
                                decoration: const InputDecoration(
                                  labelText: '批次编号',
                                ),
                              ),
                              const SizedBox(height: 12),
                              TextField(
                                key: const ValueKey('batch-remark-field'),
                                controller: _remarkController,
                                enabled: !_saving,
                                maxLines: 2,
                                decoration: const InputDecoration(
                                  labelText: '备注（可选）',
                                ),
                              ),
                              const SizedBox(height: 16),
                              Text(
                                '可选种母兔（已选 ${_selectedFemaleIds.length} 只）',
                                style: Theme.of(context).textTheme.titleSmall,
                              ),
                              const SizedBox(height: 8),
                              TextField(
                                key: const ValueKey('batch-mother-search'),
                                controller: _searchController,
                                enabled: !_saving && females.isNotEmpty,
                                onChanged: (value) {
                                  setState(() => _keyword = value);
                                },
                                decoration: InputDecoration(
                                  hintText: '搜索兔号、笼位或品种',
                                  prefixIcon: const Icon(Icons.search),
                                  suffixIcon: _keyword.isEmpty
                                      ? null
                                      : IconButton(
                                          key: const ValueKey(
                                            'batch-clear-search',
                                          ),
                                          tooltip: '清空搜索',
                                          onPressed: () {
                                            _searchController.clear();
                                            setState(() => _keyword = '');
                                          },
                                          icon: const Icon(Icons.close),
                                        ),
                                ),
                              ),
                              const SizedBox(height: 8),
                              _BatchSelectionBar(
                                total: females.length,
                                filtered: filteredFemales.length,
                                selected: _selectedFemaleIds.length,
                                allFilteredSelected: allFilteredSelected,
                                enabled: !_saving && filteredFemales.isNotEmpty,
                                onToggleFiltered: () =>
                                    _toggleFilteredSelection(filteredFemales),
                                onClear: _selectedFemaleIds.isEmpty || _saving
                                    ? null
                                    : () => setState(_selectedFemaleIds.clear),
                              ),
                              const SizedBox(height: 6),
                            ]),
                          ),
                        ),
                        if (females.isEmpty)
                          const SliverPadding(
                            padding: EdgeInsets.fromLTRB(20, 8, 20, 20),
                            sliver: SliverToBoxAdapter(
                              child: Text('暂无可选种母兔，可直接创建空批次。'),
                            ),
                          )
                        else if (filteredFemales.isEmpty)
                          const SliverPadding(
                            padding: EdgeInsets.fromLTRB(20, 8, 20, 20),
                            sliver: SliverToBoxAdapter(
                              child: Text('没有符合条件的种母兔，请更换搜索词。'),
                            ),
                          )
                        else
                          SliverPadding(
                            padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
                            sliver: SliverList(
                              delegate: SliverChildBuilderDelegate(
                                (context, index) {
                                  final rabbit = filteredFemales[index];
                                  final selected =
                                      _selectedFemaleIds.contains(rabbit.id);
                                  return CheckboxListTile(
                                    key: ValueKey(
                                      'batch-mother-option-${rabbit.id}',
                                    ),
                                    value: selected,
                                    onChanged: _saving
                                        ? null
                                        : (value) {
                                            setState(() {
                                              if (value == true) {
                                                _selectedFemaleIds
                                                    .add(rabbit.id);
                                              } else {
                                                _selectedFemaleIds
                                                    .remove(rabbit.id);
                                              }
                                            });
                                          },
                                    controlAffinity:
                                        ListTileControlAffinity.leading,
                                    title: Text(
                                      '兔 #${rabbit.id} · ${rabbit.breed.isEmpty ? '未填品种' : rabbit.breed}',
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                    subtitle: Text('笼位 #${rabbit.cageId}'),
                                    contentPadding: EdgeInsets.zero,
                                  );
                                },
                                childCount: filteredFemales.length,
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
                            _selectedFemaleIds.isEmpty
                                ? '创建空批次，成员稍后从兔只详情绑定'
                                : '将 ${_selectedFemaleIds.length} 只种母兔加入该批次',
                            key: const ValueKey('batch-selection-summary'),
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.bodyMedium,
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
                                child: ElevatedButton(
                                  key: const ValueKey('create-batch-submit'),
                                  onPressed: _saving ? null : _submit,
                                  child: _saving
                                      ? const SizedBox.square(
                                          dimension: 20,
                                          child: CircularProgressIndicator(
                                            strokeWidth: 2,
                                            color: Colors.white,
                                          ),
                                        )
                                      : const Text('创建批次'),
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

class _BatchSelectionBar extends StatelessWidget {
  const _BatchSelectionBar({
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
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ),
          TextButton.icon(
            key: const ValueKey('batch-select-filtered'),
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
            key: const ValueKey('batch-clear-selection'),
            tooltip: selected == 0 ? '没有已选母兔' : '清空已选 $selected 只',
            onPressed: onClear,
            icon: const Icon(Icons.clear_all_rounded),
          ),
        ],
      ),
    );
  }
}
