// 令牌失效之后，人必须被送回登录页。
//
// 这条链路一共四棒：ApiClient 认出 401 → 发 unauthorized 事件 →
// AuthController 清掉会话 → 路由把人跳回 /login。
// 前两棒 data/services/network/client_test.dart 已经盯住了，这里盯的是后两棒。
//
// 为什么值得单独写：真机上我拿一个失效令牌冷启动，app 停在首页显示
// 「0 到期 / 所有兔舍当前均无待处理对象」。对养殖场的人来说，这等于告诉他
// 今天没活儿要干——比直接报错还危险。所以这条不能只靠真机截图看一眼，
// 要有一个确定性的测试钉住。

import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:rabbit_flutter/src/app.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/data/services/auth/session.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues(<String, Object>{
      'userId': 7,
      'userName': '老李',
    });
    FlutterSecureStorage.setMockInitialValues(<String, String>{
      'token': 'stale-token',
    });
  });

  testWidgets('带着失效令牌冷启动，落在登录页而不是空数据的首页', (tester) async {
    final container = ProviderContainer(
      overrides: [
        apiClientProvider.overrideWith((ref) {
          return ApiClient(
            ref.watch(sessionStoreProvider),
            dio: _dioAlwaysUnauthorized(),
          );
        }),
      ],
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const RabbitManagerApp(),
      ),
    );
    await tester.pumpAndSettle(const Duration(seconds: 2));

    expect(
      find.text('登录后管理兔舍、预警和生产流程。'),
      findsOneWidget,
      reason: '令牌已经失效，不能把人留在受保护的页面上假装一切正常',
    );
  });

  testWidgets('会话中途失效，下一次请求把人踢回登录页', (tester) async {
    var unauthorized = false;
    final container = ProviderContainer(
      overrides: [
        apiClientProvider.overrideWith((ref) {
          return ApiClient(
            ref.watch(sessionStoreProvider),
            // 先让恢复会话这一步成功，随后再开始回 401，
            // 模拟“用着用着令牌过期了”，而不是一开始就没登录。
            dio: _dioUnauthorizedWhen(() => unauthorized),
          );
        }),
      ],
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const RabbitManagerApp(),
      ),
    );
    for (var i = 0; i < 30; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(find.text('登录后管理兔舍、预警和生产流程。'), findsNothing,
        reason: '会话还有效时不该被赶去登录页');

    unauthorized = true;
    // 不能用 pumpAndSettle：跳转中间会出现转圈动画，它永远不会停，
    // settle 会一直等到超时（第一版就是这么卡死的）。固定 pump 几帧就够了。
    unawaited(
      container
          .read(apiClientProvider)
          .get<void>(
            '/api/anything',
            decode: (_) {},
          )
          .catchError((_) {}),
    );
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }

    expect(
      find.text('登录后管理兔舍、预警和生产流程。'),
      findsOneWidget,
      reason: '令牌中途失效后，下一次请求就该把人送回登录页',
    );
  });
}

Dio _dioAlwaysUnauthorized() {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'));
  dio.httpClientAdapter = _StubAdapter(() => true);
  return dio;
}

Dio _dioUnauthorizedWhen(bool Function() unauthorized) {
  final dio = Dio(BaseOptions(baseUrl: 'https://rabbit.test'));
  dio.httpClientAdapter = _StubAdapter(unauthorized);
  return dio;
}

/// 后端未登录时是 HTTP 200 + body code=401，不是 HTTP 401，
/// 这里照着真实形状造，否则测试会放过只认 HTTP 状态码的写法。
class _StubAdapter implements HttpClientAdapter {
  _StubAdapter(this.unauthorized);

  final bool Function() unauthorized;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final body = unauthorized()
        ? <String, Object?>{'code': 401, 'message': '未登录', 'data': null}
        : <String, Object?>{
            'code': 0,
            'message': 'ok',
            'data': <String, Object?>{
              'userId': 7,
              'userName': '老李',
              'permissions': <String>[],
            },
          };
    return ResponseBody.fromString(
      jsonEncode(body),
      200,
      headers: <String, List<String>>{
        Headers.contentTypeHeader: <String>[Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
