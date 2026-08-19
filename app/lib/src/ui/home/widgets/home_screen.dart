import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/production_event_sheet.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';
import 'package:rabbit_flutter/src/ui/home/view_models/home_events_provider.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final houses = ref.watch(housesProvider);
    final events = ref.watch(homeEventsProvider);

    return AppPage(
      title: '鸿兔智管',
      actions: [
        IconButton(
          tooltip: '刷新',
          onPressed: () => _refreshHome(ref),
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: houses.when(
        data: (items) {
          if (items.isEmpty) {
            return EmptyState(
              icon: Icons.storefront_outlined,
              title: '尚未加入兔舍',
              message: '可以创建兔舍，或等待管理员通过手机号邀请后刷新。',
              actionLabel: '管理兔舍',
              onAction: () => context.go('/houses'),
            );
          }
          return RefreshIndicator(
            onRefresh: () => _refreshHome(ref),
            child: ListView(
              key: const ValueKey('home-scroll'),
              padding: AppSpacing.pagePadding,
              children: [
                events.when(
                  data: (items) => _HomeContent(events: items),
                  loading: () => const Padding(
                    padding: EdgeInsets.only(top: 120),
                    child: Center(child: CircularProgressIndicator()),
                  ),
                  error: (error, _) => SizedBox(
                    height: 480,
                    child: ErrorState(
                      message: error.toString(),
                      onRetry: () => ref.invalidate(homeEventsProvider),
                    ),
                  ),
                ),
              ],
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(housesProvider),
        ),
      ),
    );
  }

  Future<void> _refreshHome(WidgetRef ref) async {
    ref.invalidate(housesProvider);
    ref.invalidate(homeEventsProvider);
    await ref.read(homeEventsProvider.future);
  }
}

class _HomeContent extends ConsumerStatefulWidget {
  const _HomeContent({required this.events});

  final List<EventItem> events;

  @override
  ConsumerState<_HomeContent> createState() => _HomeContentState();
}

class _HomeContentState extends ConsumerState<_HomeContent>
    with SingleTickerProviderStateMixin {
  late final TabController _tabController;
  final _searchController = TextEditingController();
  var _query = '';
  int? _houseFilterId;
  var _dueFilter = _DueFilter.all;

  static const _tabs = [
    _FlowTab(0, '催情', ['催情'], Icons.play_circle_outline_rounded),
    _FlowTab(1, '配种', ['配种'], Icons.favorite_border_rounded),
    _FlowTab(2, '摸胎', ['摸胎'], Icons.health_and_safety_outlined),
    _FlowTab(3, '备产', ['备产'], Icons.inventory_2_outlined),
    _FlowTab(4, '分娩', ['分娩', '生产'], Icons.child_care_outlined),
    _FlowTab(5, '断奶', ['断奶'], Icons.call_split_rounded),
    _FlowTab(6, '出售', ['出售'], Icons.local_shipping_outlined),
    _FlowTab(7, '后备兔', ['后备兔', '后备成熟'], Icons.trending_up_rounded),
  ];

  @override
  void initState() {
    super.initState();
    // Keep the default production flow on配种 for existing operators;催情 remains
    // the first explicit tab so reminder filtering can still expose it directly.
    _tabController = TabController(
      length: _tabs.length,
      vsync: this,
      initialIndex: 1,
    );
  }

  @override
  void dispose() {
    _tabController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant _HomeContent oldWidget) {
    super.didUpdateWidget(oldWidget);
    final selectedHouseStillExists = widget.events.any(
      (event) => event.sourceHouseId == _houseFilterId,
    );
    if (_houseFilterId != null && !selectedHouseStillExists) {
      _houseFilterId = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final events = widget.events;
    final visibleEvents = _visibleEvents(events);
    final textScale = MediaQuery.textScalerOf(context).scale(1);
    final panelHeight =
        (MediaQuery.sizeOf(context).height * 0.52 + (textScale - 1) * 72)
            .clamp(420.0, 620.0);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _AlertHeader(events: events),
        const SizedBox(height: 14),
        _WorkQueueFilters(
          events: events,
          searchController: _searchController,
          query: _query,
          selectedHouseId: _houseFilterId,
          dueFilter: _dueFilter,
          resultCount: visibleEvents.length,
          onQueryChanged: (value) => setState(() => _query = value),
          onHouseChanged: (value) => setState(
            () => _houseFilterId = value == 0 ? null : value,
          ),
          onDueChanged: (value) => setState(() => _dueFilter = value),
          onClear: _clearFilters,
        ),
        const SizedBox(height: 14),
        _FlowTabs(
          tabController: _tabController,
          tabs: _tabs,
          events: visibleEvents,
        ),
        SizedBox(
          height: panelHeight,
          child: TabBarView(
            controller: _tabController,
            children: [
              for (final tab in _tabs)
                _FlowPanel(
                  tab: tab,
                  events: _eventsFor(tab, visibleEvents),
                  onEventTap: _handleEventTap,
                  onManageHouses: () => context.go('/houses'),
                  hasActiveFilters: _hasActiveFilters,
                  onClearFilters: _clearFilters,
                ),
            ],
          ),
        ),
      ],
    );
  }

  bool get _hasActiveFilters =>
      _query.trim().isNotEmpty ||
      _houseFilterId != null ||
      _dueFilter != _DueFilter.all;

  List<EventItem> _visibleEvents(List<EventItem> events) {
    final query = _query.trim().toLowerCase();
    return events.where((event) {
      if (_houseFilterId != null && event.sourceHouseId != _houseFilterId) {
        return false;
      }
      if (!_dueFilter.matches(event)) {
        return false;
      }
      if (query.isEmpty) {
        return true;
      }
      final searchable = [
        event.eventType,
        event.operationalTargetLabel,
        event.houseLabel,
        event.batchLabel ?? '',
        event.cycleRecordLabel ?? '',
        event.statusLabel,
        event.rabbitId?.toString() ?? '',
        event.batchId?.toString() ?? '',
      ].join(' ').toLowerCase();
      return searchable.contains(query);
    }).toList();
  }

  void _clearFilters() {
    _searchController.clear();
    setState(() {
      _query = '';
      _houseFilterId = null;
      _dueFilter = _DueFilter.all;
    });
  }

  static List<EventItem> _eventsFor(_FlowTab tab, List<EventItem> events) {
    return events.where((event) {
      if (tab.label == '后备兔') {
        return event.isReplacement;
      }
      if (tab.label == '分娩') {
        return event.isProduction &&
            tab.keywords.any((keyword) => event.eventType.contains(keyword));
      }
      return event.isProduction &&
          tab.keywords.any((keyword) => event.eventType.contains(keyword));
    }).toList();
  }

  Future<void> _handleEventTap(EventItem event) async {
    if (!eventIsActionable(event)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('该提醒暂不支持在此处理')),
      );
      return;
    }

    final houseId = event.sourceHouseId;
    if (houseId == null || houseId <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('无法识别兔舍，请刷新后重试')),
      );
      return;
    }

    final permission = await ref.read(housePermissionProvider(houseId).future);
    if (!mounted) {
      return;
    }
    if (!permission.canEdit) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('当前为只读权限，无法执行生产操作')),
      );
      return;
    }

    if (event.eventType.contains('出售') && event.rabbitId != null) {
      await context.push(
          '/houses/$houseId/outbound?entryType=RABBIT&rabbitId=${event.rabbitId}');
      return;
    }
    await showProductionEventSheet(context: context, event: event);
  }
}

