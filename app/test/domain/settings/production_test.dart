import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/settings/production.dart';

void main() {
  test('production setting update contains the current fields', () {
    final body = GlobalSetting.defaults().toUpdateJson(requestId: 'request-1');

    expect(body, {
      'aphrodisiacDays': 2,
      'palpationDays': 12,
      'prepartumDays': 15,
      'weaningDays': 30,
      'postpartumDays': 10,
      'adaptationDays': 3,
      'growingDays': 18,
      'fatteningDays': 12,
      'saleDays': 33,
      'replacementDays': 90,
      'remark': '',
      'requestId': 'request-1',
    });
  });
}
