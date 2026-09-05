import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/data/repositories/batches/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/sharing/files.dart';
import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/statistics.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';
import 'package:rabbit_flutter/src/ui/batches/sheets/carcass_yield.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';

class BatchStatisticsSection extends ConsumerStatefulWidget {
  const BatchStatisticsSection({
    super.key,
    required this.houseId,
    required this.batch,
    required this.state,
    required this.canEdit,
    required this.canViewAudit,
    required this.canExport,
    required this.onRetry,
    required this.onChanged,
  });

  final int houseId;
  final Batch batch;
  final BatchStatisticsState state;
  final bool canEdit;
  final bool canViewAudit;
  final bool canExport;
  final VoidCallback onRetry;
  final Future<void> Function() onChanged;

  @override
  ConsumerState<BatchStatisticsSection> createState() =>
      _BatchStatisticsSectionState();
}

class _BatchStatisticsSectionState
    extends ConsumerState<BatchStatisticsSection> {
  var _exporting = false;
  String? _exportError;
  var _scopeGeneration = 0;

  @override
  void didUpdateWidget(covariant BatchStatisticsSection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.houseId == widget.houseId &&
        oldWidget.batch.id == widget.batch.id) {
      return;
    }
    _scopeGeneration++;
    _exporting = false;
    _exportError = null;
  }

  Future<void> _export() async {
    if (_exporting) return;
    final generation = _scopeGeneration;
    final houseId = widget.houseId;
    final batchId = widget.batch.id;
    setState(() {
      _exporting = true;
      _exportError = null;
    });
    File? downloadedFile;
    try {
      downloadedFile =
          await ref.read(batchRepositoryProvider).downloadBatchStatistics(
                houseId: houseId,
                batchId: batchId,
              );
      if (!mounted || generation != _scopeGeneration) return;
      await ref.read(fileShareServiceProvider).shareSpreadsheet(downloadedFile);
    } catch (error) {
      if (mounted && generation == _scopeGeneration) {
        setState(() => _exportError = _errorMessage(error));
      }
    } finally {
      if (downloadedFile != null) {
        try {
          if (downloadedFile.existsSync()) downloadedFile.deleteSync();
        } catch (_) {
          // Cache cleanup must not replace the download or share result.
        }
      }
      if (mounted && generation == _scopeGeneration) {
        setState(() => _exporting = false);
      }
    }
  }

  Future<void> _editCarcassYield() async {
    final generation = _scopeGeneration;
    final metric = widget.state.statistics?.metricsByCode['CARCASS_YIELD_RATE'];
    final changed = await showBatchCarcassYieldSheet(
      context: context,
      houseId: widget.houseId,
      batch: widget.batch,
      hasExistingValue: metric?.status == BatchMetricStatus.available,
    );
    if (changed && mounted && generation == _scopeGeneration) {
      await widget.onChanged();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final statistics = state.statistics;
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 8,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text('批次统计', style: Theme.of(context).textTheme.titleMedium),
              if (state.isLoading)
                const SizedBox.square(
                  key: ValueKey('batch-statistics-refreshing'),
                  dimension: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              if (widget.canEdit)
                OutlinedButton.icon(
                  key: const ValueKey('batch-carcass-yield-edit'),
                  onPressed: _editCarcassYield,
                  icon: const Icon(Icons.monitor_weight_outlined),
                  label: const Text('录入出肉率'),
                ),
              if (widget.canViewAudit)
                OutlinedButton.icon(
                  key: const ValueKey('batch-carcass-yield-history'),
                  onPressed: () => showBatchCarcassYieldHistorySheet(
                    context: context,
                    houseId: widget.houseId,
                    batch: widget.batch,
                  ),
                  icon: const Icon(Icons.history),
                  label: const Text('版本历史'),
                ),
              if (widget.canExport)
                OutlinedButton.icon(
                  key: const ValueKey('batch-statistics-export'),
                  onPressed: _exporting ? null : _export,
                  icon: _exporting
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.ios_share_outlined),
                  label: Text(_exporting ? '正在导出' : '导出 Excel'),
                ),
            ],
          ),
          if (_exportError != null) ...[
            const SizedBox(height: 8),
            Text(
              '导出失败：$_exportError',
              key: const ValueKey('batch-statistics-export-error'),
              style: TextStyle(color: AppPalette.of(context).danger),
            ),
          ],
          const SizedBox(height: 12),
          if (state.error != null)
            _StatisticsErrorNotice(
              retained: statistics != null,
              error: state.error!,
              onRetry: widget.onRetry,
            ),
          if (state.error != null && statistics != null)
            const SizedBox(height: 12),
          if (state.isLoading && statistics == null)
            const Padding(
              key: ValueKey('batch-statistics-loading'),
              padding: EdgeInsets.symmetric(vertical: 28),
              child: Center(child: CircularProgressIndicator()),
            )
          else if (statistics != null)
            _StatisticsContent(statistics: statistics),
        ],
      ),
    );
  }
}

