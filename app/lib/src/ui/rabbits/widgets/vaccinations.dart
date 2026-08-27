import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import 'package:rabbit_flutter/src/domain/rabbits/vaccination.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/providers.dart';

/// 兔只详情里的接种记录段。
///
/// 独立成 widget 而不是塞进 `screens/list.dart`：那个文件已经 1600 行，
/// 而接种历史与兔只详情的其它段之间没有共享状态。
class RabbitVaccinationHistory extends ConsumerWidget {
  const RabbitVaccinationHistory({
    super.key,
    required this.houseId,
    required this.rabbitId,
  });

  final int houseId;
  final int rabbitId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final request = RabbitDetailRequest(houseId: houseId, rabbitId: rabbitId);
    final records = ref.watch(rabbitVaccinationsProvider(request));
    final palette = AppPalette.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('接种记录', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 10),
        records.when(
          loading: () => const Padding(
            padding: EdgeInsets.symmetric(vertical: 18),
            child: Center(
              child: SizedBox.square(
                dimension: 22,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ),
          ),
          error: (error, _) => _VaccinationInlineError(
            message: error.toString(),
            onRetry: () => ref.invalidate(rabbitVaccinationsProvider(request)),
          ),
          data: (items) {
            if (items.isEmpty) {
              return Container(
                key: const ValueKey('rabbit-vaccination-empty'),
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: palette.surface,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: palette.line),
                ),
                child: Row(
                  children: [
                    Icon(Icons.vaccines_outlined, color: palette.muted),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        '暂无接种记录，可用上方「接种疫苗」登记。',
                        style: TextStyle(color: palette.muted),
                      ),
                    ),
                  ],
                ),
              );
            }
            return Column(
              key: const ValueKey('rabbit-vaccination-list'),
              children: [
                for (var index = 0; index < items.length; index++) ...[
                  if (index > 0) const SizedBox(height: 10),
                  _VaccinationCard(record: items[index]),
                ],
              ],
            );
          },
        ),
      ],
    );
  }
}

class _VaccinationCard extends StatelessWidget {
  const _VaccinationCard({required this.record});

  final VaccinationRecord record;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final awaits = record.awaitsNextDose;
    final detail = <String>[
      if (record.vaccineBatchNo != null) '批号 ${record.vaccineBatchNo}',
      if (record.dose != null) '剂量 ${record.dose}',
      if (record.route != null) record.route!,
    ].join(' · ');

    return Container(
      key: ValueKey('rabbit-vaccination-card-${record.id}'),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  record.vaccineName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
              ),
              const SizedBox(width: 8),
              _StatusChip(
                label: record.statusLabel,
                color: awaits ? palette.warning : palette.success,
                background: awaits ? palette.warningSoft : palette.successSoft,
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            '接种 ${_formatDate(record.vaccinatedAt)}',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          if (detail.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(
              detail,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(color: palette.muted),
            ),
          ],
          if (awaits) ...[
            const SizedBox(height: 4),
            Text(
              '下次接种 ${_formatDate(record.nextDueDate)}',
              style: TextStyle(color: palette.warning),
            ),
          ],
          if (record.remark != null) ...[
            const SizedBox(height: 4),
            Text(
              record.remark!,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(color: palette.muted),
            ),
          ],
        ],
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({
    required this.label,
    required this.color,
    required this.background,
  });

  final String label;
  final Color color;
  final Color background;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        label,
        style: TextStyle(color: color, fontWeight: FontWeight.w700),
      ),
    );
  }
}

class _VaccinationInlineError extends StatelessWidget {
  const _VaccinationInlineError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.dangerSoft,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.danger.withAlpha(90)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              '接种记录加载失败：$message',
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(color: palette.text),
            ),
          ),
          const SizedBox(width: 8),
          TextButton(onPressed: onRetry, child: const Text('重试')),
        ],
      ),
    );
  }
}

String _formatDate(DateTime? value) {
  if (value == null) {
    return '—';
  }
  return DateFormat('yyyy-MM-dd').format(value.toLocal());
}
