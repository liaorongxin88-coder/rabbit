import 'package:flutter/material.dart';

import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';

class LegalDocumentScreen extends StatelessWidget {
  const LegalDocumentScreen({
    super.key,
    required this.title,
    required this.body,
  });

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    return AppPage(
      title: title,
      // 协议页是 Navigator push 进来的，用共享返回按钮保持与其他二级页面一致。
      leading: AppBackButton(
        onPressed: () => Navigator.of(context).maybePop(),
      ),
      child: ListView(
        padding: AppSpacing.pagePadding,
        children: [
          Text(
            body.trim(),
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                  height: 1.65,
                ),
          ),
        ],
      ),
    );
  }
}
