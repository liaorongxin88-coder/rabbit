import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/ui/settings/view_models/update.dart';

class AppUpdatePrompt extends ConsumerStatefulWidget {
  const AppUpdatePrompt({super.key, required this.child});

  final Widget child;

  @override
  ConsumerState<AppUpdatePrompt> createState() => _AppUpdatePromptState();
}

class _AppUpdatePromptState extends ConsumerState<AppUpdatePrompt> {
  var _dialogOpen = false;

  @override
  Widget build(BuildContext context) {
    ref.listen(appUpdateControllerProvider, (previous, next) {
      if (!next.shouldPrompt || _dialogOpen || !context.mounted) {
        return;
      }
      _dialogOpen = true;
      showDialog<void>(
        context: context,
        barrierDismissible: !next.update.forceUpdate,
        builder: (dialogContext) => const _AppUpdateDialog(),
      ).whenComplete(() {
        _dialogOpen = false;
      });
    });
    return widget.child;
  }
}

class _AppUpdateDialog extends ConsumerWidget {
  const _AppUpdateDialog();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(appUpdateControllerProvider);
    final controller = ref.read(appUpdateControllerProvider.notifier);
    final update = state.update;
    final force = update.forceUpdate;

    return PopScope(
      canPop: !force,
      child: AlertDialog(
        title: Text(force ? '必须更新后才能继续使用' : '发现新版本'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '新版本 ${update.versionName ?? ''}（${update.versionCode ?? ''}）',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            if (update.releaseNotes != null && update.releaseNotes!.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                update.releaseNotes!,
                maxLines: 8,
                overflow: TextOverflow.ellipsis,
              ),
            ],
            if (state.phase == AppUpdatePhase.downloading) ...[
              const SizedBox(height: 16),
              LinearProgressIndicator(value: state.progress <= 0 ? null : state.progress),
              const SizedBox(height: 8),
              Text('正在下载 ${(state.progress * 100).clamp(0, 100).toStringAsFixed(0)}%'),
            ],
            if (state.phase == AppUpdatePhase.failed && state.error != null) ...[
              const SizedBox(height: 12),
              Text(state.error!),
            ],
          ],
        ),
        actions: [
          if (!force && state.phase != AppUpdatePhase.downloading)
            TextButton(
              onPressed: () {
                Navigator.of(context).pop();
                controller.dismiss();
              },
              child: const Text('稍后'),
            ),
          if (state.phase == AppUpdatePhase.failed) ...[
            if (state.localFile != null)
              FilledButton(
                onPressed: controller.install,
                child: const Text('立即安装'),
              ),
            if (state.localFile == null)
              FilledButton(
                onPressed: controller.startDownload,
                child: const Text('重新下载'),
              )
            else
              TextButton(
                onPressed: controller.startDownload,
                child: const Text('重新下载'),
              ),
          ]
          else if (state.phase == AppUpdatePhase.ready)
            FilledButton(
              onPressed: controller.install,
              child: const Text('立即安装'),
            )
          else if (state.phase == AppUpdatePhase.downloading)
            const FilledButton(
              onPressed: null,
              child: Text('下载中'),
            )
          else
            FilledButton(
              onPressed: controller.startDownload,
              child: const Text('立即更新'),
            ),
        ],
      ),
    );
  }
}
