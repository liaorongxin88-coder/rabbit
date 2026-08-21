import 'package:flutter/material.dart';

import 'package:rabbit_flutter/src/ui/core/theme.dart';

class ProductionContextLine extends StatelessWidget {
  const ProductionContextLine({
    super.key,
    required this.houseLabel,
    required this.rabbitId,
    this.batchId,
    this.cycleRecordId,
  });

  final String houseLabel;
  final int rabbitId;
  final int? batchId;
  final int? cycleRecordId;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final style = Theme.of(context).textTheme.bodyMedium?.copyWith(
          color: palette.muted,
          fontWeight: FontWeight.w600,
        );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          houseLabel,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: style,
        ),
        const SizedBox(height: 5),
        Wrap(
          spacing: 12,
          runSpacing: 5,
          children: [
            _ContextItem(
              icon: Icons.pets_outlined,
              label: '母兔 #$rabbitId',
            ),
            const _ContextItem(
              icon: Icons.person_outline,
              label: '执行人 当前账号',
            ),
            if (batchId != null && batchId! > 0)
              _ContextItem(
                icon: Icons.inventory_2_outlined,
                label: '批次 #$batchId',
              ),
            if (cycleRecordId != null && cycleRecordId! > 0)
              _ContextItem(
                icon: Icons.repeat_rounded,
                label: '周期记录 #$cycleRecordId',
              ),
          ],
        ),
      ],
    );
  }
}

class _ContextItem extends StatelessWidget {
  const _ContextItem({required this.icon, required this.label});

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
        Text(
          label,
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: palette.muted,
                fontWeight: FontWeight.w600,
              ),
        ),
      ],
    );
  }
}
