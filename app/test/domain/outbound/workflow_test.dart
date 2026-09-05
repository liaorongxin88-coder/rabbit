import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/outbound/workflow.dart';

void main() {
  test('groups selected rabbits by batch with unassigned last', () {
    final groups = buildOutboundAllocationGroups(
      const [
        OutboundSelectedItem(
          rabbitId: 1,
          stateVersion: 1,
          selectionType: 'NORMAL',
        ),
        OutboundSelectedItem(
          rabbitId: 2,
          stateVersion: 1,
          selectionType: 'NORMAL',
        ),
        OutboundSelectedItem(
          rabbitId: 3,
          stateVersion: 1,
          selectionType: 'NORMAL',
        ),
      ],
      [
        _rabbit(1, 20),
        _rabbit(2, null),
        _rabbit(3, 20),
      ],
    );

    expect(groups.map((group) => group.batchId), [20, null]);
    expect(groups.map((group) => group.rabbitCount), [2, 1]);
  });

  test('server draft allocations round-trip with nullable batch groups', () {
    final task = OutboundTask.fromJson({
      'taskId': 'draft-1',
      'houseId': 8,
      'entryType': 'HOUSE',
      'status': 'WAITING_CONFIRMATION',
      'revision': 4,
      'resumed': true,
      'summary': <String, Object?>{},
      'rabbits': <Object?>[],
      'selectedItems': <Object?>[],
      'batchAllocations': [
        {'batchId': 101, 'actualWeightKg': 3.2},
        {'batchId': null, 'actualWeightKg': 3.3},
      ],
    });

    expect(
        task.batchAllocations.map((item) => item.key), ['101', 'unassigned']);
    expect(task.toJson()['batchAllocations'], [
      {'batchId': 101, 'actualWeightKg': 3.2},
      {'batchId': null, 'actualWeightKg': 3.3},
    ]);
  });

  test('requires common price, three-decimal weights, and exact sum', () {
    expect(
      validateOutboundAllocations(
        totalWeight: 6.5,
        unitPricePerKg: 18.25,
        allocations: const [
          OutboundBatchAllocation(batchId: 20, actualWeightKg: 3.2),
          OutboundBatchAllocation(batchId: null, actualWeightKg: 3.3),
        ],
      ),
      isNull,
    );
    expect(
      validateOutboundAllocations(
        totalWeight: 6.5,
        unitPricePerKg: null,
        allocations: const [
          OutboundBatchAllocation(batchId: 20, actualWeightKg: 6.5),
        ],
      ),
      contains('统一重量单价'),
    );
    expect(
      validateOutboundAllocations(
        totalWeight: 6.5,
        unitPricePerKg: 18,
        allocations: const [
          OutboundBatchAllocation(batchId: 20, actualWeightKg: 6.4999),
        ],
      ),
      contains('最多保留三位小数'),
    );
    expect(
      validateOutboundAllocations(
        totalWeight: 6.5,
        unitPricePerKg: 18,
        allocations: const [
          OutboundBatchAllocation(batchId: 20, actualWeightKg: 6.4),
        ],
      ),
      contains('合计必须等于'),
    );
  });
}

OutboundRabbit _rabbit(int id, int? batchId) => OutboundRabbit(
      rabbitId: id,
      cageId: 10,
      cageNumber: '1-1-1',
      rowCode: '1',
      layerIndex: 1,
      positionIndex: 1,
      rabbitType: '2',
      gender: '0',
      weight: 2.5,
      stage: '可出售',
      batchId: batchId,
      stateVersion: 1,
      eligibility: OutboundEligibility.normal,
      reasonCode: 'ELIGIBLE',
      message: '可出库',
      recommendedAction: '纳入',
      defaultSelected: true,
    );
