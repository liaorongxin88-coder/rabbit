import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/batch_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/report_repository.dart';
import 'package:rabbit_flutter/src/domain/models/batch.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';

final _dashboardPanelProvider =
    FutureProvider<_DashboardPanelData>((ref) async {
  final houseId = ref.watch(authControllerProvider).valueOrNull?.houseId ?? 0;
  if (houseId <= 0) {
    return _DashboardPanelData.empty();
  }

  final report = await ref.watch(currentHouseReportProvider.future);
  final rabbits = await ref.watch(houseRabbitsProvider(houseId).future);
  final batches = await ref.watch(currentHouseBatchesProvider.future);

  return _DashboardPanelData(
    houseId: houseId,
    report: report,
    rabbits: rabbits,
    batches: batches,
  );
});

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

    return AppPage(
      title: '数据面板',
      actions: [
        IconButton(
          tooltip: '刷新',
          onPressed: () => _refresh(ref),
          icon: const Icon(Icons.refresh),
        ),
      ],
      child: panel.when(
        data: (data) {
          if (data.houseId <= 0) {
            return const EmptyState(
              icon: Icons.storefront_outlined,
              title: '请选择兔舍',
              message: '进入兔舍列表选择当前兔舍后，这里会展示兔群统计和繁殖数据。',
            );
          }
          return RefreshIndicator(
            onRefresh: () => _refresh(ref),
            child: ListView(
              padding: const EdgeInsets.fromLTRB(20, 14, 20, 24),
              children: [
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
                  color: AppColors.blue,
                  values: data.monthlyBirths(_selectedYear),
                ),
                const SizedBox(height: 22),
                Text(
                  '$_selectedYear年每月满月兔子数量',
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                        color: AppColors.blue,
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                ),
                const SizedBox(height: 12),
                _MonthlyChartCard(
                  title: '每月满月兔子数量',
                  color: AppColors.green,
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
    final houseId = ref.read(authControllerProvider).valueOrNull?.houseId ?? 0;
    ref.invalidate(_dashboardPanelProvider);
    ref.invalidate(currentHouseReportProvider);
    ref.invalidate(currentHouseBatchesProvider);
    if (houseId > 0) {
      ref.invalidate(houseRabbitsProvider(houseId));
    }
    await ref.read(_dashboardPanelProvider.future);
  }
}

class _DashboardPanelData {
  const _DashboardPanelData({
    required this.houseId,
    required this.report,
    required this.rabbits,
    required this.batches,
  });

  final int houseId;
  final DashboardReport report;
  final List<Rabbit> rabbits;
  final List<Batch> batches;

  factory _DashboardPanelData.empty() {
    return _DashboardPanelData(
      houseId: 0,
      report: DashboardReport.empty(),
      rabbits: const [],
      batches: const [],
    );
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

class _DashboardHero extends StatelessWidget {
  const _DashboardHero({required this.stats});

  final _RabbitStats stats;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.line),
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
              children: const [
                TextSpan(
                  text: '兔群统计',
                  style: TextStyle(color: AppColors.blue),
                ),
                TextSpan(
                  text: '数据',
                  style: TextStyle(color: AppColors.green),
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
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: AppColors.background,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: AppColors.line),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: 6),
            Text(
              valueText ?? '${value ?? 0}',
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    color: AppColors.blue,
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
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.line),
        boxShadow: const [
          BoxShadow(
            color: Color(0x14000000),
            blurRadius: 14,
            offset: Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(item.icon, color: AppColors.muted, size: 22),
          const Spacer(),
          Text(
            item.label,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: AppColors.muted,
                  fontSize: 13,
                ),
          ),
          const SizedBox(height: 6),
          Text(
            '${item.value}',
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                  color: AppColors.blue,
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

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(
          child: Text(
            '$year年每月出生兔子数量',
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                  color: AppColors.blue,
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
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: AppColors.line),
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
    return Container(
      height: 270,
      padding: const EdgeInsets.fromLTRB(10, 12, 10, 10),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.line),
      ),
      child: Column(
        children: [
          Expanded(
            child: Stack(
              children: [
                Positioned.fill(
                  child: CustomPaint(
                    painter: _MonthChartPainter(values: values, color: color),
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
                  color: AppColors.muted,
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
  });

  final List<int> values;
  final Color color;

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
      ..color = AppColors.muted
      ..strokeWidth = 1.1;
    final guidePaint = Paint()
      ..color = AppColors.line
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
          style: const TextStyle(
            color: AppColors.muted,
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
    return oldDelegate.values != values || oldDelegate.color != color;
  }
}
