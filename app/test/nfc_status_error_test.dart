import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_cages_screen.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/nfc_queue_provider.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

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
        final firstCalls = calls;
        await tester.tap(find.byKey(const ValueKey('nfc-status-retry')));
        await tester.pumpAndSettle();
        expect(calls, greaterThan(firstCalls));
        expect(tester.takeException(), isNull);
      },
    );
  }
}

const _house = RabbitHouse(
  id: 8,
  name: '测试兔舍',
  remark: '',
  layoutRows: 1,
  layoutCols: 1,
  layoutLayers: 1,
);
