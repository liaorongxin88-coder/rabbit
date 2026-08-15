import 'package:flutter/material.dart';

class AppPage extends StatelessWidget {
  const AppPage({
    super.key,
    required this.title,
    required this.child,
    this.leading,
    this.actions,
  });

  final String title;
  final Widget child;
  final Widget? leading;
  final List<Widget>? actions;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: leading,
        title: Text(
          title,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          softWrap: false,
        ),
        actions: actions,
      ),
      body: SafeArea(
        top: false,
        child: ColoredBox(
          color: Theme.of(context).scaffoldBackgroundColor,
          child: child,
        ),
      ),
    );
  }
}
