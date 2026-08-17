import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/models/batch.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/batch_providers.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/create_batch_sheet.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';

class HouseBatchesScreen extends ConsumerWidget {
  const HouseBatchesScreen({super.key, required this.houseId});

  final int houseId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final houses = ref.watch(housesProvider);
    final permission = ref.watch(housePermissionProvider(houseId));
    final house = _findHouse(houses.valueOrNull);
    final canEdit = permission.valueOrNull?.canEdit == true;

    return AppPage(
      title: '生产批次',
      actions: [
        if (canEdit && house != null)
          IconButton(
            key: const ValueKey('batch-create-action'),
            tooltip: '创建批次',
            onPressed: () => _showCreateBatch(context, house),
            icon: const Icon(Icons.add),
          ),
        IconButton(
          tooltip: '返回兔舍详情',
          onPressed: () => context.go('/houses/$houseId'),
          icon: const Icon(Icons.storefront_outlined),
        ),
        IconButton(
          tooltip: '刷新批次',
          onPressed: () => ref.invalidate(houseBatchesProvider(houseId)),
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: houses.when(
        data: (items) {
          final currentHouse = _findHouse(items);
          if (currentHouse == null) {
            return EmptyState(
              icon: Icons.storefront_outlined,
              title: '兔舍不存在',
              message: '返回兔舍列表后重新选择一个兔舍。',
              actionLabel: '返回列表',
              onAction: () => context.go('/houses'),
            );
          }
          return _BatchesAsyncContent(house: currentHouse);
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(housesProvider),
        ),
      ),
    );
  }

  RabbitHouse? _findHouse(List<RabbitHouse>? houses) {
    for (final house in houses ?? const <RabbitHouse>[]) {
      if (house.id == houseId) {
        return house;
      }
    }
    return null;
  }

  Future<void> _showCreateBatch(
    BuildContext context,
    RabbitHouse house,
  ) {
    return showCreateBatchSheet(
      context: context,
      houseId: house.id,
      houseName: house.name,
    );
  }
}

class _BatchesAsyncContent extends ConsumerWidget {
  const _BatchesAsyncContent({required this.house});

  final RabbitHouse house;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final permission = ref.watch(housePermissionProvider(house.id));
    final batches = ref.watch(houseBatchesProvider(house.id));

    return permission.when(
      data: (currentPermission) => batches.when(
        data: (items) => _BatchListContent(
          house: house,
          batches: items,
          canEdit: currentPermission.canEdit,
          onRefresh: () => ref.invalidate(houseBatchesProvider(house.id)),
          onCreate: currentPermission.canEdit
              ? () => showCreateBatchSheet(
                    context: context,
                    houseId: house.id,
                    houseName: house.name,
                  )
              : null,
        ),
        loading: () => const _BatchLoadingState(),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(houseBatchesProvider(house.id)),
        ),
      ),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => ErrorState(
        message: error.toString(),
        onRetry: () => ref.invalidate(housePermissionProvider(house.id)),
      ),
    );
  }
}

class _BatchLoadingState extends StatelessWidget {
  const _BatchLoadingState();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox.square(
            dimension: 28,
            child: CircularProgressIndicator(strokeWidth: 3),
          ),
          const SizedBox(height: 14),
          Text(
            '正在加载全部批次...',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}

class _BatchListContent extends StatefulWidget {
  const _BatchListContent({
    required this.house,
    required this.batches,
    required this.canEdit,
    required this.onRefresh,
    required this.onCreate,
  });

  final RabbitHouse house;
  final List<Batch> batches;
  final bool canEdit;
  final VoidCallback onRefresh;
  final VoidCallback? onCreate;

  @override
  State<_BatchListContent> createState() => _BatchListContentState();
}

class _BatchListContentState extends State<_BatchListContent> {
  static const _allStatuses = '__ALL__';

  final _searchController = TextEditingController();
  var _query = '';
  var _selectedStatus = _allStatuses;

  @override
  void didUpdateWidget(covariant _BatchListContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_selectedStatus != _allStatuses &&
        !widget.batches
            .any((batch) => batch.status.trim() == _selectedStatus)) {
      _selectedStatus = _allStatuses;
    }
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  List<String> get _statuses {
    final statuses = widget.batches
        .map((batch) => batch.status.trim())
        .where((status) => status.isNotEmpty)
        .toSet()
        .toList();
    const priority = ['计划中', '进行中', '已完成'];
    statuses.sort((left, right) {
      final leftIndex = priority.indexOf(left);
      final rightIndex = priority.indexOf(right);
      if (leftIndex >= 0 && rightIndex >= 0) {
        return leftIndex.compareTo(rightIndex);
      }
      if (leftIndex >= 0) {
        return -1;
      }
      if (rightIndex >= 0) {
        return 1;
      }
      return left.compareTo(right);
    });
    return statuses;
  }

