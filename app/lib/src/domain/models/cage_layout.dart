import 'package:rabbit_flutter/src/domain/models/cage.dart';
import 'package:rabbit_flutter/src/domain/models/cage_attention.dart';

/// 把一串扁平的笼位还原成「排 → 层 → 位」的分层地图。
///
/// 数据里本来就有 `rowCode` / `layerIndex` / `positionIndex`，但两端过去都只
/// 把它拼成一行文字，用户要在几十上百个卡片里靠编号找笼。这里只做纯粹的
/// 分组与排序，不碰任何 UI，方便单测钉住排序和边界。
class CageLayout {
  const CageLayout({required this.rows, required this.unplaced});

  /// 已编排的排，按排号自然序（R2 在 R10 前面）。
  final List<CageMapRow> rows;

  /// 没有层/位坐标、排号为空或 `LEGACY` 的笼位，以及坐标撞车被挤出来的笼位。
  /// 它们放不进网格，但绝不能从界面上消失。
  final List<Cage> unplaced;

  bool get isEmpty => rows.isEmpty && unplaced.isEmpty;

  int get placedCount =>
      rows.fold(0, (total, row) => total + row.cages.length);

  static CageLayout fromCages(Iterable<Cage> cages) {
    final byRow = <String, List<Cage>>{};
    final unplaced = <Cage>[];

    for (final cage in cages) {
      if (_isPlaceable(cage)) {
        byRow.putIfAbsent(cage.rowCode, () => <Cage>[]).add(cage);
      } else {
        unplaced.add(cage);
      }
    }

    final rowCodes = byRow.keys.toList()..sort(compareRowCodes);
    final rows = <CageMapRow>[];
    for (final rowCode in rowCodes) {
      final built = _buildRow(rowCode, byRow[rowCode]!);
      rows.add(built.row);
      unplaced.addAll(built.displaced);
    }

    unplaced.sort((a, b) => a.cageNumber.compareTo(b.cageNumber));
    return CageLayout(rows: rows, unplaced: unplaced);
  }

  static bool _isPlaceable(Cage cage) {
    // `Cage.fromJson` 已把 <=0 的层/位归一成 null，这里只需判空。
    // 'LEGACY' 是历史数据的占位排号，不是真实排，进「未编排」。
    return cage.layerIndex != null &&
        cage.positionIndex != null &&
        cage.rowCode.isNotEmpty &&
        cage.rowCode != 'LEGACY';
  }

  static _RowBuild _buildRow(String rowCode, List<Cage> cages) {
    final displaced = <Cage>[];
    final slots = <int, Map<int, Cage>>{};
    var positionSpan = 0;

    for (final cage in cages) {
      final layer = cage.layerIndex!;
      final position = cage.positionIndex!;
      final layerSlots = slots.putIfAbsent(layer, () => <int, Cage>{});
      final occupant = layerSlots[position];
      if (occupant != null) {
        // 同一坐标出现两个笼位属于数据问题。保留先到的那个，另一个挪到
        // 「未编排」而不是覆盖掉，否则界面上会凭空少一个笼。
        displaced.add(cage);
        continue;
      }
      layerSlots[position] = cage;
      if (position > positionSpan) {
        positionSpan = position;
      }
    }

    // 层号从大到小：现实里最上层在最上面，地图跟着物理货架走。
    final layerIndexes = slots.keys.toList()..sort((a, b) => b.compareTo(a));
    final layers = <CageMapLayer>[];
    for (final layerIndex in layerIndexes) {
      final layerSlots = slots[layerIndex]!;
      layers.add(
        CageMapLayer(
          layerIndex: layerIndex,
          // 补齐空槽，让每层的第 N 列在屏幕上真的对齐；缺笼的位置留白。
          cells: List.generate(
            positionSpan,
            (index) => CageMapCell(
              positionIndex: index + 1,
              cage: layerSlots[index + 1],
            ),
          ),
        ),
      );
    }

    return _RowBuild(
      row: CageMapRow(
        rowCode: rowCode,
        layers: layers,
        positionSpan: positionSpan,
      ),
      displaced: displaced,
    );
  }

  /// R2 要排在 R10 前面：按「数字段按数值、其余按字符」比较。
  static int compareRowCodes(String a, String b) {
    final ai = _segments(a);
    final bi = _segments(b);
    for (var index = 0; index < ai.length && index < bi.length; index += 1) {
      final left = ai[index];
      final right = bi[index];
      if (left is int && right is int) {
        if (left != right) {
          return left.compareTo(right);
        }
        continue;
      }
      final compared = left.toString().compareTo(right.toString());
      if (compared != 0) {
        return compared;
      }
    }
    return ai.length.compareTo(bi.length);
  }

  static List<Object> _segments(String value) {
    final segments = <Object>[];
    final buffer = StringBuffer();
    var bufferIsDigit = false;

    void flush() {
      if (buffer.isEmpty) {
        return;
      }
      final text = buffer.toString();
      segments.add(bufferIsDigit ? int.parse(text) : text.toUpperCase());
      buffer.clear();
    }

    for (final rune in value.runes) {
      final char = String.fromCharCode(rune);
      final isDigit = char.codeUnitAt(0) >= 0x30 && char.codeUnitAt(0) <= 0x39;
      if (buffer.isNotEmpty && isDigit != bufferIsDigit) {
        flush();
      }
      bufferIsDigit = isDigit;
      buffer.write(char);
    }
    flush();
    return segments;
  }
}

class CageMapRow {
  const CageMapRow({
    required this.rowCode,
    required this.layers,
    required this.positionSpan,
  });

  final String rowCode;

  /// 层号从大到小。
  final List<CageMapLayer> layers;

  /// 该排最大位号，决定网格列数。
  final int positionSpan;

  Iterable<Cage> get cages =>
      layers.expand((layer) => layer.cells).map((cell) => cell.cage).nonNulls;

  int countWhere(bool Function(Cage cage) test) =>
      cages.where(test).length;

  int countAttention(CageAttention attention) =>
      countWhere((cage) => cage.attention == attention);
}

class CageMapLayer {
  const CageMapLayer({required this.layerIndex, required this.cells});

  final int layerIndex;

  /// 位号从小到大，缺笼的位置 [CageMapCell.cage] 为 null。
  final List<CageMapCell> cells;
}

class CageMapCell {
  const CageMapCell({required this.positionIndex, this.cage});

  final int positionIndex;
  final Cage? cage;

  bool get isEmptySlot => cage == null;
}

class _RowBuild {
  const _RowBuild({required this.row, required this.displaced});

  final CageMapRow row;
  final List<Cage> displaced;
}
