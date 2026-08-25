import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/batches/batch.dart';
import 'package:rabbit_flutter/src/domain/batches/weaning.dart';

void main() {
  test('parses trustworthy remaining gender counts and recommendations', () {
    final record = PendingWeaningRecord.fromJson({
      'id': 501,
      'batchId': 20,
      'rabbitId': 101,
      'breedingCycleId': 301,
      'sireRabbitId': 88,
      'weaningCount': 8,
      'waitingCount': 6,
      'maleCount': 4,
      'femaleCount': 4,
      'waitingMaleCount': 3,
      'waitingFemaleCount': 3,
    });

    expect(record.hasTrustworthyWaitingGenderCounts, isTrue);
    expect(record.waitingMaleCount, 3);
    expect(record.waitingFemaleCount, 3);
    expect(record.sireRabbitId, 88);
  });

  test('count-only history keeps remaining genders unknown', () {
    final record = PendingWeaningRecord.fromJson({
      'id': 501,
      'batchId': 20,
      'rabbitId': 101,
      'weaningCount': 8,
      'waitingCount': 2,
      'waitingMaleCount': null,
      'waitingFemaleCount': null,
    });

    expect(record.hasTrustworthyWaitingGenderCounts, isFalse);
  });

  test('production filters stay local to intake data', () {
    const open = Batch(
      id: 20,
      houseId: 8,
      batchCode: 'OPEN',
      status: '进行中',
      startDate: null,
      endDate: null,
      remark: '',
    );
    const completed = Batch(
      id: 21,
      houseId: 8,
      batchCode: 'DONE',
      status: 'COMPLETED',
      startDate: null,
      endDate: null,
      remark: '',
    );
    const otherHouse = Batch(
      id: 22,
      houseId: 9,
      batchCode: 'OTHER',
      status: '进行中',
      startDate: null,
      endDate: null,
      remark: '',
    );

    expect(
      productionIntakeBatches(
        const [open, completed, otherHouse],
        houseId: 8,
      ),
      const [open],
    );
    expect(
      pendingProductionRecords(const [
        PendingWeaningRecord(
          id: 1,
          batchId: 20,
          rabbitId: 101,
          weaningCount: 4,
          waitingCount: 0,
        ),
        PendingWeaningRecord(
          id: 2,
          batchId: 20,
          rabbitId: 102,
          weaningCount: 4,
          waitingCount: 2,
        ),
      ]).map((record) => record.id),
      [2],
    );
  });

  test('allocation validates partial gender counts and serializes pairs', () {
    const valid = CageAllocation(
      cageId: 12,
      count: 4,
      maleCount: 2,
      femaleCount: 2,
    );

    expect(
      valid.validate(
        waitingCount: 6,
        waitingMaleCount: 3,
        waitingFemaleCount: 3,
      ),
      isNull,
    );
    expect(valid.toJson(), {
      'cageId': 12,
      'count': 4,
      'maleCount': 2,
      'femaleCount': 2,
    });
    expect(
      const CageAllocation(
        cageId: 12,
        count: 4,
        maleCount: 4,
        femaleCount: 1,
      ).validate(
        waitingCount: 6,
        waitingMaleCount: 4,
        waitingFemaleCount: 3,
      ),
      contains('之和'),
    );
    expect(
      const CageAllocation(
        cageId: 12,
        count: 4,
        maleCount: 4,
        femaleCount: 0,
      ).validate(
        waitingCount: 6,
        waitingMaleCount: 3,
        waitingFemaleCount: 3,
      ),
      contains('公兔数量'),
    );
  });

  test('parses replay response like the original success', () {
    final result = WeaningSeparationResult.fromJson({
      'weaningRecordId': 501,
      'separatedCount': 4,
      'waitingCount': 2,
      'generatedRabbitIds': [9001, 9002, 9003, 9004],
      'replayed': true,
    });

    expect(result.weaningRecordId, 501);
    expect(result.separatedCount, 4);
    expect(result.waitingCount, 2);
    expect(result.generatedRabbitIds, [9001, 9002, 9003, 9004]);
    expect(result.replayed, isTrue);
  });
}
