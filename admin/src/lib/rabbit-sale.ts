import { hasAtMostDecimalPlaces } from "./decimal.ts";
import type { Rabbit } from "@/types/api";
import type { RabbitSaleRequest } from "@/types/rabbit-sale";

export type RabbitSaleDraft = Omit<RabbitSaleRequest, "requestId">;

export function isIndividualSaleRabbit(rabbit: Pick<Rabbit, "type">) {
  return rabbit.type === "0" || rabbit.type === "1";
}

export function rabbitSaleValidationError(
  totalWeight: number,
  unitPricePerKg: number,
) {
  if (!Number.isFinite(totalWeight) || totalWeight <= 0) {
    return "请填写大于 0 的销售重量";
  }
  if (!hasAtMostDecimalPlaces(totalWeight, 3)) {
    return "销售重量最多保留三位小数";
  }
  if (!Number.isFinite(unitPricePerKg) || unitPricePerKg <= 0) {
    return "请填写大于 0 的重量单价";
  }
  if (
    unitPricePerKg > 99_999_999.99 ||
    !hasAtMostDecimalPlaces(unitPricePerKg, 2)
  ) {
    return "重量单价最多保留两位小数";
  }
  return null;
}

export function getOrCreateRabbitSaleRequest(
  current: RabbitSaleRequest | null,
  draft: RabbitSaleDraft,
  createRequestId: () => string,
): RabbitSaleRequest {
  if (
    current &&
    current.rabbitIds[0] === draft.rabbitIds[0] &&
    current.saleTime === draft.saleTime &&
    current.totalWeight === draft.totalWeight &&
    current.unitPrice === draft.unitPrice &&
    current.unitPricePerKg === draft.unitPricePerKg &&
    JSON.stringify(current.batchAllocations) ===
      JSON.stringify(draft.batchAllocations) &&
    current.customer === draft.customer &&
    current.remark === draft.remark
  ) {
    return current;
  }
  return { ...draft, requestId: createRequestId() };
}

export function rabbitSalesPath() {
  return "/api/sales";
}
