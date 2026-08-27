import { useMemo, useState } from "react";
import { toast } from "sonner";

import { createRabbitsInRange } from "@/api/workspace";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import {
  buildCageRangePreview,
  MAX_RANGE_CAGE_SLOTS,
  MAX_RANGE_RABBITS,
} from "@/lib/rabbit-range";
import type { Cage, RangeRabbitEntryResult } from "@/types/api";

const axes = [
  ["row", "排"],
  ["position", "位"],
  ["layer", "层"],
] as const;

type Axis = (typeof axes)[number][0];
type Bound = "Start" | "End";

type RangeFields = Record<`${Axis}${Bound}`, string>;

const initialRange: RangeFields = {
  rowStart: "1",
  rowEnd: "1",
  positionStart: "1",
  positionEnd: "1",
  layerStart: "1",
  layerEnd: "1",
};

function parsePositive(value: string) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 0;
}

export function RabbitRangeEntryDialog({
  open,
  houseId,
  cages,
  onOpenChange,
  onSaved,
}: {
  open: boolean;
  houseId: number | null;
  cages: Cage[];
  onOpenChange: (open: boolean) => void;
  onSaved: () => Promise<void>;
}) {
  const [range, setRange] = useState<RangeFields>(initialRange);
  const [type, setType] = useState("2");
  const [gender, setGender] = useState("0");
  const [rabbitsPerCage, setRabbitsPerCage] = useState("1");
  const [breed, setBreed] = useState("");
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<RangeRabbitEntryResult | null>(null);

  const count = type === "2" ? parsePositive(rabbitsPerCage) : 1;
  const preview = useMemo(
    () =>
      buildCageRangePreview(
        cages,
        {
          rowStart: parsePositive(range.rowStart),
          rowEnd: parsePositive(range.rowEnd),
          positionStart: parsePositive(range.positionStart),
          positionEnd: parsePositive(range.positionEnd),
          layerStart: parsePositive(range.layerStart),
          layerEnd: parsePositive(range.layerEnd),
        },
        type,
        count,
      ),
    [cages, count, range, type],
  );
  const tooLarge =
    preview !== null &&
    (preview.requestedSlotCount > MAX_RANGE_CAGE_SLOTS ||
      preview.requestedSlotCount * count > MAX_RANGE_RABBITS);
  const canSubmit = Boolean(
    houseId && preview && preview.eligible.length > 0 && !tooLarge && !saving,
  );

  function updateRange(key: keyof RangeFields, value: string) {
    setRange((current) => ({ ...current, [key]: value }));
    setResult(null);
  }

  function handleOpenChange(nextOpen: boolean) {
    if (nextOpen) {
      setRange(initialRange);
      setType("2");
      setGender("0");
      setRabbitsPerCage("1");
      setBreed("");
      setResult(null);
    }
    onOpenChange(nextOpen);
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!houseId || !preview || !canSubmit) return;
    setSaving(true);
    try {
      const next = await createRabbitsInRange(houseId, {
        ...preview.range,
        rabbitsPerCage: count,
        type,
        gender,
        breed: breed.trim() || undefined,
        arrivalMethod: "0",
        arrivalDate: new Date().toISOString().slice(0, 10),
      });
      setResult(next);
      if (next.enteredRabbitCount > 0) {
        toast.success(
          `已录入 ${next.enteredRabbitCount} 只，涉及 ${next.enteredCageCount} 笼`,
        );
      }
      await onSaved();
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>按笼位范围录入</DialogTitle>
          <DialogDescription>
            填写起止排、位、层后核对预览。未编排笼位不能参与范围录入。
          </DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="overflow-y-auto pr-1">
            <div className="grid gap-4 sm:grid-cols-3">
              {axes.map(([axis, label]) => (
                <Field key={axis}>
                  <FieldLabel>{label}</FieldLabel>
                  <div className="grid grid-cols-2 gap-2">
                    <Input
                      aria-label={`${label}起始`}
                      type="number"
                      min={1}
                      inputMode="numeric"
                      value={range[`${axis}Start`]}
                      onChange={(event) =>
                        updateRange(`${axis}Start`, event.target.value)
                      }
                    />
                    <Input
                      aria-label={`${label}结束`}
                      type="number"
                      min={1}
                      inputMode="numeric"
                      value={range[`${axis}End`]}
                      onChange={(event) =>
                        updateRange(`${axis}End`, event.target.value)
                      }
                    />
                  </div>
                  <FieldDescription>起始 / 结束</FieldDescription>
                </Field>
              ))}
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              <Field>
                <FieldLabel htmlFor="range-rabbit-type">兔子类型</FieldLabel>
                <Select
                  value={type}
                  onValueChange={(value) => {
                    setType(value);
                    if (value !== "2") setRabbitsPerCage("1");
                    setResult(null);
                  }}
                >
                  <SelectTrigger id="range-rabbit-type">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="0">种兔</SelectItem>
                      <SelectItem value="1">后备兔</SelectItem>
                      <SelectItem value="2">商品兔</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="range-rabbit-gender">性别</FieldLabel>
                <Select
                  value={gender}
                  onValueChange={(value) => {
                    setGender(value);
                    setResult(null);
                  }}
                >
                  <SelectTrigger id="range-rabbit-gender">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="0">母</SelectItem>
                      <SelectItem value="1">公</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="range-rabbit-count">每笼数量</FieldLabel>
                <Input
                  id="range-rabbit-count"
                  type="number"
                  min={1}
                  max={10}
                  inputMode="numeric"
                  disabled={type !== "2"}
                  value={rabbitsPerCage}
                  onChange={(event) => {
                    setRabbitsPerCage(event.target.value);
                    setResult(null);
                  }}
                />
                <FieldDescription>
                  {type === "2" ? "商品兔每笼 1-10 只" : "单兔笼每笼固定 1 只"}
                </FieldDescription>
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="range-rabbit-breed">品种</FieldLabel>
              <Input
                id="range-rabbit-breed"
                maxLength={100}
                value={breed}
                onChange={(event) => setBreed(event.target.value)}
              />
            </Field>
            <RangePreview preview={preview} tooLarge={tooLarge} />
            {result ? <RangeSubmitResult result={result} /> : null}
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => handleOpenChange(false)}
            >
              取消
            </Button>
            <Button type="submit" disabled={!canSubmit}>
              {saving ? <Spinner data-icon="inline-start" /> : null}
              确认录入
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function RangePreview({
  preview,
  tooLarge,
}: {
  preview: ReturnType<typeof buildCageRangePreview>;
  tooLarge: boolean;
}) {
  if (!preview) {
    return <FieldDescription>填写完整的正数坐标后显示预览。</FieldDescription>;
  }
  return (
    <div
      className={
        tooLarge
          ? "rounded-md border border-warning/40 bg-warning/5 p-3 text-sm"
          : "rounded-md border border-accent/40 bg-accent/5 p-3 text-sm"
      }
    >
      <p className="font-medium">
        预览：排 {preview.range.rowStart}-{preview.range.rowEnd}，位{" "}
        {preview.range.positionStart}-{preview.range.positionEnd}，层{" "}
        {preview.range.layerStart}-{preview.range.layerEnd}
      </p>
      <p className="mt-1">
        可入栏 {preview.eligible.length} 笼，预计 {preview.enteredRabbitCount}{" "}
        只。跳过 {preview.blocked.length} 笼，缺笼 {preview.missingCageCount}{" "}
        个坐标。
      </p>
      {preview.unplacedCageCount > 0 ? (
        <p className="mt-1 text-muted-foreground">
          另有 {preview.unplacedCageCount} 个未编排笼位，补齐坐标后才能选择。
        </p>
      ) : null}
      {preview.blocked.length > 0 ? (
        <p className="mt-1 text-muted-foreground">
          {preview.blocked
            .slice(0, 4)
            .map((item) => `${item.cage.cageNumber}：${item.blockedReason}`)
            .join("；")}
          {preview.blocked.length > 4
            ? `；其余 ${preview.blocked.length - 4} 笼见提交结果`
            : ""}
        </p>
      ) : null}
      {tooLarge ? (
        <p className="mt-1 text-warning">
          范围过大，请缩小范围或降低每笼数量。
        </p>
      ) : null}
    </div>
  );
}

function RangeSubmitResult({ result }: { result: RangeRabbitEntryResult }) {
  const skipped = result.skippedCages
    .slice(0, 4)
    .map((item) => `${item.cageNumber}：${item.reason}`)
    .join("；");
  return (
    <div className="rounded-md border border-border bg-secondary p-3 text-sm">
      <p>
        已录入 {result.enteredRabbitCount} 只，涉及 {result.enteredCageCount}{" "}
        笼。
      </p>
      {result.skippedCages.length > 0 ? (
        <p className="mt-1 text-muted-foreground">
          未录入 {result.skippedCages.length} 笼：{skipped}
          {result.skippedCages.length > 4
            ? `；其余 ${result.skippedCages.length - 4} 笼`
            : ""}
        </p>
      ) : null}
    </div>
  );
}
