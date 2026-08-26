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

  group('EventItem.batchLabel', () {
    test('names the batch by its code, like the batch list does', () {
      final event = _event(
        eventType: '待配种',
        batchId: 9,
        batchCode: '一号繁育兔舍-批次-20260220',
      );

      expect(event.batchLabel, '批次 一号繁育兔舍-批次-20260220');
    });

    test('hides the field when the stage has no batch', () {
      final event = _event(eventType: '待配种');

      expect(event.batchLabel, isNull);
    });

    test('hides the field rather than falling back to the internal id', () {
      // 内部主键在批次列表里从不出现，显示出来只会被当成周期号。
      final event = _event(eventType: '待配种', batchId: 9);

      expect(event.batchLabel, isNull);
    });
  });

  group('EventItem date handling', () {
    test('reads UTC due times on the farm calendar, not the UTC calendar', () {
      // 2026-02-20 00:30 Asia/Shanghai 到期，存成 UTC 就是前一天 16:30。
      final event = _event(
        eventType: '待配种',
        eventDate: DateTime.utc(2026, 2, 19, 16, 30),
      );

      expect(event.dateLabel, '02月20日');
    });

    test('zero-pads the date the same way everywhere', () {
      final event = _event(
        eventType: '待配种',
        eventDate: DateTime(2026, 3, 5),
      );

      expect(event.dateLabel, '03月05日');
    });
  });
}

EventItem _event({
  required String eventType,
  int? batchId,
  String batchCode = '',
  DateTime? eventDate,
}) {
  return EventItem(
    recordId: 1,
    category: '生产',
    eventType: eventType,
    eventDate: eventDate ?? DateTime(2026, 8, 24),
    batchId: batchId,
    batchCode: batchCode,
    rabbitId: 42,
    status: 'due',
  );
}
