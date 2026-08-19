import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/batch_rabbit.dart';
import 'package:rabbit_flutter/src/domain/models/repro_task.dart';

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
}
