import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/feed/log.dart';

void main() {
  test('parses allocation preview groups and serializes exact kg payload', () {
    final preview = FeedAllocationPreview.fromJson({
      'groups': [
        {'batchId': 101, 'phase': 'BREEDING', 'rabbitCount': 3},
        {'batchId': null, 'phase': 'UNASSIGNED', 'rabbitCount': 1},
      ],
    });
    final allocations = [
      FeedBatchAllocation(
        batchId: preview.groups[0].batchId,
        phase: preview.groups[0].phase,
        amountKg: 1.25,
      ),
      FeedBatchAllocation(
        batchId: preview.groups[1].batchId,
        phase: preview.groups[1].phase,
        amountKg: 0.75,
      ),
    ];
    final draft = FeedLogDraft(
      rabbitIds: const [3, 4, 5, 6],
      feedTime: DateTime.utc(2026, 9, 5, 8),
      requestId: 'feed-1',
      amount: 2,
      allocations: allocations,
    );

    expect(validateFeedAllocations(2, allocations), isNull);
    expect(draft.toJson()['allocations'], [
      {'batchId': 101, 'phase': 'BREEDING', 'amountKg': 1.25},
      {'batchId': null, 'phase': 'UNASSIGNED', 'amountKg': 0.75},
    ]);
  });

  test('rejects invalid preview phases and imprecise or unbalanced kg', () {
    expect(
      () => FeedAllocationPreview.fromJson({
        'groups': [
          {'batchId': 101, 'phase': 'UNKNOWN', 'rabbitCount': 1},
        ],
      }),
      throwsA(isA<FormatException>()),
    );
    expect(
      validateFeedAllocations(2, const [
        FeedBatchAllocation(
          batchId: 101,
          phase: FeedAllocationPhase.fattening,
          amountKg: 1.999,
        ),
      ]),
      '分组投喂量最多保留两位小数',
    );
    expect(
      validateFeedAllocations(2, const [
        FeedBatchAllocation(
          batchId: 101,
          phase: FeedAllocationPhase.fattening,
          amountKg: 1.5,
        ),
      ]),
      '分组投喂量合计必须等于投喂总量',
    );
  });
}
