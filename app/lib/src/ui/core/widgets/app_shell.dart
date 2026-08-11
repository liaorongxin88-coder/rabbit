import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';

class AppShell extends StatelessWidget {
  const AppShell({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;
    final palette = AppPalette.of(context);

    return Scaffold(
      body: child,
      bottomNavigationBar: DecoratedBox(
        decoration: BoxDecoration(
          border: Border(top: BorderSide(color: palette.line)),
        ),
        child: NavigationBar(
          selectedIndex: _indexFor(location),
          onDestinationSelected: (index) => context.go(_routes[index]),
          destinations: const [
            NavigationDestination(
              icon: Icon(Icons.home_outlined),
              selectedIcon: Icon(Icons.home),
              label: '首页',
            ),
            NavigationDestination(
              key: ValueKey('nav-houses'),
              icon: Icon(Icons.storefront_outlined),
              selectedIcon: Icon(Icons.storefront),
              label: '兔舍',
            ),
            NavigationDestination(
              icon: Icon(Icons.grid_view_outlined),
              selectedIcon: Icon(Icons.grid_view),
              label: '数据面板',
            ),
            NavigationDestination(
              key: ValueKey('nav-profile'),
              icon: Icon(Icons.person_outline),
              selectedIcon: Icon(Icons.person),
              label: '我的',
            ),
          ],
        ),
      ),
    );
  }

  static const _routes = ['/', '/houses', '/dashboard', '/profile'];

  static int _indexFor(String location) {
    if (location.startsWith('/houses')) {
      return 1;
    }
    if (location.startsWith('/dashboard')) {
      return 2;
    }
    if (location.startsWith('/profile') || location.startsWith('/settings')) {
      return 3;
    }
    return 0;
  }
}
