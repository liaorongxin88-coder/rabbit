import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/screens/list.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/queue.dart';

void main() {
  for (final size in const [Size(360, 800), Size(412, 915)]) {
    testWidgets(
      'NFC queue error remains visible and retryable at true 200 percent on '
      '${size.width.toInt()}x${size.height.toInt()}',
      (tester) async {
        await tester.binding.setSurfaceSize(size);
        tester.platformDispatcher.textScaleFactorTestValue = 2;
        addTearDown(() => tester.binding.setSurfaceSize(null));
        addTearDown(
          tester.platformDispatcher.clearTextScaleFactorTestValue,
        );
        var calls = 0;

        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              housesProvider.overrideWith((_) async => const [_house]),
              houseCagesProvider(8).overrideWith((_) async => const <Cage>[]),
              housePermissionProvider(8).overrideWith(
                (_) async =>
                    const HousePermission(perms: 'view', isAdmin: false),
              ),
              nfcCageWriteQueueProvider(8).overrideWith((_) async {
                calls++;
                throw StateError('network unavailable');
              }),
            ],
            child: MaterialApp(
              theme: buildAppTheme(),
              home: const HouseCagesScreen(houseId: 8),
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byKey(const ValueKey('nfc-status-error')), findsOneWidget);
        expect(find.text('NFC 状态加载失败，请重试'), findsOneWidget);
        expect(find.text('重试'), findsOneWidget);
        final firstCalls = calls;
        final retry = find.byKey(const ValueKey('nfc-status-retry'));
        await tester.ensureVisible(retry);
        await tester.pumpAndSettle();
        await tester.tap(retry);
        await tester.pumpAndSettle();
        expect(calls, greaterThan(firstCalls));
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('笼位文字操作在 360x800 和 200% 字号下可见可用', (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(() => tester.binding.setSurfaceSize(null));
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          housesProvider.overrideWith((_) async => const [_house]),
          houseCagesProvider(8).overrideWith((_) async => const [_cage]),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'control',
              isAdmin: true,
            ),
          ),
          nfcCageWriteQueueProvider(8).overrideWith(
            (_) async => const [
              NfcCageQueueItem(
                cageId: 10,
                cageNumber: '1-1-1',
                bindingStatus: 'BOUND',
                tagUid: 'ABCD',
                payload: 'r1.8.a.1.signature',
              ),
            ],
          ),
          pendingCommodityAllocationCountProvider(8)
              .overrideWith((_) async => 4),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const HouseCagesScreen(houseId: 8),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), '1');
    await tester.pumpAndSettle();
    expect(find.text('清空'), findsOneWidget);
    expect(find.text('出库'), findsOneWidget);
    expect(find.text('写标签'), findsOneWidget);
    expect(find.text('刷新'), findsOneWidget);
    expect(find.text('待分配入笼商品兔 4 只'), findsOneWidget);

    final createCage = find.byKey(const ValueKey('cage-create-entry'));
    await tester.ensureVisible(createCage);
    await tester.pumpAndSettle();
    await tester.tap(createCage);
    await tester.pumpAndSettle();
    expect(find.text('关闭'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

const _cage = Cage(
  id: 10,
  houseId: 8,
  cageNumber: '1-1-1',
  rowCode: '1',
  layerIndex: 1,
  positionIndex: 1,
  status: '3',
  rabbitCount: 2,
  isEnabled: true,
);

const _house = RabbitHouse(
  id: 8,
  name: '测试兔舍',
  remark: '',
  layoutRows: 1,
  layoutCols: 1,
  layoutLayers: 1,
);