class _StatisticsContent extends StatelessWidget {
  const _StatisticsContent({required this.statistics});

  final BatchStatistics statistics;

  @override
  Widget build(BuildContext context) {
    final byCode = statistics.metricsByCode;
    return Column(
      key: const ValueKey('batch-statistics-content'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          '${statistics.houseName} · ${statistics.batchCode}',
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.bodyMedium,
        ),
        const SizedBox(height: 2),
        Text(
          '取数时间：${DateFormat('yyyy-MM-dd HH:mm:ss').format(farmLocalDateTime(statistics.calculatedAt))}',
          key: const ValueKey('batch-statistics-calculated-at'),
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: AppPalette.of(context).muted,
              ),
        ),
        const SizedBox(height: 16),
        for (final group in batchMetricLayout) ...[
          _MetricGroup(group: group, metrics: byCode),
          const SizedBox(height: 18),
        ],
      ],
    );
  }
}

class _MetricGroup extends StatelessWidget {
  const _MetricGroup({required this.group, required this.metrics});

  final BatchMetricGroup group;
  final Map<String, BatchStatisticMetric> metrics;

  @override
  Widget build(BuildContext context) {
    return Column(
      key: ValueKey('batch-statistics-group-${group.stage}'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Text(group.name, style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(width: 12),
            Expanded(child: Divider(color: AppPalette.of(context).line)),
          ],
        ),
        const SizedBox(height: 10),
        LayoutBuilder(
          builder: (context, constraints) {
            final singleColumn = constraints.maxWidth < 640 ||
                MediaQuery.textScalerOf(context).scale(1) >= 1.5;
            return Column(
              children: [
                for (var rowIndex = 0;
                    rowIndex < group.rows.length;
                    rowIndex++) ...[
                  _MetricRowWidget(
                    key: ValueKey(
                      'batch-statistics-row-${group.stage}-$rowIndex',
                    ),
                    row: group.rows[rowIndex],
                    metrics: metrics,
                    singleColumn: singleColumn,
                  ),
                  if (rowIndex != group.rows.length - 1)
                    const SizedBox(height: 10),
                ],
              ],
            );
          },
        ),
      ],
    );
  }
}

class _MetricRowWidget extends StatelessWidget {
  const _MetricRowWidget({
    super.key,
    required this.row,
    required this.metrics,
    required this.singleColumn,
  });

  final BatchMetricRow row;
  final Map<String, BatchStatisticMetric> metrics;
  final bool singleColumn;

  @override
  Widget build(BuildContext context) {
    final slots = row.slots.map((codes) {
      return _MetricSlot(
        metrics: [for (final code in codes) metrics[code]!],
      );
    }).toList();
    if (singleColumn || slots.length == 1) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var index = 0; index < slots.length; index++) ...[
            slots[index],
            if (index != slots.length - 1) const SizedBox(height: 10),
          ],
        ],
      );
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var index = 0; index < slots.length; index++) ...[
          Expanded(child: slots[index]),
          if (index != slots.length - 1) const SizedBox(width: 10),
        ],
      ],
    );
  }
}

