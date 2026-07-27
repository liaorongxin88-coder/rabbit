import 'package:flutter/material.dart';

import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';

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
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
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
