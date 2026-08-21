import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/data/services/auth/carrier.dart';
import 'package:rabbit_flutter/src/domain/auth/carrier.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('rabbit.test/carrier-auth');

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('method channel exposes provider capability and credential', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      return switch (call.method) {
        'getCapability' => {
            'available': true,
            'provider': 'test-carrier',
          },
        'authorize' => {
            'provider': 'test-carrier',
            'accessToken': 'short-lived-token',
          },
        _ => null,
      };
    });
    const service = MethodChannelCarrierAuthService(channel: channel);

    final capability = await service.getCapability();
    final credential = await service.authorize();

    expect(capability.isAvailable, isTrue);
    expect(capability.provider, 'test-carrier');
    expect(credential.provider, 'test-carrier');
    expect(credential.accessToken, 'short-lived-token');
  });

  test('method channel maps user cancellation to a typed failure', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      throw PlatformException(code: 'CANCELLED', message: '用户取消认证');
    });
    const service = MethodChannelCarrierAuthService(channel: channel);

    await expectLater(
      service.authorize(),
      throwsA(
        isA<CarrierAuthException>()
            .having(
              (error) => error.reason,
              'reason',
              CarrierAuthFailureReason.cancelled,
            )
            .having((error) => error.message, 'message', '已取消一键登录'),
      ),
    );
  });

  test('method channel maps a native SDK timeout to a typed failure', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      throw PlatformException(
        code: 'TIMEOUT',
        message: 'provider detail must not be displayed',
      );
    });
    const service = MethodChannelCarrierAuthService(channel: channel);

    await expectLater(
      service.authorize(),
      throwsA(
        isA<CarrierAuthException>()
            .having(
              (error) => error.reason,
              'reason',
              CarrierAuthFailureReason.timeout,
            )
            .having(
              (error) => error.message,
              'message',
              '一键登录超时，请使用短信验证码登录',
            ),
      ),
    );
  });

  test('missing capability data is unavailable instead of fabricated',
      () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      channel,
      (_) async => {'available': false, 'message': 'SDK not linked'},
    );
    const service = MethodChannelCarrierAuthService(channel: channel);

    final capability = await service.getCapability();

    expect(capability.isAvailable, isFalse);
    expect(capability.availability, CarrierAuthAvailability.unavailable);
    expect(capability.message, '当前无法使用运营商认证服务');
  });

  test('explicit cancellation is forwarded to the native adapter', () async {
    var cancelCalls = 0;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'cancelAuthorization') {
        cancelCalls += 1;
      }
      return null;
    });
    const service = MethodChannelCarrierAuthService(channel: channel);

    await service.cancelAuthorization();

    expect(cancelCalls, 1);
  });
}
