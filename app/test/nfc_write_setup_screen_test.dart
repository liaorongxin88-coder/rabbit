import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc_repository.dart';
import 'package:rabbit_flutter/src/data/services/nfc_local_store.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/nfc/widgets/nfc_write_setup_screen.dart';

void main() {
  for (final size in [const Size(360, 800), const Size(412, 915)]) {
    testWidgets(
        'NFC write setup fits ${size.width.toInt()}x${size.height.toInt()}',
        (tester) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            nfcCageWriteQueueProvider(8).overrideWith(
              (_) async => const [
                NfcCageQueueItem(
                  cageId: 10,
                  cageNumber: '一号兔舍东排(上)100',
                  bindingStatus: 'UNBOUND',
                  tagUid: null,
                  payload: 'r1.8.a.1.signature',
                ),
                NfcCageQueueItem(
                  cageId: 11,
                  cageNumber: '1(中)2',
                  bindingStatus: 'BOUND',
                  tagUid: '04AABBCC',
                  payload: 'r1.8.b.1.signature',
                ),
              ],
            ),
          ],
          child: MaterialApp(
            theme: buildAppTheme(),
            home: const NfcWriteSetupScreen(houseId: 8),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('NFC标签写入'), findsOneWidget);
      expect(find.text('开始写入 1 个笼位'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('shows actionable pending binding conflicts', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await NfcLocalStore().savePendingBindings(const [
      NfcPendingBinding(
        houseId: 8,
        cageId: 10,
        tagUid: '04AABBCC',
        payload: 'r1.8.a.1.signature',
        requestId: 'request-1',
        replaceExisting: false,
        status: NfcPendingBindingStatus.conflict,
        errorMessage: '标签已绑定',
      ),
    ]);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          nfcCageWriteQueueProvider(8).overrideWith(
            (_) async => const [
              NfcCageQueueItem(
                cageId: 10,
                cageNumber: '1-1-1',
                bindingStatus: 'UNBOUND',
                tagUid: null,
                payload: 'r1.8.a.1.signature',
              ),
            ],
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const NfcWriteSetupScreen(houseId: 8),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('待同步 1 · 需处理 1'), findsOneWidget);
    await tester.tap(find.byTooltip('管理待同步项'));
    await tester.pumpAndSettle();

    expect(find.text('待同步标签'), findsOneWidget);
    expect(find.textContaining('绑定冲突'), findsOneWidget);
    expect(find.byTooltip('强制重新绑定'), findsOneWidget);
    expect(find.byTooltip('删除待同步项'), findsOneWidget);
  });
}
