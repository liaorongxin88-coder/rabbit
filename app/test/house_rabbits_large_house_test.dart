import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_detail_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_rabbits_screen.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

void main() {
  testWidgets('large house shows complete total and lazily builds rabbit rows',
      (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final rabbits = _rabbits(1001);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith((_) async => const [_house]),
          houseCagesProvider(8).overrideWith((_) async => const [_cage]),
          houseRabbitsProvider(8).overrideWith((_) async => rabbits),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'control',
              isAdmin: true,
            ),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseRabbitsScreen(houseId: 8),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('共 1001 只 · 已全部加载'), findsOneWidget);
    expect(find.byKey(const ValueKey('house-rabbit-1')), findsOneWidget);
    expect(find.byKey(const ValueKey('house-rabbit-1001')), findsNothing);

    final list = tester.widget<ListView>(
      find.byKey(const ValueKey('house-rabbit-list')),
    );
    expect(list.childrenDelegate, isA<SliverChildBuilderDelegate>());
    expect(list.childrenDelegate.estimatedChildCount, 1006);
    expect(tester.takeException(), isNull);

    tester.view.physicalSize = const Size(412, 915);
    await tester.pump();

    expect(find.text('共 1001 只 · 已全部加载'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('house detail reports the fully loaded rabbit count',
      (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith((_) async => const [_house]),
          houseCagesProvider(8).overrideWith((_) async => const [_cage]),
          houseRabbitsProvider(8).overrideWith((_) async => _rabbits(1001)),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'control',
              isAdmin: true,
            ),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseDetailScreen(houseId: 8),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('1001'), findsOneWidget);
    expect(find.text('已全部加载'), findsNWidgets(2));
    expect(tester.takeException(), isNull);
  });
}

const _house = RabbitHouse(
  id: 8,
  name: '规模兔舍',
  remark: '',
  layoutRows: 10,
  layoutCols: 20,
  layoutLayers: 5,
);

const _cage = Cage(
  id: 1,
  houseId: 8,
  cageNumber: 'A-001',
  status: '3',
  rabbitCount: 1001,
  isEnabled: true,
);

List<Rabbit> _rabbits(int count) {
  return List.generate(
    count,
    (index) => Rabbit(
      id: index + 1,
      houseId: 8,
      cageId: 1,
      motherId: null,
      type: '2',
      gender: index.isEven ? '0' : '1',
      breed: '新西兰白兔',
      arrivalMethod: '自繁',
      arrivalDate: DateTime(2025, 1, 1),
      weight: 2.5,
      isActive: true,
    ),
  );
}
