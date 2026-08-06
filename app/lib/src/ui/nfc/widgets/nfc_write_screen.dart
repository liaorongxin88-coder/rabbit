import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/nfc_write_controller.dart';

class NfcWriteScreen extends ConsumerWidget {
  const NfcWriteScreen({super.key, required this.houseId});

  final int houseId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(nfcWriteControllerProvider(houseId));
    final controller = ref.read(nfcWriteControllerProvider(houseId).notifier);

    return NfcWriteView(
      state: state,
      onExit: () async {
        await controller.pause();
        if (context.mounted) {
          context.go('/houses/$houseId/nfc/write');
        }
      },
      onPause: controller.pause,
      onResume: controller.resume,
      onPrevious: controller.previous,
      onSkip: controller.skip,
      onRetry: controller.retry,
      onConfirmOverwrite: controller.confirmOverwrite,
      onConfirmBinding: controller.confirmBindingReplacement,
      onDone: () => context.go('/houses/$houseId/cages'),
    );
  }
}

class NfcWriteView extends StatelessWidget {
  const NfcWriteView({
    super.key,
    required this.state,
    required this.onExit,
    required this.onPause,
    required this.onResume,
    required this.onPrevious,
    required this.onSkip,
    required this.onRetry,
    required this.onConfirmOverwrite,
    required this.onConfirmBinding,
    required this.onDone,
  });

  final NfcWriteState state;
  final VoidCallback onExit;
  final VoidCallback onPause;
  final VoidCallback onResume;
  final VoidCallback onPrevious;
  final VoidCallback onSkip;
  final VoidCallback onRetry;
  final VoidCallback onConfirmOverwrite;
  final VoidCallback onConfirmBinding;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final session = state.session;
    final current = state.currentItem;
    final total = session?.items.length ?? 0;
    final index = session?.currentIndex ?? 0;

