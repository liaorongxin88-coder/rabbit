import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// 二三级页面统一的返回入口文案，参照笼位管理页面的设计。
const appBackLabel = '返回';

/// 生成的返回入口统一使用这个 key；个别页面可以用 [AppPage.backButtonKey] 覆盖。
const appBackButtonKey = ValueKey('page-back-button');

/// 计算带文字返回入口所需的 AppBar leading 宽度。
///
/// 自建 Scaffold 的页面（批量出库、连续写标签）也用它，
/// 保证文字在大字号下不会被截断。
double appBackLeadingWidth(
  BuildContext context, {
  String label = appBackLabel,
}) {
  final style = Theme.of(context).textTheme.labelLarge;
  final painter = TextPainter(
    text: TextSpan(text: label, style: style),
    textScaler: MediaQuery.textScalerOf(context),
    textDirection: Directionality.of(context),
    maxLines: 1,
  )..layout();
  return painter.width + 48;
}

/// 全局统一的返回按钮：图标加文字，触摸目标不低于 48。
///
/// 笼位管理页面先落地这个样式，二三级页面全部复用它，
/// 不再各自写只有箭头图标的 [IconButton]。
class AppBackButton extends StatelessWidget {
  const AppBackButton({
    super.key,
    required this.onPressed,
    this.label = appBackLabel,
    this.icon = Icons.arrow_back,
    this.tooltip,
    this.buttonKey = appBackButtonKey,
  });

  final VoidCallback onPressed;
  final String label;
  final IconData icon;
  final String? tooltip;
  final Key? buttonKey;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip ?? label,
      child: TextButton(
        key: buttonKey,
        onPressed: onPressed,
        style: TextButton.styleFrom(
          minimumSize: const Size(0, 48),
          padding: const EdgeInsets.symmetric(horizontal: 8),
          tapTargetSize: MaterialTapTargetSize.padded,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 24),
            const SizedBox(width: 4),
            Text(label),
          ],
        ),
      ),
    );
  }
}

class AppPage extends StatefulWidget {
  const AppPage({
    super.key,
    required this.title,
    required this.child,
    this.leading,
    this.actions,
    this.parentRoute,
    this.fallbackBackLocation,
    this.backButtonKey = appBackButtonKey,
  })  : assert(parentRoute == null || fallbackBackLocation == null),
        assert(parentRoute == null || leading == null);

  final String title;
  final Widget child;
  final Widget? leading;
  final List<Widget>? actions;
  final String? parentRoute;
  final String? fallbackBackLocation;
  final Key? backButtonKey;

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
    // 固定父路由和回退路由都走同一个带文字的返回入口，
    // 自定义 leading 只要用了 AppBackButton 也按同样的宽度排版。
    final usesLabeledBack = widget.leading == null
        ? hasGeneratedLeading
        : widget.leading is AppBackButton;
    final page = Scaffold(
      appBar: AppBar(
        toolbarHeight: usesLargeText ? _largeTextToolbarHeight(context) : null,
        leadingWidth: usesLabeledBack ? appBackLeadingWidth(context) : null,
        leading: widget.leading ??
            (parentRoute != null
                ? AppBackButton(
                    buttonKey: widget.backButtonKey,
                    onPressed: _goToParent,
                  )
                : fallbackLocation == null
                    ? null
                    : AppBackButton(
                        buttonKey: widget.backButtonKey,
                        tooltip: '返回上一页',
                        onPressed: () => _goBack(fallbackLocation),
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
