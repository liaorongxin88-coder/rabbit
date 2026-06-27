import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/routing/router.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/local_app_settings_controller.dart';

class RabbitManagerApp extends ConsumerStatefulWidget {
  const RabbitManagerApp({super.key});

  @override
  ConsumerState<RabbitManagerApp> createState() => _RabbitManagerAppState();
}

class _RabbitManagerAppState extends ConsumerState<RabbitManagerApp> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(authControllerProvider.notifier).restore());
    Future.microtask(
      () => ref.read(localAppSettingsControllerProvider.notifier).restore(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final settingsState = ref.watch(localAppSettingsControllerProvider);
    final settings = settingsState.valueOrNull;
    if (settingsState.isLoading && settings == null) {
      return MaterialApp(
        title: '智能兔管家',
        debugShowCheckedModeBanner: false,
        theme: buildAppTheme(),
        home: const Scaffold(
          body: Center(child: CircularProgressIndicator()),
        ),
      );
    }
    final router = ref.watch(appRouterProvider);

    return MaterialApp.router(
      title: '智能兔管家',
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(),
      darkTheme: buildAppTheme(brightness: Brightness.dark),
      themeMode: settings?.themeMode ?? ThemeMode.system,
      routerConfig: router,
    );
  }
}
