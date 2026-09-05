import { useEffect, useRef, useState } from "react";
import { ShoppingCartIcon } from "lucide-react";
import { toast } from "sonner";
import { createRabbitSale } from "@/api/workspace";
import {
  getOrCreateRabbitSaleRequest,
  rabbitSaleValidationError,
} from "@/lib/rabbit-sale";
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
} from "@/components/ui/dialog";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import type { Rabbit } from "@/types/api";
import type { RabbitSaleRequest } from "@/types/rabbit-sale";

export function RabbitSaleDialog({
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
  const [saleDate, setSaleDate] = useState("");
  const [totalWeight, setTotalWeight] = useState("");
  const [unitPrice, setUnitPrice] = useState("");
  const [customer, setCustomer] = useState("");
  const [remark, setRemark] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [saving, setSaving] = useState(false);
  const pendingRequest = useRef<RabbitSaleRequest | null>(null);

  useEffect(() => {
    if (!rabbit) return;
    setSaleDate(formatFarmBusinessDate());
    setTotalWeight("");
    setUnitPrice("");
    setCustomer("");
    setRemark("");
    setConfirmed(false);
    pendingRequest.current = null;
  }, [rabbit]);

  async function handleSubmit() {
    if (!rabbit || !houseId) return;
    const normalizedWeight = Number(totalWeight);
    const normalizedPrice = Number(unitPrice);
    const saleTime = farmBusinessDateToTimestamp(saleDate);
    if (saleTime === undefined) {
      toast.error("请选择出售日期");
      return;
    }
    const validationMessage = rabbitSaleValidationError(
      normalizedWeight,
      normalizedPrice,
    );
    if (validationMessage) {
      toast.error(validationMessage);
      return;
    }
    if (!confirmed) {
      toast.error("请确认出售出栏的影响");
      return;
    }

    const request = getOrCreateRabbitSaleRequest(
      pendingRequest.current,
      {
        rabbitIds: [rabbit.id],
        saleTime,
        totalWeight: normalizedWeight,
        unitPrice: normalizedPrice,
        unitPricePerKg: normalizedPrice,
        // The locked backend snapshot owns attribution for a single-rabbit sale.
        // A client-guessed null batch can reject older rabbits with an active link.
        batchAllocations: [],
        customer: customer.trim() || undefined,
        remark: remark.trim() || undefined,
      },
      () => crypto.randomUUID(),
    );
    pendingRequest.current = request;
    setSaving(true);
    try {
      await createRabbitSale(houseId, request);
      pendingRequest.current = null;
      toast.success(`兔 #${rabbit.id} 已出售出栏`);
      onOpenChange(false);
      await onSaved();
    } catch {
      // The shared request layer reports the failure. Keep the request ID for
      // an unchanged retry because the server may have completed the write.
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={Boolean(rabbit)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>出售出栏</DialogTitle>
          <DialogDescription>
            兔 #{rabbit?.id ?? ""}{" "}
            将写入销售单并标记为出售出栏，同时退出活跃批次和生产周期。
          </DialogDescription>
        </DialogHeader>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="rabbit-sale-date">出售日期</FieldLabel>
            <Input
              id="rabbit-sale-date"
              type="date"
              value={saleDate}
              onChange={(event) => setSaleDate(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="rabbit-sale-weight">销售重量（kg）</FieldLabel>
            <Input
              id="rabbit-sale-weight"
              type="number"
              min="0.001"
              step="0.001"
              value={totalWeight}
              disabled={saving}
              onChange={(event) => setTotalWeight(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="rabbit-sale-price">单价（元/kg）</FieldLabel>
            <Input
              id="rabbit-sale-price"
              type="number"
              min="0.01"
              step="0.01"
              max="99999999.99"
              value={unitPrice}
              disabled={saving}
              required
              onChange={(event) => setUnitPrice(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="rabbit-sale-customer">客户</FieldLabel>
            <Input
              id="rabbit-sale-customer"
              maxLength={100}
              value={customer}
              disabled={saving}
              onChange={(event) => setCustomer(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="rabbit-sale-remark">备注</FieldLabel>
            <Textarea
              id="rabbit-sale-remark"
              value={remark}
              disabled={saving}
              onChange={(event) => setRemark(event.target.value)}
            />
          </Field>
          <Field>
            <label
              className="flex items-start gap-3 text-sm"
              htmlFor="rabbit-sale-confirm"
            >
              <input
                id="rabbit-sale-confirm"
                type="checkbox"
                className="mt-1"
                checked={confirmed}
                disabled={saving}
                onChange={(event) => setConfirmed(event.target.checked)}
              />
              <span>
                <span className="font-medium">确认出售出栏</span>
                <br />
                <span className="text-muted-foreground">
                  该兔将离场，活跃批次、生产周期和待办会一并结束。
                </span>
              </span>
            </label>
          </Field>
        </FieldGroup>
        <DialogFooter>
          <Button
            variant="outline"
            disabled={saving}
            onClick={() => onOpenChange(false)}
          >
            取消
          </Button>
          <Button disabled={saving} onClick={() => void handleSubmit()}>
            {saving ? (
              <Spinner data-icon="inline-start" />
            ) : (
              <ShoppingCartIcon data-icon="inline-start" />
            )}
            确认出售
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
