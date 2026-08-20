import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/domain/houses/house.dart';
import 'package:rabbit_flutter/src/ui/cages/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/cages/screens/list.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/queue.dart';

/// 批量建笼生成的编号会被贴到笼子上，人拿着它对实物，所以它必须和地图对得上。
void main() {
  testWidgets('批量建笼编号统一成「排-位-层」，跟系统自动铺的一致', (tester) async {
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

    // 编号统一成「排-位-层」，跟建兔舍自动铺的笼位一致。
    // 排号输的是 R2，编号里只留数字，不然会变成 R2-1-1、跟 2-1-1 又是两套。
    expect(preview, contains('2-1-1'));
    expect(preview, contains('2-1-2'));

    // 中间那位是位号，不是流水号。写成流水号的话，两层时第 2 位会拿到 3、4——
    // 地图上那个格子写着「2」，笼上的签写着「3」，对实物时必错。
    expect(preview, contains('2-2-1'));
    expect(preview, contains('2-2-2'));
    expect(preview, contains('2-3-1'));
    expect(preview, isNot(contains('2-4-1')));
    expect(preview, isNot(contains('2-6-')));

    // 旧的「排(层)位」写法不能再出现，否则同一个兔舍里会同时存两套编号。
    expect(preview, isNot(contains('(下)')));
    expect(preview, isNot(contains('(上)')));
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
