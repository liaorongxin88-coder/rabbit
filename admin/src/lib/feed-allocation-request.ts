import { hasAtMostDecimalPlaces } from "./decimal.ts";
import type {
  FeedAllocationGroup,
  FeedBatchAllocation,
  FeedLogRequest,
} from "@/types/feed";

export interface PendingFeedRequest {
  fingerprint: string;
  request: FeedLogRequest;
}

export function feedAllocationKey(
  group: Pick<FeedAllocationGroup, "batchId" | "phase">,
) {
  return `${group.batchId ?? "unassigned"}:${group.phase}`;
}

export function canAutoAssignFeedGroup(groups: FeedAllocationGroup[]) {
  return (
    groups.length === 1 &&
    groups[0].batchId != null &&
    groups[0].phase !== "UNASSIGNED"
  );
}

export function normalizeFeedAllocations(
  groups: FeedAllocationGroup[],
  amounts: Record<string, string>,
): FeedBatchAllocation[] {
  return groups.map((group) => ({
    batchId: group.batchId,
    phase: group.phase,
    amountKg: Number(amounts[feedAllocationKey(group)] ?? 0),
  }));
}

export function feedAllocationError(
  amount: number,
  allocations: FeedBatchAllocation[],
) {
  if (!Number.isFinite(amount) || amount <= 0) return "请填写大于 0 的投喂总量";
  if (!hasAtMostDecimalPlaces(amount, 2)) {
    return "投喂总量最多保留两位小数";
  }
  if (allocations.length === 0) return "请先预览批次与阶段归属";
  if (
    allocations.some(
      (item) => !Number.isFinite(item.amountKg) || item.amountKg <= 0,
    )
  ) {
    return "请填写每个归属分组的投喂量";
  }
  if (allocations.some((item) => !hasAtMostDecimalPlaces(item.amountKg, 2))) {
    return "分组投喂量最多保留两位小数";
  }
  const allocationTotal = allocations.reduce(
    (total, item) => total + Math.round(item.amountKg * 100),
    0,
  );
  return allocationTotal === Math.round(amount * 100)
    ? null
    : "分组投喂量合计必须等于投喂总量";
}

export function getOrCreateFeedRequest(
  current: PendingFeedRequest | null,
  draft: Omit<FeedLogRequest, "requestId">,
  createRequestId: () => string,
): PendingFeedRequest {
  const fingerprint = JSON.stringify(draft);
  return current?.fingerprint === fingerprint
    ? current
    : {
        fingerprint,
        request: { ...draft, requestId: createRequestId() },
      };
}
