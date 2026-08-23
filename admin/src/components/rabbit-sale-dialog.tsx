import { useEffect, useRef, useState } from 'react'
import { ShoppingCartIcon } from 'lucide-react'
import { toast } from 'sonner'
import { createRabbitSale } from '@/api/workspace'
import { getOrCreateRabbitSaleRequest } from '@/lib/rabbit-sale'
import { formatLocalDate } from '@/lib/date'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import type { Rabbit } from '@/types/api'
import type { RabbitSaleRequest } from '@/types/rabbit-sale'

export function RabbitSaleDialog({
  rabbit,
  houseId,
  onOpenChange,
  onSaved,
}: {
  rabbit: Rabbit | null
  houseId: number | null
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [saleDate, setSaleDate] = useState('')
  const [totalWeight, setTotalWeight] = useState('')
  const [unitPrice, setUnitPrice] = useState('')
  const [customer, setCustomer] = useState('')
  const [remark, setRemark] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [saving, setSaving] = useState(false)
  const pendingRequest = useRef<RabbitSaleRequest | null>(null)

  useEffect(() => {
    if (!rabbit) return
    setSaleDate(formatLocalDate())
    setTotalWeight(rabbit.weight == null ? '' : rabbit.weight.toFixed(2))
    setUnitPrice('')
    setCustomer('')
    setRemark('')
    setConfirmed(false)
    pendingRequest.current = null
  }, [rabbit])

  async function handleSubmit() {
    if (!rabbit || !houseId) return
    const normalizedWeight = Number(totalWeight)
    const normalizedPrice = unitPrice.trim() ? Number(unitPrice) : undefined
    const saleTime = new Date(`${saleDate}T12:00:00`).getTime()
    if (!Number.isFinite(saleTime)) {
      toast.error('请选择出售日期')
      return
    }
    if (!Number.isFinite(normalizedWeight) || normalizedWeight <= 0) {
      toast.error('请填写大于 0 的销售重量')
      return
    }
    if (normalizedPrice != null && (!Number.isFinite(normalizedPrice) || normalizedPrice < 0)) {
      toast.error('单价不能小于 0')
      return
    }
    if (!confirmed) {
      toast.error('请确认出售出栏的影响')
      return
    }

    const request = getOrCreateRabbitSaleRequest(
      pendingRequest.current,
      {
        rabbitIds: [rabbit.id],
        saleTime,
        totalWeight: normalizedWeight,
        unitPrice: normalizedPrice,
        customer: customer.trim() || undefined,
        remark: remark.trim() || undefined,
      },
      () => crypto.randomUUID(),
    )
    pendingRequest.current = request
    setSaving(true)
    try {
      await createRabbitSale(houseId, request)
      pendingRequest.current = null
      toast.success(`兔 #${rabbit.id} 已出售出栏`)
      onOpenChange(false)
      await onSaved()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '出售失败，请稍后重试')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={Boolean(rabbit)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>出售出栏</DialogTitle>
          <DialogDescription>
            兔 #{rabbit?.id ?? ''} 将写入销售单并标记为出售出栏，同时退出活跃批次和生产周期。
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
              min="0"
              step="0.01"
              value={totalWeight}
              onChange={(event) => setTotalWeight(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="rabbit-sale-price">单价（元/kg）</FieldLabel>
            <Input
              id="rabbit-sale-price"
              type="number"
              min="0"
              step="0.01"
              value={unitPrice}
              onChange={(event) => setUnitPrice(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="rabbit-sale-customer">客户</FieldLabel>
            <Input
              id="rabbit-sale-customer"
              maxLength={100}
              value={customer}
              onChange={(event) => setCustomer(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="rabbit-sale-remark">备注</FieldLabel>
            <Textarea
              id="rabbit-sale-remark"
              value={remark}
              onChange={(event) => setRemark(event.target.value)}
            />
          </Field>
          <Field>
            <label className="flex items-start gap-3 text-sm" htmlFor="rabbit-sale-confirm">
              <input
                id="rabbit-sale-confirm"
                type="checkbox"
                className="mt-1"
                checked={confirmed}
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
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button disabled={saving} onClick={() => void handleSubmit()}>
            {saving ? <Spinner data-icon="inline-start" /> : <ShoppingCartIcon data-icon="inline-start" />}
            确认出售
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
