import type { Rabbit } from '@/types/api'
import type { RabbitSaleRequest } from '@/types/rabbit-sale'

export type RabbitSaleDraft = Omit<RabbitSaleRequest, 'requestId'>

export function isIndividualSaleRabbit(rabbit: Pick<Rabbit, 'type'>) {
  return rabbit.type === '0' || rabbit.type === '1'
}

export function getOrCreateRabbitSaleRequest(
  current: RabbitSaleRequest | null,
  draft: RabbitSaleDraft,
  createRequestId: () => string,
): RabbitSaleRequest {
  if (
    current
    && current.rabbitIds[0] === draft.rabbitIds[0]
    && current.saleTime === draft.saleTime
    && current.totalWeight === draft.totalWeight
    && current.unitPrice === draft.unitPrice
    && current.customer === draft.customer
    && current.remark === draft.remark
  ) {
    return current
  }
  return { ...draft, requestId: createRequestId() }
}

export function rabbitSalesPath() {
  return '/api/sales'
}
