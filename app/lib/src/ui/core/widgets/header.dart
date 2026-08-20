import 'package:flutter/material.dart';

import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';

class ContextHeaderCard extends StatelessWidget {
  const ContextHeaderCard({
    super.key,
    required this.title,
    required this.subtitle,
    required this.onBack,
    this.footer,
    this.titleMaxLines = 1,
    this.subtitleMaxLines = 1,
    this.expandForLargeText = false,
  });

  final String title;
  final String subtitle;
  final VoidCallback onBack;
  final Widget? footer;
  final int titleMaxLines;
  final int subtitleMaxLines;
  final bool expandForLargeText;

  @override
  Widget build(BuildContext context) {
    final footer = this.footer;
    final largeText = MediaQuery.textScalerOf(context).scale(10) / 10 >= 1.3;
    final effectiveTitleMaxLines =
        expandForLargeText && largeText ? 2 : titleMaxLines;
    final effectiveSubtitleMaxLines =
        expandForLargeText && largeText ? 2 : subtitleMaxLines;
    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              IconButton(
                tooltip: '返回',
                onPressed: onBack,
                icon: const Icon(Icons.arrow_back),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      maxLines: effectiveTitleMaxLines,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      subtitle,
                      maxLines: effectiveSubtitleMaxLines,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (footer != null) ...[
            const SizedBox(height: 12),
            footer,
          ],
        ],
      ),
    );
  }
}
