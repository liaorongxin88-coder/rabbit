import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/auth/session.dart';

void main() {
  test('parses business permission codes from login response', () {
    final session = AuthSession.fromJson(<String, dynamic>{
      'token': 'token',
      'userId': 7,
      'userName': 'operator',
      'phoneBound': true,
      'maskedPhone': '138****8000',
      'hasPassword': false,
      'permissions': <String>[
        'account:profile:query',
        'rabbit:houses:list',
      ],
    });

    expect(session.permissions, <String>[
      'account:profile:query',
      'rabbit:houses:list',
    ]);
    expect(session.phoneBound, isTrue);
    expect(session.maskedPhone, '138****8000');
    expect(session.hasPassword, isFalse);
    expect(session.copyWith(houseId: 9).permissions, session.permissions);
    expect(session.copyWith(houseId: 9).maskedPhone, session.maskedPhone);
  });
}