class _AlertHeader extends StatelessWidget {
  const _AlertHeader({required this.events});

  final List<EventItem> events;

  @override
  Widget build(BuildContext context) {
    final overdue = events.where((event) => event.isOverdue).length;
    final due = events.where((event) => event.isDue).length;
    final upcoming = (events.length - overdue - due).clamp(0, events.length);
    final palette = AppPalette.of(context);
    final today = DateTime.now();

    return SectionCard(
      key: const ValueKey('home-production-overview-section'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const _SectionHeader(
                      icon: Icons.today_outlined,
                      title: '今日生产',
                    ),
                    const SizedBox(height: 5),
                    Text(
                      '${today.month}月${today.day}日 · 全部兔舍',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
              _WorkloadBadge(count: events.length),
            ],
          ),
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 14),
            child: Divider(height: 1, color: palette.line),
          ),
          ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 68),
            child: IntrinsicHeight(
              child: Row(
                children: [
                  Expanded(
                    child: _StatusMetric(
                      label: '逾期',
                      count: overdue,
                      color: palette.danger,
                    ),
                  ),
                  VerticalDivider(
                    width: 1,
                    color: palette.line,
                    indent: 4,
                    endIndent: 4,
                  ),
                  Expanded(
                    child: _StatusMetric(
                      label: '到期',
                      count: due,
                      color: palette.primary,
                    ),
                  ),
                  VerticalDivider(
                    width: 1,
                    color: palette.line,
                    indent: 4,
                    endIndent: 4,
                  ),
                  Expanded(
                    child: _StatusMetric(
                      label: '未到期',
                      count: upcoming,
                      color: palette.success,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

enum _DueFilter {
  all('全部状态'),
  overdue('仅逾期'),
  due('仅到期'),
  upcoming('仅未到期');

  const _DueFilter(this.label);

  final String label;

  bool matches(EventItem event) {
    return switch (this) {
      _DueFilter.all => true,
      _DueFilter.overdue => event.isOverdue,
      _DueFilter.due => event.isDue,
      _DueFilter.upcoming => !event.isOverdue && !event.isDue,
    };
  }
}

class _WorkQueueFilters extends StatelessWidget {
  const _WorkQueueFilters({
    required this.events,
    required this.searchController,
    required this.query,
    required this.selectedHouseId,
    required this.dueFilter,
    required this.resultCount,
    required this.onQueryChanged,
    required this.onHouseChanged,
    required this.onDueChanged,
    required this.onClear,
  });

  final List<EventItem> events;
  final TextEditingController searchController;
  final String query;
  final int? selectedHouseId;
  final _DueFilter dueFilter;
  final int resultCount;
  final ValueChanged<String> onQueryChanged;
  final ValueChanged<int> onHouseChanged;
  final ValueChanged<_DueFilter> onDueChanged;
  final VoidCallback onClear;

  bool get _hasActiveFilters =>
      query.trim().isNotEmpty ||
      selectedHouseId != null ||
      dueFilter != _DueFilter.all;

  Map<int, String> get _houses {
    final result = <int, String>{};
    for (final event in events) {
      final id = event.sourceHouseId;
      if (id != null && id > 0) {
        result.putIfAbsent(id, () => event.houseLabel);
      }
    }
    return result;
  }

  @override
  Widget build(BuildContext context) {
    final houses = _houses.entries.toList()
      ..sort((a, b) => a.value.compareTo(b.value));
    return SectionCard(
      key: const ValueKey('home-work-queue-filter-section'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _SectionHeader(
            icon: Icons.filter_alt_outlined,
            title: '任务筛选',
          ),
          const SizedBox(height: 14),
          TextField(
            key: const ValueKey('production-work-search'),
            controller: searchController,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              labelText: '搜索生产任务',
              hintText: '母兔、批次、兔舍或任务类型',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: query.isEmpty
                  ? null
                  : IconButton(
                      key: const ValueKey('production-work-search-clear'),
                      tooltip: '清除搜索',
                      onPressed: () {
                        searchController.clear();
                        onQueryChanged('');
                      },
                      icon: const Icon(Icons.close),
                    ),
            ),
            onChanged: onQueryChanged,
          ),
          const SizedBox(height: 10),
          LayoutBuilder(
            builder: (context, constraints) {
              final fieldWidth = constraints.maxWidth >= 560
                  ? (constraints.maxWidth - 12) / 2
                  : constraints.maxWidth;
              return Wrap(
                spacing: 12,
                runSpacing: 10,
                children: [
                  SizedBox(
                    width: fieldWidth,
                    child: DropdownButtonFormField<int>(
                      key: const ValueKey('production-house-filter'),
                      value: selectedHouseId ?? 0,
                      isExpanded: true,
                      decoration: const InputDecoration(
                        labelText: '兔舍范围',
                        prefixIcon: Icon(Icons.home_work_outlined),
                      ),
                      items: [
                        const DropdownMenuItem(value: 0, child: Text('全部兔舍')),
                        for (final house in houses)
                          DropdownMenuItem(
                            value: house.key,
                            child: Text(
                              house.value,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                      ],
                      onChanged: (value) => onHouseChanged(value ?? 0),
                    ),
                  ),
                  SizedBox(
                    width: fieldWidth,
                    child: DropdownButtonFormField<_DueFilter>(
                      key: const ValueKey('production-due-filter'),
                      value: dueFilter,
                      decoration: const InputDecoration(
                        labelText: '到期状态',
                        prefixIcon: Icon(Icons.schedule_outlined),
                      ),
                      items: [
                        for (final filter in _DueFilter.values)
                          DropdownMenuItem(
                            value: filter,
                            child: Text(filter.label),
                          ),
                      ],
                      onChanged: (value) {
                        if (value != null) {
                          onDueChanged(value);
                        }
                      },
                    ),
                  ),
                ],
              );
            },
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: Text(
                  '显示 $resultCount / ${events.length} 条任务',
                  key: const ValueKey('production-filter-summary'),
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ),
              if (_hasActiveFilters)
                TextButton.icon(
                  key: const ValueKey('production-filter-clear'),
                  onPressed: onClear,
                  icon: const Icon(Icons.filter_alt_off_outlined),
                  label: const Text('重置筛选'),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _WorkloadBadge extends StatelessWidget {
  const _WorkloadBadge({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final active = count > 0;
    return Container(
      constraints: const BoxConstraints(minWidth: 68, minHeight: 54),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
      decoration: BoxDecoration(
        color: active ? palette.primarySoft : palette.successSoft,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: active
              ? palette.primary.withOpacity(0.25)
              : palette.success.withOpacity(0.25),
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            '$count',
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  color: active ? palette.primary : palette.success,
                  fontWeight: FontWeight.w900,
                  height: 1,
                ),
          ),
          const SizedBox(height: 4),
          Text(
            active ? '待处理' : '已清',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: active ? palette.primary : palette.success,
                  fontWeight: FontWeight.w700,
                ),
          ),
        ],
      ),
    );
  }
}

class _StatusMetric extends StatelessWidget {
  const _StatusMetric({
    required this.label,
    required this.count,
    required this.color,
  });

  final String label;
  final int count;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Semantics(
      label: '$label $count 条',
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            '$count',
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  color: count > 0 ? color : palette.muted,
                  fontWeight: FontWeight.w900,
                  height: 1,
                ),
          ),
          const SizedBox(height: 6),
          Text(
            label,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: count > 0 ? color : palette.muted,
                  fontWeight: FontWeight.w700,
                ),
          ),
        ],
      ),
    );
  }
}

class _FlowTabs extends StatelessWidget {
  const _FlowTabs({
    required this.tabController,
    required this.tabs,
    required this.events,
  });

  final TabController tabController;
  final List<_FlowTab> tabs;
  final List<EventItem> events;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return SectionCard(
      key: const ValueKey('home-production-flow-section'),
      padding: const EdgeInsets.fromLTRB(16, 14, 0, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(right: 16),
            child: _SectionHeader(
              icon: Icons.account_tree_outlined,
              title: '生产流程',
            ),
          ),
          const SizedBox(height: 8),
          Divider(height: 1, color: palette.line),
          TabBar(
            controller: tabController,
            isScrollable: true,
            labelColor: palette.primary,
            unselectedLabelColor: palette.muted,
            indicatorColor: palette.primary,
            indicatorWeight: 3,
            tabAlignment: TabAlignment.start,
            labelPadding: const EdgeInsets.symmetric(horizontal: 12),
            labelStyle: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w900,
            ),
            unselectedLabelStyle: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
            tabs: [
              for (final tab in tabs)
                Tab(
                  height: 52,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        tab.stage.toString().padLeft(2, '0'),
                        style: const TextStyle(fontSize: 11),
                      ),
                      const SizedBox(width: 5),
                      Text(tab.label),
                      if (_HomeContentState._eventsFor(tab, events)
                          .isNotEmpty) ...[
                        const SizedBox(width: 6),
                        _TinyCount(
                          count:
                              _HomeContentState._eventsFor(tab, events).length,
                        ),
                      ],
                    ],
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.icon, required this.title});

  final IconData icon;
  final String title;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Row(
      children: [
        Icon(icon, size: 19, color: palette.primary),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
          ),
        ),
      ],
    );
  }
}

