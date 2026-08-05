import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/house_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/nfc_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/rabbit_repository.dart';
import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_cages_screen.dart';

void main() {
  testWidgets('small cage list is available in a single batch', (tester) async {
    await tester.pumpWidget(_testApp(_cages(12)));
    await tester.pumpAndSettle();

    expect(find.text('总笼位 12'), findsOneWidget);
    expect(_cageGridChildCount(tester), 12);
  });

  testWidgets('large cage list loads more near the page bottom',
      (tester) async {
    await tester.pumpWidget(_testApp(_cages(45)));
    await tester.pumpAndSettle();

    expect(find.text('总笼位 45'), findsOneWidget);
    expect(_cageGridChildCount(tester), 20);

    await tester.drag(
      find.byKey(const ValueKey('house-cage-list-scroll')),
      const Offset(0, -4000),
    );
    await tester.pumpAndSettle();

    expect(_cageGridChildCount(tester), 40);

    await tester.drag(
      find.byKey(const ValueKey('house-cage-list-scroll')),
      const Offset(0, -4000),
    );
    await tester.pumpAndSettle();

    expect(_cageGridChildCount(tester), 45);
  });
}

Widget _testApp(List<Cage> cages) {
  return ProviderScope(
    overrides: [
      housesProvider.overrideWith((_) async => const [_house]),
      houseCagesProvider(8).overrideWith((_) async => cages),
      housePermissionProvider(8).overrideWith(
        (_) async => const HousePermission(perms: 'view', isAdmin: false),
      ),
      nfcCageWriteQueueProvider(8)
          .overrideWith((_) async => const <NfcCageQueueItem>[]),
    ],
    child: MaterialApp(
      theme: buildAppTheme(),
      home: const HouseCagesScreen(houseId: 8),
    ),
  );
}

int? _cageGridChildCount(WidgetTester tester) {
  final grid = tester.widget<GridView>(
    find.byKey(const ValueKey('house-cage-grid')),
  );
  return grid.childrenDelegate.estimatedChildCount;
}

List<Cage> _cages(int count) {
  return List.generate(
    count,
    (index) => Cage(
      id: index + 1,
      houseId: 8,
      cageNumber: '1-1-${index + 1}',
      status: '0',
      rabbitCount: 0,
      isEnabled: true,
    ),
  );
}

const _house = RabbitHouse(
  id: 8,
  name: '测试兔舍',
  remark: '',
  layoutRows: 5,
  layoutCols: 9,
  layoutLayers: 1,
);
