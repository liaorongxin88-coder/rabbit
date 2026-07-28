import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc_repository.dart';
import 'package:rabbit_flutter/src/data/services/nfc_intent_service.dart';
import 'package:rabbit_flutter/src/data/services/nfc_local_store.dart';
import 'package:rabbit_flutter/src/domain/models/nfc_models.dart';
import 'package:rabbit_flutter/src/routing/router.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/auth_controller.dart';
import 'package:rabbit_flutter/src/ui/core/themes/app_theme.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/nfc_pending_sync_controller.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/local_app_settings_controller.dart';

class RabbitManagerApp extends ConsumerStatefulWidget {
  const RabbitManagerApp({super.key});

  @override
  ConsumerState<RabbitManagerApp> createState() => _RabbitManagerAppState();
}

class _RabbitManagerAppState extends ConsumerState<RabbitManagerApp>
    with WidgetsBindingObserver {
  StreamSubscription<NfcLaunchEvent>? _nfcSubscription;
  Timer? _pendingSyncTimer;
  NfcLaunchEvent? _pendingNfcEvent;
  String? _lastNfcFingerprint;
  var _processingNfc = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    Future.microtask(() => ref.read(authControllerProvider.notifier).restore());
    Future.microtask(
      () => ref.read(localAppSettingsControllerProvider.notifier).restore(),
    );
    Future.microtask(_initializeNfc);
    Future.microtask(() => ref.read(nfcPendingSyncControllerProvider));
    _pendingSyncTimer = Timer.periodic(
      const Duration(seconds: 30),
      (_) => _syncPendingBindings(),
    );
  }

  Future<void> _syncPendingBindings() async {
    if (ref.read(authControllerProvider).valueOrNull == null) return;
    await ref.read(nfcPendingSyncControllerProvider.notifier).syncAll();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(_syncPendingBindings());
    }
  }

  Future<void> _initializeNfc() async {
    final service = ref.read(nfcIntentServiceProvider);
    _nfcSubscription = service.events.listen(_handleNfcEvent);
    _pendingNfcEvent =
        await ref.read(nfcLocalStoreProvider).readPendingLaunch();
    await service.initialize();
    await _processPendingNfc();
  }

  Future<void> _handleNfcEvent(NfcLaunchEvent event) async {
    final fingerprint =
        '${event.payload}|${event.tagUid}|${event.receivedAt ~/ 2000}';
    if (_lastNfcFingerprint == fingerprint) return;
    _lastNfcFingerprint = fingerprint;
    _pendingNfcEvent = event;
    await ref.read(nfcLocalStoreProvider).savePendingLaunch(event);
    await _processPendingNfc();
  }

  Future<void> _processPendingNfc() async {
    if (_processingNfc || !mounted) return;
    final session = ref.read(authControllerProvider).valueOrNull;
    final event = _pendingNfcEvent;
    if (session == null || event == null) return;
    _processingNfc = true;
    try {
      final target = NfcPayloadTarget.parse(event.payload);
      final binding = await ref.read(nfcRepositoryProvider).resolve(
            houseId: target.houseId,
            tagUid: event.tagUid,
            payload: event.payload,
          );
      await ref
          .read(authControllerProvider.notifier)
          .setHouseId(binding.houseId);
      await ref.read(nfcLocalStoreProvider).clearPendingLaunch();
      _pendingNfcEvent = null;
      if (mounted) {
        ref
            .read(appRouterProvider)
            .go('/houses/${binding.houseId}/cages/${binding.cageId}');
      }
    } catch (error) {
      await ref.read(nfcLocalStoreProvider).clearPendingLaunch();
      _pendingNfcEvent = null;
      if (mounted) {
        final message = Uri.encodeComponent(error.toString());
        ref.read(appRouterProvider).go('/nfc/error?message=$message');
      }
    } finally {
      _processingNfc = false;
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _pendingSyncTimer?.cancel();
    _nfcSubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(authControllerProvider, (_, next) {
      if (next.valueOrNull != null) {
        unawaited(_processPendingNfc());
        unawaited(_syncPendingBindings());
      }
    });
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
