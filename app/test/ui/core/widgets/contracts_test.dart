import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/header.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/notice.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/sheet.dart';

void main() {
  testWidgets('shared context and notice widgets remain usable at 200 percent',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      MaterialApp(
        theme: buildAppTheme(),
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2)),
          child: Scaffold(
            body: ListView(
              padding: const EdgeInsets.all(16),
              children: const [
                ContextHeaderCard(
                  title: '东区标准化繁育兔舍',
                  subtitle: '跨业务页面共享同一份兔舍上下文展示',
                  expandForLargeText: true,
                  footer: Text('附加操作区'),
                ),
                SizedBox(height: 12),
                InfoNotice(
                  icon: Icons.info_outline,
                  text: '这是一条跨业务复用的信息提示。',
                ),
              ],
            ),
          ),
        ),
      ),
    );

    expect(find.text('东区标准化繁育兔舍'), findsOneWidget);
    expect(find.text('附加操作区'), findsOneWidget);
    expect(find.byIcon(Icons.info_outline), findsOneWidget);
    expect(tester.takeException(), isNull);

    // 返回入口只留在 AppBar，上下文卡片不再重复一个。
    expect(find.byTooltip('返回'), findsNothing);
  });

  testWidgets('shared modal sheet opens workflow content', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: buildAppTheme(),
        home: Scaffold(
          body: Builder(
            builder: (context) => FilledButton(
              onPressed: () => showAppModalSheet<void>(
                context: context,
                builder: (_) => const SizedBox(
                  height: 240,
                  child: Center(child: Text('共享业务弹窗')),
                ),
              ),
              child: const Text('打开'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('打开'));
    await tester.pumpAndSettle();

    expect(find.text('共享业务弹窗'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
