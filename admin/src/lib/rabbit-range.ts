import type { Cage } from "@/types/api";

export const MAX_RANGE_CAGE_SLOTS = 500;
export const MAX_RANGE_RABBITS = 1000;

export interface CageCoordinateRange {
  rowStart: number;
  rowEnd: number;
  positionStart: number;
  positionEnd: number;
  layerStart: number;
  layerEnd: number;
}

export interface RangeCandidate {
  cage: Cage;
  blockedReason: string | null;
}

export interface CageRangePreview {
  range: CageCoordinateRange;
  requestedSlotCount: number;
  missingCageCount: number;
  unplacedCageCount: number;
  candidates: RangeCandidate[];
  eligible: RangeCandidate[];
  blocked: RangeCandidate[];
  enteredRabbitCount: number;
}

export function normalizeCageRange(
  range: CageCoordinateRange,
): CageCoordinateRange {
  return {
    rowStart: Math.min(range.rowStart, range.rowEnd),
    rowEnd: Math.max(range.rowStart, range.rowEnd),
    positionStart: Math.min(range.positionStart, range.positionEnd),
    positionEnd: Math.max(range.positionStart, range.positionEnd),
    layerStart: Math.min(range.layerStart, range.layerEnd),
    layerEnd: Math.max(range.layerStart, range.layerEnd),
  };
}

export function buildCageRangePreview(
  cages: Cage[],
  rangeInput: CageCoordinateRange,
  rabbitType: string,
  rabbitsPerCage: number,
): CageRangePreview | null {
  const range = normalizeCageRange(rangeInput);
  if (
    !Object.values(range).every(
      (value) => Number.isInteger(value) && value > 0,
    ) ||
    rabbitsPerCage <= 0
  ) {
    return null;
  }
  const requestedSlotCount =
    (range.rowEnd - range.rowStart + 1) *
    (range.positionEnd - range.positionStart + 1) *
    (range.layerEnd - range.layerStart + 1);
  const byCoordinate = new Map<string, Cage[]>();
  let unplacedCageCount = 0;
  for (const cage of cages) {
    const coordinate = cageCoordinate(cage);
    if (!coordinate) {
      unplacedCageCount += 1;
      continue;
    }
    if (
      coordinate.row < range.rowStart ||
      coordinate.row > range.rowEnd ||
      coordinate.position < range.positionStart ||
      coordinate.position > range.positionEnd ||
      coordinate.layer < range.layerStart ||
      coordinate.layer > range.layerEnd
    ) {
      continue;
    }
    const key = `${coordinate.row}:${coordinate.position}:${coordinate.layer}`;
    byCoordinate.set(key, [...(byCoordinate.get(key) ?? []), cage]);
  }

  const candidates: RangeCandidate[] = [];
  for (const cagesAtCoordinate of byCoordinate.values()) {
    if (cagesAtCoordinate.length > 1) {
      candidates.push(
        ...cagesAtCoordinate.map((cage) => ({
          cage,
          blockedReason: "坐标重复",
        })),
      );
    } else {
      const cage = cagesAtCoordinate[0];
      candidates.push({
        cage,
        blockedReason: entryBlockedReason(cage, rabbitType, rabbitsPerCage),
      });
    }
  }
  candidates.sort((left, right) =>
    left.cage.cageNumber.localeCompare(right.cage.cageNumber),
  );
  const eligible = candidates.filter(
    (candidate) => candidate.blockedReason === null,
  );
  const blocked = candidates.filter(
    (candidate) => candidate.blockedReason !== null,
  );
  return {
    range,
    requestedSlotCount,
    missingCageCount: Math.max(0, requestedSlotCount - byCoordinate.size),
    unplacedCageCount,
    candidates,
    eligible,
    blocked,
    enteredRabbitCount: eligible.length * rabbitsPerCage,
  };
}

function cageCoordinate(
  cage: Cage,
): { row: number; position: number; layer: number } | null {
  const rowCode = (cage.rowCode ?? "").trim();
  const matched = rowCode.match(/^[Rr]?(\d+)$/);
  if (!matched || rowCode.toUpperCase() === "LEGACY") return null;
  const row = Number(matched[1]);
  const position = cage.positionIndex ?? 0;
  const layer = cage.layerIndex ?? 0;
  if (!Number.isInteger(row) || row <= 0 || position <= 0 || layer <= 0)
    return null;
  return { row, position, layer };
}

function entryBlockedReason(
  cage: Cage,
  rabbitType: string,
  rabbitsPerCage: number,
): string | null {
  if (!cage.isEnabled) return "笼位已停用";
  const targetStatus =
    rabbitType === "0"
      ? "1"
      : rabbitType === "1"
        ? "2"
        : rabbitType === "2"
          ? "3"
          : "";
  if (cage.status !== "0" && cage.status !== targetStatus)
    return "笼位用途不匹配";
  if (rabbitType !== "2" && rabbitsPerCage !== 1)
    return "单兔笼每笼只能录入 1 只";
  if (cage.status === "1" || cage.status === "2")
    return cage.rabbitCount + rabbitsPerCage > 1 ? "单兔笼已满" : null;
  if (rabbitType === "2" && cage.rabbitCount + rabbitsPerCage > 10)
    return "商品兔笼已满（最多 10 只）";
  return null;
}
