import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/report_repository.dart';
import 'package:rabbit_flutter/src/domain/models/batch.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';

final _selectedDashboardHouseProvider = StateProvider<int?>((ref) => null);

final _dashboardPanelProvider =
    FutureProvider<_DashboardPanelData>((ref) async {
  final selectedHouseId = ref.watch(_selectedDashboardHouseProvider);
  final houses = await ref.watch(housesProvider.future);
  final selectedHouse = _findHouse(houses, selectedHouseId);
  final effectiveSelectedHouseId = selectedHouse?.id;
  final visibleHouses =
      selectedHouse == null ? houses : <RabbitHouse>[selectedHouse];
  if (visibleHouses.isEmpty) {
    return _DashboardPanelData.empty(
      selectedHouseId: effectiveSelectedHouseId,
      houses: houses,
    );
  }

  final reports = await Future.wait([
    for (final house in visibleHouses)
      ref.watch(houseReportProvider(house.id).future),
  ]);
  final rabbitsByHouse = await Future.wait([
    for (final house in visibleHouses)
      ref.watch(allActiveHouseRabbitsProvider(house.id).future),
  ]);
  final batchesByHouse = await Future.wait([
    for (final house in visibleHouses)
      ref.watch(houseBatchesProvider(house.id).future),
  ]);

  return _DashboardPanelData(
    selectedHouseId: effectiveSelectedHouseId,
    houses: houses,
    report: DashboardReport.sum(reports),
    rabbits: rabbitsByHouse.expand((items) => items).toList(),
    batches: batchesByHouse.expand((items) => items).toList(),
  );
});

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

class DashboardScreen extends ConsumerStatefulWidget {
  const DashboardScreen({super.key});