  List<Batch> get _filteredBatches {
    final query = _query.trim().toLowerCase();
    return widget.batches.where((batch) {
      if (_selectedStatus != _allStatuses &&
          batch.status.trim() != _selectedStatus) {
        return false;
      }
      if (query.isEmpty) {
        return true;
      }
      return batch.id.toString().contains(query) ||
          batch.batchCode.toLowerCase().contains(query) ||
          batch.remark.toLowerCase().contains(query);
    }).toList();
  }

  bool get _hasActiveFilters =>
      _query.trim().isNotEmpty || _selectedStatus != _allStatuses;

  void _resetFilters() {
    _searchController.clear();
    setState(() {
      _query = '';
      _selectedStatus = _allStatuses;
    });
  }

  @override
  Widget build(BuildContext context) {
    final filteredBatches = _filteredBatches;
    final emptyItemCount = filteredBatches.isEmpty ? 1 : 0;

    return ListView.builder(
      key: const ValueKey('batch-list'),
      padding: AppSpacing.pagePadding,
      itemCount: 5 + filteredBatches.length + emptyItemCount,
      itemBuilder: (context, index) {
        switch (index) {
          case 0:
            return _HouseSummaryCard(house: widget.house);
          case 1:
            return const SizedBox(height: 12);
          case 2:
            return _BatchOverviewCard(
              total: widget.batches.length,
              onRefresh: widget.onRefresh,
              onCreate: widget.onCreate,
            );
          case 3:
            return const SizedBox(height: 12);
          case 4:
            return _BatchFilters(
              controller: _searchController,
              query: _query,
              statuses: _statuses,
              selectedStatus: _selectedStatus,
              resultCount: filteredBatches.length,
              totalCount: widget.batches.length,
              hasActiveFilters: _hasActiveFilters,
              onQueryChanged: (value) => setState(() => _query = value),
              onStatusChanged: (value) {
                setState(() => _selectedStatus = value);
              },
              onReset: _resetFilters,
            );
        }

        if (filteredBatches.isEmpty) {
          final hasBatches = widget.batches.isNotEmpty;
          return Padding(
            padding: const EdgeInsets.only(top: 10),
            child: SectionCard(
              child: EmptyState(
                icon: hasBatches
                    ? Icons.filter_alt_off_outlined
                    : Icons.playlist_add_check_outlined,
                title: hasBatches ? '没有符合条件的批次' : '暂无生产批次',
                message: hasBatches
                    ? '请调整批次编号或状态筛选。'
                    : widget.canEdit
                        ? '创建首个批次后，可在这里统一查看生产周期。'
                        : '当前兔舍还没有生产批次。',
                actionLabel: hasBatches
                    ? '重置筛选'
                    : widget.canEdit
                        ? '创建批次'
                        : null,
                onAction: hasBatches ? _resetFilters : widget.onCreate,
              ),
            ),
          );
        }

        final batch = filteredBatches[index - 5];
        return Padding(
          padding: const EdgeInsets.only(top: 10),
          child: _BatchListCard(
            key: ValueKey('batch-list-item-${batch.id}'),
            batch: batch,
            onTap: () => context.push(
              '/houses/${widget.house.id}/batches/${batch.id}',
            ),
          ),
        );
      },
    );
  }
}

class _HouseSummaryCard extends StatelessWidget {
  const _HouseSummaryCard({required this.house});

  final RabbitHouse house;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      child: Row(
        children: [
          IconButton(
            tooltip: '返回',
            onPressed: () => context.go('/houses/${house.id}'),
            icon: const Icon(Icons.arrow_back),
          ),
          const SizedBox(width: 6),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  house.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 4),
                Text(
                  '兔舍级批次查看与筛选',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _BatchOverviewCard extends StatelessWidget {
  const _BatchOverviewCard({
    required this.total,
    required this.onRefresh,
    required this.onCreate,
  });

  final int total;
  final VoidCallback onRefresh;
  final VoidCallback? onCreate;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: palette.warningSoft,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(
                  Icons.playlist_add_check_outlined,
                  color: palette.warning,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '批次列表',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 3),
                    Text(
                      '共 $total 个批次 · 已全部加载',
                      key: const ValueKey('batch-list-load-status'),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: '刷新批次',
                onPressed: onRefresh,
                icon: const Icon(Icons.refresh),
              ),
            ],
          ),
          if (onCreate != null) ...[
            const SizedBox(height: 12),
            FilledButton.icon(
              key: const ValueKey('batch-create-button'),
              onPressed: onCreate,
              icon: const Icon(Icons.add),
              label: const Text('创建批次'),
            ),
          ],
        ],
      ),
    );
  }
}

