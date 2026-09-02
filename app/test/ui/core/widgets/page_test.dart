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

  testWidgets('fallback pages use the same labeled back entry as parent pages',
      (tester) async {
    // 回退路由以前只渲染一个光箭头的 IconButton，
    // 现在跟笼位管理页面一样带“返回”文字。
    final router = GoRouter(
      initialLocation: '/child',
      routes: [
        GoRoute(
          path: '/parent',
          builder: (_, __) => const Scaffold(body: Text('父页面')),
        ),
        GoRoute(
          path: '/child',
          builder: (_, __) => const AppPage(
            title: '回退路由页面',
            fallbackBackLocation: '/parent',
            child: SizedBox.expand(),
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

    final backButton = find.byKey(const ValueKey('page-back-button'));
    expect(backButton, findsOneWidget);
    expect(
      find.descendant(of: backButton, matching: find.text('返回')),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: backButton,
        matching: find.byIcon(Icons.arrow_back),
      ),
      findsOneWidget,
    );
    expect(tester.getSize(backButton).height, greaterThanOrEqualTo(48));
    final appBar = tester.widget<AppBar>(find.byType(AppBar));
    expect(appBar.leadingWidth, isNotNull);
    expect(appBar.leadingWidth, greaterThan(56));
  });

  testWidgets('a custom AppBackButton leading still gets back-entry width',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: buildAppTheme(),
        home: AppPage(
          title: '协议详情',
          leading: AppBackButton(onPressed: () {}),
          child: const SizedBox.expand(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('返回'), findsOneWidget);
    final appBar = tester.widget<AppBar>(find.byType(AppBar));
    expect(appBar.leadingWidth, greaterThan(56));
  });

  testWidgets('AppBackButton can relabel without losing the shared shape',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: buildAppTheme(),
        home: Builder(
          builder: (context) => Scaffold(
            appBar: AppBar(
              leadingWidth: appBackLeadingWidth(context, label: '退出'),
              leading: AppBackButton(
                buttonKey: const ValueKey('nfc-write-exit-button'),
                icon: Icons.close,
                label: '退出',
                tooltip: '退出写入',
                onPressed: () {},
              ),
              title: const Text('连续写标签'),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final exit = find.byKey(const ValueKey('nfc-write-exit-button'));
    expect(exit, findsOneWidget);
    expect(find.text('退出'), findsOneWidget);
    expect(find.byIcon(Icons.close), findsOneWidget);
    expect(tester.getSize(exit).height, greaterThanOrEqualTo(48));
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
