import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/network/exception.dart';
import 'package:rabbit_flutter/src/data/services/auth/carrier.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/domain/auth/session.dart';
import 'package:rabbit_flutter/src/domain/auth/carrier.dart';
import 'package:rabbit_flutter/src/ui/auth/screens/login.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
  });

  testWidgets('successful one-tap login saves only the server session token',
      (tester) async {
    final service = _FakeCarrierAuthService();
    final repository = _FakeAuthRepository();
    await _pumpLogin(tester, service: service, repository: repository);

    await tester.tap(find.byKey(const ValueKey('legal-consent-checkbox')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('carrier-login-button')));
    await tester.pumpAndSettle();

    expect(find.text('signed-in'), findsOneWidget);
    expect(service.authorizeCalls, 1);
    expect(repository.credentials.single.accessToken, 'short-lived-token');
    final secureValues = await const FlutterSecureStorage().readAll();
    expect(secureValues, {'token': 'server-session-token'});
    final preferences = await SharedPreferences.getInstance();
    expect(
      preferences.getKeys().where((key) => key.contains('carrier')),
      isEmpty,
    );
    expect(preferences.getInt('houseId.42'), isNull);
  });

  testWidgets('carrier capability is not queried before privacy consent',
      (tester) async {
    final service = _FakeCarrierAuthService();
    await _pumpLogin(tester, service: service);

    expect(service.capabilityCalls, 0);
    expect(find.byKey(const ValueKey('carrier-login-button')), findsNothing);

    await tester.tap(find.byKey(const ValueKey('legal-consent-checkbox')));
    await tester.pumpAndSettle();

    expect(service.capabilityCalls, 1);
    expect(find.byKey(const ValueKey('carrier-login-button')), findsOneWidget);
  });

  testWidgets('HTTP backend never exposes or invokes carrier login',
      (tester) async {
    final service = _FakeCarrierAuthService();
    final repository = _FakeAuthRepository();
    await _pumpLogin(
      tester,
      service: service,
      repository: repository,
      baseUrl: 'http://192.168.31.169:8080',
    );

    await tester.tap(find.byKey(const ValueKey('legal-consent-checkbox')));
    await tester.pumpAndSettle();

    expect(service.capabilityCalls, 0);
    expect(service.authorizeCalls, 0);
    expect(repository.credentials, isEmpty);
    expect(find.byKey(const ValueKey('carrier-login-button')), findsNothing);
    expect(find.byKey(const ValueKey('phone-number-input')), findsOneWidget);
    expect(find.byKey(const ValueKey('phone-login-button')), findsOneWidget);
  });

  testWidgets('carrier cancellation keeps the SMS fallback available',
      (tester) async {
    final service = _FakeCarrierAuthService(
      authorizeError: const CarrierAuthException(
        CarrierAuthFailureReason.cancelled,
        '已取消一键登录',
      ),
    );
    await _pumpLogin(tester, service: service);

    await tester.tap(find.byKey(const ValueKey('legal-consent-checkbox')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('carrier-login-button')));
    await tester.pumpAndSettle();

    expect(find.text('已取消一键登录'), findsOneWidget);
    expect(find.byKey(const ValueKey('phone-number-input')), findsOneWidget);
    expect(find.byKey(const ValueKey('phone-login-button')), findsOneWidget);
  });

  testWidgets('unavailable adapter hides one-tap and keeps SMS login',
      (tester) async {
    final service = _FakeCarrierAuthService(
      capability: const CarrierAuthCapability.unavailable(
        message: 'SDK not linked',
      ),
    );
    await _pumpLogin(tester, service: service);

    await tester.tap(find.byKey(const ValueKey('legal-consent-checkbox')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('carrier-login-button')), findsNothing);
    expect(find.byKey(const ValueKey('phone-number-input')), findsOneWidget);
    expect(find.byKey(const ValueKey('phone-login-button')), findsOneWidget);
  });

  testWidgets('repeated taps start only one carrier authorization',
      (tester) async {
    final authorization = Completer<CarrierAuthCredential>();
    final service =
        _FakeCarrierAuthService(authorization: authorization.future);
    await _pumpLogin(tester, service: service);

    await tester.tap(find.byKey(const ValueKey('legal-consent-checkbox')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('carrier-login-button')));
    await tester.tap(find.byKey(const ValueKey('carrier-login-button')));
    await tester.pump();

    expect(service.authorizeCalls, 1);
    authorization.completeError(
      const CarrierAuthException(
        CarrierAuthFailureReason.cancelled,
        '已取消一键登录',
      ),
    );
    await tester.pumpAndSettle();
  });

  testWidgets('disposing an active carrier flow cancels the native adapter',
      (tester) async {
    final authorization = Completer<CarrierAuthCredential>();
    final service =
        _FakeCarrierAuthService(authorization: authorization.future);
    await _pumpLogin(tester, service: service);

    await tester.tap(find.byKey(const ValueKey('legal-consent-checkbox')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('carrier-login-button')));
    await tester.pump();
    expect(service.authorizeCalls, 1);

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();

    expect(service.cancelCalls, 1);
    authorization.completeError(
      const CarrierAuthException(
        CarrierAuthFailureReason.cancelled,
        '已取消一键登录',
      ),
    );
    await tester.pump();
  });

  testWidgets('backend failure keeps the SMS fallback available',
      (tester) async {
    final repository = _FakeAuthRepository(
      loginError: const ApiException('运营商凭证校验失败'),
    );
    await _pumpLogin(tester, repository: repository);

    await tester.tap(find.byKey(const ValueKey('legal-consent-checkbox')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('carrier-login-button')));
    await tester.pumpAndSettle();

    expect(find.text('运营商凭证校验失败'), findsOneWidget);
    expect(find.byKey(const ValueKey('phone-number-input')), findsOneWidget);
    expect(find.byKey(const ValueKey('phone-login-button')), findsOneWidget);
  });

  for (final size in const [Size(360, 800), Size(412, 915)]) {
    testWidgets(
        'one-tap login fits ${size.width.toInt()}x${size.height.toInt()}',
        (tester) async {
      await tester.binding.setSurfaceSize(size);
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await _pumpLogin(tester);

      await tester.tap(find.byKey(const ValueKey('legal-consent-checkbox')));
      await tester.pumpAndSettle();
      expect(
          find.byKey(const ValueKey('carrier-login-button')), findsOneWidget);
      await tester.ensureVisible(
        find.byKey(const ValueKey('legal-consent-row')),
      );
      expect(tester.takeException(), isNull);
    });
  }
}

