import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/auth/widgets/login_screen.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/house_batches_screen.dart';
import 'package:rabbit_flutter/src/ui/batches/widgets/house_batch_detail_screen.dart';
import 'package:rabbit_flutter/src/ui/cages/widgets/cage_detail_screen.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/app_shell.dart';
import 'package:rabbit_flutter/src/ui/dashboard/widgets/dashboard_screen.dart';
import 'package:rabbit_flutter/src/ui/home/widgets/home_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_cages_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_detail_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_members_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/house_rabbits_screen.dart';
import 'package:rabbit_flutter/src/ui/houses/widgets/houses_screen.dart';
import 'package:rabbit_flutter/src/ui/profile/widgets/profile_screen.dart';
import 'package:rabbit_flutter/src/ui/nfc/widgets/nfc_error_screen.dart';
import 'package:rabbit_flutter/src/ui/nfc/widgets/nfc_write_screen.dart';
import 'package:rabbit_flutter/src/ui/nfc/widgets/nfc_write_setup_screen.dart';
import 'package:rabbit_flutter/src/ui/outbound/view_models/outbound_controller.dart';
import 'package:rabbit_flutter/src/ui/outbound/widgets/outbound_flow_screen.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/settings_screen.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/local_app_settings_controller.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/account_settings_screen.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/app_settings_screen.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/production_settings_screen.dart';

final _rootNavigatorKey = GlobalKey<NavigatorState>();
final _shellNavigatorKey = GlobalKey<NavigatorState>();
const _authLoadingPath = '/auth-loading';

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
      final isLoading = authState.isLoading && authState.valueOrNull == null;
      final isLoggedIn = authState.valueOrNull != null;
      final isLoginRoute = state.matchedLocation == '/login';
      final isLoadingRoute = state.matchedLocation == _authLoadingPath;

      if (isLoading) {
        if (isLoginRoute || isLoadingRoute) {
          return null;
        }
        final from = Uri.encodeComponent(state.uri.toString());
        return '$_authLoadingPath?from=$from';
      }
      if (isLoadingRoute) {
        if (!isLoggedIn) {
          return '/login';
        }
        final from = state.uri.queryParameters['from'];
        if (_isSafeProtectedLocation(from)) {
          return from;
        }
        return '/';
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
        path: _authLoadingPath,
        builder: (context, state) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        },
      ),
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/houses/:houseId/nfc/write/session',
        builder: (context, state) {
          final houseId =
              int.tryParse(state.pathParameters['houseId'] ?? '') ?? 0;
          return NfcWriteScreen(houseId: houseId);
        },
      ),
      GoRoute(
        path: '/houses/:houseId/outbound',
        parentNavigatorKey: _rootNavigatorKey,
        builder: (context, state) {
          final houseId =
              int.tryParse(state.pathParameters['houseId'] ?? '') ?? 0;
          final userId =
              ref.read(authControllerProvider).valueOrNull?.userId ?? 0;
          final query = state.uri.queryParameters;
          return OutboundFlowScreen(
            entry: OutboundEntry(
              userId: userId,
              houseId: houseId,
              entryType: (query['entryType'] ?? 'HOUSE').toUpperCase(),
              rabbitId: int.tryParse(query['rabbitId'] ?? ''),
              cageId: int.tryParse(query['cageId'] ?? ''),
              rowCode: query['rowCode'],
            ),
          );
        },
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
            path: '/houses/:houseId/members',
            pageBuilder: (context, state) {
              final houseId =
                  int.tryParse(state.pathParameters['houseId'] ?? '') ?? 0;
              final houseName = state.uri.queryParameters['name'] ?? '';
              return NoTransitionPage(
                child: HouseMembersScreen(
                  houseId: houseId,
                  houseName: houseName,
                ),
              );
            },
          ),
          GoRoute(
            path: '/houses/:houseId/cages/:cageId',
            pageBuilder: (context, state) {
              final houseId =
                  int.tryParse(state.pathParameters['houseId'] ?? '') ?? 0;
              final cageId =
                  int.tryParse(state.pathParameters['cageId'] ?? '') ?? 0;
              return NoTransitionPage(
                child: CageDetailScreen(houseId: houseId, cageId: cageId),
              );
            },
          ),
          GoRoute(
            path: '/houses/:houseId/nfc/write',
            pageBuilder: (context, state) {
              final houseId =
                  int.tryParse(state.pathParameters['houseId'] ?? '') ?? 0;
              return NoTransitionPage(
                child: NfcWriteSetupScreen(houseId: houseId),
              );
            },
          ),
          GoRoute(
            path: '/nfc/error',
            pageBuilder: (context, state) => NoTransitionPage(
              child: NfcErrorScreen(
                message: state.uri.queryParameters['message'] ?? '标签无法识别',
              ),
            ),
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
            path: '/houses/:houseId/batches',
            pageBuilder: (context, state) {
              final houseId =
                  int.tryParse(state.pathParameters['houseId'] ?? '') ?? 0;
              return NoTransitionPage(
                child: HouseBatchesScreen(houseId: houseId),
              );
            },
          ),
          GoRoute(
            path: '/houses/:houseId/batches/:batchId',
            pageBuilder: (context, state) {
              final houseId =
                  int.tryParse(state.pathParameters['houseId'] ?? '') ?? 0;
              final batchId =
                  int.tryParse(state.pathParameters['batchId'] ?? '') ?? 0;
              return NoTransitionPage(
                child: HouseBatchDetailScreen(
                  houseId: houseId,
                  batchId: batchId,
                ),
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

bool _isSafeProtectedLocation(String? location) {
  if (location == null || location.isEmpty) {
    return false;
  }
  final uri = Uri.tryParse(location);
  if (uri == null || uri.hasScheme || uri.hasAuthority) {
    return false;
  }
  final path = uri.path;
  return path.startsWith('/') && path != '/login' && path != _authLoadingPath;
}

class _RouterRefreshNotifier extends ChangeNotifier {
  _RouterRefreshNotifier(Ref ref) {
    ref.listen(authControllerProvider, (_, __) => notifyListeners());
  }
}
