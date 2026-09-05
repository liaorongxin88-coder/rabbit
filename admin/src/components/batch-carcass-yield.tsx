import { useEffect, useRef, useState } from "react";
import { SaveIcon } from "lucide-react";
import { toast } from "sonner";
import {
  createBatchCarcassYield,
  listBatchCarcassYields,
  uploadWorkspaceImage,
} from "@/api/workspace";
import { formatStatisticsTime } from "@/lib/batch-statistics";
import { formatFarmBusinessDate } from "@/lib/date";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import type {
  BatchCarcassYieldPage,
  BatchCarcassYieldRecord,
  ProductionBatch,
} from "@/types/api";

interface PendingCarcassYield {
  fingerprint: string;
  requestId: string;
  evidence: File | null;
  evidenceFileId?: string;
}

export function BatchCarcassYieldDialog({
  batch,
  houseId,
  hasExistingValue,
  open,
  onOpenChange,
  onSaved,
}: {
  batch: ProductionBatch;
  houseId: number;
  hasExistingValue: boolean;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved: () => Promise<void>;
}) {
  const [yieldPercent, setYieldPercent] = useState("");
  const [sourceUnit, setSourceUnit] = useState("");
  const [measuredDate, setMeasuredDate] = useState(formatFarmBusinessDate());
  const [reportNumber, setReportNumber] = useState("");
  const [evidence, setEvidence] = useState<File | null>(null);
  const [remark, setRemark] = useState("");
  const [changeReason, setChangeReason] = useState("");
  const [saving, setSaving] = useState(false);
  const pending = useRef<PendingCarcassYield | null>(null);

  useEffect(() => {
    if (!open) return;
    setYieldPercent("");
    setSourceUnit("");
    setMeasuredDate(formatFarmBusinessDate());
    setReportNumber("");
    setEvidence(null);
    setRemark("");
    setChangeReason(hasExistingValue ? "" : "首次录入");
    pending.current = null;
  }, [hasExistingValue, open]);

  async function submit(event: React.SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;
    const normalizedPercent = Number(yieldPercent);
    const normalizedSource = sourceUnit.trim();
    const normalizedReason = changeReason.trim();
    if (
      !Number.isFinite(normalizedPercent) ||
      normalizedPercent <= 0 ||
      normalizedPercent > 100
    ) {
      toast.error("出肉率必须大于 0% 且不超过 100%");
      return;
    }
    if (!normalizedSource || !measuredDate || !normalizedReason) {
      toast.error("请填写来源单位、检测日期和修改说明");
      return;
    }

    const fingerprint = JSON.stringify({
      batchId: batch.id,
      yieldPercent: normalizedPercent,
      sourceUnit: normalizedSource,
      measuredDate,
      reportNumber: reportNumber.trim(),
      evidence: evidence
        ? [evidence.name, evidence.size, evidence.lastModified]
        : null,
      remark: remark.trim(),
      changeReason: normalizedReason,
    });
    if (
      !pending.current ||
      pending.current.fingerprint !== fingerprint ||
      pending.current.evidence !== evidence
    ) {
      pending.current = {
        fingerprint,
        requestId: crypto.randomUUID(),
        evidence,
      };
    }

    setSaving(true);
    try {
      if (evidence && !pending.current.evidenceFileId) {
        const uploaded = await uploadWorkspaceImage(houseId, evidence);
        pending.current.evidenceFileId = uploaded.fileId;
      }
      await createBatchCarcassYield(houseId, batch.id, {
        yieldRate: normalizedPercent / 100,
        sourceUnit: normalizedSource,
        measuredDate,
        reportNumber: reportNumber.trim() || undefined,
        evidenceFileId: pending.current.evidenceFileId,
        remark: remark.trim() || undefined,
        changeReason: normalizedReason,
        requestId: pending.current.requestId,
      });
      pending.current = null;
      toast.success(`批次 ${batch.batchCode} 的出肉率已保存`);
      onOpenChange(false);
      await onSaved();
    } catch {
      // Keep requestId and an uploaded evidence ID for an unchanged retry.
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {hasExistingValue ? "修正出肉率" : "录入出肉率"}
          </DialogTitle>
          <DialogDescription>
            批次 {batch.batchCode} 的每次保存都会新增版本，不覆盖历史记录。
          </DialogDescription>
        </DialogHeader>
        <form className="contents" onSubmit={submit}>
          <FieldGroup className="overflow-y-auto py-1">
            <Field>
              <FieldLabel htmlFor="carcass-yield-percent">
                出肉率（%）
              </FieldLabel>
              <Input
                id="carcass-yield-percent"
                type="number"
                min="0.0001"
                max="100"
                step="0.0001"
                value={yieldPercent}
                disabled={saving}
                required
                onChange={(event) => setYieldPercent(event.target.value)}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="carcass-yield-source">来源单位</FieldLabel>
              <Input
                id="carcass-yield-source"
                value={sourceUnit}
                disabled={saving}
                maxLength={100}
                required
                onChange={(event) => setSourceUnit(event.target.value)}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="carcass-yield-date">
                检测或屠宰日期
              </FieldLabel>
              <Input
                id="carcass-yield-date"
                type="date"
                value={measuredDate}
                disabled={saving}
                required
                onChange={(event) => setMeasuredDate(event.target.value)}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="carcass-yield-report">报告编号</FieldLabel>
              <Input
                id="carcass-yield-report"
                value={reportNumber}
                disabled={saving}
                maxLength={100}
                onChange={(event) => setReportNumber(event.target.value)}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="carcass-yield-evidence">凭证图片</FieldLabel>
              <Input
                id="carcass-yield-evidence"
                type="file"
                accept="image/jpeg,image/png,image/webp,image/heic"
                disabled={saving}
                onChange={(event) =>
                  setEvidence(event.target.files?.[0] ?? null)
                }
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="carcass-yield-reason">修改说明</FieldLabel>
              <Input
                id="carcass-yield-reason"
                value={changeReason}
                disabled={saving}
                maxLength={300}
                required
                onChange={(event) => setChangeReason(event.target.value)}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="carcass-yield-remark">备注</FieldLabel>
              <Textarea
                id="carcass-yield-remark"
                value={remark}
                disabled={saving}
                maxLength={2000}
                onChange={(event) => setRemark(event.target.value)}
              />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={saving}
              onClick={() => onOpenChange(false)}
            >
              取消
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? (
                <Spinner data-icon="inline-start" />
              ) : (
                <SaveIcon data-icon="inline-start" />
              )}
              保存版本
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function BatchCarcassYieldHistoryDialog({
  batch,
  houseId,
  open,
  onOpenChange,
}: {
  batch: ProductionBatch;
  houseId: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [page, setPage] = useState<BatchCarcassYieldPage | null>(null);
  const [pageNumber, setPageNumber] = useState(1);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (open) setPageNumber(1);
  }, [batch.id, open]);

  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true);
    setFailed(false);
    void listBatchCarcassYields(houseId, batch.id, pageNumber)
      .then((nextPage) => {
        if (active) setPage(nextPage);
      })
      .catch(() => {
        if (active) {
          setPage(null);
          setFailed(true);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [batch.id, houseId, open, pageNumber]);

  const pageSize = page?.pageSize ?? 20;
  const pageCount = Math.max(1, Math.ceil((page?.total ?? 0) / pageSize));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl">
        <DialogHeader>
          <DialogTitle>出肉率版本历史</DialogTitle>
          <DialogDescription>
            批次 {batch.batchCode} 的完整追加记录，最近版本排在前面。
          </DialogDescription>
        </DialogHeader>
        <div className="min-h-40 overflow-auto">
          {loading ? (
            <div className="flex min-h-40 items-center justify-center">
              <Spinner />
            </div>
          ) : failed ? (
            <p className="text-sm text-destructive" role="alert">
              版本历史读取失败，请关闭后重试。
            </p>
          ) : page?.items.length ? (
            <Table className="min-w-[760px]">
              <TableHeader>
                <TableRow>
                  <TableHead>出肉率</TableHead>
                  <TableHead>来源单位</TableHead>
                  <TableHead>检测日期</TableHead>
                  <TableHead>修改说明</TableHead>
                  <TableHead>录入人</TableHead>
                  <TableHead>录入时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {page.items.map((record) => (
                  <CarcassYieldHistoryRow key={record.id} record={record} />
                ))}
              </TableBody>
            </Table>
          ) : (
            <p className="py-12 text-center text-sm text-muted-foreground">
              还没有出肉率版本。
            </p>
          )}
        </div>
        <DialogFooter className="items-center sm:justify-between">
          <span className="text-xs text-muted-foreground">
            第 {pageNumber}/{pageCount} 页 · 共 {page?.total ?? 0} 条
          </span>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={loading || pageNumber <= 1}
              onClick={() =>
                setPageNumber((current) => Math.max(1, current - 1))
              }
            >
              上一页
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={loading || pageNumber >= pageCount}
              onClick={() =>
                setPageNumber((current) => Math.min(pageCount, current + 1))
              }
            >
              下一页
            </Button>
            <Button type="button" onClick={() => onOpenChange(false)}>
              关闭
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function CarcassYieldHistoryRow({
  record,
}: {
  record: BatchCarcassYieldRecord;
}) {
  return (
    <TableRow>
      <TableCell className="font-medium tabular-nums">
        {(record.yieldRate * 100).toFixed(2)}%
      </TableCell>
      <TableCell>
        <div>{record.sourceUnit}</div>
        {record.reportNumber || record.evidenceFileId ? (
          <div className="mt-1 text-xs text-muted-foreground">
            {record.reportNumber
              ? `报告 ${record.reportNumber}`
              : "未填写报告编号"}
            {record.evidenceFileId ? " · 已保存凭证" : ""}
          </div>
        ) : null}
      </TableCell>
      <TableCell>{record.measuredDate}</TableCell>
      <TableCell className="max-w-64 whitespace-normal break-words">
        <div>{record.changeReason}</div>
        {record.remark ? (
          <div className="mt-1 text-xs text-muted-foreground">
            {record.remark}
          </div>
        ) : null}
      </TableCell>
      <TableCell>
        {record.createdByName || `用户 #${record.createdBy}`}
      </TableCell>
      <TableCell>{formatStatisticsTime(record.createdAt)}</TableCell>
    </TableRow>
  );
}
