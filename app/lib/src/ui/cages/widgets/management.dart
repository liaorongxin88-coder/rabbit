import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/data/repositories/cages/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/cages/attention.dart';
import 'package:rabbit_flutter/src/domain/cages/layout.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/rabbits/rabbit.dart';
import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';
import 'package:rabbit_flutter/src/ui/cages/widgets/map.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/events.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/queue.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

enum _CageOccupancyFilter { all, empty, occupied }

enum _CageUsageFilter {
  all,
  breeding,
  doeBreeding,
  buckBreeding,
  replacement,
  commodity,
}

class CageManagementSection extends ConsumerStatefulWidget {
  const CageManagementSection({
    super.key,
    required this.house,
    required this.scrollController,
  });

  final RabbitHouse house;
  final ScrollController scrollController;

  @override
  ConsumerState<CageManagementSection> createState() =>
      _CageManagementSectionState();
}

class _CageManagementSectionState extends ConsumerState<CageManagementSection> {
  /// 地图按「排」分页：排数远少于笼数，一次多铺几排也不至于卡。
  static const _rowBatchSize = 6;

  final _searchController = TextEditingController();
  var _keyword = '';
  var _occupancyFilter = _CageOccupancyFilter.all;
  var _usageFilter = _CageUsageFilter.all;

  /// 筛选默认折叠：展开后它比地图本身还高，真机上会把笼位整个挤到首屏之外。
  var _filtersExpanded = false;
  var _visibleRowCount = _rowBatchSize;
  int? _lastSourceCageCount;

  @override
  void initState() {
    super.initState();
    _searchController.addListener(_handleSearchChanged);
  }

