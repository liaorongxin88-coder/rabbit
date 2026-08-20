import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class AppPage extends StatelessWidget {
  const AppPage({
    super.key,
    required this.title,
    required this.child,
    this.leading,
    this.actions,
    this.fallbackBackLocation,
  });

  final String title;
  final Widget child;
  final Widget? leading;
  final List<Widget>? actions;
  final String? fallbackBackLocation;

  @override
  Widget build(BuildContext context) {
    final fallbackLocation = fallbackBackLocation;
    final page = Scaffold(
      appBar: AppBar(
        leading: leading ??
            (fallbackLocation == null
                ? null
                : IconButton(
                    key: const ValueKey('page-back-button'),
                    tooltip: '返回上一页',
                    onPressed: () => _goBack(context, fallbackLocation),
                    icon: const Icon(Icons.arrow_back),
                  )),
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

    if (fallbackLocation == null) {
      return page;
    }
    return PopScope<Object?>(
      canPop: Navigator.of(context).canPop(),
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) {
          context.go(fallbackLocation);
        }
      },
      child: page,
    );
  }

  void _goBack(BuildContext context, String fallbackLocation) {
    final navigator = Navigator.of(context);
    if (navigator.canPop()) {
      navigator.pop();
      return;
    }
    context.go(fallbackLocation);
  }
}
