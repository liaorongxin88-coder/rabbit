import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/ui/auth/widgets/login_screen.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';

void main() {
  for (final size in const [Size(360, 800), Size(412, 915)]) {
    testWidgets(
      'reset password dialog stays within the viewport at true 200 percent on '
      '${size.width.toInt()}x${size.height.toInt()}',
      (tester) async {
        await tester.binding.setSurfaceSize(size);
        addTearDown(() => tester.binding.setSurfaceSize(null));

        await tester.pumpWidget(
          ProviderScope(
            child: MaterialApp(
              theme: buildAppTheme(),
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(context).copyWith(
                  textScaler: const TextScaler.linear(2),
                ),
                child: child!,
              ),
              home: const LoginScreen(),
            ),
          ),
        );
        await tester.pumpAndSettle();

        await tester.tap(find.text('账号'));
        await tester.pumpAndSettle();
        final resetButton = find.text('忘记密码');
        await tester.ensureVisible(resetButton);
        await tester.tap(resetButton);
        await tester.pumpAndSettle();

        final dialog = find.byType(AlertDialog);
        expect(dialog, findsOneWidget);
        final rect = tester.getRect(dialog);
        expect(rect.left, greaterThanOrEqualTo(0));
        expect(rect.right, lessThanOrEqualTo(size.width));
        expect(tester.takeException(), isNull);
      },
    );
  }
}
