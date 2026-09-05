import type { OutboundBatchAllocation } from "@/types/api";

export interface RabbitSaleRequest {
  rabbitIds: [number];
  saleTime: number;
  totalWeight: number;
  unitPrice: number;
  unitPricePerKg: number;
  batchAllocations: OutboundBatchAllocation[];
  customer?: string;
  remark?: string;
  requestId: string;
}

export interface RabbitSaleResult {
  id: number;
  houseId: number;
  saleTime: string;
  customer: string | null;
  totalWeight: number;
  unitPrice: number | null;
  totalAmount: number | null;
  remark: string | null;
  requestId: string;
  createBy: string | null;
  createTime: string | null;
  updateBy: string | null;
  updateTime: string | null;
}