class _MetricSlot extends StatelessWidget {
  const _MetricSlot({required this.metrics});

  final List<BatchStatisticMetric> metrics;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      decoration: BoxDecoration(
        border: Border.all(color: palette.line),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        children: [
          for (var index = 0; index < metrics.length; index++) ...[
            _MetricItem(metric: metrics[index]),
            if (index != metrics.length - 1)
              Divider(height: 1, color: palette.line),
          ],
        ],
      ),
    );
  }
}

class _MetricItem extends StatelessWidget {
  const _MetricItem({required this.metric});

  final BatchStatisticMetric metric;

  @override
  Widget build(BuildContext context) {
    return Padding(
      key: ValueKey('batch-statistic-${metric.code}'),
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 6,
            crossAxisAlignment: WrapCrossAlignment.center,
            alignment: WrapAlignment.spaceBetween,
            children: [
              Text(metric.name, style: Theme.of(context).textTheme.bodySmall),
              _MetricStatus(status: metric.status),
            ],
          ),
          const SizedBox(height: 5),
          Text(
            metric.visibleValue,
            style: Theme.of(context).textTheme.titleLarge,
          ),
          ExpansionTile(
            key: ValueKey('batch-statistic-details-${metric.code}'),
            tilePadding: EdgeInsets.zero,
            childrenPadding: EdgeInsets.zero,
            dense: true,
            title: const Text('查看口径'),
            children: [
              Align(
                alignment: Alignment.centerLeft,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('公式：${metric.formula.isEmpty ? '-' : metric.formula}'),
                    if (metric.numerator != null)
                      Text(_formatOperand(metric.numerator!)),
                    if (metric.denominator != null)
                      Text(_formatOperand(metric.denominator!)),
                    for (final component in metric.components)
                      Text(_formatOperand(component)),
                    for (final count
                        in metric.dateValue?.dailyCycleCounts ?? const [])
                      Text('${count.date}：${count.cycleCount} 个周期'),
                    for (final cause in metric.missingCauses)
                      Text('${cause.message}（${cause.code}）'),
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

class _MetricStatus extends StatelessWidget {
  const _MetricStatus({required this.status});

  final BatchMetricStatus status;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final color = status == BatchMetricStatus.dataMissing
        ? palette.danger
        : status == BatchMetricStatus.available
            ? palette.success
            : palette.warning;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        border: Border.all(color: color),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        status.label,
        style: Theme.of(context).textTheme.labelSmall?.copyWith(color: color),
      ),
    );
  }
}

class _StatisticsErrorNotice extends StatelessWidget {
  const _StatisticsErrorNotice({
    required this.retained,
    required this.error,
    required this.onRetry,
  });

  final bool retained;
  final Object error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const ValueKey('batch-statistics-error'),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        border: Border.all(color: AppPalette.of(context).warning),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            retained
                ? '更新失败，已保留上次成功统计和取数时间。'
                : '统计加载失败：${_errorMessage(error)}',
          ),
          const SizedBox(height: 6),
          TextButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: Text(retained ? '重试更新' : '重试统计'),
          ),
        ],
      ),
    );
  }
}

String _formatOperand(BatchMetricOperand operand) {
  const units = {
    'COUNT': '个',
    'RABBIT': '只',
    'LITTER': '窝',
    'KG': 'kg',
    'CURRENCY': '元',
    'CURRENCY_PER_KG': '元/kg',
  };
  final value = operand.value;
  final valueText = value == null
      ? '-'
      : value == value.roundToDouble()
          ? value.toInt().toString()
          : value.toString();
  final unit = units[operand.unit] ?? operand.unit;
  return '${operand.label}：$valueText${unit.isEmpty ? '' : ' $unit'}';
}

String _errorMessage(Object error) =>
    error is ApiException ? error.message : error.toString();
