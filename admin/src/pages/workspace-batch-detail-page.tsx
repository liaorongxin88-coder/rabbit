import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  ArrowLeftIcon,
  ClipboardClockIcon,
  DownloadIcon,
  Edit3Icon,
  RefreshCwIcon,
  Rows3Icon,
} from "lucide-react";
import { toast } from "sonner";
import {
  downloadBatchStatistics,
  getBatch,
  getBatchStatistics,
} from "@/api/workspace";
import {
  BatchCarcassYieldDialog,
  BatchCarcassYieldHistoryDialog,
} from "@/components/batch-carcass-yield";
import {
  BatchStatisticsPanel,
  type BatchStatisticsStatus,
} from "@/components/batch-statistics";
import { PageHeader } from "@/components/page-header";
import { HousePermissionBadge } from "@/components/permission-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Empty, EmptyDescription, EmptyTitle } from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { batchStatusLabel, isCompletedBatchStatus } from "@/lib/batch-workflow";
import { triggerBrowserDownload } from "@/lib/download";
import { hasPermission, useWorkspace } from "@/lib/workspace";
import type { BatchStatistics, ProductionBatch } from "@/types/api";

export function WorkspaceBatchDetailPage() {
  const workspace = useWorkspace();
  const params = useParams();
  const batchId = Number(params.batchId);
  const [batch, setBatch] = useState<ProductionBatch | null>(null);
  const [batchLoading, setBatchLoading] = useState(true);
  const [statistics, setStatistics] = useState<BatchStatistics | null>(null);
  const [statisticsStatus, setStatisticsStatus] =
    useState<BatchStatisticsStatus>("idle");
  const [carcassYieldOpen, setCarcassYieldOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [exporting, setExporting] = useState(false);
  const exportInProgress = useRef(false);
  const exportVersion = useRef(0);
  const batchLoadVersion = useRef(0);
  const statisticsLoadVersion = useRef(0);
  const statisticsHouseId = useRef<number | null>(null);
  const houseId = workspace.selectedHouse?.id ?? null;
  const activeHouseIdRef = useRef(houseId);
  activeHouseIdRef.current = houseId;
  const canEditBatch = hasPermission(
    workspace.permission,
    "rabbit:batches:edit",
  );
  const canReadAudit = hasPermission(workspace.permission, "rabbit:audit:list");
  const canExport = hasPermission(
    workspace.permission,
    "rabbit:reports:export",
  );

  const validBatchId = Number.isSafeInteger(batchId) && batchId > 0;

  const loadBatch = useCallback(async () => {
    if (houseId !== activeHouseIdRef.current) return;
    const version = ++batchLoadVersion.current;
    if (!houseId || !validBatchId) {
      setBatch(null);
      setBatchLoading(false);
      return;
    }
    setBatchLoading(true);
    try {
      const nextBatch = await getBatch(houseId, batchId);
      if (nextBatch.id !== batchId || nextBatch.houseId !== houseId) {
        throw new Error("批次响应与当前兔场不一致");
      }
      if (
        version === batchLoadVersion.current &&
        houseId === activeHouseIdRef.current
      ) {
        setBatch(nextBatch);
      }
    } catch {
      if (
        version === batchLoadVersion.current &&
        houseId === activeHouseIdRef.current
      ) {
        setBatch(null);
      }
    } finally {
      if (
        version === batchLoadVersion.current &&
        houseId === activeHouseIdRef.current
      ) {
        setBatchLoading(false);
      }
    }
  }, [batchId, houseId, validBatchId]);

  const loadStatistics = useCallback(async () => {
    if (houseId !== activeHouseIdRef.current) return;
    const version = ++statisticsLoadVersion.current;
    if (!houseId || !validBatchId) {
      statisticsHouseId.current = null;
      setStatistics(null);
      setStatisticsStatus("idle");
      return;
    }
    setStatisticsStatus("loading");
    try {
      const nextStatistics = await getBatchStatistics(houseId, batchId);
      if (nextStatistics.batchId !== batchId) {
        throw new Error("批次统计响应与当前批次不一致");
      }
      if (
        version !== statisticsLoadVersion.current ||
        houseId !== activeHouseIdRef.current
      ) {
        return;
      }
      statisticsHouseId.current = houseId;
      setStatistics(nextStatistics);
      setStatisticsStatus("ready");
    } catch {
      if (
        version === statisticsLoadVersion.current &&
        houseId === activeHouseIdRef.current
      ) {
        setStatisticsStatus("error");
      }
    }
  }, [batchId, houseId, validBatchId]);

  useEffect(() => {
    exportVersion.current += 1;
    exportInProgress.current = false;
    setExporting(false);
    setBatch(null);
    statisticsHouseId.current = null;
    setStatistics(null);
    setStatisticsStatus("idle");
    void Promise.all([loadBatch(), loadStatistics()]);
  }, [loadBatch, loadStatistics]);

  const visibleStatistics =
    statisticsHouseId.current === houseId && statistics?.batchId === batchId
      ? statistics
      : null;
  const carcassMetric = useMemo(
    () =>
      visibleStatistics?.metrics.find(
        (metric) => metric.code === "CARCASS_YIELD_RATE",
      ) ?? null,
    [visibleStatistics],
  );

  async function exportStatistics() {
    if (
      !houseId ||
      !batch ||
      batch.houseId !== houseId ||
      exportInProgress.current
    ) {
      return;
    }
    const version = ++exportVersion.current;
    exportInProgress.current = true;
    setExporting(true);
    try {
      const download = await downloadBatchStatistics(houseId, batch.id);
      if (
        version !== exportVersion.current ||
        houseId !== activeHouseIdRef.current
      ) {
        return;
      }
      triggerBrowserDownload(download.blob, download.filename);
      toast.success(`批次 ${batch.batchCode} 统计已导出`);
    } catch (error) {
      if (version === exportVersion.current) {
        toast.error(
          error instanceof Error ? error.message : "导出失败，请稍后重试",
        );
      }
    } finally {
      if (version === exportVersion.current) {
        exportInProgress.current = false;
        setExporting(false);
      }
    }
  }

  if (
    batchLoading ||
    Boolean(batch && (batch.houseId !== houseId || batch.id !== batchId))
  ) {
    return (
      <div className="motion-page flex flex-col gap-6">
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-[34rem] w-full" />
      </div>
    );
  }

  if (!workspace.selectedHouse) {
    return (
      <Empty>
        <Rows3Icon aria-hidden="true" />
        <EmptyTitle>请选择兔场</EmptyTitle>
        <EmptyDescription>批次详情始终限定在一个兔场内。</EmptyDescription>
        <Button asChild>
          <Link to="/workspace/production">返回批次列表</Link>
        </Button>
      </Empty>
    );
  }

  if (!validBatchId || !batch) {
    return (
      <Empty>
        <Rows3Icon aria-hidden="true" />
        <EmptyTitle>批次不存在</EmptyTitle>
        <EmptyDescription>
          该批次不在当前兔场，或记录已不可访问。
        </EmptyDescription>
        <Button asChild>
          <Link to="/workspace/production">返回批次列表</Link>
        </Button>
      </Empty>
    );
  }

  return (
    <>
      <PageHeader
        title={`批次 ${batch.batchCode}`}
        description={`${workspace.selectedHouse.name} · 完整批次统计`}
        actions={
          <>
            <Button variant="outline" asChild>
              <Link to="/workspace/production">
                <ArrowLeftIcon data-icon="inline-start" />
                返回列表
              </Link>
            </Button>
            <HousePermissionBadge permission={workspace.permission} />
            <Button
              variant="outline"
              disabled={statisticsStatus === "loading"}
              onClick={() => void loadStatistics()}
            >
              <RefreshCwIcon data-icon="inline-start" />
              刷新统计
            </Button>
            {canEditBatch ? (
              <Button
                variant="outline"
                onClick={() => setCarcassYieldOpen(true)}
              >
                <Edit3Icon data-icon="inline-start" />
                {carcassMetric?.status === "AVAILABLE"
                  ? "修正出肉率"
                  : "录入出肉率"}
              </Button>
            ) : null}
            {canReadAudit ? (
              <Button variant="outline" onClick={() => setHistoryOpen(true)}>
                <ClipboardClockIcon data-icon="inline-start" />
                出肉率历史
              </Button>
            ) : null}
            {canExport ? (
              <Button
                disabled={exporting}
                onClick={() => void exportStatistics()}
              >
                {exporting ? (
                  <span
                    className="size-4 animate-spin rounded-full border-2 border-current border-r-transparent"
                    data-icon="inline-start"
                  />
                ) : (
                  <DownloadIcon data-icon="inline-start" />
                )}
                导出 Excel
              </Button>
            ) : null}
          </>
        }
      />

      <dl className="grid gap-4 border-y py-4 text-sm sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <dt className="text-xs text-muted-foreground">状态</dt>
          <dd className="mt-1">
            <Badge
              variant={
                isCompletedBatchStatus(batch.status) ? "secondary" : "default"
              }
            >
              {batchStatusLabel(batch.status)}
            </Badge>
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">开始日期</dt>
          <dd className="mt-1 font-medium tabular-nums">
            {batch.startDate || "-"}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">结束日期</dt>
          <dd className="mt-1 font-medium tabular-nums">
            {batch.endDate || "-"}
          </dd>
        </div>
        <div className="min-w-0">
          <dt className="text-xs text-muted-foreground">备注</dt>
          <dd className="mt-1 break-words font-medium">
            {batch.remark || "-"}
          </dd>
        </div>
      </dl>

      <BatchStatisticsPanel
        statistics={visibleStatistics}
        status={statisticsStatus}
        onRetry={() => void loadStatistics()}
      />

      <BatchCarcassYieldDialog
        batch={batch}
        houseId={workspace.selectedHouse.id}
        hasExistingValue={Boolean(
          carcassMetric && carcassMetric.status !== "NOT_RECORDED",
        )}
        open={carcassYieldOpen}
        onOpenChange={setCarcassYieldOpen}
        onSaved={loadStatistics}
      />
      <BatchCarcassYieldHistoryDialog
        batch={batch}
        houseId={workspace.selectedHouse.id}
        open={historyOpen}
        onOpenChange={setHistoryOpen}
      />
    </>
  );
}