class _TinyCount extends StatelessWidget {
  const _TinyCount({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      constraints: const BoxConstraints(minWidth: 20, minHeight: 20),
      alignment: Alignment.center,
      padding: const EdgeInsets.symmetric(horizontal: 5),
      decoration: BoxDecoration(
        color: palette.primarySoft,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        '$count',
        style: TextStyle(
          color: palette.primary,
          fontSize: 11,
          fontWeight: FontWeight.w900,
        ),
      ),
    );
  }
}

class _FlowPanel extends StatelessWidget {
  const _FlowPanel({
    required this.tab,
    required this.events,
    required this.onEventTap,
    required this.onManageHouses,
    required this.hasActiveFilters,
    required this.onClearFilters,
  });

  final _FlowTab tab;
  final List<EventItem> events;
  final Future<void> Function(EventItem event) onEventTap;
  final VoidCallback onManageHouses;
  final bool hasActiveFilters;
  final VoidCallback onClearFilters;

  @override
  Widget build(BuildContext context) {
    if (events.isEmpty) {
      return EmptyState(
        icon: tab.icon,
        title: hasActiveFilters ? '没有匹配的${tab.label}任务' : '${tab.label}任务已清',
        message: hasActiveFilters ? '调整搜索或筛选条件后再试。' : '所有兔舍当前均无待处理对象。',
        actionLabel: hasActiveFilters ? '重置筛选' : '查看兔舍',
        onAction: hasActiveFilters ? onClearFilters : onManageHouses,
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.only(top: 18, bottom: 22),
      itemBuilder: (context, index) => _EventCard(
        event: events[index],
        onTap: () => onEventTap(events[index]),
      ),
      separatorBuilder: (context, index) => const SizedBox(height: 10),
      itemCount: events.length,
    );
  }
}

class _EventCard extends StatelessWidget {
  const _EventCard({
    required this.event,
    required this.onTap,
  });

