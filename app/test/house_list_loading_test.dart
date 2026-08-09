import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/houses_screen.dart';

void main() {
  testWidgets('empty house list explains creation and phone invitation',
      (tester) async {
    await tester.pumpWidget(_testApp(const []));
    await tester.pumpAndSettle();

    expect(find.text('尚未加入兔舍'), findsOneWidget);
    expect(find.textContaining('通过手机号邀请'), findsOneWidget);
    expect(find.text('创建兔舍'), findsOneWidget);
  });

  testWidgets('small house list is available in a single batch',
      (tester) async {
    final houses = _houses(12);
    await tester.pumpWidget(_testApp(houses));
    await tester.pumpAndSettle();

    expect(find.text('共 12 个兔舍，点击兔舍进入管理。'), findsOneWidget);
    expect(_houseListChildCount(tester), 13);
  });

  testWidgets('large house list loads more when approaching the bottom',
      (tester) async {
    final houses = _houses(45);
    await tester.pumpWidget(_testApp(houses));
    await tester.pumpAndSettle();

    expect(find.text('共 45 个兔舍，点击兔舍进入管理。'), findsOneWidget);
    expect(_houseListChildCount(tester), 22);

    await tester.drag(
      find.byKey(const ValueKey('house-list')),
      const Offset(0, -4000),
    );
    await tester.pumpAndSettle();
    expect(_houseListChildCount(tester), 46);
  });
}

Widget _testApp(List<RabbitHouse> houses) {
  return ProviderScope(
    overrides: [
      housesProvider.overrideWith((_) async => houses),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: const HousesScreen(),
    ),
  );
}

int? _houseListChildCount(WidgetTester tester) {
  final listView = tester.widget<ListView>(
    find.byKey(const ValueKey('house-list')),
  );
  return listView.childrenDelegate.estimatedChildCount;
}

List<RabbitHouse> _houses(int count) {
  return List.generate(
    count,
    (index) => RabbitHouse(
      id: index + 1,
      name: '测试兔舍 ${index + 1}',
      remark: '',
      layoutRows: 1,
      layoutCols: 1,
      layoutLayers: 1,
    ),
  );
}
