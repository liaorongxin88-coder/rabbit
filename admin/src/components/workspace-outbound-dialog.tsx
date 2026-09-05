import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  AlertTriangleIcon,
  CheckCircle2Icon,
  ShieldCheckIcon,
  TruckIcon,
} from "lucide-react";
import { toast } from "sonner";
import {
  cancelOutboundTask,
  createOutboundTask,
  getOutboundRequestStatus,
  precheckOutboundTask,
  requestId,
  saveOutboundTask,
  submitOutboundTask,
} from "@/api/workspace";
import {
  farmBusinessDateToTimestamp,
  formatFarmBusinessDate,
} from "@/lib/date";
import {
  buildOutboundAllocationGroups,
  getOrCreateOutboundSubmission,
  normalizeOutboundAllocations,
  outboundAllocationError,
  type PendingOutboundSubmission,
} from "@/lib/outbound-allocation";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import type {
  OutboundEligibility,
  OutboundSelectedItem,
  OutboundSubmitResult,
  OutboundTask,
} from "@/types/api";

type Phase = "select" | "confirm" | "result";

const eligibilityLabels: Record<OutboundEligibility, string> = {
  NORMAL: "正常出售",
  EARLY_SALE: "提前出售",
  NEEDS_ACTION: "需先处理",
  BLOCKED: "禁止出库",
};