Future<void> _pumpLogin(
  WidgetTester tester, {
  CarrierAuthService? service,
  AuthRepository? repository,
  String baseUrl = 'https://rabbit.test',
}) async {
  final router = GoRouter(
    initialLocation: '/login',
    routes: [
      GoRoute(
        path: '/login',
        builder: (_, __) => const LoginScreen(),
      ),
      GoRoute(
        path: '/',
        builder: (_, __) => const Scaffold(body: Text('signed-in')),
      ),
    ],
  );
  addTearDown(router.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        apiBaseUrlProvider.overrideWithValue(baseUrl),
        carrierAuthEnabledProvider.overrideWithValue(true),
        carrierAuthServiceProvider.overrideWithValue(
          service ?? _FakeCarrierAuthService(),
        ),
        authRepositoryProvider.overrideWithValue(
          repository ?? _FakeAuthRepository(),
        ),
      ],
      child: MaterialApp.router(routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();
}

class _FakeCarrierAuthService implements CarrierAuthService {
  _FakeCarrierAuthService({
    this.capability = const CarrierAuthCapability.available(
      provider: 'test-carrier',
    ),
    this.authorizeError,
    this.authorization,
  });

  final CarrierAuthCapability capability;
  final Object? authorizeError;
  final Future<CarrierAuthCredential>? authorization;
  int authorizeCalls = 0;
  int capabilityCalls = 0;
  int cancelCalls = 0;

  @override
  Future<CarrierAuthCapability> getCapability() async {
    capabilityCalls += 1;
    return capability;
  }

  @override
  Future<CarrierAuthCredential> authorize() async {
    authorizeCalls += 1;
    final error = authorizeError;
    if (error != null) {
      throw error;
    }
    return authorization ??
        const CarrierAuthCredential(
          provider: 'test-carrier',
          accessToken: 'short-lived-token',
        );
  }

  @override
  Future<void> cancelAuthorization() async {
    cancelCalls += 1;
  }
}

class _FakeAuthRepository extends AuthRepository {
  _FakeAuthRepository({this.loginError}) : super(ApiClient(SessionStore()));

  final ApiException? loginError;
  final credentials = <CarrierAuthCredential>[];
  final _unauthorizedController = StreamController<void>.broadcast(sync: true);

  @override
  Stream<void> get unauthorizedEvents => _unauthorizedController.stream;

  @override
  Future<AuthSession> loginWithCarrier(
    CarrierAuthCredential credential, {
    String? requestId,
  }) async {
    credentials.add(credential);
    final error = loginError;
    if (error != null) {
      throw error;
    }
    return const AuthSession(
      token: 'server-session-token',
      userId: 42,
      userName: 'phone_user',
      houseId: 0,
      phoneBound: true,
      maskedPhone: '138****8000',
      hasPassword: false,
    );
  }
}
