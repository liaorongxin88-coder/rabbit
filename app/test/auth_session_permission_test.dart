import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/models/auth_session.dart';

void main() {
  test('parses business permission codes from login response', () {
    final session = AuthSession.fromJson(<String, dynamic>{
      'token': 'token',
      'userId': 7,
      'userName': 'operator',
      'permissions': <String>[
        'account:profile:query',
        'rabbit:houses:list',
      ],
    });

    expect(session.permissions, <String>[
      'account:profile:query',
      'rabbit:houses:list',
    ]);
    expect(session.copyWith(houseId: 9).permissions, session.permissions);
  });
}
