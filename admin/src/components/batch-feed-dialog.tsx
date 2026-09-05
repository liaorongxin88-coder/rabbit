import { useEffect, useMemo, useRef, useState } from "react";
import { CalculatorIcon, WheatIcon } from "lucide-react";
import { toast } from "sonner";
import { createFeedLog, previewFeedAllocations } from "@/api/workspace";
import { ApiError } from "@/lib/request";
import {
  canAutoAssignFeedGroup,
  feedAllocationError,
  feedAllocationKey,
  getOrCreateFeedRequest,
  normalizeFeedAllocations,
  type PendingFeedRequest,
} from "@/lib/feed-allocation-request";
import {
  farmBusinessDateToTimestamp,
  formatFarmBusinessDate,
} from "@/lib/date";
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
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import type { ProductionBatch, Rabbit } from "@/types/api";
import type { FeedAllocationPreview } from "@/types/feed";

export function BatchFeedDialog({
  houseId,
  rabbits,
  batches,
  disabled,
  onSaved,
}: {
  houseId: number | null;
  rabbits: Rabbit[];
  batches: ProductionBatch[];
  disabled: boolean;
  onSaved: () => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [selectedRabbitIds, setSelectedRabbitIds] = useState<number[]>([]);
  const [feedDate, setFeedDate] = useState(formatFarmBusinessDate());
  const [amount, setAmount] = useState("");
  const [feedType, setFeedType] = useState("");
  const [remark, setRemark] = useState("");
  const [preview, setPreview] = useState<FeedAllocationPreview | null>(null);
  const [allocationAmounts, setAllocationAmounts] = useState<
    Record<string, string>
  >({});
  const [previewing, setPreviewing] = useState(false);
  const [saving, setSaving] = useState(false);
  const pending = useRef<PendingFeedRequest | null>(null);
  const previewVersion = useRef(0);
  const activeRabbits = useMemo(
    () => rabbits.filter((rabbit) => rabbit.isActive),
    [rabbits],
  );
  const batchById = useMemo(
    () => new Map(batches.map((batch) => [batch.id, batch])),
    [batches],
  );
  const allocations = useMemo(
    () => normalizeFeedAllocations(preview?.groups ?? [], allocationAmounts),
    [allocationAmounts, preview?.groups],
  );

  useEffect(() => {
    previewVersion.current += 1;
    setPreviewing(false);
    if (!open) return;
    setSelectedRabbitIds([]);
    setFeedDate(formatFarmBusinessDate());
    setAmount("");
    setFeedType("");
    setRemark("");
    setPreview(null);
    setAllocationAmounts({});
    pending.current = null;
  }, [open]);

  useEffect(() => {
    previewVersion.current += 1;
    setOpen(false);
    setPreview(null);
    setAllocationAmounts({});
    setPreviewing(false);
    pending.current = null;
  }, [houseId]);

  function invalidatePreview() {
    previewVersion.current += 1;
    setPreview(null);
    setAllocationAmounts({});
    pending.current = null;
  }

  function toggleRabbit(rabbitId: number) {
    setSelectedRabbitIds((current) =>
      current.includes(rabbitId)
        ? current.filter((id) => id !== rabbitId)
        : [...current, rabbitId].sort((left, right) => left - right),
    );
    invalidatePreview();
  }

  async function loadPreview() {
    if (!houseId || selectedRabbitIds.length === 0) {
      toast.error("请至少选择一只兔");
      return;
    }
    const feedTime = farmBusinessDateToTimestamp(feedDate);
    if (feedTime === undefined) {
      toast.error("请选择有效投喂日期");
      return;
    }
    const version = ++previewVersion.current;
    setPreviewing(true);
    try {
      const nextPreview = await previewFeedAllocations(houseId, {
        rabbitIds: selectedRabbitIds,
        feedTime,
      });
      if (version !== previewVersion.current) return;
      setPreview(nextPreview);
      const nextAmounts: Record<string, string> = {};
      for (const group of nextPreview.groups) {
        nextAmounts[feedAllocationKey(group)] = "";
      }
      const onlyGroup = nextPreview.groups[0];
      if (canAutoAssignFeedGroup(nextPreview.groups) && Number(amount) > 0) {
        nextAmounts[feedAllocationKey(onlyGroup)] = Number(amount).toFixed(2);
      }
      setAllocationAmounts(nextAmounts);
    } finally {
      if (version === previewVersion.current) setPreviewing(false);
    }
  }

  useEffect(() => {
    const onlyGroup = preview?.groups[0];
    if (
      !preview ||
      !canAutoAssignFeedGroup(preview.groups) ||
      !onlyGroup ||
      Number(amount) <= 0
    ) {
      return;
    }
    const key = feedAllocationKey(onlyGroup);
    setAllocationAmounts((current) => ({
      ...current,
      [key]: Number(amount).toFixed(2),
    }));
  }, [amount, preview]);

  async function submit(event: React.SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!houseId || !preview || saving) return;
    const feedTime = farmBusinessDateToTimestamp(feedDate);
    if (feedTime === undefined) {
      toast.error("请选择有效投喂日期");
      return;
    }
    const allocationMessage = feedAllocationError(Number(amount), allocations);
    if (allocationMessage) {
      toast.error(allocationMessage);
      return;
    }
    const nextPending = getOrCreateFeedRequest(
      pending.current,
      {
        rabbitIds: selectedRabbitIds,
        feedTime,
        amount: Number(amount),
        unit: "kg",
        feedType: feedType.trim() || undefined,
        remark: remark.trim() || undefined,
        allocations,
      },
      () => crypto.randomUUID(),
    );
    pending.current = nextPending;
    setSaving(true);
    try {
      await createFeedLog(houseId, nextPending.request);
      pending.current = null;
      toast.success(`已记录 ${Number(amount).toFixed(2)} kg 投喂`);
      setOpen(false);
      await onSaved();
    } catch (error) {
      if (error instanceof ApiError && error.code === 409) {
        // The backend re-resolves groups at save time. Keep the draft values,
        // but require a fresh preview before another submission.
        invalidatePreview();
      }
      // Keep the requestId for an unchanged retry unless the preview expired.
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" disabled={disabled || !houseId}>
          <WheatIcon data-icon="inline-start" />
          批次投喂
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>记录批次投喂</DialogTitle>
          <DialogDescription>
            按投喂时点确认兔只的批次和阶段，混合分组需要分别填写实际用量。
          </DialogDescription>
        </DialogHeader>
        <form className="contents" onSubmit={submit}>
          <FieldGroup className="overflow-y-auto py-1">
            <Field>
              <FieldLabel>投喂兔只</FieldLabel>
              <div className="max-h-48 divide-y overflow-y-auto rounded-lg border">
                {activeRabbits.map((rabbit) => (
                  <label
                    key={rabbit.id}
                    className="flex min-h-11 items-center gap-3 px-3 py-2 text-sm"
                  >
                    <input
                      type="checkbox"
                      checked={selectedRabbitIds.includes(rabbit.id)}
                      disabled={previewing || saving}
                      onChange={() => toggleRabbit(rabbit.id)}
                    />
                    <span className="min-w-0 flex-1 truncate">
                      兔 #{rabbit.id} · 笼位 #{rabbit.cageId}
                    </span>
                  </label>
                ))}
                {activeRabbits.length === 0 ? (
                  <p className="p-4 text-sm text-muted-foreground">
                    当前没有可投喂兔只。
                  </p>
                ) : null}
              </div>
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="feed-date">投喂日期</FieldLabel>
                <Input
                  id="feed-date"
                  type="date"
                  value={feedDate}
                  disabled={previewing || saving}
                  required
                  onChange={(event) => {
                    setFeedDate(event.target.value);
                    invalidatePreview();
                  }}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="feed-amount">总用量（kg）</FieldLabel>
                <Input
                  id="feed-amount"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={amount}
                  disabled={previewing || saving}
                  required
                  onChange={(event) => setAmount(event.target.value)}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="feed-type">饲料类型</FieldLabel>
                <Input
                  id="feed-type"
                  value={feedType}
                  disabled={previewing || saving}
                  maxLength={100}
                  onChange={(event) => setFeedType(event.target.value)}
                />
              </Field>
              <Field className="justify-end">
                <Button
                  type="button"
                  variant="outline"
                  disabled={
                    previewing || saving || selectedRabbitIds.length === 0
                  }
                  onClick={() => void loadPreview()}
                >
                  {previewing ? (
                    <Spinner data-icon="inline-start" />
                  ) : (
                    <CalculatorIcon data-icon="inline-start" />
                  )}
                  预览归属
                </Button>
              </Field>
            </div>
            {preview ? (
              <fieldset className="rounded-lg border p-4">
                <legend className="px-1 text-sm font-medium">
                  批次与阶段分配
                </legend>
                <div className="mt-1 grid gap-4 sm:grid-cols-2">
                  {preview.groups.map((group) => {
                    const key = feedAllocationKey(group);
                    const batch =
                      group.batchId == null
                        ? null
                        : batchById.get(group.batchId);
                    const phase =
                      group.phase === "BREEDING"
                        ? "繁殖期"
                        : group.phase === "FATTENING"
                          ? "育肥期"
                          : "未归属";
                    return (
                      <Field key={key}>
                        <FieldLabel htmlFor={`feed-allocation-${key}`}>
                          {batch?.batchCode ??
                            (group.batchId == null
                              ? "未归批次"
                              : `批次 #${group.batchId}`)}{" "}
                          · {phase} · {group.rabbitCount} 只
                        </FieldLabel>
                        <Input
                          id={`feed-allocation-${key}`}
                          type="number"
                          min="0.01"
                          step="0.01"
                          value={allocationAmounts[key] ?? ""}
                          disabled={
                            saving ||
                            canAutoAssignFeedGroup(preview.groups)
                          }
                          required
                          onChange={(event) =>
                            setAllocationAmounts((current) => ({
                              ...current,
                              [key]: event.target.value,
                            }))
                          }
                        />
                      </Field>
                    );
                  })}
                </div>
              </fieldset>
            ) : null}
            <Field>
              <FieldLabel htmlFor="feed-remark">备注</FieldLabel>
              <Textarea
                id="feed-remark"
                value={remark}
                disabled={saving}
                maxLength={500}
                onChange={(event) => setRemark(event.target.value)}
              />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={saving}
              onClick={() => setOpen(false)}
            >
              取消
            </Button>
            <Button
              type="submit"
              disabled={
                saving ||
                !preview ||
                feedAllocationError(Number(amount), allocations) !== null
              }
            >
              {saving ? (
                <Spinner data-icon="inline-start" />
              ) : (
                <WheatIcon data-icon="inline-start" />
              )}
              保存投喂
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
