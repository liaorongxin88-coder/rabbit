import 'package:rabbit_flutter/src/domain/cages/cage.dart';

const maxRangeCageSlots = 500;
const maxRangeRabbits = 1000;

class CageCoordinateRange {
  const CageCoordinateRange({
    required this.rowStart,
    required this.rowEnd,
    required this.positionStart,
    required this.positionEnd,
    required this.layerStart,
    required this.layerEnd,
  });

  final int rowStart;
  final int rowEnd;
  final int positionStart;
  final int positionEnd;
  final int layerStart;
  final int layerEnd;

  factory CageCoordinateRange.normalized({
    required int rowStart,
    required int rowEnd,
    required int positionStart,
    required int positionEnd,
    required int layerStart,
    required int layerEnd,
  }) {
    return CageCoordinateRange(
      rowStart: rowStart < rowEnd ? rowStart : rowEnd,
      rowEnd: rowStart < rowEnd ? rowEnd : rowStart,
      positionStart: positionStart < positionEnd ? positionStart : positionEnd,
      positionEnd: positionStart < positionEnd ? positionEnd : positionStart,
      layerStart: layerStart < layerEnd ? layerStart : layerEnd,
      layerEnd: layerStart < layerEnd ? layerEnd : layerStart,
    );
  }

  int get slotCount =>
      (rowEnd - rowStart + 1) *
      (positionEnd - positionStart + 1) *
      (layerEnd - layerStart + 1);

  bool get isValid =>
      rowStart > 0 && positionStart > 0 && layerStart > 0 && slotCount > 0;

  bool contains(Cage cage) {
    final row = _numericRow(cage.rowCode);
    final position = cage.positionIndex;
    final layer = cage.layerIndex;
    return row != null &&
        position != null &&
        layer != null &&
        row >= rowStart &&
        row <= rowEnd &&
        position >= positionStart &&
        position <= positionEnd &&
        layer >= layerStart &&
        layer <= layerEnd;
  }

  String get label =>
      '排 $rowStart-$rowEnd · 位 $positionStart-$positionEnd · 层 $layerStart-$layerEnd';
}

class CageRangeCandidate {
  const CageRangeCandidate({required this.cage, this.blockedReason});

  final Cage cage;
  final String? blockedReason;

  bool get canEnter => blockedReason == null;
}

class CageRangePreview {
  const CageRangePreview({
    required this.range,
    required this.candidates,
    required this.missingCageCount,
    required this.unplacedCageCount,
    required this.rabbitsPerCage,
  });

  final CageCoordinateRange range;
  final List<CageRangeCandidate> candidates;
  final int missingCageCount;
  final int unplacedCageCount;
  final int rabbitsPerCage;

  List<CageRangeCandidate> get eligible =>
      candidates.where((candidate) => candidate.canEnter).toList();

  List<CageRangeCandidate> get blocked =>
      candidates.where((candidate) => !candidate.canEnter).toList();

  int get enteredRabbitCount => eligible.length * rabbitsPerCage;

  static CageRangePreview fromCages({
    required Iterable<Cage> cages,
    required CageCoordinateRange range,
    required String rabbitType,
    required int rabbitsPerCage,
  }) {
    final ranged = <Cage>[];
    var unplaced = 0;
    for (final cage in cages) {
      if (_hasCoordinate(cage)) {
        if (range.contains(cage)) ranged.add(cage);
      } else {
        unplaced++;
      }
    }

    final byCoordinate = <String, List<Cage>>{};
    for (final cage in ranged) {
      final key =
          '${_numericRow(cage.rowCode)}:${cage.positionIndex}:${cage.layerIndex}';
      (byCoordinate[key] ??= []).add(cage);
    }
    final candidates = <CageRangeCandidate>[];
    for (final entries in byCoordinate.values) {
      if (entries.length > 1) {
        candidates.addAll(entries.map(
          (cage) => CageRangeCandidate(cage: cage, blockedReason: '坐标重复'),
        ));
      } else {
        final cage = entries.single;
        candidates.add(CageRangeCandidate(
          cage: cage,
          blockedReason: _entryBlockedReason(cage, rabbitType, rabbitsPerCage),
        ));
      }
    }
    candidates.sort((a, b) => a.cage.cageNumber.compareTo(b.cage.cageNumber));
    final preview = CageRangePreview(
      range: range,
      candidates: candidates,
      missingCageCount:
          (range.slotCount - byCoordinate.length).clamp(0, range.slotCount),
      unplacedCageCount: unplaced,
      rabbitsPerCage: rabbitsPerCage,
    );
    return preview;
  }
}

bool _hasCoordinate(Cage cage) =>
    _numericRow(cage.rowCode) != null &&
    cage.positionIndex != null &&
    cage.positionIndex! > 0 &&
    cage.layerIndex != null &&
    cage.layerIndex! > 0;

int? _numericRow(String rowCode) {
  final normalized = rowCode.trim();
  if (normalized.isEmpty || normalized.toUpperCase() == 'LEGACY') return null;
  final value = RegExp(r'^[Rr]?(\d+)$').firstMatch(normalized)?.group(1);
  return value == null ? null : int.tryParse(value);
}

String? _entryBlockedReason(Cage cage, String type, int rabbitsPerCage) {
  if (!cage.isEnabled) return '笼位已停用';
  if (!cage.acceptsRabbitType(type)) return '用途不匹配';
  if (type != '2' && rabbitsPerCage != 1) return '单兔笼每笼只能录入 1 只';
  if (cage.status == '1' || cage.status == '2') {
    return cage.rabbitCount + rabbitsPerCage > 1 ? '单兔笼已满' : null;
  }
  if (type == '2' &&
      cage.rabbitCount + rabbitsPerCage > Cage.commodityCapacity) {
    return '商品兔笼已满';
  }
  return null;
}
