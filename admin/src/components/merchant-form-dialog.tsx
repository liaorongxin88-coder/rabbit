import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { createMerchant, updateMerchant } from '@/api/merchants'
import type { Merchant, MerchantStatus } from '@/types/api'

interface FormState {
  name: string
  contactName: string
  contactPhone: string
  remark: string
}

const emptyForm: FormState = {
  name: '',
  contactName: '',
  contactPhone: '',
  remark: '',
}

export function MerchantFormDialog({
  open,
  merchant,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  merchant?: Merchant | null
  onOpenChange: (open: boolean) => void
  onSaved: (merchant: Merchant) => void
}) {
  const [form, setForm] = useState<FormState>(emptyForm)
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  const nameError = submitted && !form.name.trim()

  useEffect(() => {
    if (!open) {
      return
    }
    setSubmitted(false)
    setForm(
      merchant
        ? {
            name: merchant.name,
            contactName: merchant.contactName ?? '',
            contactPhone: merchant.contactPhone ?? '',
            remark: merchant.remark ?? '',
          }
        : emptyForm,
    )
  }, [merchant, open])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
    if (!form.name.trim()) {
      toast.error('商户名称不能为空')
      return
    }
    setSubmitting(true)
    try {
      const saved = merchant
        ? await updateMerchant(merchant.id, form)
        : await createMerchant(form)
      toast.success(merchant ? '商户已更新' : '商户已创建')
      onSaved({
        ...saved,
        status: saved.status as MerchantStatus,
      })
      onOpenChange(false)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{merchant ? '编辑商户' : '新增商户'}</DialogTitle>
          <DialogDescription>维护商户名称、联系人和备注信息。</DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-1 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="min-h-0 overflow-y-auto pr-1">
            <Field data-invalid={nameError ? true : undefined}>
              <FieldLabel htmlFor="merchant-name">商户名称</FieldLabel>
              <Input
                id="merchant-name"
                value={form.name}
                aria-invalid={nameError ? true : undefined}
                onChange={(event) =>
                  setForm((current) => ({ ...current, name: event.target.value }))
                }
                maxLength={100}
              />
              {nameError ? (
                <FieldDescription className="text-destructive">
                  请输入商户名称。
                </FieldDescription>
              ) : (
                <FieldDescription>用于平台后台识别商户，最多 100 个字符。</FieldDescription>
              )}
            </Field>
            <Field>
              <FieldLabel htmlFor="merchant-contact">联系人</FieldLabel>
              <Input
                id="merchant-contact"
                value={form.contactName}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    contactName: event.target.value,
                  }))
                }
                maxLength={64}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="merchant-phone">联系电话</FieldLabel>
              <Input
                id="merchant-phone"
                value={form.contactPhone}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    contactPhone: event.target.value,
                  }))
                }
                maxLength={32}
              />
              <FieldDescription>建议填写平台运营可联系到的手机号或座机。</FieldDescription>
            </Field>
            <Field>
              <FieldLabel htmlFor="merchant-remark">备注</FieldLabel>
              <Textarea
                id="merchant-remark"
                value={form.remark}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    remark: event.target.value,
                  }))
                }
                maxLength={1000}
              />
              <FieldDescription>{form.remark.length} / 1000</FieldDescription>
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              取消
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? <Spinner /> : null}
              {merchant ? '保存修改' : '创建商户'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