    return PopScope(
      onPopInvokedWithResult: (_, __) => onPause(),
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(
            tooltip: '退出写入',
            onPressed: onExit,
            icon: const Icon(Icons.close),
          ),
          title: const Text('连续写标签'),
          actions: [
            IconButton(
              tooltip: state.phase == NfcWritePhase.paused ? '继续' : '暂停',
              onPressed:
                  state.phase == NfcWritePhase.paused ? onResume : onPause,
              icon: Icon(
                state.phase == NfcWritePhase.paused
                    ? Icons.play_arrow
                    : Icons.pause,
              ),
            ),
          ],
        ),
        body: SafeArea(
          top: false,
          child: Column(
            children: [
              LinearProgressIndicator(
                value: total == 0 ? 0 : index.clamp(0, total) / total,
                minHeight: 5,
                backgroundColor: palette.line,
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 14, 20, 0),
                child: Row(
                  children: [
                    Text(
                      total == 0
                          ? '准备中'
                          : '${state.phase == NfcWritePhase.completed ? total : (index + 1).clamp(1, total)}/$total',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const Spacer(),
                    if (state.pendingSyncCount > 0)
                      Text(
                        '待同步 ${state.pendingSyncCount}',
                        style: TextStyle(
                          color: palette.warning,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                  ],
                ),
              ),
              Expanded(
                child: state.phase == NfcWritePhase.loading
                    ? const Center(child: CircularProgressIndicator())
                    : state.phase == NfcWritePhase.completed
                        ? _CompletedPanel(state: state)
                        : _WriterPanel(state: state, current: current),
              ),
              _ActionBar(
                state: state,
                onPrevious: onPrevious,
                onSkip: onSkip,
                onRetry: onRetry,
                onConfirmOverwrite: onConfirmOverwrite,
                onConfirmBinding: onConfirmBinding,
                onResume: onResume,
                onDone: onDone,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _WriterPanel extends StatelessWidget {
  const _WriterPanel({required this.state, required this.current});

  final NfcWriteState state;
  final NfcWriteSessionItem? current;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final item = current?.queueItem;
    final label = item == null
        ? '—'
        : item.cageNumber.isEmpty
            ? '#${item.cageId}'
            : item.cageNumber;
    final statusColor = _statusColor(palette);
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 16),
      child: Column(
        children: [
          Expanded(
            child: Container(
              width: double.infinity,
              decoration: BoxDecoration(
                color: palette.surface,
                border: Border.all(color: statusColor, width: 2),
                borderRadius: BorderRadius.circular(8),
              ),
              padding: const EdgeInsets.all(22),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.location_on_outlined,
                      color: statusColor, size: 34),
                  const SizedBox(height: 14),
                  Text('当前笼位', style: Theme.of(context).textTheme.bodyLarge),
                  const SizedBox(height: 8),
                  SizedBox(
                    height: 72,
                    width: double.infinity,
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Text(
                        label,
                        maxLines: 1,
                        style: TextStyle(
                          color: palette.text,
                          fontSize: 48,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 28),
                  Icon(Icons.nfc_rounded, color: statusColor, size: 76),
                  const SizedBox(height: 16),
                  Text(
                    state.message ?? '等待标签',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                          color: statusColor,
                        ),
                  ),
                  if (state.conflict?.existingPayload case final payload?) ...[
                    const SizedBox(height: 10),
                    Text(
                      _existingTarget(payload),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ],
                ],
              ),
            ),
          ),
          if (state.session case final session?) ...[
            const SizedBox(height: 12),
            _NextLabel(session: session),
          ],
        ],
      ),
    );
  }

  Color _statusColor(AppPalette palette) {
    switch (state.phase) {
      case NfcWritePhase.confirmOverwrite:
      case NfcWritePhase.confirmBindingReplacement:
        return palette.warning;
      case NfcWritePhase.error:
        return palette.danger;
      case NfcWritePhase.paused:
        return palette.muted;
      case NfcWritePhase.binding:
      case NfcWritePhase.success:
        return palette.success;
      default:
        return palette.primary;
    }
  }

  String _existingTarget(String payload) {
    try {
      final target = NfcPayloadTarget.parse(payload);
      return '原标签目标：兔舍 ${target.houseId} · 笼位 ${target.cageId}';
    } on FormatException {
      return '原标签包含其他系统数据';
    }
  }
}

class _NextLabel extends StatelessWidget {
  const _NextLabel({required this.session});

  final NfcWriteSession session;

  @override
  Widget build(BuildContext context) {
    final nextIndex = session.currentIndex + 1;
    final next = nextIndex < session.items.length
        ? session.items[nextIndex].queueItem
        : null;
    return Text(
      next == null
          ? '当前为最后一个笼位'
          : '下一个：${next.cageNumber.isEmpty ? '#${next.cageId}' : next.cageNumber}',
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: Theme.of(context).textTheme.bodyMedium,
    );
  }
}

class _CompletedPanel extends StatelessWidget {
  const _CompletedPanel({required this.state});

  final NfcWriteState state;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.task_alt, color: palette.success, size: 72),
            const SizedBox(height: 18),
            Text('写入完成', style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 16),
            Wrap(
              spacing: 18,
              runSpacing: 8,
              alignment: WrapAlignment.center,
              children: [
                Text('已绑定 ${state.completedCount}'),
                Text('待同步 ${state.pendingSyncCount}'),
                Text('已跳过 ${state.skippedCount}'),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ActionBar extends StatelessWidget {
  const _ActionBar({
    required this.state,
    required this.onPrevious,
    required this.onSkip,
    required this.onRetry,
    required this.onConfirmOverwrite,
    required this.onConfirmBinding,
    required this.onResume,
    required this.onDone,
  });

  final NfcWriteState state;
  final VoidCallback onPrevious;
  final VoidCallback onSkip;
  final VoidCallback onRetry;
  final VoidCallback onConfirmOverwrite;
  final VoidCallback onConfirmBinding;
  final VoidCallback onResume;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    Widget primary;
    switch (state.phase) {
      case NfcWritePhase.confirmOverwrite:
        primary = FilledButton.icon(
          onPressed: onConfirmOverwrite,
          icon: const Icon(Icons.sync),
          label: const Text('确认覆盖标签'),
        );
      case NfcWritePhase.confirmBindingReplacement:
        primary = FilledButton.icon(
          onPressed: onConfirmBinding,
          icon: const Icon(Icons.link),
          label: const Text('确认重新绑定'),
        );
      case NfcWritePhase.error:
        primary = FilledButton.icon(
          onPressed: onRetry,
          icon: const Icon(Icons.refresh),
          label: const Text('重新识别'),
        );
      case NfcWritePhase.paused:
        primary = FilledButton.icon(
          onPressed: onResume,
          icon: const Icon(Icons.play_arrow),
          label: const Text('继续写入'),
        );
      case NfcWritePhase.completed:
        primary = FilledButton.icon(
          onPressed: onDone,
          icon: const Icon(Icons.done),
          label: const Text('返回笼位'),
        );
      default:
        primary = FilledButton.icon(
          onPressed: null,
          icon: const Icon(Icons.nfc),
          label: const Text('等待标签'),
        );
    }
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 16),
      decoration: BoxDecoration(
        color: palette.surface,
        border: Border(top: BorderSide(color: palette.line)),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            IconButton(
              tooltip: '上一个',
              onPressed: state.session?.currentIndex == 0 ? null : onPrevious,
              icon: const Icon(Icons.arrow_back),
            ),
            IconButton(
              tooltip: '跳过',
              onPressed: state.phase == NfcWritePhase.completed ? null : onSkip,
              icon: const Icon(Icons.skip_next),
            ),
            const SizedBox(width: 8),
            Expanded(child: primary),
          ],
        ),
      ),
    );
  }
}
