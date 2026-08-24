import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';
import 'package:rabbit_flutter/src/ui/auth/screens/login.dart';
import 'package:rabbit_flutter/src/ui/batches/screens/list.dart';
import 'package:rabbit_flutter/src/ui/batches/screens/detail.dart';
import 'package:rabbit_flutter/src/ui/cages/screens/detail.dart';
import 'package:rabbit_flutter/src/ui/core/widgets/shell.dart';
import 'package:rabbit_flutter/src/ui/dashboard/screens/overview.dart';
import 'package:rabbit_flutter/src/ui/home/screens/overview.dart';
import 'package:rabbit_flutter/src/ui/cages/screens/list.dart';
import 'package:rabbit_flutter/src/ui/houses/screens/detail.dart';
import 'package:rabbit_flutter/src/ui/houses/screens/members.dart';
import 'package:rabbit_flutter/src/ui/rabbits/screens/list.dart';
import 'package:rabbit_flutter/src/ui/rabbits/screens/detail.dart';
import 'package:rabbit_flutter/src/ui/houses/screens/list.dart';
import 'package:rabbit_flutter/src/ui/profile/screens/overview.dart';
import 'package:rabbit_flutter/src/ui/nfc/screens/error.dart';
import 'package:rabbit_flutter/src/ui/nfc/screens/write.dart';
import 'package:rabbit_flutter/src/ui/nfc/screens/setup.dart';
import 'package:rabbit_flutter/src/ui/outbound/view_models/controller.dart';
import 'package:rabbit_flutter/src/ui/outbound/screens/flow.dart';
import 'package:rabbit_flutter/src/ui/settings/screens/overview.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/local.dart';
import 'package:rabbit_flutter/src/ui/settings/screens/account.dart';
import 'package:rabbit_flutter/src/ui/settings/screens/app.dart';
import 'package:rabbit_flutter/src/ui/settings/screens/production.dart';
import 'package:rabbit_flutter/src/ui/settings/screens/reminders.dart';
import 'package:rabbit_flutter/src/ui/settings/screens/about.dart';

final _rootNavigatorKey = GlobalKey<NavigatorState>();
final _shellNavigatorKey = GlobalKey<NavigatorState>();
const _appSettingsLoadingPath = '/app-settings-loading';
const _appSettingsErrorPath = '/app-settings-error';
const _authLoadingPath = '/auth-loading';

final appRouterProvider = Provider<GoRouter>((ref) {
  final notifier = _RouterRefreshNotifier(ref);
  ref.onDispose(notifier.dispose);

  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: _appSettingsLoadingPath,
    refreshListenable: notifier,
    redirect: (context, state) {
      final settingsState = ref.read(localAppSettingsControllerProvider);
      final settings = settingsState.valueOrNull;
      final isSettingsLoading = settingsState.isLoading && settings == null;
      final isSettingsError = settingsState.hasError && settings == null;
      final isSettingsLoadingRoute =
          state.matchedLocation == _appSettingsLoadingPath;
      final isSettingsErrorRoute =
          state.matchedLocation == _appSettingsErrorPath;

      if (isSettingsLoading) {
        return isSettingsLoadingRoute ? null : _appSettingsLoadingPath;
      }
      if (isSettingsError) {
        return isSettingsErrorRoute ? null : _appSettingsErrorPath;
      }
      if (isSettingsLoadingRoute || isSettingsErrorRoute) {
        return settings?.startRoute ?? '/';
      }

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
        path: _appSettingsLoadingPath,
        builder: (context, state) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        },
      ),
      GoRoute(
        path: _appSettingsErrorPath,
        builder: (context, state) {
          return Scaffold(
            body: Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text('无法读取本机设置'),
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: () => ref
                          .read(localAppSettingsControllerProvider.notifier)
                          .restore(),
                      child: const Text('重试'),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
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
            path: '/houses/:houseId/rabbits/:rabbitId',
            pageBuilder: (context, state) {
              final houseId =
                  int.tryParse(state.pathParameters['houseId'] ?? '') ?? 0;
              final rabbitId =
                  int.tryParse(state.pathParameters['rabbitId'] ?? '') ?? 0;
              return NoTransitionPage(
                child: RabbitDetailScreen(
                  houseId: houseId,
                  rabbitId: rabbitId,
                ),
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
          GoRoute(
            path: '/settings/reminders',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: ReminderSettingsScreen(),
            ),
          ),
          GoRoute(
            path: '/settings/about',
            pageBuilder: (context, state) => const NoTransitionPage(
              child: AboutScreen(),
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
    ref.listen(
      localAppSettingsControllerProvider,
      (_, __) => notifyListeners(),
    );
  }
}
