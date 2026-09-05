import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/domain/reports/dashboard.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/dashboard/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';

const _uninitializedDashboardHouseId = -1;

final _selectedDashboardHouseProvider = StateProvider<int?>(
  (ref) => _uninitializedDashboardHouseId,
);
final _selectedDashboardBatchProvider = StateProvider<int?>((ref) => null);
final _selectedDashboardYearProvider = StateProvider<int>(
  (ref) => DateTime.now().year,
);

RabbitHouse? _findHouse(List<RabbitHouse> houses, int? houseId) {
  if (houseId == null) {
    return null;
  }
  for (final house in houses) {
    if (house.id == houseId) {
      return house;
    }
  }
  return null;
}

RabbitHouse? _resolveDashboardHouse(
  List<RabbitHouse> houses,
  int? selectedHouseId,
  int? preferredHouseId,
) {
  if (selectedHouseId == null) {
    return null;
  }
  if (selectedHouseId != _uninitializedDashboardHouseId) {
    return _findHouse(houses, selectedHouseId);
  }
  return _findHouse(houses, preferredHouseId) ??
      (houses.isEmpty ? null : houses.first);
}

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final houses = ref.watch(housesProvider);
    final selectedHouseId = ref.watch(_selectedDashboardHouseProvider);
    final preferredHouseId =
        ref.watch(authControllerProvider).valueOrNull?.houseId;
    final selectedBatchId = ref.watch(_selectedDashboardBatchProvider);
    final selectedYear = ref.watch(_selectedDashboardYearProvider);
    final palette = AppPalette.of(context);

    return AppPage(
      title: '数据面板',
      actions: [
        IconButton(
          tooltip: '刷新',
          onPressed: () => _refresh(ref),
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: houses.when(
        data: (items) {
          if (items.isEmpty) {
            return const EmptyState(
              icon: Icons.storefront_outlined,
              title: '尚未加入兔舍',
              message: '创建兔舍或接受手机号邀请后，这里会汇总可访问兔舍的生产数据。',
            );
          }
          final selectedHouse = _resolveDashboardHouse(
            items,
            selectedHouseId,
            preferredHouseId,
          );
          final effectiveHouseId = selectedHouse?.id;
          final effectiveBatchId =
              effectiveHouseId == null ? null : selectedBatchId;
          final query = (
            houseId: effectiveHouseId,
            batchId: effectiveBatchId,
            year: selectedYear,
          );
          final summary = ref.watch(dashboardSummaryProvider(query));
          final batches = effectiveHouseId == null
              ? null
              : ref.watch(houseBatchesProvider(effectiveHouseId));

          return RefreshIndicator(
            onRefresh: () => _refresh(ref),
            child: ListView(
              padding: AppSpacing.pagePadding,
              children: [
                _DashboardFilters(
                  houses: items,
                  selectedHouseId: effectiveHouseId,
                  selectedBatchId: effectiveBatchId,
                  batches: batches,
                  onHouseChanged: (houseId) {
                    ref.read(_selectedDashboardBatchProvider.notifier).state =
                        null;
                    ref.read(_selectedDashboardHouseProvider.notifier).state =
                        houseId;
                  },
                  onBatchChanged: (batchId) {
                    ref.read(_selectedDashboardBatchProvider.notifier).state =
                        batchId;
                  },
                  onRetryBatches: effectiveHouseId == null
                      ? null
                      : () => ref.invalidate(
                            houseBatchesProvider(effectiveHouseId),
                          ),
                ),
                const SizedBox(height: 12),
                _DashboardScopeBanner(
                  houses: items,
                  selectedHouse: selectedHouse,
                  selectedBatchId: effectiveBatchId,
                  batches: batches,
                  year: selectedYear,
                  onViewBatch:
                      effectiveHouseId == null || effectiveBatchId == null
                          ? null
                          : () => context.push(
                                '/houses/$effectiveHouseId/batches/$effectiveBatchId',
                              ),
                ),
                const SizedBox(height: 12),
                ...summary.when(
                  data: (value) {
                    final data = _DashboardPanelData(summary: value);
                    return [
                      _DashboardHero(stats: data.stats),
                      const SizedBox(height: 18),
                      _MetricGrid(metrics: data.metrics),
                      const SizedBox(height: 26),
                      _SectionTitle(
                        year: selectedYear,
                        onYearChanged: (year) {
                          ref
                              .read(_selectedDashboardYearProvider.notifier)
                              .state = year;
                        },
                      ),
                      const SizedBox(height: 12),
                      _MonthlyChartCard(
                        title: '每月出生兔子数量',
                        color: palette.primary,
                        values: data.monthlyBirths,
                      ),
                      const SizedBox(height: 22),
                      Text(
                        '$selectedYear年每月满月兔子数量',
                        style: Theme.of(context)
                            .textTheme
                            .headlineMedium
                            ?.copyWith(
                              color: palette.primary,
                              fontSize: 22,
                              fontWeight: FontWeight.w800,
                            ),
                      ),
                      const SizedBox(height: 12),
                      _MonthlyChartCard(
                        title: '每月满月兔子数量',
                        color: palette.success,
                        values: data.monthlyWeaned,
                      ),
                    ];
                  },
                  loading: () => const [
                    SizedBox(
                      height: 180,
                      child: Center(child: CircularProgressIndicator()),
                    ),
                  ],
                  error: (error, _) => [
                    ErrorState(
                      message: error.toString(),
                      onRetry: () =>
                          ref.invalidate(dashboardSummaryProvider(query)),
                    ),
                  ],
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

  Future<void> _refresh(WidgetRef ref) async {
    final houses =
        ref.read(housesProvider).valueOrNull ?? const <RabbitHouse>[];
    final selectedHouse = _resolveDashboardHouse(
      houses,
      ref.read(_selectedDashboardHouseProvider),
      ref.read(authControllerProvider).valueOrNull?.houseId,
    );
    final houseId = selectedHouse?.id;
    final batchId =
        houseId == null ? null : ref.read(_selectedDashboardBatchProvider);
    final query = (
      houseId: houseId,
      batchId: batchId,
      year: ref.read(_selectedDashboardYearProvider),
    );

    ref.invalidate(housesProvider);
    if (houseId != null) {
      ref.invalidate(houseBatchesProvider(houseId));
    }
    ref.invalidate(dashboardSummaryProvider(query));
    await ref.read(dashboardSummaryProvider(query).future);
  }
}

class _DashboardPanelData {
  const _DashboardPanelData({required this.summary});

  final DashboardSummary summary;

  _RabbitStats get stats => _RabbitStats.fromSummary(summary);

  List<_MetricItem> get metrics {
    final s = stats;
    return [
      _MetricItem('种兔数量', s.seedRabbits, Icons.cruelty_free_outlined),
      _MetricItem('公兔数量', s.males, Icons.male_rounded),
      _MetricItem('母兔数量', s.females, Icons.female_rounded),
      _MetricItem('繁殖周期中', s.bred, Icons.timeline_rounded),
      _MetricItem('未在周期中', s.readyForBreeding, Icons.event_available_outlined),
      _MetricItem('已分娩窝数', s.litters, Icons.child_care_outlined),
      _MetricItem('哺乳期数量', s.nursingKits, Icons.local_drink_outlined),
      _MetricItem('商品兔数量', s.commodityRabbits, Icons.inventory_2_outlined),
      _MetricItem('后备兔数量', s.replacementRabbits, Icons.spa_outlined),
    ];
  }

  List<int> get monthlyBirths => summary.monthlyBirths;

  List<int> get monthlyWeaned => summary.monthlyWeaned;
}

class _RabbitStats {
  const _RabbitStats({
    required this.total,
    required this.seedRabbits,
    required this.males,
    required this.females,
    required this.bred,
    required this.readyForBreeding,
    required this.litters,
    required this.nursingKits,
    required this.commodityRabbits,
    required this.replacementRabbits,
    required this.liveRate,
  });

  final int total;
  final int seedRabbits;
  final int males;
  final int females;
  final int bred;
  final int readyForBreeding;
  final int litters;
  final int nursingKits;
  final int commodityRabbits;
  final int replacementRabbits;
  final double liveRate;

  factory _RabbitStats.fromSummary(DashboardSummary summary) {
    return _RabbitStats(
      total: summary.totalRabbits,
      seedRabbits: summary.seedRabbits,
      males: summary.maleRabbits,
      females: summary.femaleRabbits,
      bred: summary.bredRabbits,
      readyForBreeding: summary.readyForBreeding,
      litters: summary.litters,
      nursingKits: summary.nursingKits,
      commodityRabbits: summary.commodityRabbits,
      replacementRabbits: summary.replacementRabbits,
      liveRate: summary.liveRate,
    );
  }
}

class _DashboardFilters extends StatelessWidget {
  const _DashboardFilters({
    required this.houses,
    required this.selectedHouseId,
    required this.selectedBatchId,
    required this.batches,
    required this.onHouseChanged,
    required this.onBatchChanged,
    required this.onRetryBatches,
  });

  final List<RabbitHouse> houses;
  final int? selectedHouseId;
  final int? selectedBatchId;
  final AsyncValue<List<Batch>>? batches;
  final ValueChanged<int?> onHouseChanged;
  final ValueChanged<int?> onBatchChanged;
  final VoidCallback? onRetryBatches;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final house = _SelectorField(
            label: '兔舍范围',
            child: _selectorShell(
              context,
              DropdownButton<int>(
                key: const ValueKey('dashboard-house-selector'),
                value: selectedHouseId ?? 0,
                isExpanded: true,
                items: [
                  const DropdownMenuItem(value: 0, child: Text('全部兔舍')),
                  for (final house in houses)
                    DropdownMenuItem(
                      value: house.id,
                      child: Text(
                        house.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                ],
                onChanged: (value) =>
                    onHouseChanged(value == null || value == 0 ? null : value),
              ),
            ),
          );
          final batch = _SelectorField(
            label: '批次范围',
            child: _buildBatchSelector(context),
          );

          if (constraints.maxWidth >= 560) {
            return Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: house),
                const SizedBox(width: 12),
                Expanded(child: batch),
              ],
            );
          }
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              house,
              const SizedBox(height: 12),
              batch,
            ],
          );
        },
      ),
    );
  }

  Widget _buildBatchSelector(BuildContext context) {
    if (selectedHouseId == null) {
      return _statusBatchSelector(context, '选择单一兔舍后可选批次');
    }
    final state = batches;
    if (state == null) {
      return _statusBatchSelector(context, '正在加载批次');
    }
    return state.when(
      data: (items) {
        if (items.isEmpty) {
          return _statusBatchSelector(context, '当前兔舍暂无批次');
        }
        final selectedExists = selectedBatchId != null &&
            items.any((batch) => batch.id == selectedBatchId);
        return _selectorShell(
          context,
          DropdownButton<int>(
            key: const ValueKey('dashboard-batch-selector'),
            value: selectedBatchId ?? 0,
            isExpanded: true,
            items: [
              const DropdownMenuItem(value: 0, child: Text('全部批次')),
              if (selectedBatchId != null && !selectedExists)
                DropdownMenuItem(
                  value: selectedBatchId,
                  child: Text('批次 #$selectedBatchId'),
                ),
              for (final batch in items)
                DropdownMenuItem(
                  value: batch.id,
                  child: Text(
                    batch.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
            ],
            onChanged: (value) =>
                onBatchChanged(value == null || value == 0 ? null : value),
          ),
        );
      },
      loading: () => _statusBatchSelector(context, '正在加载批次'),
      error: (_, __) => _selectorShell(
        context,
        Row(
          children: [
            const Expanded(
              child: Text(
                '批次加载失败',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            IconButton(
              tooltip: '重新加载批次',
              onPressed: onRetryBatches,
              icon: const Icon(Icons.refresh_rounded),
            ),
          ],
        ),
      ),
    );
  }

  Widget _statusBatchSelector(BuildContext context, String text) {
    return _selectorShell(
      context,
      DropdownButton<int>(
        key: const ValueKey('dashboard-batch-selector'),
        value: 0,
        isExpanded: true,
        items: [DropdownMenuItem(value: 0, child: Text(text))],
        onChanged: null,
      ),
    );
  }

  Widget _selectorShell(BuildContext context, Widget child) {
    final palette = AppPalette.of(context);
    return Container(
      constraints: const BoxConstraints(minHeight: 52),
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: DropdownButtonHideUnderline(child: child),
    );
  }
}

class _SelectorField extends StatelessWidget {
  const _SelectorField({required this.label, required this.child});

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(height: 6),
        child,
      ],
    );
  }
}

class _DashboardScopeBanner extends StatelessWidget {
  const _DashboardScopeBanner({
    required this.houses,
    required this.selectedHouse,
    required this.selectedBatchId,
    required this.batches,
    required this.year,
    required this.onViewBatch,
  });

  final List<RabbitHouse> houses;
  final RabbitHouse? selectedHouse;
  final int? selectedBatchId;
  final AsyncValue<List<Batch>>? batches;
  final int year;
  final VoidCallback? onViewBatch;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final selectedBatch = selectedBatchId == null
        ? null
        : _findBatch(batches?.valueOrNull ?? const <Batch>[], selectedBatchId!);
    final scope = selectedHouse == null
        ? '全部兔舍 / $year年'
        : '${selectedHouse!.name} / '
            '${selectedBatchId == null ? '全部批次' : selectedBatch?.title ?? '批次 #$selectedBatchId'} / '
            '$year年';
    final description = selectedHouse == null
        ? '已汇总 ${houses.length} 个兔舍'
        : selectedBatchId == null
            ? '统计该兔舍的全部生产数据'
            : '统计所选批次的生产数据';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: selectedHouse == null
                  ? palette.primarySoft
                  : palette.successSoft,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(
              selectedHouse == null
                  ? Icons.grid_view_rounded
                  : Icons.filter_alt_outlined,
              color: selectedHouse == null ? palette.primary : palette.success,
              size: 20,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '当前统计范围：$scope',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                ),
                const SizedBox(height: 2),
                Text(
                  description,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: palette.muted,
                      ),
                ),
                if (onViewBatch != null) ...[
                  const SizedBox(height: 8),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton.icon(
                      key: const ValueKey('dashboard-view-batch-statistics'),
                      onPressed: onViewBatch,
                      icon: const Icon(Icons.analytics_outlined),
                      label: const Text('查看完整批次统计'),
                    ),
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

Batch? _findBatch(List<Batch> batches, int batchId) {
  for (final batch in batches) {
    if (batch.id == batchId) {
      return batch;
    }
  }
  return null;
}

class _DashboardHero extends StatelessWidget {
  const _DashboardHero({required this.stats});

  final _RabbitStats stats;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          RichText(
            text: TextSpan(
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontSize: 24,
                    fontWeight: FontWeight.w900,
                  ),
              children: [
                TextSpan(
                  text: '兔群统计',
                  style: TextStyle(color: palette.primary),
                ),
                TextSpan(
                  text: '数据',
                  style: TextStyle(color: palette.success),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              _HeroNumber(label: '在养兔只', value: stats.total),
              const SizedBox(width: 14),
              _HeroNumber(
                label: '仔兔成活率',
                valueText: '${(stats.liveRate * 100).round()}%',
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _HeroNumber extends StatelessWidget {
  const _HeroNumber({
    required this.label,
    this.value,
    this.valueText,
  });

  final String label;
  final int? value;
  final String? valueText;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: palette.surfaceSubtle,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: palette.line),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: 6),
            Text(
              valueText ?? '${value ?? 0}',
              key: ValueKey('dashboard-hero-value-$label'),
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    color: palette.primary,
                    fontSize: 26,
                  ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MetricGrid extends StatelessWidget {
  const _MetricGrid({required this.metrics});

  final List<_MetricItem> metrics;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final textScale = MediaQuery.textScalerOf(context).scale(10) / 10;
        final columns = textScale >= 1.3 || constraints.maxWidth < 360 ? 2 : 3;
        final tileExtent = _metricTileExtent(
          context,
          availableWidth: constraints.maxWidth,
          columns: columns,
          metrics: metrics,
        );
        return GridView.builder(
          key: const ValueKey('dashboard-metric-grid'),
          itemCount: metrics.length,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: columns,
            crossAxisSpacing: 12,
            mainAxisSpacing: 12,
            mainAxisExtent: tileExtent,
          ),
          itemBuilder: (context, index) {
            final item = metrics[index];
            return _MetricCard(
              key: ValueKey('dashboard-metric-card-${item.label}'),
              item: item,
            );
          },
        );
      },
    );
  }

  double _metricTileExtent(
    BuildContext context, {
    required double availableWidth,
    required int columns,
    required List<_MetricItem> metrics,
  }) {
    const gridGap = 12.0;
    const cardChromeWidth = 30.0;
    const cardChromeHeight = 30.0;
    const iconHeight = 22.0;
    const iconToLabelGap = 12.0;
    const labelToValueGap = 6.0;

    final tileWidth = (availableWidth - gridGap * (columns - 1)) / columns;
    final contentWidth = math.max(1.0, tileWidth - cardChromeWidth);
    final textScaler = MediaQuery.textScalerOf(context);
    final textDirection = Directionality.of(context);
    final labelStyle = Theme.of(context).textTheme.labelLarge?.copyWith(
          fontSize: 13,
        );
    final valueStyle = Theme.of(context).textTheme.headlineMedium?.copyWith(
          fontSize: 24,
          fontWeight: FontWeight.w900,
        );

    double textHeight(
      String text,
      TextStyle? style, {
      required int maxLines,
    }) {
      final painter = TextPainter(
        text: TextSpan(text: text, style: style),
        maxLines: maxLines,
        ellipsis: '...',
        textDirection: textDirection,
        textScaler: textScaler,
      )..layout(maxWidth: contentWidth);
      return painter.height;
    }

    final labelHeight = metrics.fold<double>(
      0,
      (height, item) => math.max(
        height,
        textHeight(item.label, labelStyle, maxLines: 2),
      ),
    );
    final valueHeight = metrics.fold<double>(
      0,
      (height, item) => math.max(
        height,
        textHeight('${item.value}', valueStyle, maxLines: 1),
      ),
    );
    final contentHeight = cardChromeHeight +
        iconHeight +
        iconToLabelGap +
        labelHeight +
        labelToValueGap +
        valueHeight;

    return math.max(140.0, contentHeight.ceilToDouble());
  }
}

class _MetricItem {
  const _MetricItem(this.label, this.value, this.icon);

  final String label;
  final int value;
  final IconData icon;
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({super.key, required this.item});

  final _MetricItem item;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: palette.line),
        boxShadow: [
          BoxShadow(
            color: palette.shadow,
            blurRadius: 14,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(item.icon, color: palette.muted, size: 22),
          const Spacer(),
          Text(
            item.label,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: palette.muted,
                  fontSize: 13,
                ),
          ),
          const SizedBox(height: 6),
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text(
              '${item.value}',
              maxLines: 1,
              softWrap: false,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    color: palette.primary,
                    fontSize: 24,
                    fontWeight: FontWeight.w900,
                  ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  const _SectionTitle({
    required this.year,
    required this.onYearChanged,
  });

  final int year;
  final ValueChanged<int> onYearChanged;

  @override
  Widget build(BuildContext context) {
    final years = [DateTime.now().year - 1, DateTime.now().year];
    final palette = AppPalette.of(context);
    final largeText = MediaQuery.textScalerOf(context).scale(10) / 10 >= 1.3;
    final title = Text(
      '$year年每月出生兔子数量',
      style: Theme.of(context).textTheme.headlineMedium?.copyWith(
            color: palette.primary,
            fontSize: 22,
            fontWeight: FontWeight.w800,
          ),
    );
    final yearPicker = Container(
      constraints: const BoxConstraints(minHeight: 48),
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<int>(
          key: const ValueKey('dashboard-year-selector'),
          value: year,
          icon: const Icon(Icons.keyboard_arrow_down_rounded),
          items: [
            for (final item in years)
              DropdownMenuItem(value: item, child: Text('$item年')),
          ],
          onChanged: (value) {
            if (value != null) {
              onYearChanged(value);
            }
          },
        ),
      ),
    );

    if (largeText) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          title,
          const SizedBox(height: 8),
          Align(alignment: Alignment.centerRight, child: yearPicker),
        ],
      );
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(child: title),
        const SizedBox(width: 10),
        yearPicker,
      ],
    );
  }
}

