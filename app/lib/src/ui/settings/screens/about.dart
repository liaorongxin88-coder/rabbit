import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/services/app_update/package.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/update.dart';

class AboutScreen extends ConsumerWidget {
  const AboutScreen({super.key});

  static String channelLabel(String channel) {
    return switch (channel) {
      'prod' => '正式',
      'test' => '测试',
      _ => '开发',
    };
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final identity = ref.watch(appPackageIdentityProvider);
    final updateState = ref.watch(appUpdateControllerProvider);
    ref.listen(appUpdateControllerProvider, (previous, next) {
      if (previous?.phase != AppUpdatePhase.checking) {
        return;
      }
      if (next.phase == AppUpdatePhase.idle &&
          next.manual &&
          !next.update.hasUpdate &&
          context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('已是最新版本')),
        );
      }
    });

    return AppPage(
      title: '关于应用',
      fallbackBackLocation: '/settings',
      child: identity.when(
        data: (data) => ListView(
          padding: AppSpacing.pagePadding,
          children: [
            SectionCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('鸿兔智管', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Text(
                    '当前版本 ${data.versionName}（${data.versionCode}）',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '渠道：${channelLabel(data.channel)}',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            FilledButton.icon(
              key: const ValueKey('about-check-update'),
              onPressed: updateState.phase == AppUpdatePhase.checking ||
                      updateState.phase == AppUpdatePhase.downloading
                  ? null
                  : () => ref
                      .read(appUpdateControllerProvider.notifier)
                      .check(manual: true),
              icon: updateState.phase == AppUpdatePhase.checking
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.system_update_alt),
              label: Text(
                updateState.phase == AppUpdatePhase.checking ? '正在检查' : '检查更新',
              ),
            ),
          ],
        ),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () => ref.invalidate(appPackageIdentityProvider),
        ),
      ),
    );
  }
}