  @override
  void didUpdateWidget(covariant CageManagementSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.house.id != widget.house.id) {
      _lastSourceCageCount = null;
      _resetPagination();
    }
  }

  @override
  void dispose() {
    _searchController
      ..removeListener(_handleSearchChanged)
      ..dispose();
    super.dispose();
  }

  void _handleSearchChanged() {
    final next = _searchController.text.trim();
    if (next == _keyword) {
      return;
    }
    setState(() {
      _keyword = next;
      _resetPagination();
    });
  }

  void _resetPagination() {
    _visibleRowCount = _rowBatchSize;
  }

  void _setOccupancyFilter(_CageOccupancyFilter value) {
    if (_occupancyFilter == value) {
      return;
    }
    _scrollToTop();
    setState(() {
      _occupancyFilter = value;
      _resetPagination();
    });
  }

  void _setUsageFilter(_CageUsageFilter value) {
    if (_usageFilter == value) {
      return;
    }
    _scrollToTop();
    setState(() {
      _usageFilter = value;
      _resetPagination();
    });
  }

  void _clearFilters() {
    if (_occupancyFilter == _CageOccupancyFilter.all &&
        _usageFilter == _CageUsageFilter.all) {
      return;
    }
    _scrollToTop();
    setState(() {
      _occupancyFilter = _CageOccupancyFilter.all;
      _usageFilter = _CageUsageFilter.all;
      _resetPagination();
    });
  }

  bool _matchesFilters(Cage cage) {
    final matchesOccupancy = switch (_occupancyFilter) {
      _CageOccupancyFilter.all => true,
      _CageOccupancyFilter.empty => cage.rabbitCount == 0,
      _CageOccupancyFilter.occupied => cage.rabbitCount > 0,
    };
    if (!matchesOccupancy) {
      return false;
    }

    return switch (_usageFilter) {
      _CageUsageFilter.all => true,
      _CageUsageFilter.breeding => cage.status == '1',
      _CageUsageFilter.doeBreeding => cage.isDoeBreedingCage,
      _CageUsageFilter.buckBreeding => cage.isBuckBreedingCage,
      _CageUsageFilter.replacement => cage.status == '2',
      _CageUsageFilter.commodity => cage.status == '3',
    };
  }

  void _scrollToTop() {
    final controller = widget.scrollController;
    if (controller.hasClients &&
        controller.offset > controller.position.minScrollExtent) {
      controller.jumpTo(controller.position.minScrollExtent);
    }
  }

  @override
  Widget build(BuildContext context) {
    final houseId = widget.house.id;
    final cages = ref.watch(houseCagesProvider(houseId));
    final rabbits = ref.watch(houseBreedingRabbitsProvider(houseId));
    final permission = ref.watch(housePermissionProvider(houseId));
    final palette = AppPalette.of(context);

    return SectionCard(
      padding: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
            child: permission.when(
              data: (perm) => _CageHeader(
                house: widget.house,
                canManageCages: perm.canControl,
                showEntryHint: perm.canEdit,
                onCreate: perm.canControl
                    ? () => _showCreateCagesSheet(context)
                    : null,
                onRefresh: () {
                  ref.invalidate(houseCagesProvider(houseId));
                  ref.invalidate(houseBreedingRabbitsProvider(houseId));
                  ref.invalidate(housePermissionProvider(houseId));
                },
              ),
              loading: () => _CageHeader(
                house: widget.house,
                canManageCages: false,
                onCreate: null,
                onRefresh: () {
                  ref.invalidate(houseCagesProvider(houseId));
                  ref.invalidate(houseBreedingRabbitsProvider(houseId));
                },
              ),
              error: (_, __) => _CageHeader(
                house: widget.house,
                canManageCages: false,
                onCreate: null,
                onRefresh: () {
                  ref.invalidate(houseCagesProvider(houseId));
                  ref.invalidate(houseBreedingRabbitsProvider(houseId));
                  ref.invalidate(housePermissionProvider(houseId));
                },
              ),
            ),
          ),
          Divider(height: 1, color: palette.line),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _NfcStatusBand(houseId: houseId),
                const SizedBox(height: 14),
                TextField(
                  controller: _searchController,
                  decoration: InputDecoration(
                    hintText: '请输入兔子位置进行搜索',
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: _keyword.isEmpty
                        ? null
                        : IconButton(
                            tooltip: '清空搜索',
                            icon: const Icon(Icons.close),
                            onPressed: _searchController.clear,
                          ),
                  ),
                ),
                const SizedBox(height: 14),
                cages.when(
                  data: (items) => _buildCageGrid(
                    context,
                    items,
                    permission,
                    _doeStatusByCage(rabbits.valueOrNull ?? const <Rabbit>[]),
                  ),
                  loading: () => const _CageLoading(),
                  error: (error, _) => _InlineSectionError(
                    message: error.toString(),
                    onRetry: () => ref.invalidate(houseCagesProvider(houseId)),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCageGrid(
    BuildContext context,
    List<Cage> cages,
    AsyncValue<HousePermission> permission,
    Map<int, String> doeStatusByCage,
  ) {
    if (_lastSourceCageCount != cages.length) {
      _lastSourceCageCount = cages.length;
      _resetPagination();
    }

    final keyword = _keyword.toLowerCase();
    final filtered = cages
        .where(
          (cage) =>
              (keyword.isEmpty ||
                  cage.cageNumber.toLowerCase().contains(keyword)) &&
              _matchesFilters(cage),
        )
        .toList();

    if (cages.isEmpty) {
      final canControl = permission.valueOrNull?.canControl == true;
      return _CageEmptyState(
        title: '暂无笼位',
        message:
            canControl ? '点击“新增笼位”，按整排编号、层数和每排位置批量生成。' : '当前兔舍还没有笼位，请联系管理员添加。',
        actionLabel: canControl ? '新增笼位' : null,
        onAction: canControl ? () => _showCreateCagesSheet(context) : null,
      );
    }

    final matches = filtered.map((cage) => cage.id).toSet();
    final showDoeBreeding = cages.any((cage) => cage.isDoeBreedingCage) ||
        _usageFilter == _CageUsageFilter.doeBreeding;
    final showBuckBreeding = cages.any((cage) => cage.isBuckBreedingCage) ||
        _usageFilter == _CageUsageFilter.buckBreeding;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _CageFilters(
          occupancyFilter: _occupancyFilter,
          usageFilter: _usageFilter,
          matchingCount: filtered.length,
          totalCount: cages.length,
          showDoeBreeding: showDoeBreeding,
          showBuckBreeding: showBuckBreeding,
          expanded: _filtersExpanded,
          onToggleExpanded: () => setState(
            () => _filtersExpanded = !_filtersExpanded,
          ),
          onOccupancyChanged: _setOccupancyFilter,
          onUsageChanged: _setUsageFilter,
          onReset: _hasActiveFilters ? _clearFilters : null,
        ),
        const SizedBox(height: 14),
        if (filtered.isEmpty)
          _CageEmptyState(
            title: '没有匹配笼位',
            message: _hasActiveFilters
                ? '调整笼位编号或筛选条件后再试。'
                : '换一个位置编号试试，或清空搜索查看全部笼位。',
            actionLabel: _hasActiveFilters ? '重置筛选' : null,
            onAction: _hasActiveFilters ? _clearFilters : null,
          )
        else
          _buildCageMap(
            context,
            cages,
            matches,
            permission,
            doeStatusByCage,
          ),
      ],
    );
  }

  /// 分层地图：坐标用全量笼位构建，筛选/搜索只决定哪些格子高亮。
  /// 如果改成用筛选后的笼位建地图，排和层会随筛选塔缩，“第几排第几位”就不再可信。
  Widget _buildCageMap(
    BuildContext context,
    List<Cage> cages,
    Set<int> matchedCageIds,
    AsyncValue<HousePermission> permission,
    Map<int, String> doeStatusByCage,
  ) {
    final layout = CageLayout.fromCages(cages);
    final counts = <CageAttention, int>{};
    for (final cage in cages) {
      counts.update(cage.attention, (value) => value + 1, ifAbsent: () => 1);
    }
    final canEdit = permission.valueOrNull?.canEdit == true;
    final houseId = widget.house.id;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        CageAttentionLegend(counts: counts),
        const SizedBox(height: 12),
        CageMapView(
          layout: layout,
          visibleRowLimit: _visibleRowCount,
          onShowMoreRows: () => setState(
            () => _visibleRowCount += _rowBatchSize,
          ),
          isMatch: (cage) => matchedCageIds.contains(cage.id),
          statusLabel: (cage) => doeStatusByCage[cage.id] ?? '无状态',
          onTapCage: (cage) => context.go('/houses/$houseId/cages/${cage.id}'),
          rowTrailingBuilder: (row) {
            if (!canEdit || row.rowCode == 'LEGACY') {
              return null;
            }
            return IconButton(
              key: ValueKey('cage-map-row-outbound-${row.rowCode}'),
              tooltip: '${row.rowCode} 排批量出库',
              constraints: const BoxConstraints.tightFor(
                width: 48,
                height: 48,
              ),
              onPressed: () => context.push(
                '/houses/$houseId/outbound?entryType=ROW'
                '&rowCode=${Uri.encodeQueryComponent(row.rowCode)}',
              ),
              icon: const Icon(Icons.local_shipping_outlined),
            );
          },
        ),
      ],
    );
  }

  bool get _hasActiveFilters =>
      _occupancyFilter != _CageOccupancyFilter.all ||
      _usageFilter != _CageUsageFilter.all;

  Future<void> _showCreateCagesSheet(BuildContext context) {
    return showAppModalSheet<void>(
      context: context,
      useRootNavigator: false,
      builder: (context) => _CreateCagesSheet(houseId: widget.house.id),
    );
  }
}

