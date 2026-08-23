import 'package:flutter_test/flutter_test.dart';
import 'package:rabbit_flutter/src/domain/reproduction/event.dart';

void main() {
  group('EventItem.operationalTargetLabel', () {
    test('uses a generic rabbit label for commodity daily care', () {
      final event = _event(eventType: '幼兔适应观察');

      expect(event.isCommodityCare, isTrue);
      expect(event.operationalTargetLabel, '兔 #42');
    });

    test('keeps the doe label for breeding production tasks', () {
      final event = _event(eventType: '待配种');

      expect(event.isCommodityCare, isFalse);
      expect(event.operationalTargetLabel, '母兔 #42');
    });
  });
}

EventItem _event({required String eventType}) {
  return EventItem(
    recordId: 1,
    category: '生产',
    eventType: eventType,
    eventDate: DateTime(2026, 8, 24),
    batchId: null,
    rabbitId: 42,
    status: 'due',
  );
}
