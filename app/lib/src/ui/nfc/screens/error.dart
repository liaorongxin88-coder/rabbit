import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/states.dart';

class NfcErrorScreen extends StatelessWidget {
  const NfcErrorScreen({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return AppPage(
      title: 'NFC标签',
      fallbackBackLocation: '/',
      child: EmptyState(
        icon: Icons.nfc_outlined,
        title: '无法打开笼位',
        message: message,
        actionLabel: '返回首页',
        onAction: () => context.go('/'),
      ),
    );
  }
}
