export interface RabbitSaleRequest {
  rabbitIds: [number];
  saleTime: number;
  totalWeight: number;
  unitPrice?: number;
  customer?: string;
  remark?: string;
  requestId: string;
}
