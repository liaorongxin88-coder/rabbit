import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/local_app_settings_controller.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(authControllerProvider).valueOrNull;
    final localSettings =
        ref.watch(localAppSettingsControllerProvider).valueOrNull;
    final palette = AppPalette.of(context);

    return AppPage(
      title: '我的',
      actions: [
        IconButton(
          tooltip: '设置',
          onPressed: () => context.go('/settings'),
          icon: const Icon(Icons.settings_outlined),
        ),
      ],
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 14, 20, 22),
        children: [
          SectionCard(
            child: Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: palette.primarySoft,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(Icons.person, color: palette.primary),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        session != null && session.userName.isNotEmpty
                            ? session.userName
                            : '已登录用户',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '用户ID：${session?.userId ?? '-'}',
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          SectionCard(
            child: Column(
              children: [
                _ProfileEntry(
                  icon: Icons.manage_accounts_outlined,
                  iconColor: palette.primary,
                  iconBackground: palette.primarySoft,
                  title: '账号设置',
                  subtitle: '用户名、密码和登录资料',
                  onTap: () => context.go('/settings/account'),
                ),
                const Divider(height: 1),
                _ProfileEntry(
                  icon: Icons.tune_outlined,
                  iconColor: palette.success,
                  iconBackground: palette.successSoft,
                  title: '应用设置',
                  subtitle: localSettings == null
                      ? '主题、启动页和本地缓存'
                      : '${localSettings.themeLabel} · 默认${localSettings.startRouteLabel}',
                  onTap: () => context.go('/settings/app'),
                ),
                const Divider(height: 1),
                _ProfileEntry(
                  icon: Icons.calendar_month_outlined,
                  iconColor: palette.warning,
                  iconBackground: palette.warningSoft,
                  title: '兔舍生产设置',
                  subtitle: '所有兔舍共用的周期配置',
                  onTap: () => context.go('/settings/production'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          OutlinedButton.icon(
            onPressed: () => ref.read(authControllerProvider.notifier).logout(),
            icon: const Icon(Icons.logout),
            label: const Text('退出登录'),
          ),
        ],
      ),
    );
  }
}

class _ProfileEntry extends StatelessWidget {
  const _ProfileEntry({
    required this.icon,
    required this.iconColor,
    required this.iconBackground,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final Color iconColor;
  final Color iconBackground;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: iconBackground,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Icon(icon, color: iconColor),
      ),
      title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    );
  }
}
