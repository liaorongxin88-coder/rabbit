import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/domain/models/rabbit_house.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_cages_screen.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/nfc_queue_provider.dart';
import 'package:rabbit_flutter/src/ui/rabbits/view_models/rabbit_providers.dart';

/// 批量建笼生成的编号会被贴到笼子上，人拿着它对实物，所以它必须和地图对得上。
void main() {
  testWidgets('批量建笼编号是「排(层)位」：层号 1 在最下面，尾数是位号', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1000, 2400));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(_testApp());
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('cage-create-entry')));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const ValueKey('cage-bulk-row')), 'R2');
    await tester.enterText(find.byKey(const ValueKey('cage-bulk-layers')), '2');
    await tester.enterText(
      find.byKey(const ValueKey('cage-bulk-positions')),
      '3',
    );
    await tester.pumpAndSettle();

    final preview = _previewText(tester);

    // 层号 1 是最下面那层：现场从地面往上数。
    expect(preview, contains('R2(下)1'));
    expect(preview, contains('R2(上)1'));

    // 尾数是位号，不是流水号。写成流水号的话，两层时第 2 位会拿到 3、4——
    // 地图上那个格子写着「2」，笼上的签写着「3」，对实物时必错。
    expect(preview, contains('R2(下)2'));
    expect(preview, contains('R2(上)2'));
    expect(preview, contains('R2(下)3'));
    expect(preview, isNot(contains('R2(下)4')));
    expect(preview, isNot(contains('(上)6')));
  });
}

/// 预览是若干个 Text 拼出来的，这里合成一整串再断言。
String _previewText(WidgetTester tester) {
  return tester
      .widgetList<Text>(find.byType(Text))
      .map((text) => text.data ?? '')
      .join('\n');
}

Widget _testApp() {
  return ProviderScope(
    overrides: [
      housesProvider.overrideWith((_) async => const [_house]),
      houseCagesProvider(8).overrideWith((_) async => const <Cage>[]),
      housePermissionProvider(8).overrideWith(
        (_) async => const HousePermission(perms: 'control', isAdmin: true),
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

const _house = RabbitHouse(
  id: 8,
  name: '测试兔舍',
  remark: '',
  layoutRows: 2,
  layoutCols: 3,
  layoutLayers: 2,
);
