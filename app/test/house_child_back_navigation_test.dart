import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/domain/models/global_setting.dart';
import 'package:rabbit_flutter/src/domain/models/house_member.dart';
import 'package:rabbit_flutter/src/domain/models/house_permission.dart';
import 'package:rabbit_flutter/src/domain/models/reminder_preference.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/house_providers.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_members_screen.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/settings_providers.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/production_settings_screen.dart';

void main() {
  testWidgets(
    'house production settings returns by button and Android back',
    (tester) async {
      const settingsPath = '/houses/8/settings/production';
      final router = GoRouter(
        initialLocation: settingsPath,
        routes: [
          _houseRoute(),
          GoRoute(
            path: '/houses/:houseId/settings/production',
            builder: (_, __) => const ProductionSettingsScreen(
              houseId: 8,
              houseName: '测试兔舍',
            ),
          ),
        ],
      );
      addTearDown(router.dispose);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            houseSettingProvider(8).overrideWith(
              (_) async => HouseSettingState(
                setting: GlobalSetting.defaults(),
                customized: false,
              ),
            ),
            reminderPreferenceProvider(8).overrideWith(
              (_) async => ReminderPreference.defaults.copyWith(),
            ),
          ],
          child: MaterialApp.router(
            theme: buildAppTheme(),
            routerConfig: router,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await _verifyBackNavigation(
        tester,
        router: router,
        pagePath: settingsPath,
        pageTitle: '兔舍生产设置',
      );
    },
  );

  testWidgets(
    'house member management returns by button and Android back',
    (tester) async {
      const membersPath = '/houses/8/members';
      final router = GoRouter(
        initialLocation: membersPath,
        routes: [
          _houseRoute(),
          GoRoute(
            path: '/houses/:houseId/members',
            builder: (_, __) => const HouseMembersScreen(
              houseId: 8,
              houseName: '测试兔舍',
            ),
          ),
        ],
      );
      addTearDown(router.dispose);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            housePermissionProvider(8).overrideWith(
              (_) async => const HousePermission(
                perms: 'control',
                isAdmin: true,
                role: 'OWNER',
                permissions: ['rabbit:house-members:list'],
              ),
            ),
            houseMembersProvider(8).overrideWith(
              (_) async => const <HouseMember>[],
            ),
          ],
          child: MaterialApp.router(
            theme: buildAppTheme(),
            routerConfig: router,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await _verifyBackNavigation(
        tester,
        router: router,
        pagePath: membersPath,
        pageTitle: '测试兔舍 · 人员管理',
      );
    },
  );
}

Future<void> _verifyBackNavigation(
  WidgetTester tester, {
  required GoRouter router,
  required String pagePath,
  required String pageTitle,
}) async {
  expect(find.text(pageTitle), findsOneWidget);
  expect(find.byKey(const ValueKey('page-back-button')), findsOneWidget);

  await tester.tap(find.byKey(const ValueKey('page-back-button')));
  await tester.pumpAndSettle();
  expect(find.text('兔舍详情 #8'), findsOneWidget);

  router.go(pagePath);
  await tester.pumpAndSettle();
  await tester.binding.handlePopRoute();
  await tester.pumpAndSettle();
  expect(find.text('兔舍详情 #8'), findsOneWidget);
}

GoRoute _houseRoute() {
  return GoRoute(
    path: '/houses/:houseId',
    builder: (_, state) => Scaffold(
      body: Center(
        child: Text('兔舍详情 #${state.pathParameters['houseId']}'),
      ),
    ),
  );
}
