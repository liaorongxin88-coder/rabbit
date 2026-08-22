import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/settings/production.dart';

void main() {
  test('production setting update omits removed gestation duration', () {
    final setting = GlobalSetting.fromJson({
      'id': 1,
      'userId': 2,
      'houseId': 3,
      'gestationDays': 31,
    });

    final body = setting.toUpdateJson(requestId: 'request-1');

    expect(body, isNot(contains('gestationDays')));
  });
}
