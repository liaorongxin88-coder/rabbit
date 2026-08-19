import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/ui/batches/widgets/abortion_sheet.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';

void main() {
  testWidgets('abortion requires the stillbirth count from the form contract',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          theme: buildAppTheme(),
          home: Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: FilledButton(
                  key: const ValueKey('open-abortion-form'),
                  onPressed: () => showAbortionSheet(
                    context: context,
                    houseId: 8,
                    cycleId: 71,
                    rabbitId: 18,
                    batchId: 9,
                  ),
                  child: const Text('打开流产表单'),
                ),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const ValueKey('open-abortion-form')));
    await tester.pumpAndSettle();

    final submit = find.byKey(const ValueKey('abortion-submit'));
    await tester.ensureVisible(submit);
    await tester.tap(submit);
    await tester.pump();

    expect(find.text('请填写流产死胎数'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
