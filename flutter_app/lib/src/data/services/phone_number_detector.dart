import 'package:flutter_riverpod/flutter_riverpod.dart';

final phoneNumberDetectorProvider = Provider<PhoneNumberDetector>((ref) {
  return const ManualPhoneNumberDetector();
});

enum PhoneNumberDetectionSource {
  manualInput,
  iosNative,
  harmonyNative,
  carrierToken,
}

class PhoneNumberDetectionResult {
  const PhoneNumberDetectionResult({
    required this.source,
    this.phoneNumber,
    this.message,
  });

  final PhoneNumberDetectionSource source;
  final String? phoneNumber;
  final String? message;

  bool get hasPhoneNumber => phoneNumber != null && phoneNumber!.isNotEmpty;
}

abstract class PhoneNumberDetector {
  Future<PhoneNumberDetectionResult> detect();

  String normalize(String input);

  bool isValidMainlandMobile(String input);
}

class ManualPhoneNumberDetector implements PhoneNumberDetector {
  const ManualPhoneNumberDetector();

  static final _mainlandMobilePattern = RegExp(r'^1[3-9]\d{9}$');

  @override
  Future<PhoneNumberDetectionResult> detect() async {
    return const PhoneNumberDetectionResult(
      source: PhoneNumberDetectionSource.manualInput,
      message: '当前平台暂未提供手机号检测，请手动输入',
    );
  }

  @override
  String normalize(String input) {
    return input.replaceAll(RegExp(r'\D'), '');
  }

  @override
  bool isValidMainlandMobile(String input) {
    return _mainlandMobilePattern.hasMatch(normalize(input));
  }
}
