export type FeedAllocationPhase = "BREEDING" | "FATTENING" | "UNASSIGNED";

export interface FeedBatchAllocation {
  batchId: number | null;
  phase: FeedAllocationPhase;
  amountKg: number;
}

export interface FeedAllocationGroup {
  batchId: number | null;
  phase: FeedAllocationPhase;
  rabbitCount: number;
}

export interface FeedAllocationPreview {
  groups: FeedAllocationGroup[];
}

export interface FeedAllocationPreviewRequest {
  rabbitIds: number[];
  feedTime: number;
}

export interface FeedLogRequest extends FeedAllocationPreviewRequest {
  amount: number;
  unit: "kg";
  feedType?: string;
  itemId?: number;
  remark?: string;
  allocations: FeedBatchAllocation[];
  requestId: string;
}
