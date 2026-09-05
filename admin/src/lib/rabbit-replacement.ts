import { hasAtMostDecimalPlaces } from "./decimal.ts";
import type {
  BatchRabbit,
  Rabbit,
  RabbitReplacementRequest,
} from "@/types/api";

export type RabbitReplacementDraft = Omit<
  RabbitReplacementRequest,
  "requestId"
>;

export type RabbitReplacementSource =
  | { status: "ready"; batchId: number | null }
  | { status: "unavailable" | "ambiguous"; batchId: null };

export function rabbitReplacementSource(
  rabbit: Pick<Rabbit, "birthBatchId">,
  memberships: Pick<BatchRabbit, "batchId" | "batchRole" | "isActive">[] | null,
): RabbitReplacementSource {
  if (rabbit.birthBatchId != null) {
    return { status: "ready", batchId: rabbit.birthBatchId };
  }
  if (memberships === null) {
    return { status: "unavailable", batchId: null };
  }
  const batchIds = [
    ...new Set(
      memberships
        .filter(
          (membership) =>
            membership.isActive &&
            membership.batchRole?.toLowerCase() === "fattening",
        )
        .map((membership) => membership.batchId),
    ),
  ].sort((left, right) => left - right);
  if (batchIds.length > 1) {
    return { status: "ambiguous", batchId: null };
  }
  return { status: "ready", batchId: batchIds[0] ?? null };
}

export function rabbitReplacementWeightError(totalWeightKg: number) {
  if (!Number.isFinite(totalWeightKg) || totalWeightKg <= 0) {
    return "请填写大于 0 的转换实测总重";
  }
  return hasAtMostDecimalPlaces(totalWeightKg, 3)
    ? null
    : "转换实测总重最多保留三位小数";
}

export function getOrCreateRabbitReplacementRequest(
  current: RabbitReplacementRequest | null,
  draft: RabbitReplacementDraft,
  createRequestId: () => string,
): RabbitReplacementRequest {
  if (
    current &&
    current.forceExitBatch === draft.forceExitBatch &&
    current.targetCageId === draft.targetCageId &&
    current.rabbitIds.length === draft.rabbitIds.length &&
    current.rabbitIds.every(
      (rabbitId, index) => rabbitId === draft.rabbitIds[index],
    ) &&
    JSON.stringify(current.batchAllocations) ===
      JSON.stringify(draft.batchAllocations)
  ) {
    return current;
  }

  return {
    rabbitIds: [...draft.rabbitIds],
    forceExitBatch: draft.forceExitBatch,
    targetCageId: draft.targetCageId,
    batchAllocations: draft.batchAllocations.map((allocation) => ({
      ...allocation,
    })),
    requestId: createRequestId(),
  };
}

export function rabbitReplacementPath() {
  return "/api/rabbits/replacement";
}
