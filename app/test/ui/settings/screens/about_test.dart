import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/services/app_update/package.dart';
import 'package:rabbit_flutter/src/domain/app_update/release.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/settings/screens/about.dart';

void main() {
  testWidgets('about page shows the current version and check button',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(412, 915));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          appPackageReaderProvider.overrideWithValue(const _FakePackageReader()),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const AboutScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('当前版本 1.0.2（4003）'), findsOneWidget);
    expect(find.text('渠道：开发'), findsOneWidget);
    expect(find.byKey(const ValueKey('about-check-update')), findsOneWidget);
  });
}

class _FakePackageReader implements AppPackageReader {
  const _FakePackageReader();

  @override
  Future<AppPackageIdentity> current() async {
    return const AppPackageIdentity(
      versionName: '1.0.2',
      versionCode: 4003,
      channel: 'dev',
      packageName: 'com.rabbit.app.flutter.dev',
    );
  }
}
