import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/domain/settings/local.dart';
import 'package:rabbit_flutter/src/ui/app_update/view_models/controller.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/local.dart';

class AppSettingsScreen extends ConsumerWidget {
  const AppSettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(localAppSettingsControllerProvider);
    return AppPage(
      title: '应用设置',
      fallbackBackLocation: '/profile',
      child: settings.when(
        data: (data) => _AppSettingsContent(settings: data),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ErrorState(
          message: error.toString(),
          onRetry: () =>
              ref.read(localAppSettingsControllerProvider.notifier).restore(),
        ),
      ),
    );
  }
}

class _AppSettingsContent extends ConsumerWidget {
  const _AppSettingsContent({required this.settings});

  final LocalAppSettings settings;

  static const _startRoutes = [
    _StartRouteChoice('/', '首页', Icons.home_outlined),
    _StartRouteChoice('/houses', '兔舍', Icons.storefront_outlined),
    _StartRouteChoice('/dashboard', '数据面板', Icons.grid_view_outlined),
    _StartRouteChoice('/profile', '我的', Icons.person_outline),
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.read(localAppSettingsControllerProvider.notifier);
    return ListView(
      padding: AppSpacing.pagePadding,
      children: [
        SectionCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('主题模式', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 12),
              SegmentedButton<ThemeMode>(
                key: const ValueKey('app-theme-mode'),
                segments: const [
                  ButtonSegment(
                    value: ThemeMode.system,
                    icon: Icon(Icons.brightness_auto_outlined),
                    label: Text('系统'),
                  ),
                  ButtonSegment(
                    value: ThemeMode.light,
                    icon: Icon(Icons.light_mode_outlined),
                    label: Text('浅色'),
                  ),
                  ButtonSegment(
                    value: ThemeMode.dark,
                    icon: Icon(Icons.dark_mode_outlined),
                    label: Text('深色'),
                  ),
                ],
                selected: {settings.themeMode},
                onSelectionChanged: (values) {
                  controller.setThemeMode(values.first);
                },
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        SectionCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('默认启动页', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              Text(
                '下次打开应用时进入：${settings.startRouteLabel}',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 12),
              for (final route in _startRoutes) ...[
                RadioListTile<String>(
                  key: ValueKey('app-start-route-${route.route}'),
                  contentPadding: EdgeInsets.zero,
                  value: route.route,
                  groupValue: settings.startRoute,
                  onChanged: (value) {
                    if (value != null) {
                      controller.setStartRoute(value);
                    }
                  },
                  secondary: Icon(route.icon, color: AppColors.blue),
                  title: Text(route.label),
                ),
                if (route != _startRoutes.last) const Divider(height: 1),
              ],
            ],
          ),
        ),
        const SizedBox(height: 12),
        SectionCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('本地缓存', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              Text(
                '清除主题和启动页偏好，不会退出登录，也不会修改后端地址。',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 14),
              OutlinedButton.icon(
                key: const ValueKey('app-clear-local-button'),
                onPressed: () => _clearLocalPreferences(context, ref),
                icon: const Icon(Icons.cleaning_services_outlined),
                label: const Text('清理本地设置'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _UpdateCard(),
      ],
    );
  }

  Future<void> _clearLocalPreferences(
      BuildContext context, WidgetRef ref) async {
    await ref
        .read(localAppSettingsControllerProvider.notifier)
        .clearLocalPreferences();
    if (!context.mounted) {
      return;
    }
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('本地设置已恢复默认')),
    );
  }
}

class _UpdateCard extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(appUpdateControllerProvider);
    final checking = state.phase == AppUpdatePhase.checking;
    final downloading = state.phase == AppUpdatePhase.downloading;
    final version = state.currentVersion?.label ?? '未读取';
    final description = switch (state.phase) {
      AppUpdatePhase.upToDate => '当前已是最新版',
      AppUpdatePhase.available => '发现新版本 ${state.release!.versionName}',
      AppUpdatePhase.failed => state.message ?? '检查更新失败',
      AppUpdatePhase.downloading => '正在下载更新包',
      AppUpdatePhase.permissionRequired => '下载完成，等待安装授权',
      AppUpdatePhase.installing => '系统安装器已打开',
      _ => '检查新版本并安装',
    };

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('应用更新', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Text('当前版本：$version', style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: 4),
          Text(description, style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: 14),
          OutlinedButton.icon(
            key: const ValueKey('app-update-check-button'),
            onPressed: checking || downloading
                ? null
                : () => ref.read(appUpdateControllerProvider.notifier).check(),
            icon: checking
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.system_update_outlined),
            label: Text(checking ? '正在检查' : '检查更新'),
          ),
        ],
      ),
    );
  }
}

class _StartRouteChoice {
  const _StartRouteChoice(this.route, this.label, this.icon);

  final String route;
  final String label;
  final IconData icon;
}