class _BatchFilters extends StatelessWidget {
  const _BatchFilters({
    required this.controller,
    required this.query,
    required this.statuses,
    required this.selectedStatus,
    required this.resultCount,
    required this.totalCount,
    required this.hasActiveFilters,
    required this.onQueryChanged,
    required this.onStatusChanged,
    required this.onReset,
  });

  final TextEditingController controller;
  final String query;
  final List<String> statuses;
  final String selectedStatus;
  final int resultCount;
  final int totalCount;
  final bool hasActiveFilters;
  final ValueChanged<String> onQueryChanged;
  final ValueChanged<String> onStatusChanged;
  final VoidCallback onReset;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            key: const ValueKey('batch-search-field'),
            controller: controller,
            textInputAction: TextInputAction.search,
            onChanged: onQueryChanged,
            decoration: InputDecoration(
              labelText: '搜索批次',
              hintText: '批次编号、ID 或备注',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: query.isEmpty
                  ? null
                  : IconButton(
                      key: const ValueKey('batch-search-clear'),
                      tooltip: '清除搜索',
                      onPressed: () {
                        controller.clear();
                        onQueryChanged('');
                      },
                      icon: const Icon(Icons.close),
                    ),
            ),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            key: const ValueKey('batch-status-filter'),
            value: selectedStatus,
            isExpanded: true,
            decoration: const InputDecoration(
              labelText: '批次状态',
              prefixIcon: Icon(Icons.filter_alt_outlined),
            ),
            items: [
              const DropdownMenuItem(
                value: _BatchListContentState._allStatuses,
                child: Text('全部状态'),
              ),
              for (final status in statuses)
                DropdownMenuItem(
                  value: status,
                  child: Text(
                    status,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
            ],
            onChanged: (value) {
              if (value != null) {
                onStatusChanged(value);
              }
            },
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: Text(
                  '显示 $resultCount / $totalCount 个批次',
                  key: const ValueKey('batch-filter-summary'),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ),
              if (hasActiveFilters)
                IconButton(
                  key: const ValueKey('batch-filter-reset'),
                  tooltip: '重置筛选',
                  onPressed: onReset,
                  icon: const Icon(Icons.filter_alt_off_outlined),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _BatchListCard extends StatelessWidget {
  const _BatchListCard({
    super.key,
    required this.batch,
    required this.onTap,
  });

  final Batch batch;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final trimmedStatus = batch.status.trim();
    final status = trimmedStatus.isEmpty ? '状态未设置' : trimmedStatus;
    final colors = _statusColors(palette, trimmedStatus);

    return Card(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: colors.background,
                  borderRadius: BorderRadius.circular(8),
                ),
                child:
                    Icon(Icons.event_note_outlined, color: colors.foreground),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      batch.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 7),
                    Wrap(
                      spacing: 8,
                      runSpacing: 6,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        Container(
                          constraints: const BoxConstraints(maxWidth: 180),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 9,
                            vertical: 5,
                          ),
                          decoration: BoxDecoration(
                            color: colors.background,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            status,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context)
                                .textTheme
                                .labelMedium
                                ?.copyWith(
                                  color: colors.foreground,
                                ),
                          ),
                        ),
                        Text(
                          batch.dateLabel,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                        Text(
                          'ID #${batch.id}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodyMedium,
                        ),
                      ],
                    ),
                    if (batch.remark.trim().isNotEmpty) ...[
                      const SizedBox(height: 7),
                      Text(
                        batch.remark,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: 4),
              const SizedBox.square(
                dimension: 48,
                child: Icon(Icons.chevron_right),
              ),
            ],
          ),
        ),
      ),
    );
  }

  _BatchStatusColors _statusColors(AppPalette palette, String status) {
    switch (status) {
      case '计划中':
        return _BatchStatusColors(palette.warningSoft, palette.warning);
      case '进行中':
        return _BatchStatusColors(palette.primarySoft, palette.primary);
      case '已完成':
        return _BatchStatusColors(palette.successSoft, palette.success);
      default:
        return _BatchStatusColors(palette.surfaceSubtle, palette.muted);
    }
  }
}

class _BatchStatusColors {
  const _BatchStatusColors(this.background, this.foreground);

  final Color background;
  final Color foreground;
}
