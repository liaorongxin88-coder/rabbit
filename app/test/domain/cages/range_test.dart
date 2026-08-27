import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/cages/range.dart';

Cage _cage({
  required int id,
  String rowCode = 'R1',
  int? positionIndex = 1,
  int? layerIndex = 1,
  String status = '0',
  int rabbitCount = 0,
  bool isEnabled = true,
}) {
  return Cage(
    id: id,
    houseId: 1,
    cageNumber: '$rowCode-$positionIndex-$layerIndex',
    rowCode: rowCode,
    positionIndex: positionIndex,
    layerIndex: layerIndex,
    status: status,
    rabbitCount: rabbitCount,
    isEnabled: isEnabled,
  );
}

void main() {
  group('CageCoordinateRange', () {
    test('normalizes reversed endpoints before calculating the preview', () {
      final range = CageCoordinateRange.normalized(
        rowStart: 3,
        rowEnd: 1,
        positionStart: 4,
        positionEnd: 2,
        layerStart: 2,
        layerEnd: 1,
      );

      expect(range.rowStart, 1);
      expect(range.rowEnd, 3);
      expect(range.positionStart, 2);
      expect(range.positionEnd, 4);
      expect(range.layerStart, 1);
      expect(range.layerEnd, 2);
      expect(range.slotCount, 18);
    });

    test('keeps a single coordinate as one slot', () {
      final range = CageCoordinateRange.normalized(
        rowStart: 2,
        rowEnd: 2,
        positionStart: 3,
        positionEnd: 3,
        layerStart: 1,
        layerEnd: 1,
      );
      final preview = CageRangePreview.fromCages(
        cages: [_cage(id: 1, rowCode: 'R2', positionIndex: 3)],
        range: range,
        rabbitType: '2',
        rabbitsPerCage: 2,
      );

      expect(range.slotCount, 1);
      expect(preview.eligible.single.cage.id, 1);
      expect(preview.enteredRabbitCount, 2);
      expect(preview.missingCageCount, 0);
    });

    test('marks full and disabled cages for skip without hiding the rest', () {
      final range = CageCoordinateRange.normalized(
        rowStart: 1,
        rowEnd: 1,
        positionStart: 1,
        positionEnd: 3,
        layerStart: 1,
        layerEnd: 1,
      );
      final preview = CageRangePreview.fromCages(
        cages: [
          _cage(id: 1, status: '3', rabbitCount: Cage.commodityCapacity),
          _cage(id: 2, positionIndex: 2, isEnabled: false),
          _cage(id: 3, positionIndex: 3),
          _cage(id: 4, rowCode: 'LEGACY', positionIndex: null),
        ],
        range: range,
        rabbitType: '2',
        rabbitsPerCage: 1,
      );

      expect(preview.eligible.map((item) => item.cage.id), [3]);
      expect(preview.blocked.map((item) => item.cage.id).toSet(), {1, 2});
      expect(preview.unplacedCageCount, 1);
    });

    test('limits a request before it can generate an unbounded bulk write', () {
      final range = CageCoordinateRange.normalized(
        rowStart: 1,
        rowEnd: 501,
        positionStart: 1,
        positionEnd: 1,
        layerStart: 1,
        layerEnd: 1,
      );

      expect(range.slotCount, maxRangeCageSlots + 1);
      expect(range.slotCount > maxRangeCageSlots, isTrue);
    });
  });
}
