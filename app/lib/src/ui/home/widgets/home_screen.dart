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
      title: '智能兔管家',
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

  static const _tabs = [
    _FlowTab('配种', ['配种']),
    _FlowTab('摸胎', ['摸胎']),
    _FlowTab('备产', ['备产']),
    _FlowTab('生产情况', ['分娩', '生产']),
    _FlowTab('断奶', ['断奶']),
    _FlowTab('出售', ['出售']),
    _FlowTab('后备兔', ['后备兔', '后备成熟']),
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _tabs.length, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final events = widget.events;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _AlertHeader(events: events),
        const SizedBox(height: 18),
        _FlowTabs(tabController: _tabController, tabs: _tabs, events: events),
        SizedBox(
          height: 420,
          child: TabBarView(
            controller: _tabController,
            children: [
              for (final tab in _tabs)
                _FlowPanel(
                  title: tab.label,
                  events: _eventsFor(tab, events),
                  onEventTap: _handleEventTap,
                ),
            ],
          ),
        ),
      ],
    );
  }

  static List<EventItem> _eventsFor(_FlowTab tab, List<EventItem> events) {
    return events.where((event) {
      if (tab.label == '后备兔') {
        return event.isReplacement;
      }
      if (tab.label == '生产情况') {
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
    final upcoming = events.length - overdue - due;
    final palette = AppPalette.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Expanded(
              child: Text(
                '今日预警!',
                style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                      color: palette.primary,
                      fontWeight: FontWeight.w700,
                      fontSize: 32,
                    ),
              ),
            ),
            _AlertCountBadge(count: events.length),
          ],
        ),
        const SizedBox(height: 14),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            _StatusChip(
              label: '逾期',
              count: overdue,
              color: palette.danger,
              background: palette.dangerSoft,
            ),
            _StatusChip(
              label: '到期',
              count: due,
              color: palette.primary,
              background: palette.primarySoft,
            ),
            _StatusChip(
              label: '未到期',
              count: upcoming < 0 ? 0 : upcoming,
              color: palette.success,
              background: palette.successSoft,
            ),
          ],
        ),
      ],
    );
  }
}

class _AlertCountBadge extends StatelessWidget {
  const _AlertCountBadge({required this.count});

  final int count;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: count > 0 ? palette.text : palette.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Text(
        '$count 条',
        style: TextStyle(
          color: count > 0 ? palette.background : palette.muted,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({
    required this.label,
    required this.count,
    required this.color,
    required this.background,
  });

  final String label;
  final int count;
  final Color color;
  final Color background;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 8),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Text(
        '$label $count',
        style: TextStyle(color: color, fontWeight: FontWeight.w800),
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
    return Container(
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: palette.line)),
      ),
      child: TabBar(
        controller: tabController,
        isScrollable: true,
        labelColor: palette.text,
        unselectedLabelColor: palette.muted,
        indicatorColor: palette.danger,
        indicatorWeight: 3,
        tabAlignment: TabAlignment.start,
        labelStyle: const TextStyle(
          fontSize: 16,
          fontWeight: FontWeight.w900,
        ),
        unselectedLabelStyle: const TextStyle(
          fontSize: 16,
          fontWeight: FontWeight.w700,
        ),
        tabs: [
          for (final tab in tabs)
            Tab(
              child: Row(
                children: [
                  Text(tab.label),
                  if (_HomeContentState._eventsFor(tab, events).isNotEmpty) ...[
                    const SizedBox(width: 6),
                    _TinyCount(
                      count: _HomeContentState._eventsFor(tab, events).length,
                    ),
                  ],
                ],
              ),
            ),
        ],
      ),
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
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: palette.dangerSoft,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        '$count',
        style: TextStyle(
          color: palette.danger,
          fontSize: 11,
          fontWeight: FontWeight.w900,
        ),
      ),
    );
  }
}

class _FlowPanel extends StatelessWidget {
  const _FlowPanel({
    required this.title,
    required this.events,
    required this.onEventTap,
  });

  final String title;
  final List<EventItem> events;
  final Future<void> Function(EventItem event) onEventTap;

  @override
  Widget build(BuildContext context) {
    if (events.isEmpty) {
      final message = title == '配种'
          ? '创建兔舍 → 笼位录入种母兔 → 兔舍详情创建生产批次 → 系统将提醒配种'
          : title == '断奶'
              ? '创建兔舍后，在笼位管理点击空笼录入种兔；'
                  '分娩后到期提醒会出现在此，点击可断奶并放入商品兔笼。'
              : '创建兔舍-添加兔子-创建批次-自动提醒配种.摸胎.备产.断奶';
      return EmptyState(
        icon: Icons.inventory_2_outlined,
        title: '系统使用步骤',
        message: message,
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

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final color = event.isOverdue
        ? palette.danger
        : event.isDue
            ? palette.primary
            : palette.success;

    return SectionCard(
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: _actionable ? onTap : null,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: color.withOpacity(0.1),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(Icons.notifications_active_outlined, color: color),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
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
                      Text(
                        event.statusLabel,
                        style: TextStyle(
                          color: color,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    '${event.houseLabel} · ${event.dateLabel} · ${event.targetLabel}',
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (_actionable) ...[
                    const SizedBox(height: 8),
                    Text(
                      _actionHint,
                      style: TextStyle(
                        color: palette.primary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            if (_actionable) Icon(Icons.chevron_right, color: palette.muted),
          ],
        ),
      ),
    );
  }
}

class _FlowTab {
  const _FlowTab(this.label, this.keywords);

  final String label;
  final List<String> keywords;
}