class _CageFilters extends StatelessWidget {
  const _CageFilters({
    required this.occupancyFilter,
    required this.usageFilter,
    required this.matchingCount,
    required this.totalCount,
    required this.showDoeBreeding,
    required this.showBuckBreeding,
    required this.expanded,
    required this.onToggleExpanded,
    required this.onOccupancyChanged,
    required this.onUsageChanged,
    this.onReset,
  });

  final _CageOccupancyFilter occupancyFilter;
  final _CageUsageFilter usageFilter;
  final int matchingCount;
  final int totalCount;
  final bool showDoeBreeding;
  final bool showBuckBreeding;
  final bool expanded;
  final VoidCallback onToggleExpanded;
  final ValueChanged<_CageOccupancyFilter> onOccupancyChanged;
  final ValueChanged<_CageUsageFilter> onUsageChanged;
  final VoidCallback? onReset;

  @override
  Widget build(BuildContext context) {
    final largeText = MediaQuery.textScalerOf(context).scale(10) / 10 >= 1.3;
    final summary = Text(
      '匹配 $matchingCount / $totalCount 个笼位',
      key: const ValueKey('cage-filter-summary'),
      style: Theme.of(context).textTheme.bodyMedium,
    );
    final reset = onReset == null
        ? null
        : TextButton.icon(
            key: const ValueKey('cage-filter-reset'),
            onPressed: onReset,
            icon: const Icon(Icons.filter_alt_off_outlined),
            label: const Text('重置筛选'),
          );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 折叠头：收起时也要能看到“当前匹配多少”和“是否正在筛选”，
        // 否则用户会对着一张被筛过的地图找不到笼。
        InkWell(
          key: const ValueKey('cage-filter-toggle'),
          onTap: onToggleExpanded,
          borderRadius: BorderRadius.circular(8),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Row(
              children: [
                Icon(
                  expanded ? Icons.expand_less : Icons.filter_alt_outlined,
                  size: 18,
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    onReset == null ? '笼位筛选' : '笼位筛选（已启用）',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                ),
                Text(
                  '$matchingCount / $totalCount',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ),
        if (!expanded) const SizedBox(height: 2),
        if (expanded) ...[
          const SizedBox(height: 8),
          Text('在栏状态', style: Theme.of(context).textTheme.labelLarge),
          const SizedBox(height: 6),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              ChoiceChip(
                key: const ValueKey('cage-occupancy-all-filter'),
                label: const Text('全部状态'),
                selected: occupancyFilter == _CageOccupancyFilter.all,
                onSelected: (_) => onOccupancyChanged(_CageOccupancyFilter.all),
              ),
              ChoiceChip(
                key: const ValueKey('cage-occupancy-empty-filter'),
                label: const Text('空笼'),
                selected: occupancyFilter == _CageOccupancyFilter.empty,
                onSelected: (_) =>
                    onOccupancyChanged(_CageOccupancyFilter.empty),
              ),
              ChoiceChip(
                key: const ValueKey('cage-occupancy-occupied-filter'),
                label: const Text('有兔'),
                selected: occupancyFilter == _CageOccupancyFilter.occupied,
                onSelected: (_) =>
                    onOccupancyChanged(_CageOccupancyFilter.occupied),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text('笼位用途', style: Theme.of(context).textTheme.labelLarge),
          const SizedBox(height: 6),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              ChoiceChip(
                key: const ValueKey('cage-usage-all-filter'),
                label: const Text('全部用途'),
                selected: usageFilter == _CageUsageFilter.all,
                onSelected: (_) => onUsageChanged(_CageUsageFilter.all),
              ),
              ChoiceChip(
                key: const ValueKey('cage-usage-breeding-filter'),
                label: const Text('繁殖笼'),
                selected: usageFilter == _CageUsageFilter.breeding,
                onSelected: (_) => onUsageChanged(_CageUsageFilter.breeding),
              ),
              if (showDoeBreeding)
                ChoiceChip(
                  key: const ValueKey('cage-usage-doe-breeding-filter'),
                  label: const Text('种母兔笼'),
                  selected: usageFilter == _CageUsageFilter.doeBreeding,
                  onSelected: (_) =>
                      onUsageChanged(_CageUsageFilter.doeBreeding),
                ),
              if (showBuckBreeding)
                ChoiceChip(
                  key: const ValueKey('cage-usage-buck-breeding-filter'),
                  label: const Text('种公兔笼'),
                  selected: usageFilter == _CageUsageFilter.buckBreeding,
                  onSelected: (_) =>
                      onUsageChanged(_CageUsageFilter.buckBreeding),
                ),
              ChoiceChip(
                key: const ValueKey('cage-usage-replacement-filter'),
                label: const Text('后备笼'),
                selected: usageFilter == _CageUsageFilter.replacement,
                onSelected: (_) => onUsageChanged(_CageUsageFilter.replacement),
              ),
              ChoiceChip(
                key: const ValueKey('cage-usage-commodity-filter'),
                label: const Text('商品笼'),
                selected: usageFilter == _CageUsageFilter.commodity,
                onSelected: (_) => onUsageChanged(_CageUsageFilter.commodity),
              ),
            ],
          ),
          const SizedBox(height: 8),
          if (reset == null)
            summary
          else if (largeText)
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                summary,
                const SizedBox(height: 4),
                Align(alignment: Alignment.centerRight, child: reset),
              ],
            )
          else
            Row(
              children: [
                Expanded(child: summary),
                reset,
              ],
            ),
        ],
      ],
    );
  }
}

