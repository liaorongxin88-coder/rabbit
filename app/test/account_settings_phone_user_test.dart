import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/repositories/account_repository.dart';
import 'package:rabbit_flutter/src/data/repositories/auth_repository.dart';
import 'package:rabbit_flutter/src/data/services/api_client.dart';
import 'package:rabbit_flutter/src/data/services/session_store.dart';
import 'package:rabbit_flutter/src/domain/models/user_profile.dart';
import 'package:rabbit_flutter/src/domain/models/sms_code_delivery.dart';
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
    await tester.scrollUntilVisible(
      find.text('设置密码'),
      500,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pump();
    await tester.tap(find.text('设置密码'));
    await tester.pumpAndSettle();

    expect(repository.oldPassword, isEmpty);
    expect(repository.newPassword, 'new-password');
    expect(find.text('密码已设置'), findsOneWidget);
  });

  testWidgets('account without a phone can verify and bind one',
      (tester) async {
    await tester.binding.setSurfaceSize(const Size(412, 915));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final accountRepository = _RecordingAccountRepository();
    final authRepository = _RecordingAuthRepository();
    const profile = UserProfile(
      userId: 10,
      userName: 'account-user',
      openidBound: false,
      phoneBound: false,
      hasPassword: true,
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          accountRepositoryProvider.overrideWithValue(accountRepository),
          authRepositoryProvider.overrideWithValue(authRepository),
          userProfileProvider.overrideWith((_) async => profile),
        ],
        child: MaterialApp(
          theme: buildAppTheme(),
          home: const AccountSettingsScreen(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(
      find.widgetWithText(TextFormField, '新手机号'),
      '13800138018',
    );
    await tester.tap(find.text('获取验证码'));
    await tester.pump();
    expect(authRepository.phone, '13800138018');
    expect(authRepository.purpose, 'BIND_PHONE');

    await tester.enterText(
      find.widgetWithText(TextFormField, '新手机号验证码'),
      '123456',
    );
    await tester.tap(find.text('绑定手机号'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(accountRepository.phone, '13800138018');
    expect(accountRepository.code, '123456');
  });
}

class _RecordingAccountRepository extends AccountRepository {
  _RecordingAccountRepository() : super(ApiClient(SessionStore()));

  String? oldPassword;
  String? newPassword;
  String? phone;
  String? code;

  @override
  Future<void> updatePassword({
    required String oldPassword,
    required String newPassword,
  }) async {
    this.oldPassword = oldPassword;
    this.newPassword = newPassword;
  }

  @override
  Future<UserProfile> updatePhone({
    required String phone,
    required String code,
    String currentPassword = '',
    String currentPhone = '',
    String currentPhoneCode = '',
  }) async {
    this.phone = phone;
    this.code = code;
    return const UserProfile(
      userId: 10,
      userName: 'account-user',
      openidBound: false,
      phoneBound: true,
      maskedPhone: '138****8018',
      hasPassword: true,
    );
  }
}

class _RecordingAuthRepository extends AuthRepository {
  _RecordingAuthRepository() : super(ApiClient(SessionStore()));

  String? phone;
  String? purpose;

  @override
  Future<SmsCodeDelivery> sendSmsCodeForPurpose(
    String phone,
    String purpose,
  ) async {
    this.phone = phone;
    this.purpose = purpose;
    return const SmsCodeDelivery(
      expiresInSeconds: 300,
      retryAfterSeconds: 60,
    );
  }
}
