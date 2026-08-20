import 'package:flutter/material.dart';

import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';

class SheetLoadingState extends StatelessWidget {
  const SheetLoadingState({
    super.key,
    required this.sheetTitle,
    required this.message,
    required this.onClose,
  });

  final String sheetTitle;
  final String message;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Semantics(
      liveRegion: true,
      label: message,
      child: ConstrainedBox(
        key: const ValueKey('batch-sheet-loading'),
        constraints: const BoxConstraints(minHeight: 240),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 14, 12, 28),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(
                        sheetTitle,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                    ),
                  ),
                  IconButton(
                    key: const ValueKey('batch-sheet-loading-close'),
                    tooltip: '关闭',
                    onPressed: onClose,
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
              const Spacer(),
              const SizedBox.square(
                dimension: 32,
                child: CircularProgressIndicator(strokeWidth: 3),
              ),
              const SizedBox(height: 18),
              Text(
                message,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: palette.muted,
                      fontWeight: FontWeight.w600,
                    ),
              ),
              const Spacer(),
            ],
          ),
        ),
      ),
    );
  }
}

class SheetErrorState extends StatelessWidget {
  const SheetErrorState({
    super.key,
    required this.sheetTitle,
    required this.error,
    required this.fallbackMessage,
    required this.onRetry,
    required this.onClose,
  });

  final String sheetTitle;
  final Object error;
  final String fallbackMessage;
  final VoidCallback onRetry;
  final VoidCallback onClose;

  String get _message {
    if (error case ApiException(:final message)
        when message.trim().isNotEmpty) {
      return message.trim();
    }
    return fallbackMessage;
  }

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Semantics(
      liveRegion: true,
      child: SingleChildScrollView(
        key: const ValueKey('batch-sheet-error'),
        padding: const EdgeInsets.fromLTRB(24, 24, 24, 20),
        child: SizedBox(
          width: double.infinity,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                sheetTitle,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: palette.muted,
                    ),
              ),
              const SizedBox(height: 18),
              Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                  color: palette.dangerSoft,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(Icons.cloud_off_outlined, color: palette.danger),
              ),
              const SizedBox(height: 14),
              Text(
                '加载失败',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 8),
              Text(
                _message,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: palette.muted,
                    ),
              ),
              const SizedBox(height: 24),
              LayoutBuilder(
                builder: (context, constraints) {
                  final textScale =
                      MediaQuery.textScalerOf(context).scale(10) / 10;
                  final stackActions =
                      textScale >= 1.6 || constraints.maxWidth < 300;
                  final close = OutlinedButton.icon(
                    key: const ValueKey('batch-sheet-error-close'),
                    onPressed: onClose,
                    icon: const Icon(Icons.close),
                    label: const Text('关闭'),
                  );
                  final retry = FilledButton.icon(
                    key: const ValueKey('batch-sheet-error-retry'),
                    onPressed: onRetry,
                    icon: const Icon(Icons.refresh),
                    label: const Text('重试'),
                  );
                  if (stackActions) {
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        retry,
                        const SizedBox(height: 10),
                        close,
                      ],
                    );
                  }
                  return Row(
                    children: [
                      Expanded(child: close),
                      const SizedBox(width: 12),
                      Expanded(child: retry),
                    ],
                  );
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}
