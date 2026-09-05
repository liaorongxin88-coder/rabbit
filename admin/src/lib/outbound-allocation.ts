import { hasAtMostDecimalPlaces } from "./decimal.ts";
import type {
  OutboundBatchAllocation,
  OutboundRabbit,
  OutboundSelectedItem,
} from "@/types/api";

export interface OutboundAllocationGroup {
  key: string;
  batchId: number | null;
  rabbitCount: number;
}

export interface PendingOutboundSubmission<T> {
  fingerprint: string;
  requestId: string;
  payload: T;
}

export function outboundAllocationKey(batchId: number | null) {
  return batchId == null ? "unassigned" : String(batchId);
}

export function buildOutboundAllocationGroups(
  selectedItems: OutboundSelectedItem[],
  rabbits: OutboundRabbit[],
): OutboundAllocationGroup[] {
  const rabbitById = new Map(
    rabbits.map((rabbit) => [rabbit.rabbitId, rabbit]),
  );
  const groups = new Map<string, OutboundAllocationGroup>();
  for (const item of selectedItems) {
    const batchId = rabbitById.get(item.rabbitId)?.batchId ?? null;
    const key = outboundAllocationKey(batchId);
    const group = groups.get(key);
    if (group) {
      group.rabbitCount += 1;
    } else {
      groups.set(key, { key, batchId, rabbitCount: 1 });
    }
  }
  return [...groups.values()].sort((left, right) => {
    if (left.batchId == null) return 1;
    if (right.batchId == null) return -1;
    return left.batchId - right.batchId;
  });
}

export function normalizeOutboundAllocations(
  groups: OutboundAllocationGroup[],
  weights: Record<string, string>,
): OutboundBatchAllocation[] {
  return groups.map((group) => ({
    batchId: group.batchId,
    actualWeightKg: Number(weights[group.key] ?? 0),
  }));
}

export function outboundAllocationError(
  totalWeight: number,
  unitPricePerKg: number,
  allocations: OutboundBatchAllocation[],
) {
  if (!Number.isFinite(totalWeight) || totalWeight <= 0) {
    return "请填写大于 0 的总重量";
  }
  if (totalWeight > 100_000 || !hasAtMostDecimalPlaces(totalWeight, 3)) {
    return "总重量不能超过 100000 kg，且最多保留三位小数";
  }
  if (!Number.isFinite(unitPricePerKg) || unitPricePerKg <= 0) {
    return "请填写大于 0 的统一重量单价";
  }
  if (
    unitPricePerKg > 99_999_999.99 ||
    !hasAtMostDecimalPlaces(unitPricePerKg, 2)
  ) {
    return "统一重量单价最多保留两位小数";
  }
  if (
    allocations.some(
      (item) =>
        !Number.isFinite(item.actualWeightKg) || item.actualWeightKg <= 0,
    )
  ) {
    return "请填写每个批次分组的实际重量";
  }
  if (
    allocations.some((item) => !hasAtMostDecimalPlaces(item.actualWeightKg, 3))
  ) {
    return "批次分组重量最多保留三位小数";
  }
  const allocationTotal = allocations.reduce(
    (total, item) => total + Math.round(item.actualWeightKg * 1_000),
    0,
  );
  if (allocationTotal !== Math.round(totalWeight * 1_000)) {
    return "批次分组重量合计必须等于订单总重量";
  }
  return null;
}

export function getOrCreateOutboundSubmission<T extends object>(
  current: PendingOutboundSubmission<T> | null,
  payload: T,
  createRequestId: () => string,
): PendingOutboundSubmission<T> {
  const fingerprint = JSON.stringify(payload);
  return current?.fingerprint === fingerprint
    ? current
    : { fingerprint, payload, requestId: createRequestId() };
}
