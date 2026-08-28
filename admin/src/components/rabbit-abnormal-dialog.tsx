import { useEffect, useRef, useState } from "react";
import { CameraIcon } from "lucide-react";
import { toast } from "sonner";
import {
  createWorkspaceAbnormalCondition,
  uploadWorkspaceImage,
} from "@/api/workspace";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import type { Rabbit } from "@/types/api";

const abnormalStatuses = [
  "外伤",
  "采食异常",
  "精神萎靡",
  "疑似疾病",
  "其他异常",
];

interface PendingRequest {
  fingerprint: string;
  requestId: string;
}

export function RabbitAbnormalDialog({
  rabbit,
  houseId,
  onOpenChange,
  onSaved,
}: {
  rabbit: Rabbit | null;
  houseId: number | null;
  onOpenChange: (open: boolean) => void;
  onSaved: () => Promise<void>;
}) {
  const [warningStatus, setWarningStatus] = useState(abnormalStatuses[0]);
  const [remark, setRemark] = useState("");
  const [image, setImage] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const pendingRequest = useRef<PendingRequest | null>(null);

  useEffect(() => {
    if (!rabbit) return;
    setWarningStatus(abnormalStatuses[0]);
    setRemark("");
    setImage(null);
    pendingRequest.current = null;
  }, [rabbit]);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!rabbit || !houseId || saving) return;
    const normalizedRemark = remark.trim();
    if (!image) {
      toast.error("请上传一张相关图片");
      return;
    }
    if (!normalizedRemark) {
      toast.error("请填写异常说明");
      return;
    }

    const fingerprint = JSON.stringify({
      rabbitId: rabbit.id,
      warningStatus,
      remark: normalizedRemark,
      image: [image.name, image.size, image.lastModified],
    });
    let pending = pendingRequest.current;
    if (!pending || pending.fingerprint !== fingerprint) {
      pending = { fingerprint, requestId: crypto.randomUUID() };
      pendingRequest.current = pending;
    }

    setSaving(true);
    try {
      const uploaded = await uploadWorkspaceImage(houseId, image);
      if (!uploaded.fileId) {
        throw new Error("图片上传结果不正确");
      }
      await createWorkspaceAbnormalCondition(houseId, {
        rabbitId: rabbit.id,
        warningStatus,
        imageFileId: uploaded.fileId,
        remark: normalizedRemark,
        requestId: pending.requestId,
      });
      pendingRequest.current = null;
      toast.success(`兔 #${rabbit.id} 已新增异常记录`);
      onOpenChange(false);
      await onSaved();
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "新增异常记录失败，请稍后重试",
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={Boolean(rabbit)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>新增异常记录</DialogTitle>
          <DialogDescription>
            兔 #{rabbit?.id ?? ""} 的异常会保留为待处理记录，处理完成后再关闭。
          </DialogDescription>
        </DialogHeader>
        <form className="contents" onSubmit={handleSubmit}>
          <FieldGroup className="overflow-y-auto py-1">
            <Field>
              <FieldLabel>异常类型</FieldLabel>
              <Select
                value={warningStatus}
                onValueChange={setWarningStatus}
                disabled={saving}
              >
                <SelectTrigger aria-label="异常类型">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {abnormalStatuses.map((status) => (
                    <SelectItem key={status} value={status}>
                      {status}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel htmlFor="rabbit-abnormal-image">相关图片</FieldLabel>
              <Input
                id="rabbit-abnormal-image"
                type="file"
                accept="image/jpeg,image/png,image/webp,image/heic"
                disabled={saving}
                required
                onChange={(event) => setImage(event.target.files?.[0] ?? null)}
              />
              <p className="text-xs text-muted-foreground">单张不超过 5 MB。</p>
            </Field>
            <Field>
              <FieldLabel htmlFor="rabbit-abnormal-remark">异常说明</FieldLabel>
              <Textarea
                id="rabbit-abnormal-remark"
                value={remark}
                disabled={saving}
                maxLength={255}
                required
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
                <CameraIcon data-icon="inline-start" />
              )}
              记录异常
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
