import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/domain/batches/rabbit.dart';
import 'package:rabbit_flutter/src/domain/batches/tracking.dart';
import 'package:rabbit_flutter/src/domain/reproduction/date_policy.dart';
import 'package:rabbit_flutter/src/ui/batches/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';

Future<void> showBatchTrackingSheet({
  required BuildContext context,
  required int houseId,
  required BatchRabbitItem item,
}) {
  return showAppModalSheet<void>(
    context: context,
    useRootNavigator: false,
    builder: (context) => _BatchTrackingSheet(
      houseId: houseId,
      item: item,
    ),
  );
}

class _BatchTrackingSheet extends ConsumerWidget {
  const _BatchTrackingSheet({required this.houseId, required this.item});

  final int houseId;
  final BatchRabbitItem item;

  BatchTrackingRequest get _request => BatchTrackingRequest(
        houseId: houseId,
        batchId: item.batchId,
        motherRabbitId: item.rabbitId,
      );

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final events = ref.watch(batchTrackingEventsProvider(_request));
    final mediaQuery = MediaQuery.of(context);
    final maxHeight = (mediaQuery.size.height - mediaQuery.viewInsets.bottom)
        .clamp(360.0, mediaQuery.size.height);

    return SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxHeight: maxHeight),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 18, 12, 10),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '批次生产记录',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '批次 #${item.batchId} · 母兔 #${item.rabbitId}',
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: '刷新记录',
                    onPressed: () =>
                        ref.invalidate(batchTrackingEventsProvider(_request)),
                    icon: const Icon(Icons.refresh),
                  ),
                  IconButton(
                    tooltip: '关闭',
                    onPressed: () => Navigator.of(context).pop(),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 14),
              child: _TrackingSummary(item: item),
            ),
            Divider(height: 1, color: AppPalette.of(context).line),
            Flexible(
              child: events.when(
                loading: () => const Center(
                  child: Padding(
                    padding: EdgeInsets.all(32),
                    child: CircularProgressIndicator(),
                  ),
                ),
                error: (error, _) => ErrorState(
                  message: error.toString(),
                  onRetry: () =>
                      ref.invalidate(batchTrackingEventsProvider(_request)),
                ),
                data: (items) => items.isEmpty
                    ? const EmptyState(
                        icon: Icons.history_toggle_off_outlined,
                        title: '暂无生产操作',
                        message: '该批次标签下还没有生产操作记录。',
                      )
                    : ListView.separated(
                        key: const ValueKey('batch-tracking-event-list'),
                        padding: const EdgeInsets.fromLTRB(20, 14, 20, 24),
                        itemCount: items.length,
                        separatorBuilder: (_, __) => const SizedBox(height: 10),
                        itemBuilder: (context, index) =>
                            _TrackingEventTile(event: items[index]),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TrackingSummary extends StatelessWidget {
  const _TrackingSummary({required this.item});

  final BatchRabbitItem item;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = (constraints.maxWidth - 8) / 2;
        return Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            _SummaryTile(
              width: width,
              label: '生产周期',
              value: item.batchCycleCount,
            ),
            _SummaryTile(
              width: width,
              label: '操作留痕',
              value: item.batchOperationCount,
            ),
            _SummaryTile(
              width: width,
              label: '产仔 / 活仔',
              valueText: '${item.batchTotalKits} / ${item.batchLiveKits}',
            ),
            _SummaryTile(
              width: width,
              label: '断奶',
              value: item.batchWeanedKits,
            ),
          ],
        );
      },
    );
  }
}

class _SummaryTile extends StatelessWidget {
  const _SummaryTile({
    required this.width,
    required this.label,
    this.value,
    this.valueText,
  });

  final double width;
  final String label;
  final int? value;
  final String? valueText;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      width: width,
      constraints: const BoxConstraints(minHeight: 68),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            valueText ?? '${value ?? 0}',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 2),
          Text(label, style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
    );
  }
}

class _TrackingEventTile extends StatelessWidget {
  const _TrackingEventTile({required this.event});

  final BatchTrackingEvent event;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final transition = [
      event.fromStageLabel,
      event.toStageLabel,
    ].whereType<String>().join(' → ');
    return Container(
      key: ValueKey('batch-tracking-event-${event.id}'),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.history, color: palette.primary),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  event.eventLabel,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 4),
                Text(
                  [
                    _dateTimeLabel(event.occurredAt),
                    event.operatorName,
                    if (event.cycleId != null) '周期 #${event.cycleId}',
                  ].join(' · '),
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                ),
                if (transition.isNotEmpty) ...[
                  const SizedBox(height: 3),
                  Text(
                    transition,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: palette.muted,
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

// 走兔场时区而不是设备时区：手机换了时区时，同一条记录不应该跟其它页面对不上号。
String _dateTimeLabel(DateTime? value) => value == null
    ? '时间未记录'
    : DateFormat('yyyy-MM-dd HH:mm').format(farmLocalDateTime(value));
