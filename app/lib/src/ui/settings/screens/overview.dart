import 'package:flutter/material.dart';

import 'package:rabbit_flutter/src/config/legal.dart';
import 'package:rabbit_flutter/src/ui/auth/screens/legal.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    return AppPage(
      title: '关于鸿兔智管',
      fallbackBackLocation: '/profile',
      child: ListView(
        padding: AppSpacing.pagePadding,
        children: [
          SectionCard(
            child: Row(
              key: const ValueKey('settings-about-summary'),
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: palette.primarySoft,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(Icons.pets_outlined, color: palette.primary),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '鸿兔智管',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '兔舍生产管理工具',
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.only(left: 4, bottom: 8),
            child: Text(
              '法律与隐私',
              style: Theme.of(context).textTheme.titleSmall,
            ),
          ),
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
