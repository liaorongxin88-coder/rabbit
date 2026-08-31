import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class AppPage extends StatefulWidget {
  const AppPage({
    super.key,
    required this.title,
    required this.child,
    this.leading,
    this.actions,
    this.parentRoute,
    this.fallbackBackLocation,
  })  : assert(parentRoute == null || fallbackBackLocation == null),
        assert(parentRoute == null || leading == null);

  final String title;
  final Widget child;
  final Widget? leading;
  final List<Widget>? actions;
  final String? parentRoute;
  final String? fallbackBackLocation;

  @override
  State<AppPage> createState() => _AppPageState();
}

class _AppPageState extends State<AppPage> {
  var _navigatingToParent = false;

  @override
  Widget build(BuildContext context) {
    final parentRoute = widget.parentRoute;
    final fallbackLocation = widget.fallbackBackLocation;
    final hasGeneratedLeading = parentRoute != null || fallbackLocation != null;
    final usesLargeText =
        MediaQuery.textScalerOf(context).scale(10) / 10 >= 1.3;
    final page = Scaffold(
      appBar: AppBar(
        toolbarHeight: usesLargeText ? _largeTextToolbarHeight(context) : null,
        leadingWidth: parentRoute == null ? null : _backLeadingWidth(context),
        leading: widget.leading ??
            (parentRoute != null
                ? _ParentBackButton(onPressed: _goToParent)
                : fallbackLocation == null
                    ? null
                    : IconButton(
                        key: const ValueKey('page-back-button'),
                        tooltip: '返回上一页',
                        onPressed: () => _goBack(fallbackLocation),
                        icon: const Icon(Icons.arrow_back),
                      )),
        automaticallyImplyLeading: !hasGeneratedLeading,
        title: Text(
          widget.title,
          maxLines: usesLargeText ? 2 : 1,
          overflow: TextOverflow.ellipsis,
          softWrap: usesLargeText,
        ),
        actions: widget.actions,
      ),
      body: SafeArea(
        top: false,
        child: ColoredBox(
          color: Theme.of(context).scaffoldBackgroundColor,
          child: widget.child,
        ),
      ),
    );

    if (parentRoute == null && fallbackLocation == null) {
      return page;
    }
    final canUseNavigatorHistory =
        parentRoute == null && Navigator.of(context).canPop();
    return PopScope<Object?>(
      canPop: canUseNavigatorHistory,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) {
          return;
        }
        if (parentRoute != null) {
          _goToParent();
        } else {
          context.go(fallbackLocation!);
        }
      },
      child: page,
    );
  }

  double _backLeadingWidth(BuildContext context) {
    final style = Theme.of(context).textTheme.labelLarge;
    final painter = TextPainter(
      text: TextSpan(text: '返回', style: style),
      textScaler: MediaQuery.textScalerOf(context),
      textDirection: Directionality.of(context),
      maxLines: 1,
    )..layout();
    return painter.width + 48;
  }

  double _largeTextToolbarHeight(BuildContext context) {
    final style = Theme.of(context).appBarTheme.titleTextStyle ??
        Theme.of(context).textTheme.titleLarge;
    final fontSize = style?.fontSize ?? 20;
    final lineHeight = MediaQuery.textScalerOf(context).scale(fontSize) *
        (style?.height ?? 1.2);
    final height = lineHeight * 2 + 8;
    return height < kToolbarHeight ? kToolbarHeight : height;
  }

  void _goToParent() {
    final parentRoute = widget.parentRoute;
    if (parentRoute == null || _navigatingToParent) {
      return;
    }
    _navigatingToParent = true;
    context.go(parentRoute);
  }

  void _goBack(String fallbackLocation) {
    final navigator = Navigator.of(context);
    if (navigator.canPop()) {
      navigator.pop();
      return;
    }
    context.go(fallbackLocation);
  }
}

class _ParentBackButton extends StatelessWidget {
  const _ParentBackButton({required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: '返回',
      child: TextButton(
        key: const ValueKey('page-back-button'),
        onPressed: onPressed,
        style: TextButton.styleFrom(
          minimumSize: const Size(0, 48),
          padding: const EdgeInsets.symmetric(horizontal: 8),
          tapTargetSize: MaterialTapTargetSize.padded,
        ),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.arrow_back, size: 24),
            SizedBox(width: 4),
            Text('返回'),
          ],
        ),
      ),
    );
  }
}
