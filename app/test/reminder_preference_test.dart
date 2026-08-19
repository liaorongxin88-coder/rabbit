import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/event_item.dart';
import 'package:rabbit_flutter/src/domain/models/reminder_preference.dart';

void main() {
  test('reminder preference filters event types and overdue events', () {
    final preference = ReminderPreference.fromJson({
      'id': 1,
      'houseId': 8,
      'enabled': true,
      'advanceDays': 3,
      'notifyOverdue': false,
      'taskTypes': ['MATING', 'PALPATION'],
    });

    expect(preference.includes(_event('配种', status: 'due')), isTrue);
    expect(preference.includes(_event('摸胎', status: 'upcoming')), isTrue);
    expect(preference.includes(_event('分娩', status: 'due')), isFalse);
    expect(preference.includes(_event('配种', status: 'overdue')), isFalse);
  });

  test('reminder due bound includes the configured number of full days', () {
    const preference = ReminderPreference(
      id: 1,
      houseId: 8,
      enabled: true,
      advanceDays: 2,
      notifyOverdue: true,
      taskTypes: {'ALL'},
    );

    expect(
      preference.dueBefore(DateTime(2026, 8, 19, 12)),
      DateTime(2026, 8, 21, 23, 59, 59, 999),
    );
  });
}

EventItem _event(String type, {required String status}) {
  return EventItem(
    recordId: 1,
    category: '生产周期',
    eventType: type,
    eventDate: DateTime(2026, 8, 19),
    batchId: null,
    rabbitId: 1,
    status: status,
  );
}
