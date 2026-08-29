import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/data/repositories/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/auth/carrier.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/auth/image_captcha.dart';
import 'package:rabbit_flutter/src/ui/auth/screens/login.dart';

/// 1x1 的合法 PNG，只为让 Image.memory 能解码。
const _pngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42m'
    'P8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({});
  });

  testWidgets('图片验证码刷新按钮的图标在按钮内居中', (tester) async {
    await _pumpAccountLogin(tester);

    final button = find.byKey(const ValueKey('image-captcha-refresh'));
    expect(button, findsOneWidget);

    final icon = find.descendant(
      of: button,
      matching: find.byIcon(Icons.refresh),
    );
    expect(icon, findsOneWidget);

    final buttonRect = tester.getRect(button);
    final iconRect = tester.getRect(icon);
    final iconSize =
        tester.widget<Icon>(icon).size ?? IconTheme.of(tester.element(icon)).size!;

    // 按钮默认左右内边距会把图标盒挤到只剩几个像素宽，字形随后向右溢出绘制，
    // 看上去就是图标不居中。这里校验图标盒拿到了完整宽度，没有被挤压。
    expect(
      iconRect.width,
      moreOrLessEquals(iconSize, epsilon: 0.5),
      reason: '图标盒被压缩到 ${iconRect.width.toStringAsFixed(1)}px（应为 '
          '${iconSize.toStringAsFixed(1)}px），字形会溢出到右侧导致视觉偏移',
    );
    expect(iconRect.height, moreOrLessEquals(iconSize, epsilon: 0.5));

    expect(
      iconRect.center.dx,
      moreOrLessEquals(buttonRect.center.dx, epsilon: 0.5),
      reason: '图标水平方向偏离按钮中心 '
          '${(iconRect.center.dx - buttonRect.center.dx).toStringAsFixed(2)}px',
    );
    expect(
      iconRect.center.dy,
      moreOrLessEquals(buttonRect.center.dy, epsilon: 0.5),
      reason: '图标垂直方向偏离按钮中心 '
          '${(iconRect.center.dy - buttonRect.center.dy).toStringAsFixed(2)}px',
    );
  });

  testWidgets('刷新按钮保持 52x52 的可点区域', (tester) async {
    await _pumpAccountLogin(tester);

    final buttonRect =
        tester.getRect(find.byKey(const ValueKey('image-captcha-refresh')));
    expect(buttonRect.width, moreOrLessEquals(52, epsilon: 0.5));
    expect(buttonRect.height, moreOrLessEquals(52, epsilon: 0.5));
  });
}

Future<void> _pumpAccountLogin(WidgetTester tester) async {
  final router = GoRouter(
    initialLocation: '/login',
    routes: [
      GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
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
        apiBaseUrlProvider.overrideWithValue('https://rabbit.test'),
        carrierAuthEnabledProvider.overrideWithValue(false),
        authRepositoryProvider.overrideWithValue(_CaptchaAuthRepository()),
      ],
      child: MaterialApp.router(routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();

  // 切到「账号」页签，图片验证码只在这里出现。
  await tester.tap(find.text('账号'));
  await tester.pumpAndSettle();
}

class _CaptchaAuthRepository extends AuthRepository {
  _CaptchaAuthRepository() : super(ApiClient(SessionStore()));

  final _unauthorizedController = StreamController<void>.broadcast(sync: true);

  @override
  Stream<void> get unauthorizedEvents => _unauthorizedController.stream;

  @override
  Future<ImageCaptcha> getImageCaptcha() async {
    return const ImageCaptcha(
      captchaId: 'captcha-1',
      imageBase64: _pngBase64,
      expiresInSeconds: 120,
    );
  }
}
