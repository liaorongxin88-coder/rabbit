import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:rabbit_flutter/src/config/app.dart';
import 'package:rabbit_flutter/src/data/services/network/client.dart';
import 'package:rabbit_flutter/src/domain/auth/carrier.dart';

final carrierAuthEnabledProvider = Provider<bool>(
  (_) => AppConfig.carrierAuthEnabled,
);

final carrierAuthServiceProvider = Provider<CarrierAuthService>(
  (_) => const MethodChannelCarrierAuthService(),
);

final carrierAuthCapabilityProvider =
    FutureProvider.autoDispose<CarrierAuthCapability>((ref) async {
  final baseUrl = ref.watch(apiBaseUrlProvider);
  if (!ref.watch(carrierAuthEnabledProvider) ||
      !ApiClient.isSecureBaseUrl(baseUrl)) {
    return const CarrierAuthCapability.disabled();
  }
  return ref.watch(carrierAuthServiceProvider).getCapability();
});

abstract class CarrierAuthService {
  Future<CarrierAuthCapability> getCapability();

  Future<CarrierAuthCredential> authorize();

  Future<void> cancelAuthorization();
}

class MethodChannelCarrierAuthService implements CarrierAuthService {
  const MethodChannelCarrierAuthService({
    MethodChannel channel = const MethodChannel(_channelName),
  }) : _channel = channel;

  static const _channelName = 'com.rabbit.app.flutter/carrier_auth';

  final MethodChannel _channel;

  @override
  Future<CarrierAuthCapability> getCapability() async {
    try {
      final response = await _channel.invokeMapMethod<String, dynamic>(
        'getCapability',
      );
      if (response == null || response['available'] != true) {
        return const CarrierAuthCapability.unavailable(
          message: '当前无法使用运营商认证服务',
        );
      }
      final provider = response['provider'] as String? ?? '';
      if (provider.trim().isEmpty) {
        return const CarrierAuthCapability.unavailable(
          message: '运营商认证服务返回无效能力信息',
        );
      }
      return CarrierAuthCapability.available(provider: provider.trim());
    } on MissingPluginException {
      return const CarrierAuthCapability.unavailable(
        message: '当前安装包未集成运营商认证服务',
      );
    } on PlatformException {
      return const CarrierAuthCapability.unavailable(
        message: '当前无法使用运营商认证服务',
      );
    }
  }

  @override
  Future<CarrierAuthCredential> authorize() async {
    try {
      final response =
          await _channel.invokeMapMethod<String, dynamic>('authorize');
      final provider = response?['provider'] as String? ?? '';
      final accessToken = response?['accessToken'] as String? ?? '';
      if (provider.trim().isEmpty || accessToken.trim().isEmpty) {
        throw const CarrierAuthException(
          CarrierAuthFailureReason.failed,
          '运营商认证结果无效，请使用短信验证码登录',
        );
      }
      return CarrierAuthCredential(
        provider: provider.trim(),
        accessToken: accessToken.trim(),
      );
    } on MissingPluginException {
      throw const CarrierAuthException(
        CarrierAuthFailureReason.unavailable,
        '当前安装包不支持一键登录，请使用短信验证码登录',
      );
    } on PlatformException catch (error) {
      final reason = switch (error.code) {
        'CANCELLED' => CarrierAuthFailureReason.cancelled,
        'UNAVAILABLE' => CarrierAuthFailureReason.unavailable,
        'TIMEOUT' => CarrierAuthFailureReason.timeout,
        _ => CarrierAuthFailureReason.failed,
      };
      throw CarrierAuthException(
        reason,
        _defaultMessage(reason),
      );
    }
  }

  @override
  Future<void> cancelAuthorization() async {
    try {
      await _channel.invokeMethod<void>('cancelAuthorization');
    } on MissingPluginException {
      return;
    } on PlatformException {
      return;
    }
  }

  static String _defaultMessage(CarrierAuthFailureReason reason) {
    return switch (reason) {
      CarrierAuthFailureReason.cancelled => '已取消一键登录',
      CarrierAuthFailureReason.unavailable => '当前无法使用一键登录，请使用短信验证码登录',
      CarrierAuthFailureReason.timeout => '一键登录超时，请使用短信验证码登录',
      CarrierAuthFailureReason.failed => '一键登录失败，请使用短信验证码登录',
    };
  }
}
