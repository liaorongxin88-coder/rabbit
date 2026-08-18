import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/config/legal_documents.dart';
import 'package:rabbit_flutter/src/ui/auth/widgets/legal_document_screen.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return AppPage(
      title: '设置',
      child: ListView(
        padding: AppSpacing.pagePadding,
        children: [
          SectionCard(
            child: Column(
              children: [
                _SettingsEntry(
                  entryKey: const ValueKey('settings-entry-account'),
                  icon: Icons.manage_accounts_outlined,
                  iconColor: palette.primary,
                  iconBackground: palette.primarySoft,
                  title: '账号设置',
                  subtitle: '修改用户名和登录密码',
                  onTap: () => context.go('/settings/account'),
                ),
                const Divider(height: 1),
                _SettingsEntry(
                  entryKey: const ValueKey('settings-entry-app'),
                  icon: Icons.tune_outlined,
                  iconColor: palette.success,
                  iconBackground: palette.successSoft,
                  title: '应用设置',
                  subtitle: '主题、启动页、本地缓存',
                  onTap: () => context.go('/settings/app'),
                ),
                const Divider(height: 1),
                _SettingsEntry(
                  entryKey: const ValueKey('settings-entry-production'),
                  icon: Icons.calendar_month_outlined,
                  iconColor: palette.warning,
                  iconBackground: palette.warningSoft,
                  title: '兔舍生产设置',
                  subtitle: '配置所有兔舍共用的生产周期',
                  onTap: () => context.go('/settings/production'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          SectionCard(
            child: Column(
              children: [
                _SettingsEntry(
                  entryKey: const ValueKey('settings-entry-privacy'),
                  icon: Icons.privacy_tip_outlined,
                  iconColor: palette.primary,
                  iconBackground: palette.primarySoft,
                  title: '隐私政策',
                  subtitle: LegalDocuments.privacyPolicyUpdatedAt,
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (context) => const LegalDocumentScreen(
                        title: LegalDocuments.privacyPolicyTitle,
                        body: LegalDocuments.privacyPolicy,
                      ),
                    ),
                  ),
                ),
                const Divider(height: 1),
                _SettingsEntry(
                  entryKey: const ValueKey('settings-entry-agreement'),
                  icon: Icons.description_outlined,
                  iconColor: palette.success,
                  iconBackground: palette.successSoft,
                  title: '用户协议',
                  subtitle: LegalDocuments.userAgreementUpdatedAt,
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (context) => const LegalDocumentScreen(
                        title: LegalDocuments.userAgreementTitle,
                        body: LegalDocuments.userAgreement,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SettingsEntry extends StatelessWidget {
  const _SettingsEntry({
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
