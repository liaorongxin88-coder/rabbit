import 'package:flutter/material.dart';

import 'package:rabbit_flutter/src/ui/core/theme.dart';

class InfoNotice extends StatelessWidget {
  const InfoNotice({
    super.key,
    required this.text,
    this.icon,
  });

  final String text;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final noticeIcon = icon;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: palette.surfaceSubtle,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: palette.line),
      ),
      child: noticeIcon == null
          ? Text(text, style: Theme.of(context).textTheme.bodyMedium)
          : Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(noticeIcon, size: 18, color: palette.muted),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    text,
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ),
              ],
            ),
    );
  }
}