class _NfcStatusBand extends ConsumerWidget {
  const _NfcStatusBand({required this.houseId});

  final int houseId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final palette = AppPalette.of(context);
    final queue = ref.watch(nfcCageWriteQueueProvider(houseId));
    return queue.when(
      data: (items) {
        final bound = items.where((item) => item.isBound).length;
        final conflicts = items.where((item) => item.hasConflict).length;
        return Container(
          padding: const EdgeInsets.fromLTRB(14, 12, 10, 12),
          decoration: BoxDecoration(
            color: conflicts > 0 ? palette.warningSoft : palette.primarySoft,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Row(
            children: [
              Icon(
                conflicts > 0 ? Icons.warning_amber : Icons.nfc,
                color: conflicts > 0 ? palette.warning : palette.primary,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  conflicts > 0
                      ? 'NFC 已绑定 $bound · 异常 $conflicts'
                      : 'NFC 已绑定 $bound / ${items.length}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
              ),
              IconButton(
                tooltip: '批量写标签',
                onPressed: () => context.go('/houses/$houseId/nfc/write'),
                icon: const Icon(Icons.chevron_right),
              ),
            ],
          ),
        );
      },
      loading: () => const LinearProgressIndicator(),
      error: (_, __) => Container(
        key: const ValueKey('nfc-status-error'),
        padding: const EdgeInsets.fromLTRB(14, 10, 10, 10),
        decoration: BoxDecoration(
          color: palette.warningSoft,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            Icon(Icons.warning_amber_outlined, color: palette.warning),
            const SizedBox(width: 10),
            const Expanded(child: Text('NFC 状态加载失败，请重试')),
            IconButton(
              key: const ValueKey('nfc-status-retry'),
              tooltip: '重试 NFC 状态',
              onPressed: () =>
                  ref.invalidate(nfcCageWriteQueueProvider(houseId)),
              icon: const Icon(Icons.refresh),
            ),
          ],
        ),
      ),
    );
  }
}

