import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/account_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';
import 'package:rabbit_flutter/src/domain/models/user_profile.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/profile/view_models/profile_providers.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/account_settings_screen.dart';

void main() {
  testWidgets('phone-only user sets a password without an old password',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(412, 915));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final repository = _RecordingAccountRepository();
    const profile = UserProfile(
      userId: 9,
      userName: 'mobile-user',
      openidBound: false,
      phoneBound: true,
      maskedPhone: '138****8000',
      hasPassword: false,
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          accountRepositoryProvider.overrideWithValue(repository),
          userProfileProvider.overrideWith((_) async => profile),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const AccountSettingsScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('手机号：138****8000'), findsOneWidget);
    expect(find.text('设置登录密码'), findsOneWidget);
    expect(find.text('旧密码'), findsNothing);

    await tester.enterText(
      find.widgetWithText(TextFormField, '新密码'),
      'new-password',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, '确认新密码'),
      'new-password',
    );
    await tester.ensureVisible(find.text('设置密码'));
    await tester.tap(find.text('设置密码'));
    await tester.pumpAndSettle();

    expect(repository.oldPassword, isEmpty);
    expect(repository.newPassword, 'new-password');
    expect(find.text('密码已设置'), findsOneWidget);
  });
}

class _RecordingAccountRepository extends AccountRepository {
  _RecordingAccountRepository() : super(ApiClient(SessionStore()));

  String? oldPassword;
  String? newPassword;

  @override
  Future<void> updatePassword({
    required String oldPassword,
    required String newPassword,
  }) async {
    this.oldPassword = oldPassword;
    this.newPassword = newPassword;
  }
}