  @override
  ConsumerState<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends ConsumerState<DashboardScreen> {
  late int _selectedYear = DateTime.now().year;

  @override
  Widget build(BuildContext context) {
    final panel = ref.watch(_dashboardPanelProvider);
    final selectedHouseId = ref.watch(_selectedDashboardHouseProvider);
    final palette = AppPalette.of(context);

    return AppPage(
      title: '数据面板',
      actions: [
        panel.maybeWhen(
          data: (data) => _HouseFilterMenu(
            houses: data.houses,
            selectedHouseId: selectedHouseId,
            onChanged: (houseId) {
              ref.read(_selectedDashboardHouseProvider.notifier).state =
                  houseId;
            },
          ),
          orElse: () => const SizedBox.shrink(),
        ),
        IconButton(
          tooltip: '刷新',
          onPressed: () => _refresh(ref),
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: panel.when(
        data: (data) {
          if (data.houses.isEmpty) {
            return const EmptyState(
              icon: Icons.storefront_outlined,
              title: '暂无兔舍',
              message: '创建兔舍后，这里会默认汇总所有兔舍的兔群统计和繁殖数据。',
            );
          }
          return RefreshIndicator(
            onRefresh: () => _refresh(ref),
            child: ListView(
              padding: AppSpacing.pagePadding,
              children: [
                _DashboardScopeBanner(data: data),
                const SizedBox(height: 12),
                _DashboardHero(stats: data.stats),
                const SizedBox(height: 18),
                _MetricGrid(metrics: data.metrics),
                const SizedBox(height: 26),
                _SectionTitle(
                  year: _selectedYear,
                  onYearChanged: (year) {
                    setState(() => _selectedYear = year);
                  },
                ),
                const SizedBox(height: 12),
                _MonthlyChartCard(
                  title: '每月出生兔子数量',
                  color: palette.primary,
                  values: data.monthlyBirths(_selectedYear),
                ),
                const SizedBox(height: 22),
                Text(
                  '$_selectedYear年每月满月兔子数量',
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        color: palette.primary,
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                ),
                const SizedBox(height: 12),
                _MonthlyChartCard(
                  title: '每月满月兔子数量',
                  color: palette.success,
                  values: data.monthlyWeaned(_selectedYear),
                ),
              ],
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(_dashboardPanelProvider),
        ),
      ),
    );
  }

  Future<void> _refresh(WidgetRef ref) async {
    final data = ref.read(_dashboardPanelProvider).valueOrNull;
    ref.invalidate(_dashboardPanelProvider);
    ref.invalidate(housesProvider);
    if (data != null) {
      for (final house in data.visibleHouses) {
        ref.invalidate(houseReportProvider(house.id));
        ref.invalidate(houseBatchesProvider(house.id));
        ref.invalidate(houseRabbitsProvider(house.id));
        ref.invalidate(allActiveHouseRabbitsProvider(house.id));
      }
    }
    await ref.read(_dashboardPanelProvider.future);
  }
}

class _DashboardPanelData {
  const _DashboardPanelData({
    required this.selectedHouseId,
    required this.houses,
    required this.report,
    required this.rabbits,
    required this.batches,
  });

  final int? selectedHouseId;
  final List<RabbitHouse> houses;
  final DashboardReport report;
  final List<Rabbit> rabbits;
  final List<Batch> batches;

  factory _DashboardPanelData.empty({
    required int? selectedHouseId,
    required List<RabbitHouse> houses,
  }) {
    return _DashboardPanelData(
      selectedHouseId: selectedHouseId,
      houses: houses,
      report: DashboardReport.empty(),
      rabbits: const [],
      batches: const [],
    );
  }

  bool get isAllHouses => selectedHouseId == null;

  List<RabbitHouse> get visibleHouses {
    if (selectedHouseId == null) {
      return houses;
    }
    return houses.where((house) => house.id == selectedHouseId).toList();
  }

  String get scopeTitle {
    if (isAllHouses) {
      return '全部兔舍';
    }
    final visible = visibleHouses;
    return visible.isEmpty ? '已选择兔舍' : visible.first.name;
  }

  String get scopeDescription {
    if (isAllHouses) {
      return '已汇总 ${houses.length} 个兔舍';
    }
    return '仅显示当前选择的兔舍';
  }

  _RabbitStats get stats => _RabbitStats.from(rabbits, batches, report);

  List<_MetricItem> get metrics {
    final s = stats;
    return [
      _MetricItem('种兔数量', s.seedRabbits, Icons.cruelty_free_outlined),
      _MetricItem('公兔数量', s.males, Icons.male_rounded),
      _MetricItem('母兔数量', s.females, Icons.female_rounded),
      _MetricItem('已配种数量', s.bred, Icons.timeline_rounded),
      _MetricItem('待配种数量', s.readyForBreeding, Icons.event_available_outlined),
      _MetricItem('已分娩窝数', s.litters, Icons.child_care_outlined),
      _MetricItem('哺乳期数量', s.nursingKits, Icons.local_drink_outlined),
      _MetricItem('商品兔数量', s.commodityRabbits, Icons.inventory_2_outlined),
      _MetricItem('后备兔数量', s.replacementRabbits, Icons.spa_outlined),
    ];
  }

  List<int> monthlyBirths(int year) => List<int>.filled(12, 0);

  List<int> monthlyWeaned(int year) => List<int>.filled(12, 0);
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

  factory _RabbitStats.from(
    List<Rabbit> rabbits,
    List<Batch> batches,
    DashboardReport report,
  ) {
    final activeRabbits = rabbits.where((rabbit) => rabbit.isActive).toList();
    final seedRabbits =
        activeRabbits.where((rabbit) => rabbit.type == '0').length;
    final femaleSeedRabbits = activeRabbits
        .where((rabbit) => rabbit.type == '0' && rabbit.gender == '0')
        .length;
    final bred = math.max(report.breeding.successBreedingCount, batches.length);
    final nursing = math.max(
        report.breeding.totalLiveKits - report.breeding.totalWeaned, 0);

    return _RabbitStats(
      total: activeRabbits.length,
      seedRabbits: seedRabbits,
      males: activeRabbits.where((rabbit) => rabbit.gender == '1').length,
      females: activeRabbits.where((rabbit) => rabbit.gender == '0').length,
      bred: bred,
      readyForBreeding: math.max(femaleSeedRabbits - bred, 0),
      litters: report.breeding.totalLitters,
      nursingKits: nursing,
      commodityRabbits:
          activeRabbits.where((rabbit) => rabbit.type == '2').length,
      replacementRabbits:
          activeRabbits.where((rabbit) => rabbit.type == '1').length,
      liveRate: report.breeding.liveRate,
    );
  }
}

class _HouseFilterMenu extends StatelessWidget {
  const _HouseFilterMenu({
    required this.houses,
    required this.selectedHouseId,
    required this.onChanged,
  });

  final List<RabbitHouse> houses;
  final int? selectedHouseId;
  final ValueChanged<int?> onChanged;

  @override
  Widget build(BuildContext context) {
    final selectedValue = selectedHouseId ?? 0;
    return PopupMenuButton<int>(
      tooltip: '选择兔舍',
      enabled: houses.isNotEmpty,
      initialValue: selectedValue,
      onSelected: (value) => onChanged(value == 0 ? null : value),
      itemBuilder: (context) => [
        PopupMenuItem<int>(
          value: 0,
          child: _HouseFilterMenuItem(
            selected: selectedHouseId == null,
            title: '全部兔舍',
          ),
        ),
        for (final house in houses)
          PopupMenuItem<int>(
            value: house.id,
            child: _HouseFilterMenuItem(
              selected: selectedHouseId == house.id,
              title: house.name,
            ),
          ),
      ],
      icon: const Icon(Icons.filter_list_rounded),
    );
  }
}

class _HouseFilterMenuItem extends StatelessWidget {
  const _HouseFilterMenuItem({
    required this.selected,
    required this.title,
  });

  final bool selected;
  final String title;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(
          selected ? Icons.check_rounded : Icons.storefront_outlined,
          size: 20,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );
  }
}

class _DashboardScopeBanner extends StatelessWidget {
  const _DashboardScopeBanner({required this.data});

  final _DashboardPanelData data;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: palette.line),
      ),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color:
                  data.isAllHouses ? palette.primarySoft : palette.successSoft,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(
              data.isAllHouses
                  ? Icons.grid_view_rounded
                  : Icons.storefront_outlined,
              color: data.isAllHouses ? palette.primary : palette.success,
              size: 20,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  data.scopeTitle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                ),
                const SizedBox(height: 2),
                Text(
                  data.scopeDescription,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: palette.muted,
                      ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
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
        final columns = constraints.maxWidth < 360 ? 2 : 3;
        return GridView.builder(
          itemCount: metrics.length,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: columns,
            crossAxisSpacing: 12,
            mainAxisSpacing: 12,
            childAspectRatio: columns == 3 ? 0.92 : 1.18,
          ),
          itemBuilder: (context, index) => _MetricCard(item: metrics[index]),
        );
      },
    );
  }
}

class _MetricItem {
  const _MetricItem(this.label, this.value, this.icon);

  final String label;
  final int value;
  final IconData icon;
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({required this.item});

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
          Text(
            '${item.value}',
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                  color: palette.primary,
                  fontSize: 24,
                  fontWeight: FontWeight.w900,
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

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(
          child: Text(
            '$year年每月出生兔子数量',
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                  color: palette.primary,
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                ),
          ),
        ),
        const SizedBox(width: 10),
        Container(
          height: 42,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          decoration: BoxDecoration(
            color: palette.surface,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: palette.line),
          ),
          child: DropdownButtonHideUnderline(
            child: DropdownButton<int>(
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
        ),
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