class _CageHeader extends StatelessWidget {
  const _CageHeader({
    required this.house,
    required this.canManageCages,
    required this.onCreate,
    required this.onRefresh,
    this.showEntryHint = false,
  });

  final RabbitHouse house;
  final bool canManageCages;
  final bool showEntryHint;
  final VoidCallback? onCreate;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final largeText = MediaQuery.textScalerOf(context).scale(10) / 10 >= 1.3;
    final heading = Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: palette.primarySoft,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(Icons.grid_view_rounded, color: palette.primary),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('分层笼位图', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 3),
              Text(
                '${house.name} · ${house.layoutLabel}',
                maxLines: largeText ? 2 : 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              if (showEntryHint) ...[
                const SizedBox(height: 2),
                Text(
                  '点击笼位可录入新兔子（兔场初始化）',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: palette.muted,
                      ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
    final actions = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          tooltip: '刷新笼位',
          onPressed: onRefresh,
          icon: const Icon(Icons.refresh),
        ),
        const SizedBox(width: 4),
        FilledButton.icon(
          key: const ValueKey('cage-create-entry'),
          onPressed: onCreate,
          icon: const Icon(Icons.add),
          label: const Text('新增'),
        ),
      ],
    );
    if (!largeText) {
      return Row(children: [Expanded(child: heading), actions]);
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        heading,
        const SizedBox(height: 8),
        Align(alignment: Alignment.centerRight, child: actions),
      ],
    );
  }
}

Map<int, String> _doeStatusByCage(List<Rabbit> rabbits) {
  final result = <int, String>{};
  for (final rabbit in rabbits) {
    if (!rabbit.isActive || rabbit.type != '0' || rabbit.gender != '0') {
      continue;
    }
    final rawStage = rabbit.currentStage?.trim();
    final label = ReproStage.tryParse(rawStage)?.label ?? '无状态';
    result.putIfAbsent(rabbit.cageId, () => label);
  }
  return result;
}

class _CreateCagesSheet extends ConsumerStatefulWidget {
  const _CreateCagesSheet({required this.houseId});

  final int houseId;

  @override
  ConsumerState<_CreateCagesSheet> createState() => _CreateCagesSheetState();
}