export function WorkspaceOutboundDialog({
  houseId,
  disabled,
  canControl,
  onOpenChange,
  onSaved,
  open: controlledOpen,
}: {
  houseId: number | null;
  disabled: boolean;
  canControl: boolean;
  onOpenChange?: (open: boolean) => void;
  onSaved: () => Promise<void>;
  open?: boolean;
}) {
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const open = controlledOpen ?? uncontrolledOpen;

  const setOpen = useCallback(
    (nextOpen: boolean) => {
      if (controlledOpen === undefined) {
        setUncontrolledOpen(nextOpen);
      }
      onOpenChange?.(nextOpen);
    },
    [controlledOpen, onOpenChange],
  );
  const [phase, setPhase] = useState<Phase>("select");
  const [task, setTask] = useState<OutboundTask | null>(null);
  const [selected, setSelected] = useState<
    Record<number, OutboundSelectedItem>
  >({});
  const [saleDate, setSaleDate] = useState(formatFarmBusinessDate());
  const [totalWeight, setTotalWeight] = useState("");
  const [unitPrice, setUnitPrice] = useState("");
  const [allocationWeights, setAllocationWeights] = useState<
    Record<string, string>
  >({});
  const [customer, setCustomer] = useState("");
  const [remark, setRemark] = useState("");
  const [result, setResult] = useState<OutboundSubmitResult | null>(null);
  const [busy, setBusy] = useState(false);
  const initializedHouseId = useRef<number | null>(null);
  const taskLoadVersion = useRef(0);
  const pendingSubmission = useRef<PendingOutboundSubmission<
    Omit<Parameters<typeof submitOutboundTask>[2], "requestId">
  > | null>(null);

  const selectedItems = useMemo(() => Object.values(selected), [selected]);
  const allocationGroups = useMemo(
    () => buildOutboundAllocationGroups(selectedItems, task?.rabbits ?? []),
    [selectedItems, task?.rabbits],
  );
  const batchAllocations = useMemo(
    () => normalizeOutboundAllocations(allocationGroups, allocationWeights),
    [allocationGroups, allocationWeights],
  );

  const startTask = useCallback(async () => {
    if (!houseId || initializedHouseId.current === houseId) return;
    const version = ++taskLoadVersion.current;
    initializedHouseId.current = houseId;
    if (
      task &&
      task.houseId !== houseId &&
      !["COMPLETED", "CANCELLED"].includes(task.status)
    ) {
      void cancelOutboundTask(task.houseId, task.taskId).catch(() => undefined);
    }
    setBusy(true);
    setPhase("select");
    setTask(null);
    setResult(null);
    setSaleDate(formatFarmBusinessDate());
    setTotalWeight("");
    setUnitPrice("");
    setAllocationWeights({});
    pendingSubmission.current = null;
    setCustomer("");
    setRemark("");
    try {
      const nextTask = await createOutboundTask(houseId);
      if (version !== taskLoadVersion.current) {
        if (!["COMPLETED", "CANCELLED"].includes(nextTask.status)) {
          void cancelOutboundTask(nextTask.houseId, nextTask.taskId).catch(
            () => undefined,
          );
        }
        return;
      }
      setTask(nextTask);
      setSelected(
        Object.fromEntries(
          nextTask.selectedItems.map((item) => [item.rabbitId, item]),
        ),
      );
    } catch {
      if (version === taskLoadVersion.current) {
        initializedHouseId.current = null;
        setOpen(false);
      }
    } finally {
      if (version === taskLoadVersion.current) setBusy(false);
    }
  }, [houseId, setOpen, task]);

  useEffect(() => {
    if (open && houseId) {
      void startTask();
    }
  }, [houseId, open, startTask]);

  useEffect(() => {
    setAllocationWeights((current) => {
      const next = Object.fromEntries(
        allocationGroups.map((group) => [group.key, current[group.key] ?? ""]),
      );
      if (allocationGroups.length === 1 && Number(totalWeight) > 0) {
        next[allocationGroups[0].key] = Number(totalWeight).toFixed(3);
      }
      return JSON.stringify(next) === JSON.stringify(current) ? current : next;
    });
  }, [allocationGroups, totalWeight]);

  async function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      taskLoadVersion.current += 1;
      initializedHouseId.current = null;
      setOpen(false);
      if (
        task &&
        result?.status !== "COMPLETED" &&
        !["COMPLETED", "CANCELLED"].includes(task.status)
      ) {
        void cancelOutboundTask(task.houseId, task.taskId).catch(
          () => undefined,
        );
      }
      return;
    }
    if (!houseId) return;
    setOpen(true);
    await startTask();
  }

  function toggleRabbit(rabbitId: number) {
    if (!task) return;
    const rabbit = task.rabbits.find((item) => item.rabbitId === rabbitId);
    if (!rabbit) return;
    setSelected((current) => {
      if (current[rabbitId]) {
        const next = { ...current };
        delete next[rabbitId];
        return next;
      }
      return {
        ...current,
        [rabbitId]: {
          rabbitId,
          stateVersion: rabbit.stateVersion,
          selectionType:
            rabbit.eligibility === "EARLY_SALE" ? "EARLY_SALE" : "NORMAL",
          earlySaleReason: "",
        },
      };
    });
  }

  function updateEarlyReason(rabbitId: number, value: string) {
    setSelected((current) => ({
      ...current,
      [rabbitId]: { ...current[rabbitId], earlySaleReason: value },
    }));
  }

  function formPayload() {
    const normalizedPrice = Number(unitPrice);
    const saleTime = farmBusinessDateToTimestamp(saleDate);
    return {
      saleTime: saleTime ?? Number.NaN,
      totalWeight: Number(totalWeight),
      unitPricePerKg: normalizedPrice,
      batchAllocations,
      customer: customer.trim() || undefined,
      remark: remark.trim() || undefined,
    };
  }

  async function freezeSelection(event: React.SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!task || selectedItems.length === 0 || Number(totalWeight) <= 0) return;
    if (farmBusinessDateToTimestamp(saleDate) === undefined) {
      toast.error("请选择有效销售日期");
      return;
    }
    const missingReason = selectedItems.some(
      (item) =>
        item.selectionType === "EARLY_SALE" && !item.earlySaleReason?.trim(),
    );
    if (missingReason) {
      toast.error("请填写每只提前出售兔的原因");
      return;
    }
    const allocationMessage = outboundAllocationError(
      Number(totalWeight),
      Number(unitPrice),
      batchAllocations,
    );
    if (allocationMessage) {
      toast.error(allocationMessage);
      return;
    }
    setBusy(true);
    try {
      const payload = formPayload();
      const frozen = await saveOutboundTask(task.houseId, task.taskId, {
        revision: task.revision,
        status: "WAITING_CONFIRMATION",
        items: selectedItems,
        saleTime: payload.saleTime,
        totalWeight: payload.totalWeight,
        unitPricePerKg: payload.unitPricePerKg,
        batchAllocations: payload.batchAllocations,
        customer: payload.customer,
        remark: payload.remark,
      });
      setTask(frozen);
      setSelected(
        Object.fromEntries(
          frozen.selectedItems.map((item) => [item.rabbitId, item]),
        ),
      );
      setPhase("confirm");
    } finally {
      setBusy(false);
    }
  }

  async function submit() {
    if (!task || task.selectedItems.length === 0) return;
    setBusy(true);
    try {
      const stateVersions = Object.fromEntries(
        task.selectedItems.map((item) => [
          String(item.rabbitId),
          item.stateVersion,
        ]),
      );
      const earlySaleReasons = Object.fromEntries(
        task.selectedItems
          .filter((item) => item.selectionType === "EARLY_SALE")
          .map((item) => [
            String(item.rabbitId),
            item.earlySaleReason?.trim() ?? "",
          ]),
      );
      const payload = {
        rabbitIds: task.selectedItems.map((item) => item.rabbitId),
        stateVersions,
        earlySaleReasons,
        ...formPayload(),
      };
      const pending = getOrCreateOutboundSubmission(
        pendingSubmission.current,
        payload,
        requestId,
      );
      pendingSubmission.current = pending;
      const nextResult = await submitOutboundTask(task.houseId, task.taskId, {
        ...pending.payload,
        requestId: pending.requestId,
      });
      await acceptResult(nextResult);
    } finally {
      setBusy(false);
    }
  }

  async function acceptResult(nextResult: OutboundSubmitResult) {
    setResult(nextResult);
    setPhase("result");
    if (nextResult.status === "COMPLETED") {
      pendingSubmission.current = null;
      toast.success(`出库完成：${nextResult.rabbitCount} 只`);
      await onSaved();
    }
  }

  async function checkSubmissionStatus() {
    if (!result || !task) return;
    setBusy(true);
    try {
      await acceptResult(
        await getOutboundRequestStatus(task.houseId, result.requestId),
      );
    } finally {
      setBusy(false);
    }
  }

  async function returnToSelection(removeConflicts: boolean) {
    if (!result || !task) return;
    const conflictIds = removeConflicts
      ? new Set(result.conflicts.map((item) => item.rabbitId))
      : new Set<number>();
    const remaining = task.selectedItems.filter(
      (item) => !conflictIds.has(item.rabbitId),
    );
    setBusy(true);
    try {
      const refreshed = await precheckOutboundTask(task.houseId, task.taskId);
      setTask(refreshed);
      setSelected(
        Object.fromEntries(remaining.map((item) => [item.rabbitId, item])),
      );
      setResult(null);
      pendingSubmission.current = null;
      setPhase("select");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => void handleOpenChange(nextOpen)}
    >
      <DialogTrigger asChild>
        <Button disabled={disabled || !houseId}>
          <TruckIcon data-icon="inline-start" />
          批量出库
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldCheckIcon className="size-5" aria-hidden="true" />
            安全批量出库
          </DialogTitle>
          <DialogDescription>
            出库资格、销售快照和并发状态均归属当前兔场。
          </DialogDescription>
        </DialogHeader>

        {busy && !task ? (
          <div className="flex min-h-48 items-center justify-center">
            <Spinner />
          </div>
        ) : task && phase === "select" ? (
          <form className="flex flex-col gap-4" onSubmit={freezeSelection}>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              <Summary label="正常" value={task.summary.normal} />
              <Summary label="提前" value={task.summary.earlySale} />
              <Summary label="需处理" value={task.summary.needsAction} />
              <Summary label="禁止" value={task.summary.blocked} />
            </div>
            <div className="max-h-64 space-y-2 overflow-y-auto pr-1">
              {task.rabbits.map((rabbit) => {
                const selectable =
                  rabbit.eligibility === "NORMAL" ||
                  (rabbit.eligibility === "EARLY_SALE" && canControl);
                const checked = Boolean(selected[rabbit.rabbitId]);
                return (
                  <div key={rabbit.rabbitId} className="rounded-md border p-3">
                    <label className="flex items-start gap-3">
                      <input
                        className="mt-1"
                        type="checkbox"
                        checked={checked}
                        disabled={!selectable}
                        onChange={() => toggleRabbit(rabbit.rabbitId)}
                      />
                      <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap items-center gap-2 text-sm font-medium">
                          兔 #{rabbit.rabbitId} · {rabbit.cageNumber}
                          <EligibilityBadge value={rabbit.eligibility} />
                        </span>
                        <span className="mt-1 block text-xs text-muted-foreground">
                          {rabbit.stage} · {rabbit.message}
                          {!selectable && rabbit.recommendedAction
                            ? ` · ${rabbit.recommendedAction}`
                            : ""}
                        </span>
                      </span>
                    </label>
                    {checked && rabbit.eligibility === "EARLY_SALE" ? (
                      <Input
                        className="mt-3"
                        aria-label={`兔 #${rabbit.rabbitId} 提前出售原因`}
                        placeholder="提前出售原因"
                        value={selected[rabbit.rabbitId]?.earlySaleReason ?? ""}
                        maxLength={200}
                        required
                        onChange={(event) =>
                          updateEarlyReason(rabbit.rabbitId, event.target.value)
                        }
                      />
                    ) : null}
                  </div>
                );
              })}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="outbound-date">销售日期</FieldLabel>
                <Input
                  id="outbound-date"
                  type="date"
                  value={saleDate}
                  required
                  onChange={(event) => setSaleDate(event.target.value)}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="outbound-weight">总重量（kg）</FieldLabel>
                <Input
                  id="outbound-weight"
                  type="number"
                  min="0.001"
                  step="0.001"
                  max="100000"
                  value={totalWeight}
                  required
                  onChange={(event) => setTotalWeight(event.target.value)}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="outbound-price">单价（元/kg）</FieldLabel>
                <Input
                  id="outbound-price"
                  type="number"
                  min="0.01"
                  step="0.01"
                  max="99999999.99"
                  value={unitPrice}
                  required
                  onChange={(event) => setUnitPrice(event.target.value)}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="outbound-customer">客户</FieldLabel>
                <Input
                  id="outbound-customer"
                  value={customer}
                  maxLength={100}
                  onChange={(event) => setCustomer(event.target.value)}
                />
              </Field>
            </div>
            {allocationGroups.length > 0 ? (
              <fieldset className="rounded-lg border p-4">
                <legend className="px-1 text-sm font-medium">
                  批次实际重量分配
                </legend>
                <div className="mt-1 grid gap-4 sm:grid-cols-2">
                  {allocationGroups.map((group) => (
                    <Field key={group.key}>
                      <FieldLabel htmlFor={`outbound-allocation-${group.key}`}>
                        {group.batchId == null
                          ? "未归批次"
                          : `批次 #${group.batchId}`}{" "}
                        · {group.rabbitCount} 只
                      </FieldLabel>
                      <Input
                        id={`outbound-allocation-${group.key}`}
                        type="number"
                        min="0.001"
                        step="0.001"
                        value={allocationWeights[group.key] ?? ""}
                        required
                        disabled={allocationGroups.length === 1}
                        onChange={(event) =>
                          setAllocationWeights((current) => ({
                            ...current,
                            [group.key]: event.target.value,
                          }))
                        }
                      />
                    </Field>
                  ))}
                </div>
                <p className="mt-3 text-xs text-muted-foreground">
                  混批时按过磅结果填写，各组之和必须等于订单总重量。
                </p>
              </fieldset>
            ) : null}
            <Field>
              <FieldLabel htmlFor="outbound-remark">备注</FieldLabel>
              <Textarea
                id="outbound-remark"
                value={remark}
                maxLength={500}
                onChange={(event) => setRemark(event.target.value)}
              />
            </Field>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => void handleOpenChange(false)}
              >
                取消
              </Button>
              <Button
                type="submit"
                disabled={
                  busy ||
                  selectedItems.length === 0 ||
                  outboundAllocationError(
                    Number(totalWeight),
                    Number(unitPrice),
                    batchAllocations,
                  ) !== null
                }
              >
                {busy ? <Spinner data-icon="inline-start" /> : null}
                核对出库
              </Button>
            </DialogFooter>
          </form>
        ) : task && phase === "confirm" ? (
          <div className="flex flex-col gap-4">
            <div className="rounded-md border p-4">
              <div className="text-sm font-medium">
                本次出库 {task.selectedItems.length} 只
              </div>
              <div className="mt-2 flex flex-wrap gap-2">
                {task.selectedItems.map((item) => (
                  <Badge key={item.rabbitId} variant="outline">
                    兔 #{item.rabbitId}
                  </Badge>
                ))}
              </div>
            </div>
            <div className="grid gap-3 text-sm sm:grid-cols-2">
              <Review label="销售日期" value={saleDate} />
              <Review label="总重量" value={`${totalWeight} kg`} />
              <Review
                label="单价"
                value={unitPrice ? `${unitPrice} 元/kg` : "-"}
              />
              <Review label="客户" value={customer || "-"} />
            </div>
            <div className="rounded-lg border p-4">
              <p className="text-sm font-medium">批次重量快照</p>
              <dl className="mt-3 grid gap-3 sm:grid-cols-2">
                {batchAllocations.map((allocation) => (
                  <Review
                    key={allocation.batchId ?? "unassigned"}
                    label={
                      allocation.batchId == null
                        ? "未归批次"
                        : `批次 #${allocation.batchId}`
                    }
                    value={`${allocation.actualWeightKg.toFixed(3)} kg`}
                  />
                ))}
              </dl>
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                disabled={busy}
                onClick={() => setPhase("select")}
              >
                返回调整
              </Button>
              <Button
                type="button"
                disabled={busy}
                onClick={() => void submit()}
              >
                {busy ? (
                  <Spinner data-icon="inline-start" />
                ) : (
                  <ShieldCheckIcon data-icon="inline-start" />
                )}
                确认出库
              </Button>
            </DialogFooter>
          </div>
        ) : result ? (
          <div className="flex flex-col gap-4">
            {result.status === "COMPLETED" ? (
              <div className="rounded-md border border-accent/30 bg-accent/5 p-4 text-foreground">
                <div className="flex items-center gap-2 font-medium">
                  <CheckCircle2Icon className="size-5" aria-hidden="true" />
                  出库完成
                </div>
                <div className="mt-2 text-sm">
                  销售单 {result.saleOrderNumber ?? `#${result.saleOrderId}`} ·{" "}
                  {result.rabbitCount} 只 · {result.totalWeight ?? totalWeight}{" "}
                  kg
                </div>
              </div>
            ) : result.status === "PROCESSING" ? (
              <div className="rounded-md border border-warning/40 p-4">
                <div className="flex items-center gap-2 font-medium text-warning">
                  <Spinner />
                  正在确认出库结果
                </div>
                <p className="mt-2 text-sm text-muted-foreground">
                  {result.message || "请等待服务端确认，不要重复创建出库单。"}
                </p>
              </div>
            ) : result.status === "FAILED" ? (
              <div className="rounded-md border border-destructive/30 p-4">
                <div className="flex items-center gap-2 font-medium text-destructive">
                  <AlertTriangleIcon className="size-5" aria-hidden="true" />
                  出库未生效
                </div>
                <p className="mt-2 text-sm text-muted-foreground">
                  {result.message || "请返回调整后重新提交。"}
                </p>
              </div>
            ) : (
              <div className="rounded-md border border-destructive/30 p-4">
                <div className="flex items-center gap-2 font-medium text-destructive">
                  <AlertTriangleIcon className="size-5" aria-hidden="true" />
                  出库状态冲突
                </div>
                <div className="mt-3 max-h-52 space-y-2 overflow-y-auto">
                  {result.conflicts.map((conflict) => (
                    <div
                      key={`${conflict.rabbitId}-${conflict.errorCode}`}
                      className="rounded-md border bg-background p-3 text-sm"
                    >
                      <div className="font-medium">
                        兔 #{conflict.rabbitId} · {conflict.currentState}
                      </div>
                      <div className="mt-1 text-muted-foreground">
                        {conflict.message} · {conflict.recommendedAction}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
            <DialogFooter>
              {result.status === "COMPLETED" ? (
                <Button
                  type="button"
                  onClick={() => void handleOpenChange(false)}
                >
                  完成
                </Button>
              ) : (
                <>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => void handleOpenChange(false)}
                  >
                    取消
                  </Button>
                  {result.status === "PROCESSING" ? (
                    <Button
                      type="button"
                      disabled={busy}
                      onClick={() => void checkSubmissionStatus()}
                    >
                      {busy ? <Spinner data-icon="inline-start" /> : null}
                      查询结果
                    </Button>
                  ) : (
                    <Button
                      type="button"
                      disabled={busy}
                      onClick={() =>
                        void returnToSelection(result.status === "CONFLICT")
                      }
                    >
                      {busy ? <Spinner data-icon="inline-start" /> : null}
                      {result.status === "CONFLICT" ? "移除冲突项" : "返回调整"}
                    </Button>
                  )}
                </>
              )}
            </DialogFooter>
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function Summary({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-md border px-3 py-2">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-1 text-lg font-semibold">{value}</div>
    </div>
  );
}

function EligibilityBadge({ value }: { value: OutboundEligibility }) {
  const variant =
    value === "BLOCKED"
      ? "destructive"
      : value === "NORMAL"
        ? "default"
        : "secondary";
  return <Badge variant={variant}>{eligibilityLabels[value]}</Badge>;
}

function Review({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-1 font-medium">{value}</div>
    </div>
  );
}
