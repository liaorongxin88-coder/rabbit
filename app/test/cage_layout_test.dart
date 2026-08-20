import 'package:flutter_test/flutter_test.dart';

import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/cages/attention.dart';
import 'package:rabbit_flutter/src/domain/cages/layout.dart';

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
    test('层是顶层维度，从 1 层往上排', () {
      final layout = CageLayout.fromCages([
        _cage(id: 1, row: 'R1', layer: 2, position: 1),
        _cage(id: 2, row: 'R1', layer: 1, position: 1),
        _cage(id: 3, row: 'R1', layer: 3, position: 1),
      ]);

      expect(
        layout.layers.map((layer) => layer.layerIndex),
        [1, 2, 3],
        reason: '层是切换空间，切换器按 1 层、2 层、3 层顺着来',
      );
      expect(layout.layers.first.rows.single.cages.map((cage) => cage.id), [2]);
    });

    test('一排就是一条线，位号从左往右递增', () {
      final layout = CageLayout.fromCages([
        for (var position = 1; position <= 10; position++)
          _cage(id: position, row: 'B', layer: 1, position: position),
      ]);

      final row = layout.layers.single.rows.single;
      expect(
        row.cells.map((cell) => cell.positionIndex),
        [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
        reason: '排内不折行，第 N 位就在第 N 个格子',
      );
      expect(row.positionSpan, 10);
    });

    test('排宽取跨层最大位号，切层时网格不跳', () {
      final layout = CageLayout.fromCages([
        for (var position = 1; position <= 6; position++)
          _cage(id: position, row: 'B', layer: 1, position: position),
        _cage(id: 7, row: 'B', layer: 2, position: 1),
      ]);

      final secondLayerRow = layout.layers.last.rows.single;
      expect(secondLayerRow.positionSpan, 6);
      expect(
        secondLayerRow.cells.map((cell) => cell.positionIndex),
        [1, 2, 3, 4, 5, 6],
      );
      expect(
        secondLayerRow.cells.skip(1).map((cell) => cell.cage),
        everyElement(isNull),
        reason: '二层只装了一个笼，其余是空槽，但排宽跟一层一致',
      );
    });

    test('同层内的排按排号自然序，R2 在 R10 前面', () {
      final layout = CageLayout.fromCages([
        _cage(id: 1, row: 'R10'),
        _cage(id: 2, row: 'R2'),
        _cage(id: 3, row: 'R1'),
      ]);

      expect(
        layout.layers.single.rows.map((row) => row.rowCode),
        ['R1', 'R2', 'R10'],
      );
    });

    test('缺笼的位置留成空槽，位号照样对齐', () {
      final layout = CageLayout.fromCages([
        _cage(id: 1, row: 'R1', layer: 1, position: 1),
        _cage(id: 2, row: 'R1', layer: 1, position: 4),
      ]);

      final cells = layout.layers.single.rows.single.cells;
      expect(cells.map((cell) => cell.positionIndex), [1, 2, 3, 4]);
      expect(
        cells
            .where((cell) => cell.isEmptySlot)
            .map((cell) => cell.positionIndex),
        [2, 3],
      );
    });

    test('没坐标、LEGACY 排号的笼位进未编排，但不会消失', () {
      final layout = CageLayout.fromCages([
        _cage(id: 1, row: 'R1', layer: 1, position: 1),
        _cage(id: 2, row: 'R1', layer: null, position: 3, number: '缺层'),
        _cage(id: 3, row: 'R1', layer: 2, position: null, number: '缺位'),
        _cage(id: 4, row: 'LEGACY', layer: 1, position: 1, number: '历史'),
        _cage(id: 5, row: '', layer: 1, position: 1, number: '无排号'),
      ]);

      expect(
          layout.layers.single.rows.single.cages.map((cage) => cage.id), [1]);
      expect(layout.unplaced.map((cage) => cage.id).toSet(), {2, 3, 4, 5});
      expect(layout.placedCount, 1);
    });

    test('坐标撞车的笼位被挤到未编排，而不是被覆盖', () {
      final layout = CageLayout.fromCages([
        _cage(id: 1, row: 'R1', layer: 1, position: 1, number: '先到'),
        _cage(id: 2, row: 'R1', layer: 1, position: 1, number: '撞车'),
      ]);

      expect(
        layout.layers.single.rows.single.cells.single.cage?.id,
        1,
      );
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
