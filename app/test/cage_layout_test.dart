import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/cage_attention.dart';
import 'package:rabbit_flutter/src/domain/models/cage_layout.dart';

Cage _cage({
  required int id,
  String row = 'R1',
  int? layer = 1,
  int? position = 1,
  String status = '0',
  int count = 0,
  bool enabled = true,
  bool fed = true,
  String? number,
}) {
  return Cage(
    id: id,
    houseId: 8,
    cageNumber: number ?? '$row-C$position-L$layer',
    rowCode: row,
    layerIndex: layer,
    positionIndex: position,
    status: status,
    rabbitCount: count,
    isEnabled: enabled,
    isFed: fed,
  );
}

void main() {
  group('CageLayout', () {
    test('groups cages into rows, layers descending and positions ascending',
        () {
      final layout = CageLayout.fromCages([
        _cage(id: 1, row: 'R1', layer: 1, position: 2),
        _cage(id: 2, row: 'R1', layer: 2, position: 1),
        _cage(id: 3, row: 'R1', layer: 1, position: 1),
        _cage(id: 4, row: 'R1', layer: 2, position: 2),
      ]);

      expect(layout.rows, hasLength(1));
      final row = layout.rows.single;
      expect(row.rowCode, 'R1');
      expect(row.positionSpan, 2);
      expect(
        row.layers.map((layer) => layer.layerIndex),
        [2, 1],
        reason: '最上层显示在最上面，跟物理货架一致',
      );
      expect(
        row.layers.first.cells.map((cell) => cell.cage?.id),
        [2, 4],
      );
      expect(
        row.layers.last.cells.map((cell) => cell.cage?.id),
        [1, 3].reversed,
      );
    });

    test('sorts row codes naturally so R2 comes before R10', () {
      final layout = CageLayout.fromCages([
        _cage(id: 1, row: 'R10'),
        _cage(id: 2, row: 'R2'),
        _cage(id: 3, row: 'R1'),
      ]);

      expect(layout.rows.map((row) => row.rowCode), ['R1', 'R2', 'R10']);
    });

    test('keeps missing positions as empty slots so columns stay aligned', () {
      final layout = CageLayout.fromCages([
        _cage(id: 1, row: 'R1', layer: 1, position: 1),
        _cage(id: 2, row: 'R1', layer: 1, position: 4),
      ]);

      final cells = layout.rows.single.layers.single.cells;
      expect(cells, hasLength(4));
      expect(cells.map((cell) => cell.positionIndex), [1, 2, 3, 4]);
      expect(cells.map((cell) => cell.isEmptySlot), [false, true, true, false]);
    });

    test('routes cages without coordinates and LEGACY rows to unplaced', () {
      final layout = CageLayout.fromCages([
        _cage(id: 1, row: 'R1', layer: 1, position: 1),
        _cage(id: 2, row: 'R1', layer: null, position: 3, number: '缺层'),
        _cage(id: 3, row: 'R1', layer: 2, position: null, number: '缺位'),
        _cage(id: 4, row: 'LEGACY', layer: 1, position: 1, number: '历史'),
        _cage(id: 5, row: '', layer: 1, position: 1, number: '无排号'),
      ]);

      expect(layout.rows.single.cages.map((cage) => cage.id), [1]);
      expect(
        layout.unplaced.map((cage) => cage.id).toSet(),
        {2, 3, 4, 5},
      );
      expect(layout.placedCount, 1);
    });

    test('displaces a duplicate coordinate instead of dropping the cage', () {
      final layout = CageLayout.fromCages([
        _cage(id: 1, row: 'R1', layer: 1, position: 1, number: '先到'),
        _cage(id: 2, row: 'R1', layer: 1, position: 1, number: '撞车'),
      ]);

      expect(layout.rows.single.layers.single.cells.single.cage?.id, 1);
      expect(
        layout.unplaced.map((cage) => cage.id),
        [2],
        reason: '坐标撞车是数据问题，但界面上不能凭空少一个笼',
      );
    });
  });

  group('CageAttention', () {
    test('flags accounting mismatches before anything else', () {
      expect(
        _cage(id: 1, status: '0', count: 2).attention,
        CageAttention.alert,
      );
      expect(
        _cage(id: 2, status: '1', count: 2).attention,
        CageAttention.alert,
      );
      expect(
        _cage(id: 3, status: '3', count: Cage.commodityCapacity + 1).attention,
        CageAttention.alert,
      );
      expect(
        _cage(id: 4, status: '1', count: 1, enabled: false).attention,
        CageAttention.alert,
        reason: '停用笼里还留着兔，比单纯停用更需要处理',
      );
      expect(
        _cage(id: 5, status: '1', count: 1, enabled: false)
            .attentionAlertReason,
        contains('已停用'),
      );
    });

    test('disabled empty cage is merely unavailable', () {
      final cage = _cage(id: 1, status: '0', enabled: false);
      expect(cage.attention, CageAttention.disabled);
      expect(cage.attentionAlertReason, isNull);
    });

    test('unfed only counts when the cage actually holds rabbits', () {
      expect(
        _cage(id: 1, status: '1', count: 1, fed: false).attention,
        CageAttention.needsFeeding,
      );
      expect(
        _cage(id: 2, status: '1', count: 0, fed: false).attention,
        CageAttention.vacancy,
        reason: '空笼没有兔可喂，不该染成待投喂',
      );
    });

    test('separates full cages from cages that can still take a rabbit', () {
      expect(_cage(id: 1, status: '1', count: 1).attention, CageAttention.full);
      expect(
        _cage(id: 2, status: '3', count: Cage.commodityCapacity).attention,
        CageAttention.full,
      );
      expect(
        _cage(id: 3, status: '3', count: 3).attention,
        CageAttention.vacancy,
      );
      expect(_cage(id: 4, status: '0').attention, CageAttention.vacancy);
    });

    test('occupancy text stays readable inside a small cell', () {
      expect(_cage(id: 1, status: '0').occupancyText, '空');
      expect(_cage(id: 2, status: '3', count: 3).occupancyText, '3/10');
      expect(_cage(id: 3, status: '1', count: 1).occupancyText, '1 只');
    });
  });
}
