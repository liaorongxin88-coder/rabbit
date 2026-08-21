import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/batches/rabbit.dart';
import 'package:rabbit_flutter/src/domain/batches/tracking.dart';
import 'package:rabbit_flutter/src/domain/reproduction/task.dart';

void main() {
  test('wire enums normalize server whitespace and case', () {
    expect(ReproStage.tryParse(' await_mating '), ReproStage.awaitMating);
    expect(ReproAction.tryParse(' mating '), ReproAction.mating);
  });

  test('batch member display status prefers the live stage projection', () {
    const item = BatchRabbitItem(
      id: 1,
      batchId: 2,
      rabbitId: 3,
      currentStatus: '待催情',
      currentStage: ' await_palpation ',
      currentCycleId: 4,
      nextEventType: '配种',
      batchRole: 'breeding',
    );

    expect(item.displayStatus, '待摸胎');
    expect(item.isActivityActive, isTrue);
  });

  test('batch member parses batch-scoped production tracking totals', () {
    final item = BatchRabbitItem.fromJson({
      'id': 1,
      'batchId': 2,
      'rabbitId': 3,
      'currentStatus': '旧状态',
      'nextEventType': '',
      'batchRole': 'breeding',
      'batchCycleCount': 2,
      'batchOperationCount': 9,
      'batchLitterCount': 1,
      'batchTotalKits': 8,
      'batchLiveKits': 7,
      'batchWeanedKits': 6,
      'batchLastOperationAt': '2026-08-20T10:30:00',
    });

    expect(item.displayStatus, '周期已结束');
    expect(item.isActivityActive, isFalse);
    expect(item.batchCycleCount, 2);
    expect(item.batchOperationCount, 9);
    expect(item.batchLitterCount, 1);
    expect(item.batchTotalKits, 8);
    expect(item.batchLiveKits, 7);
    expect(item.batchWeanedKits, 6);
    expect(item.batchLastOperationAt, DateTime(2026, 8, 20, 10, 30));
  });

  test('batch tracking event parses the public timeline contract', () {
    final event = BatchTrackingEvent.fromJson({
      'id': 11,
      'cycleId': 21,
      'motherRabbitId': 31,
      'batchId': 41,
      'eventType': 'PALPATION_PREGNANT',
      'eventLabel': '摸胎-怀孕',
      'fromStageLabel': '待摸胎',
      'toStageLabel': '待备产',
      'occurredAt': '2026-08-20T10:30:00',
      'operatorName': '生产员甲',
    });

    expect(event.id, 11);
    expect(event.cycleId, 21);
    expect(event.batchId, 41);
    expect(event.eventLabel, '摸胎-怀孕');
    expect(event.fromStageLabel, '待摸胎');
    expect(event.toStageLabel, '待备产');
    expect(event.occurredAt, DateTime(2026, 8, 20, 10, 30));
    expect(event.operatorName, '生产员甲');
  });

  test('action result parses the authoritative current cycle projection', () {
    final result = ReproActionResult.fromJson({
      'cycleId': 301,
      'currentCycleId': 302,
      'followUpCycleId': 303,
    });

    expect(result.cycleId, 301);
    expect(result.currentCycleId, 302);
    expect(result.followUpCycleId, 303);
  });

  test('action result keeps null or missing current cycle nullable', () {
    final explicitNull = ReproActionResult.fromJson({
      'cycleId': 401,
      'currentCycleId': null,
    });
    final missing = ReproActionResult.fromJson({'cycleId': 402});

    expect(explicitNull.currentCycleId, isNull);
    expect(missing.currentCycleId, isNull);
  });
}
