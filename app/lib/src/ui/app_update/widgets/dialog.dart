import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/domain/app_update/release.dart';
import 'package:rabbit_flutter/src/ui/app_update/view_models/controller.dart';

Future<void> showAppUpdateDialog(
  BuildContext context,
  WidgetRef ref,
  AppRelease release,
) {
  return showDialog<void>(
    context: context,
    barrierDismissible: !release.forceUpdate,
    builder: (_) => _AppUpdateDialog(release: release),
  );
}

class _AppUpdateDialog extends ConsumerWidget {
  const _AppUpdateDialog({required this.release});

  final AppRelease release;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(appUpdateControllerProvider);
    final controller = ref.read(appUpdateControllerProvider.notifier);
    final activeRelease = state.release ?? release;
    final canUseLater = !activeRelease.forceUpdate ||
        state.phase == AppUpdatePhase.failed ||
        state.phase == AppUpdatePhase.permissionRequired;
    final progress = state.progress;

    return PopScope(
      canPop: canUseLater,
      child: AlertDialog(
        title: Text('发现新版本 ${activeRelease.versionName}'),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 360),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (activeRelease.forceUpdate) const Text('此版本需要更新后继续使用。'),
              if (activeRelease.releaseNotes.isNotEmpty) ...[
                if (activeRelease.forceUpdate) const SizedBox(height: 8),
                Text(activeRelease.releaseNotes),
              ],
              if (state.phase == AppUpdatePhase.downloading) ...[
                const SizedBox(height: 16),
                LinearProgressIndicator(value: progress),
                const SizedBox(height: 8),
                Text(_downloadLabel(progress)),
              ],
              if (state.message?.isNotEmpty == true) ...[
                const SizedBox(height: 16),
                Text(state.message!),
              ],
            ],
          ),
        ),
        actions: _actions(context, controller, state, canUseLater),
      ),
    );
  }

  List<Widget> _actions(
    BuildContext context,
    AppUpdateController controller,
    AppUpdateState state,
    bool canUseLater,
  ) {
    switch (state.phase) {
      case AppUpdatePhase.downloading:
        return [
          TextButton(
            key: const ValueKey('app-update-cancel-download'),
            onPressed: controller.cancelDownload,
            child: const Text('取消下载'),
          ),
        ];
      case AppUpdatePhase.permissionRequired:
        return [
          if (canUseLater)
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('暂时继续使用'),
            ),
          FilledButton(
            key: const ValueKey('app-update-authorize-install'),
            onPressed: controller.authorizeAndInstall,
            child: const Text('前往授权并继续'),
          ),
        ];
      case AppUpdatePhase.installing:
        // 系统安装器是弹层，误触一下就没了。之前这里只有一个退出按钮，
        // 包已经在本地却没地方重新拉起安装。这个按钮不会重新下载。
        return [
          if (canUseLater)
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('暂时继续使用'),
            ),
          FilledButton(
            key: const ValueKey('app-update-reopen-installer'),
            onPressed: controller.authorizeAndInstall,
            child: const Text('重新打开安装'),
          ),
        ];
      case AppUpdatePhase.failed:
        return [
          if (canUseLater)
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('暂时继续使用'),
            ),
          FilledButton(
            onPressed: controller.startDownload,
            child: const Text('重新下载'),
          ),
        ];
      default:
        return [
          if (canUseLater)
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('暂不更新'),
            ),
          FilledButton(
            key: const ValueKey('app-update-start-download'),
            onPressed: controller.startDownload,
            child: const Text('立即更新'),
          ),
        ];
    }
  }

  String _downloadLabel(double? progress) {
    if (progress == null) return '正在下载更新包';
    return '正在下载 ${(progress * 100).clamp(0, 100).round()}%';
  }
}
