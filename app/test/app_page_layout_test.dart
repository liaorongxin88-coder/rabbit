import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_page.dart';

void main() {
  testWidgets('AppPage truncates a long title beside actions at 200 percent',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

    await tester.pumpWidget(
      MaterialApp(
        theme: buildAppTheme(),
        home: AppPage(
          title: '一号大型繁育兔舍完整批次数据管理',
          actions: [
            IconButton(
              tooltip: '筛选',
              onPressed: () {},
              icon: const Icon(Icons.filter_list),
            ),
            IconButton(
              tooltip: '刷新',
              onPressed: () {},
              icon: const Icon(Icons.refresh),
            ),
          ],
          child: const SizedBox.expand(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final title = tester.widget<Text>(
      find.text('一号大型繁育兔舍完整批次数据管理'),
    );
    expect(title.maxLines, 1);
    expect(title.overflow, TextOverflow.ellipsis);
    expect(title.softWrap, isFalse);
    expect(tester.takeException(), isNull);
  });
}
