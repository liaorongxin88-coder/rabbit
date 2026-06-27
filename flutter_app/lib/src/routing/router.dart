import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/auth/widgets/login_screen.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_shell.dart';
import 'package:rabbit_flutter/src/ui/dashboard/widgets/dashboard_screen.dart';
import 'package:rabbit_flutter/src/ui/home/widgets/home_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_cages_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_detail_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_rabbits_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/houses_screen.dart';
import 'package:rabbit_flutter/src/ui/profile/widgets/profile_screen.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/settings_screen.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/local_app_settings_controller.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/account_settings_screen.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/app_settings_screen.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/production_settings_screen.dart';

final _rootNavigatorKey = GlobalKey<NavigatorState>();
final _shellNavigatorKey = GlobalKey<NavigatorState>();

final appRouterProvider = Provider<GoRouter>((ref) {
  final notifier = _RouterRefreshNotifier(ref);
  ref.onDispose(notifier.dispose);

  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation:
        ref.read(localAppSettingsControllerProvider).valueOrNull?.startRoute ??
            '/',
    refreshListenable: notifier,
    redirect: (context, state) {
      final authState = ref.read(authControllerProvider);
      final isLoading = authState.isLoading;
      final isLoggedIn = authState.valueOrNull != null;
      final isLoginRoute = state.matchedLocation == '/login';

      if (isLoading) {
        return null;
      }
      if (!isLoggedIn && !isLoginRoute) {
        return '/login';
      }
      if (isLoggedIn && isLoginRoute) {
        return '/';
      }
      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      ShellRoute(
        navigatorKey: _shellNavigatorKey,
        builder: (context, state, child) => AppShell(child: child),
        routes: [
          GoRoute(
            path: '/',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: HomeScreen(),
            ),
          ),
          GoRoute(
            path: '/houses',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: HousesScreen(),
            ),
          ),
          GoRoute(
            path: '/houses/:houseId',
            pageBuilder: (context, state) {
              final houseId =
                  int.tryParse(state.pathParameters['houseId'] ?? '') ?? 0;
              return NoTransitionPage(
                child: HouseDetailScreen(houseId: houseId),
              );
            },
          ),
          GoRoute(
            path: '/houses/:houseId/cages',
            pageBuilder: (context, state) {
              final houseId =
                  int.tryParse(state.pathParameters['houseId'] ?? '') ?? 0;
              return NoTransitionPage(
                child: HouseCagesScreen(houseId: houseId),
              );
            },
          ),
          GoRoute(
            path: '/houses/:houseId/rabbits',
            pageBuilder: (context, state) {
              final houseId =
                  int.tryParse(state.pathParameters['houseId'] ?? '') ?? 0;
              return NoTransitionPage(
                child: HouseRabbitsScreen(houseId: houseId),
              );
            },
          ),
          GoRoute(
            path: '/houses/:houseId/settings/production',
            pageBuilder: (context, state) {
              final houseId =
                  int.tryParse(state.pathParameters['houseId'] ?? '') ?? 0;
              final houseName = state.uri.queryParameters['name'];
              return NoTransitionPage(
                child: ProductionSettingsScreen(
                  houseId: houseId,
                  houseName: houseName,
                ),
              );
            },
          ),
          GoRoute(
            path: '/dashboard',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: DashboardScreen(),
            ),
          ),
          GoRoute(
            path: '/profile',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: ProfileScreen(),
            ),
          ),
          GoRoute(
            path: '/settings',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: SettingsScreen(),
            ),
          ),
          GoRoute(
            path: '/settings/account',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: AccountSettingsScreen(),
            ),
          ),
          GoRoute(
            path: '/settings/app',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: AppSettingsScreen(),
            ),
          ),
          GoRoute(
            path: '/settings/production',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: ProductionSettingsScreen(),
            ),
          ),
        ],
      ),
    ],
  );
});

class _RouterRefreshNotifier extends ChangeNotifier {
  _RouterRefreshNotifier(Ref ref) {
    ref.listen(authControllerProvider, (_, __) => notifyListeners());
  }
}
