import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/user_profile.dart';

void main() {
  test('parses bound phone details from the account profile', () {
    final profile = UserProfile.fromJson(<String, dynamic>{
      'userId': 7,
      'userName': 'phone-user',
      'openidBound': false,
      'phoneBound': true,
      'maskedPhone': '138****8000',
      'hasPassword': false,
    });

    expect(profile.phoneBound, isTrue);
    expect(profile.maskedPhone, '138****8000');
    expect(profile.hasPassword, isFalse);
  });

  test('keeps phone details optional for older responses', () {
    final profile = UserProfile.fromJson(<String, dynamic>{
      'userId': 7,
      'userName': 'legacy-user',
      'openidBound': false,
    });

    expect(profile.phoneBound, isFalse);
    expect(profile.maskedPhone, isEmpty);
    expect(profile.hasPassword, isTrue);
  });
}
