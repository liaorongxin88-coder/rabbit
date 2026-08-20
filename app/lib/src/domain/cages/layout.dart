import 'package:rabbit_flutter/src/domain/cages/cage.dart';
import 'package:rabbit_flutter/src/domain/cages/attention.dart';

/// 把一串扁平的笼位还原成现场的样子：**层是一个要切换的空间，不是往上叠的一格**。
///
/// 兔场的多层笼是错位的：二层不在一层正上方，两层之间也不共用过道。把层竖着
/// 堆在同一排里，看着像货架剖面图，实际找笼时反而对不上——人站在某一层前面时，
/// 眼里只有这一层的那几排。所以顶层结构是「层 → 排 → 位」，界面一次只显示一层。
///
/// 一层之内，排从上往下（排号自然序），位从左往右（位号递增），
/// 就是站在过道里看这一层的样子。
///
/// 这里只做分组与排序，不碰任何 UI，方便单测钉住边界。
class CageLayout {
  const CageLayout({required this.layers, required this.unplaced});

  /// 层号从小到大：1 层在最下面，也是切换器里的第一个。
  final List<CageMapLayer> layers;

  /// 没有层/位坐标、排号为空或 `LEGACY` 的笼位，以及坐标撞车被挤出来的笼位。
  /// 它们放不进网格，但绝不能从界面上消失。
  final List<Cage> unplaced;

  bool get isEmpty => layers.isEmpty && unplaced.isEmpty;

  int get placedCount =>
      layers.fold(0, (total, layer) => total + layer.cages.length);

  static CageLayout fromCages(Iterable<Cage> cages) {
    final placed = <Cage>[];
    final unplaced = <Cage>[];
    for (final cage in cages) {
      (_isPlaceable(cage) ? placed : unplaced).add(cage);
    }

    // 每排的位宽取「跨所有层的最大位号」：切层时网格不该忽宽忽窄地跳。
    final spanByRow = <String, int>{};
    for (final cage in placed) {
      final current = spanByRow[cage.rowCode] ?? 0;
      final position = cage.positionIndex!;
      if (position > current) {
        spanByRow[cage.rowCode] = position;
      }
    }

    final byLayer = <int, Map<String, List<Cage>>>{};
    for (final cage in placed) {
      byLayer
          .putIfAbsent(cage.layerIndex!, () => <String, List<Cage>>{})
          .putIfAbsent(cage.rowCode, () => <Cage>[])
          .add(cage);
    }

    final layerIndexes = byLayer.keys.toList()..sort();
    final layers = <CageMapLayer>[];
    for (final layerIndex in layerIndexes) {
      final rowsByCode = byLayer[layerIndex]!;
      final rowCodes = rowsByCode.keys.toList()..sort(compareRowCodes);
      final rows = <CageMapRow>[];
      for (final rowCode in rowCodes) {
        final built = _buildRow(
          rowCode,
          rowsByCode[rowCode]!,
          spanByRow[rowCode] ?? 0,
        );
        rows.add(built.row);
        unplaced.addAll(built.displaced);
      }
      layers.add(CageMapLayer(layerIndex: layerIndex, rows: rows));
    }

    unplaced.sort((a, b) => a.cageNumber.compareTo(b.cageNumber));
    return CageLayout(layers: layers, unplaced: unplaced);
  }

  static bool _isPlaceable(Cage cage) {
    // `Cage.fromJson` 已把 <=0 的层/位归一成 null，这里只需判空。
    // 'LEGACY' 是历史数据的占位排号，不是真实排，进「未编排」。
    return cage.layerIndex != null &&
        cage.positionIndex != null &&
        cage.rowCode.isNotEmpty &&
        cage.rowCode != 'LEGACY';
  }

  static _RowBuild _buildRow(String rowCode, List<Cage> cages, int span) {
    final displaced = <Cage>[];
    final byPosition = <int, Cage>{};

    for (final cage in cages) {
      final position = cage.positionIndex!;
      if (byPosition.containsKey(position)) {
        // 同一坐标出现两个笼位属于数据问题。保留先到的那个，另一个挪到
        // 「未编排」而不是覆盖掉，否则界面上会凭空少一个笼。
        displaced.add(cage);
        continue;
      }
      byPosition[position] = cage;
    }

    // 补齐空槽：缺笼的位置留白，这样第 N 位在屏幕上始终对得齐。
    final cells = List.generate(
      span,
      (index) => CageMapCell(
        positionIndex: index + 1,
        cage: byPosition[index + 1],
      ),
    );

    return _RowBuild(
      row: CageMapRow(
        rowCode: rowCode,
        cells: cells,
        positionSpan: span,
      ),
      displaced: displaced,
    );
  }

  /// R2 要排在 R10 前面：按「数字段按数值、其余按字符」比较。
  static int compareRowCodes(String a, String b) {
    final left = _segments(a);
    final right = _segments(b);
    final shared = left.length < right.length ? left.length : right.length;
    for (var index = 0; index < shared; index++) {
      final l = left[index];
      final r = right[index];
      final lNumber = int.tryParse(l);
      final rNumber = int.tryParse(r);
      final int compared;
      if (lNumber != null && rNumber != null) {
        compared = lNumber.compareTo(rNumber);
      } else {
        compared = l.compareTo(r);
      }
      if (compared != 0) return compared;
    }
    return left.length.compareTo(right.length);
  }

  static List<String> _segments(String value) {
    final segments = <String>[];
    final buffer = StringBuffer();
    bool? bufferIsDigit;

    void flush() {
      if (buffer.isNotEmpty) {
        segments.add(buffer.toString());
        buffer.clear();
      }
    }

    for (final rune in value.runes) {
      final char = String.fromCharCode(rune);
      final isDigit = char.codeUnitAt(0) >= 48 && char.codeUnitAt(0) <= 57;
      if (bufferIsDigit != null && isDigit != bufferIsDigit) {
        flush();
      }
      bufferIsDigit = isDigit;
      buffer.write(char);
    }
    flush();
    return segments;
  }
}

class CageMapLayer {
  const CageMapLayer({required this.layerIndex, required this.rows});

  final int layerIndex;

  /// 该层里的排，按排号自然序。
  final List<CageMapRow> rows;

  Iterable<Cage> get cages => rows.expand((row) => row.cages);

  int countWhere(bool Function(Cage cage) test) => cages.where(test).length;

  int countAttention(CageAttention attention) =>
      countWhere((cage) => cage.attention == attention);
}

class CageMapRow {
  const CageMapRow({
    required this.rowCode,
    required this.cells,
    required this.positionSpan,
  });

  final String rowCode;

  /// 这一排的位，按位号从左往右；缺笼的位置是空槽，不把后面的位往前挤。
  final List<CageMapCell> cells;

  /// 该排最大位号，决定网格列数。
  final int positionSpan;

  Iterable<Cage> get cages => cells.map((cell) => cell.cage).nonNulls;

  int countWhere(bool Function(Cage cage) test) => cages.where(test).length;

  int countAttention(CageAttention attention) =>
      countWhere((cage) => cage.attention == attention);
}

class CageMapCell {
  const CageMapCell({required this.positionIndex, this.cage});

  final int positionIndex;
  final Cage? cage;

  /// 有这个位号但没有笼：现场缺一个笼，格子留白。
  bool get isEmptySlot => cage == null;
}

class _RowBuild {
  const _RowBuild({required this.row, required this.displaced});

  final CageMapRow row;
  final List<Cage> displaced;
}