  final EventItem event;
  final VoidCallback onTap;

  bool get _actionable => eventIsActionable(event);
  String get _actionHint => productionActionHint(event);

  IconData get _eventIcon {
    final type = event.eventType;
    if (type.contains('配种')) return Icons.favorite_border_rounded;
    if (type.contains('摸胎')) return Icons.health_and_safety_outlined;
    if (type.contains('备产')) return Icons.inventory_2_outlined;
    if (type.contains('分娩') || type.contains('生产')) {
      return Icons.child_care_outlined;
    }
    if (type.contains('断奶')) return Icons.call_split_rounded;
    if (type.contains('出售')) return Icons.local_shipping_outlined;
    if (type.contains('后备')) return Icons.trending_up_rounded;
    return Icons.notifications_active_outlined;
  }

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final color = event.isOverdue
        ? palette.danger
        : event.isDue
            ? palette.primary
            : palette.success;

    return SectionCard(
      padding: EdgeInsets.zero,
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: Border(left: BorderSide(color: color, width: 4)),
        ),
        child: InkWell(
          key: ValueKey('production-event-rabbit-${event.rabbitId ?? 0}'),
          borderRadius: BorderRadius.circular(8),
          onTap: _actionable ? onTap : null,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(_eventIcon, size: 19, color: palette.muted),
                    const SizedBox(width: 7),
                    Expanded(
                      child: Text(
                        event.eventType.isEmpty
                            ? event.category
                            : event.eventType,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                    _EventStatus(label: event.statusLabel, color: color),
                  ],
                ),
                const SizedBox(height: 11),
                Text(
                  event.operationalTargetLabel,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontSize: 17,
                        fontWeight: FontWeight.w800,
                      ),
                ),
                const SizedBox(height: 7),
                Row(
                  children: [
                    Icon(Icons.home_work_outlined,
                        size: 15, color: palette.muted),
                    const SizedBox(width: 5),
                    Expanded(
                      child: Text(
                        event.houseLabel,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 7),
                Wrap(
                  spacing: 12,
                  runSpacing: 6,
                  children: [
                    if (event.batchLabel != null)
                      _EventMeta(
                        icon: Icons.inventory_2_outlined,
                        label: event.batchLabel!,
                      ),
                    if (event.cycleRecordLabel != null)
                      _EventMeta(
                        icon: Icons.repeat_rounded,
                        label: event.cycleRecordLabel!,
                      ),
                    _EventMeta(
                      icon: Icons.calendar_today_outlined,
                      label: event.dateLabel,
                    ),
                  ],
                ),
                if (_actionable) ...[
                  const SizedBox(height: 11),
                  Divider(height: 1, color: palette.line),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          _actionHint,
                          style:
                              Theme.of(context).textTheme.labelLarge?.copyWith(
                                    color: palette.primary,
                                  ),
                        ),
                      ),
                      Icon(Icons.chevron_right,
                          size: 20, color: palette.primary),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _EventStatus extends StatelessWidget {
  const _EventStatus({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 26),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 12,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _EventMeta extends StatelessWidget {
  const _EventMeta({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: palette.muted),
        const SizedBox(width: 4),
        Text(label, style: Theme.of(context).textTheme.bodyMedium),
      ],
    );
  }
}

class _FlowTab {
  const _FlowTab(this.stage, this.label, this.keywords, this.icon);

  final int stage;
  final String label;
  final List<String> keywords;
  final IconData icon;
}
