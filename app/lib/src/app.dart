import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'package:rabbit_flutter/src/data/repositories/nfc/repository.dart';
import 'package:rabbit_flutter/src/data/services/nfc/capture_scope.dart';
import 'package:rabbit_flutter/src/data/services/nfc/intents.dart';
import 'package:rabbit_flutter/src/data/services/storage/nfc.dart';
import 'package:rabbit_flutter/src/domain/nfc/workflow.dart';
import 'package:rabbit_flutter/src/routing/routes.dart';
import 'package:rabbit_flutter/src/ui/auth/view_models/controller.dart';
import 'package:rabbit_flutter/src/ui/core/theme.dart';
import 'package:rabbit_flutter/src/ui/houses/view_models/providers.dart';
import 'package:rabbit_flutter/src/ui/nfc/view_models/pending_sync.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/local.dart';
import 'package:rabbit_flutter/src/ui/settings/view_models/update.dart';
import 'package:rabbit_flutter/src/ui/settings/widgets/prompt.dart';

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
    Future.microtask(
      () => ref.read(appUpdateControllerProvider.notifier).checkSilently(),
    );
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
      unawaited(ref.read(appUpdateControllerProvider.notifier).checkSilently());
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
    // 采集窗口开着时（例如换笼时碰目标笼）不能再跳笼位详情，
    // 否则正在填的表单会被顶掉；事件由那个界面自己消费。
    if (ref.read(nfcCaptureActiveProvider)) {
      return;
    }
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
    ref.listen(housesProvider, (_, next) {
      final houses = next.valueOrNull;
      if (houses != null) {
        unawaited(
          ref
              .read(authControllerProvider.notifier)
              .reconcileHouseIds(houses.map((house) => house.id)),
        );
      }
    });
    ref.listen(authControllerProvider, (_, next) {
      if (next.valueOrNull != null) {
        unawaited(_processPendingNfc());
        unawaited(_syncPendingBindings());
      }
    });
    final settingsState = ref.watch(localAppSettingsControllerProvider);
    final settings = settingsState.valueOrNull;
    final router = ref.watch(appRouterProvider);

    return MaterialApp.router(
      title: '鸿兔智管',
      debugShowCheckedModeBanner: false,
      locale: const Locale('zh', 'CN'),
      supportedLocales: const [Locale('zh', 'CN')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      theme: buildAppTheme(),
      darkTheme: buildAppTheme(brightness: Brightness.dark),
      themeMode: settings?.themeMode ?? ThemeMode.system,
      builder: (context, child) => AppUpdatePrompt(
        child: _SystemUiFrame(child: child),
      ),
      routerConfig: router,
    );
  }
}

class _SystemUiFrame extends StatelessWidget {
  const _SystemUiFrame({required this.child});

  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final palette = AppPalette.of(context);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final baseStyle =
        isDark ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.dark;
    final mediaQuery = MediaQuery.maybeOf(context);
    final content = child ?? const SizedBox.shrink();
    final scaledContent = mediaQuery == null
        ? content
        : MediaQuery(
            data: mediaQuery.copyWith(
              textScaler:
                  AppTypography.ergonomicTextScaler(mediaQuery.textScaler),
            ),
            child: content,
          );
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: baseStyle.copyWith(
        statusBarColor: palette.background,
        systemStatusBarContrastEnforced: false,
        systemNavigationBarColor: palette.background,
        systemNavigationBarDividerColor: palette.line,
        systemNavigationBarIconBrightness:
            isDark ? Brightness.light : Brightness.dark,
        systemNavigationBarContrastEnforced: false,
      ),
      child: scaledContent,
    );
  }
}
