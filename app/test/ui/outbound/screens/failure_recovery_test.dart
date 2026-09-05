import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/outbound/repository.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/domain/houses/permission.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/outbound/view_models/controller.dart';
import 'package:rabbit_flutter/src/ui/outbound/screens/flow.dart';

import '../view_models/controller_test.dart' show FakeOutboundGateway;

void main() {
  testWidgets('failed outbound submit returns to editing at 360x800',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    final gateway = FakeOutboundGateway()
      ..submitError = const ApiException('提交被拒绝', statusCode: 400);
    const entry = OutboundEntry(userId: 401, houseId: 8, entryType: 'HOUSE');

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          outboundRepositoryProvider.overrideWithValue(gateway),
          housePermissionProvider(8).overrideWith(
            (_) async => const HousePermission(
              perms: 'edit',
              isAdmin: false,
            ),
          ),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const OutboundFlowScreen(entry: entry),
        ),
      ),
    );
    await _pumpUntilFound(tester, find.text('正常可出库'));

    await tester.tap(find.byKey(const ValueKey('outbound-continue-button')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('outbound-total-weight')),
      '6.5',
    );
    await tester.ensureVisible(
      find.byKey(const ValueKey('outbound-unit-price')),
    );
    await tester.enterText(
      find.byKey(const ValueKey('outbound-unit-price')),
      '18',
    );
    await tester.ensureVisible(
      find.byKey(const ValueKey('outbound-submit-button')),
    );
    tester.testTextInput.hide();
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('outbound-submit-button')));
    await tester.pumpAndSettle();

    expect(find.text('无法提交'), findsOneWidget);
    expect(find.text('继续修改'), findsOneWidget);
    await tester.tap(find.text('继续修改'));
    await tester.pumpAndSettle();

    expect(find.text('批量出库'), findsOneWidget);
    expect(find.text('正常可出库'), findsOneWidget);
    expect(find.text('下一步 · 1 只'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

Future<void> _pumpUntilFound(WidgetTester tester, Finder finder) async {
  for (var i = 0; i < 100; i++) {
    await tester.pump(const Duration(milliseconds: 20));
    if (finder.evaluate().isNotEmpty) return;
  }
  fail('Expected widget was not found');
}