class _MonthlyChartCard extends StatelessWidget {
  const _MonthlyChartCard({
    required this.title,
    required this.values,
    required this.color,
  });

  final String title;
  final List<int> values;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      height: 270,
      padding: const EdgeInsets.fromLTRB(10, 12, 10, 10),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        children: [
          Expanded(
            child: Stack(
              children: [
                Positioned.fill(
                  child: CustomPaint(
                    painter: _MonthChartPainter(
                      values: values,
                      color: color,
                      axisColor: palette.muted,
                      guideColor: palette.line,
                      labelColor: palette.muted,
                    ),
                  ),
                ),
                if (values.every((value) => value == 0))
                  Center(
                    child: Text(
                      '暂无月度明细',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 4),
          Text(
            title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: palette.muted,
                ),
          ),
        ],
      ),
    );
  }
}

class _MonthChartPainter extends CustomPainter {
  const _MonthChartPainter({
    required this.values,
    required this.color,
    required this.axisColor,
    required this.guideColor,
    required this.labelColor,
  });

  final List<int> values;
  final Color color;
  final Color axisColor;
  final Color guideColor;
  final Color labelColor;

  @override
  void paint(Canvas canvas, Size size) {
    const left = 10.0;
    const right = 6.0;
    const top = 12.0;
    const bottom = 30.0;
    final chartWidth = size.width - left - right;
    final chartHeight = size.height - top - bottom;
    final axisY = top + chartHeight;

    final axisPaint = Paint()
      ..color = axisColor
      ..strokeWidth = 1.1;
    final guidePaint = Paint()
      ..color = guideColor
      ..strokeWidth = 0.8;

    for (var i = 0; i < 3; i++) {
      final y = top + chartHeight * i / 3;
      canvas.drawLine(
          Offset(left, y), Offset(left + chartWidth, y), guidePaint);
    }
    canvas.drawLine(
      Offset(left, axisY),
      Offset(left + chartWidth, axisY),
      axisPaint,
    );

    final maxValue = values.fold<int>(0, math.max);
    final step = chartWidth / 12;
    final barPaint = Paint()..color = color;

    for (var i = 0; i < 12; i++) {
      final x = left + step * i + step / 2;
      canvas.drawLine(Offset(x, axisY), Offset(x, axisY + 5), axisPaint);
      final label = TextPainter(
        text: TextSpan(
          text: '${i + 1}',
          style: TextStyle(
            color: labelColor,
            fontSize: 11,
            fontWeight: FontWeight.w700,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      label.paint(canvas, Offset(x - label.width / 2, axisY + 9));

      final value = i < values.length ? values[i] : 0;
      if (value <= 0 || maxValue <= 0) {
        continue;
      }
      final height = chartHeight * value / maxValue;
      final rect = RRect.fromRectAndRadius(
        Rect.fromLTWH(x - 5, axisY - height, 10, height),
        const Radius.circular(5),
      );
      canvas.drawRRect(rect, barPaint);
    }
  }

  @override
  bool shouldRepaint(covariant _MonthChartPainter oldDelegate) {
    return oldDelegate.values != values ||
        oldDelegate.color != color ||
        oldDelegate.axisColor != axisColor ||
        oldDelegate.guideColor != guideColor ||
        oldDelegate.labelColor != labelColor;
  }
}
