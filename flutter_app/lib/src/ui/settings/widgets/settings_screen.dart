import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/state_views.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return AppPage(
      title: '设置',
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 14, 20, 22),
        children: [
          EmptyState(
            icon: Icons.tune_outlined,
            title: '设置正在调整',
            message: '已取消全局兔舍选择。生产周期配置后续会从具体兔舍详情进入，避免首页和全局状态互相影响。',
            actionLabel: '查看兔舍',
            onAction: () => context.go('/houses'),
          ),
        ],
      ),
    );
  }
}
