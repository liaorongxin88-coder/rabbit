import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/local.dart';

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
      child: ListView(
        padding: AppSpacing.pagePadding,
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
          const SizedBox(height: 16),
          _ProfileSection(
            title: '账号',
            children: [
              _ProfileEntry(
                entryKey: const ValueKey('profile-entry-account'),
                icon: Icons.manage_accounts_outlined,
                iconColor: palette.primary,
                iconBackground: palette.primarySoft,
                title: '账号与安全',
                subtitle: '账号、手机号、用户名和登录密码',
                onTap: () => context.push('/settings/account'),
              ),
            ],
          ),
          const SizedBox(height: 16),
          _ProfileSection(
            title: '兔舍管理',
            children: [
              _ProfileEntry(
                entryKey: const ValueKey('profile-entry-reminders'),
                icon: Icons.notifications_active_outlined,
                iconColor: palette.warning,
                iconBackground: palette.warningSoft,
                title: '事件提醒',
                subtitle: '按兔舍设置提醒类型和提前天数',
                onTap: () => context.push('/settings/reminders'),
              ),
              const Divider(height: 1),
              _ProfileEntry(
                entryKey: const ValueKey('profile-entry-production'),
                icon: Icons.calendar_month_outlined,
                iconColor: palette.success,
                iconBackground: palette.successSoft,
                title: '默认生产设置',
                subtitle: '为新建兔舍预设生产周期',
                onTap: () => context.push('/settings/production'),
              ),
            ],
          ),
          const SizedBox(height: 16),
          _ProfileSection(
            title: '应用',
            children: [
              _ProfileEntry(
                entryKey: const ValueKey('profile-entry-app'),
                icon: Icons.tune_outlined,
                iconColor: palette.primary,
                iconBackground: palette.primarySoft,
                title: '应用设置',
                subtitle: localSettings == null
                    ? '主题、默认启动页和本地设置'
                    : '${localSettings.themeLabel} · 默认${localSettings.startRouteLabel}',
                onTap: () => context.push('/settings/app'),
              ),
              const Divider(height: 1),
              _ProfileEntry(
                entryKey: const ValueKey('profile-entry-about'),
                icon: Icons.info_outline,
                iconColor: palette.success,
                iconBackground: palette.successSoft,
                title: '关于鸿兔智管',
                subtitle: '隐私政策和用户协议',
                onTap: () => context.push('/settings'),
              ),
            ],
          ),
          const SizedBox(height: 20),
          OutlinedButton.icon(
            key: const ValueKey('profile-logout-button'),
            onPressed: () => ref.read(authControllerProvider.notifier).logout(),
            icon: const Icon(Icons.logout),
            label: const Text('退出登录'),
          ),
        ],
      ),
    );
  }
}

class _ProfileSection extends StatelessWidget {
  const _ProfileSection({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 8),
          child: Text(
            title,
            style: Theme.of(context).textTheme.titleSmall,
          ),
        ),
        SectionCard(child: Column(children: children)),
      ],
    );
  }
}

class _ProfileEntry extends StatelessWidget {
  const _ProfileEntry({
    required this.entryKey,
    required this.icon,
    required this.iconColor,
    required this.iconBackground,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final Key entryKey;
  final IconData icon;
  final Color iconColor;
  final Color iconBackground;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      key: entryKey,
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
