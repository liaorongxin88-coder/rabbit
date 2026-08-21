import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/data/services/auth/phone_number.dart';

void main() {
  test('manual detector normalizes and validates mainland mobile numbers', () {
    const detector = ManualPhoneNumberDetector();

    expect(detector.normalize(' 138 0000-0000 '), '13800000000');
    expect(detector.isValidMainlandMobile('13800000000'), isTrue);
    expect(detector.isValidMainlandMobile('10086'), isFalse);
  });

  test('manual detector reports that platform detection is not available',
      () async {
    const detector = ManualPhoneNumberDetector();

    final result = await detector.detect();

    expect(result.hasPhoneNumber, isFalse);
    expect(result.source, PhoneNumberDetectionSource.manualInput);
    expect(result.message, contains('请手动输入'));
  });
}