class _CreateCagesSheetState extends ConsumerState<_CreateCagesSheet> {
  final _formKey = GlobalKey<FormState>();
  final _rowController = TextEditingController();
  final _layersController = TextEditingController(text: '3');
  final _positionsController = TextEditingController(text: '3');
  var _saving = false;

  @override
  void dispose() {
    _rowController.dispose();
    _layersController.dispose();
    _positionsController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final inset = MediaQuery.of(context).viewInsets.bottom;
    final preview = _previewLabels();

    return Padding(
      padding: EdgeInsets.fromLTRB(20, 18, 20, 20 + inset),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      '新增笼位',
                      style: Theme.of(context).textTheme.titleLarge,
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
              const SizedBox(height: 14),
              _RequiredNumberField(
                fieldKey: const ValueKey('cage-bulk-row'),
                controller: _rowController,
                label: '整排编号',
                hintText: '例如 1',
                allowText: true,
                onChanged: _refreshPreview,
              ),
              const SizedBox(height: 14),
              _RequiredNumberField(
                fieldKey: const ValueKey('cage-bulk-layers'),
                controller: _layersController,
                label: '笼子高几层',
                hintText: '例如 3',
                onChanged: _refreshPreview,
              ),
              const SizedBox(height: 7),
              const _FieldHelpText(
                '如下:根据自己兔场的情况填写,一层就写1,二层就写2,三层就写3',
              ),
              const SizedBox(height: 14),
              _RequiredNumberField(
                fieldKey: const ValueKey('cage-bulk-positions'),
                controller: _positionsController,
                label: '每排几个位置',
                hintText: '例如 3',
                onChanged: _refreshPreview,
              ),
              const SizedBox(height: 7),
              const _FieldHelpText('如下:每排有多少个就写多少'),
              if (preview.isNotEmpty) ...[
                const SizedBox(height: 16),
                Text('生成预览', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final label in preview.take(9))
                      _PreviewLabel(label: label),
                    if (preview.length > 9)
                      _PreviewLabel(label: '+${preview.length - 9}'),
                  ],
                ),
              ],
              const SizedBox(height: 20),
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
                      key: const ValueKey('cage-bulk-submit'),
                      onPressed: _saving ? null : _save,
                      child: _saving
                          ? const SizedBox.square(
                              dimension: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Text('确定'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  List<String> _previewLabels() {
    final row = _rowController.text.trim();
    final layers = int.tryParse(_layersController.text.trim()) ?? 0;
    final positions = int.tryParse(_positionsController.text.trim()) ?? 0;
    if (row.isEmpty || layers <= 0 || positions <= 0) {
      return const <String>[];
    }
    return _buildCageNumbers(row: row, layers: layers, positions: positions);
  }

  void _refreshPreview(String _) {
    setState(() {});
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final row = _rowController.text.trim();
    final layers = int.parse(_layersController.text.trim());
    final positions = int.parse(_positionsController.text.trim());
    final rowCode = row.toUpperCase().startsWith('R') ? row : 'R$row';

    setState(() => _saving = true);
    var created = 0;
    try {
      final repository = ref.read(cageRepositoryProvider);
      for (var position = 1; position <= positions; position++) {
        for (var layer = 1; layer <= layers; layer++) {
          // 只报坐标，编号交给后端生成：建兔舍铺的笼位走的是同一套规则，
          // 两边各拼各的，同一个兔舍里就会长出两种编号。
          await repository.createCage(
            houseId: widget.houseId,
            rowCode: rowCode,
            layerIndex: layer,
            positionIndex: position,
            remark: '客户端批量新增',
          );
          created++;
        }
      }
      ref.invalidate(houseCagesProvider(widget.houseId));
      ref.invalidate(houseRabbitsProvider(widget.houseId));
      ref.invalidate(homeEventsProvider);
      if (mounted) {
        Navigator.of(context).pop();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('已新增 $created 个笼位')),
        );
      }
    } catch (error) {
      if (!mounted) {
        return;
      }
      if (created > 0) {
        // The API creates cages one by one. Reflect a partial success immediately
        // so the operator does not retry against a stale cage list.
        ref.invalidate(houseCagesProvider(widget.houseId));
        ref.invalidate(houseRabbitsProvider(widget.houseId));
        ref.invalidate(nfcCageWriteQueueProvider(widget.houseId));
      }
      final message = error is ApiException ? error.message : error.toString();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
            content: Text(created > 0
                ? '已新增 $created 个，后续创建失败：$message。请修改编号后重试。'
                : message)),
      );
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  /// 预览编号形如 `2-3-1`，即 **排-位-层**，跟后端 CageNumbers 是同一套规则。
  ///
  /// 这里只是让人按确定之前看一眼要建出什么，真正落库的编号由后端生成。
  /// 万一两边算得不一样，预览会跟建出来的对不上——那是看得见的错，
  /// 好过客户端自说自话地把另一套编号写进库里。
  List<String> _buildCageNumbers({
    required String row,
    required int layers,
    required int positions,
  }) {
    final rowLabel = _rowLabel(row);
    final labels = <String>[];
    for (var position = 1; position <= positions; position++) {
      for (var layer = 1; layer <= layers; layer++) {
        labels.add('$rowLabel-$position-$layer');
      }
    }
    return labels;
  }

