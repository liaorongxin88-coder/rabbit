import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/data/services/app_update/installer.dart';

/// 这一组用例锁的是 1.0.8 真机故障的表现层。
///
/// release 构建的 shrinkResources 把 @xml/ota_file_paths 删了，
/// FileProvider 解析元数据时抛 XmlPullParserException（受检异常），
/// 逃到 DartMessenger 后回了 null，Dart 侧看到的就是 MissingPluginException。
/// 当时界面显示「当前设备不支持应用更新」，把可恢复故障说成设备不兼容。
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('com.rabbit.app.flutter/app_update');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  void handle(Future<Object?>? Function(MethodCall call)? handler) {
    messenger.setMockMethodCallHandler(channel, handler);
  }

  tearDown(() => handle(null));

  group('通道不可用时', () {
    setUp(() => handle(null)); // 没有 handler = MissingPluginException

    test('不再宣称设备不支持', () async {
      const installer = MethodChannelAppUpdateInstaller();

      await expectLater(
        installer.currentVersion(),
        throwsA(isA<AppUpdateInstallException>().having(
          (error) => error.message,
          'message',
          isNot(contains('不支持')),
        )),
      );
    });

    test('canInstallPackages 报错而不是静静返回 false', () async {
      const installer = MethodChannelAppUpdateInstaller();

      // 返回 false 会被上层当成「没授权」，把用户反复送去系统设置页，
      // 授了权还是装不上。
      await expectLater(
        installer.canInstallPackages(),
        throwsA(isA<AppUpdateInstallException>()),
      );
    });

    test('installApk 给出可操作的提示', () async {
      const installer = MethodChannelAppUpdateInstaller();

      await expectLater(
        installer.installApk(path: '/tmp/a.apk', sha256: 'a' * 64),
        throwsA(isA<AppUpdateInstallException>().having(
          (error) => error.message,
          'message',
          contains('重启应用'),
        )),
      );
    });
  });

  test('原生带回的失败原因要透传，别被统一文案盖掉', () async {
    handle((call) async {
      throw PlatformException(
        code: 'FILE_PROVIDER_UNAVAILABLE',
        message: '无法生成安装包访问地址：XmlPullParserException: setInput() not supported',
      );
    });
    const installer = MethodChannelAppUpdateInstaller();

    await expectLater(
      installer.installApk(path: '/tmp/a.apk', sha256: 'a' * 64),
      throwsA(isA<AppUpdateInstallException>().having(
        (error) => error.message,
        'message',
        contains('XmlPullParserException'),
      )),
    );
  });

  test('安装器正常打开时返回 installerOpened', () async {
    handle((call) async {
      expect(call.method, 'installApk');
      expect(call.arguments['path'], '/tmp/a.apk');
      return 'INSTALLER_OPENED';
    });
    const installer = MethodChannelAppUpdateInstaller();

    expect(
      await installer.installApk(path: '/tmp/a.apk', sha256: 'a' * 64),
      AppInstallResult.installerOpened,
    );
  });
}
