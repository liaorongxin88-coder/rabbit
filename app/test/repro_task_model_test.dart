import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/batches/rabbit.dart';
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
      nextEventType: '配种',
      batchRole: 'breeding',
    );

    expect(item.displayStatus, '待摸胎');
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