  /// 排号存成 `R2`，编号里只留数字部分；不是 R+数字就原样保留。
  String _rowLabel(String row) {
    final trimmed = row.trim();
    if (trimmed.length > 1 && (trimmed[0] == 'R' || trimmed[0] == 'r')) {
      final rest = trimmed.substring(1);
      if (RegExp(r'^\d+$').hasMatch(rest)) {
        return int.parse(rest).toString();
      }
    }
    return trimmed;
  }
}

class _RequiredNumberField extends StatelessWidget {
  const _RequiredNumberField({
    required this.controller,
    required this.label,
    required this.hintText,
    this.allowText = false,
    this.onChanged,
    this.fieldKey,
  });

  final TextEditingController controller;
  final String label;
  final String hintText;
  final bool allowText;
  final ValueChanged<String>? onChanged;
  final Key? fieldKey;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      key: fieldKey,
      controller: controller,
      enabled: true,
      decoration: InputDecoration(
        label: Text.rich(
          TextSpan(
            children: [
              const TextSpan(
                text: '* ',
                style: TextStyle(color: AppColors.red),
              ),
              TextSpan(text: label),
            ],
          ),
        ),
        hintText: hintText,
      ),
      keyboardType: allowText ? TextInputType.text : TextInputType.number,
      inputFormatters: allowText
          ? const <TextInputFormatter>[]
          : [FilteringTextInputFormatter.digitsOnly],
      validator: (value) {
        final text = value?.trim() ?? '';
        if (text.isEmpty) {
          return '请填写$label';
        }
        if (!allowText) {
          final number = int.tryParse(text) ?? 0;
          if (number <= 0) {
            return '$label必须大于 0';
          }
          if (number > 50) {
            return '$label不能超过 50';
          }
        }
        return null;
      },
      onChanged: (value) {
        final formState = Form.maybeOf(context);
        formState?.validate();
        onChanged?.call(value);
      },
    );
  }
}

class _FieldHelpText extends StatelessWidget {
  const _FieldHelpText(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Text(
      text,
      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
            color: palette.muted,
            fontWeight: FontWeight.w700,
          ),
    );
  }
}

class _PreviewLabel extends StatelessWidget {
  const _PreviewLabel({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: palette.text,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _CageEmptyState extends StatelessWidget {
  const _CageEmptyState({
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        children: [
          Icon(Icons.grid_view_outlined, color: palette.muted),
          const SizedBox(height: 8),
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(
            message,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          if (actionLabel != null && onAction != null) ...[
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: onAction,
              icon: const Icon(Icons.add),
              label: Text(actionLabel!),
            ),
          ],
        ],
      ),
    );
  }
}

class _CageLoading extends StatelessWidget {
  const _CageLoading();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const SizedBox.square(
          dimension: 18,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
        const SizedBox(width: 10),
        Text('加载笼位中...', style: Theme.of(context).textTheme.bodyMedium),
      ],
    );
  }
}

class _InlineSectionError extends StatelessWidget {
  const _InlineSectionError({
    required this.message,
    required this.onRetry,
  });

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.dangerSoft,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Row(
        children: [
          Icon(Icons.error_outline, color: palette.danger),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          TextButton(onPressed: onRetry, child: const Text('重试')),
        ],
      ),
    );
  }
}
