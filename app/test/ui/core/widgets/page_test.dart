import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/page.dart';

void main() {
  testWidgets('AppPage shows a scalable text back entry at 200 percent',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

    final router = GoRouter(
      initialLocation: '/child',
      routes: [
        GoRoute(
          path: '/parent',
          builder: (_, __) => const Scaffold(body: Text('父页面')),
        ),
        GoRoute(
          path: '/child',
          builder: (_, __) => AppPage(
            title: '一号大型繁育兔舍完整批次数据管理',
            parentRoute: '/parent',
            actions: [
              IconButton(
                tooltip: '刷新',
                onPressed: () {},
                icon: const Icon(Icons.refresh),
              ),
            ],
            child: const SizedBox.expand(),
          ),
        ),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      MaterialApp.router(
        theme: buildAppTheme(),
        routerConfig: router,
      ),
    );
    await tester.pumpAndSettle();

    final title = tester.widget<Text>(
      find.text('一号大型繁育兔舍完整批次数据管理'),
    );
    final appBar = tester.widget<AppBar>(find.byType(AppBar));
    final backButton = find.byKey(const ValueKey('page-back-button'));
    expect(find.text('返回'), findsOneWidget);
    expect(tester.getSize(backButton).height, greaterThanOrEqualTo(48));
    expect(appBar.leadingWidth, greaterThan(80));
    expect(appBar.toolbarHeight, greaterThan(kToolbarHeight));
    expect(title.maxLines, 2);
    expect(title.overflow, TextOverflow.ellipsis);
    expect(title.softWrap, isTrue);
    expect(
      tester.getSize(find.text(title.data!)).height,
      greaterThanOrEqualTo(48),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('legacy fallback keeps pop-first navigation semantics',
      (tester) async {
    late GoRouter router;
    router = GoRouter(
      initialLocation: '/source',
      routes: [
        GoRoute(
          path: '/source',
          builder: (_, __) => Scaffold(
            body: FilledButton(
              onPressed: () => router.push('/legacy'),
              child: const Text('普通进入'),
            ),
          ),
        ),
        GoRoute(
          path: '/legacy',
          builder: (_, __) => const AppPage(
            title: '未迁移页面',
            fallbackBackLocation: '/fallback',
            child: SizedBox.expand(),
          ),
        ),
        GoRoute(
          path: '/fallback',
          builder: (_, __) => const Scaffold(body: Text('回退页面')),
        ),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      MaterialApp.router(
        theme: buildAppTheme(),
        routerConfig: router,
      ),
    );
    await tester.tap(find.text('普通进入'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('page-back-button')));
    await tester.pumpAndSettle();
    expect(find.text('普通进入'), findsOneWidget);

    router.go('/legacy');
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('page-back-button')));
    await tester.pumpAndSettle();
    expect(find.text('回退页面'), findsOneWidget);
  });
}
