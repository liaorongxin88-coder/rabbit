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
  userName: string
  password: string
  confirmPassword: string
}

const emptyForm: FormState = {
  name: '',
  contactName: '',
  contactPhone: '',
  remark: '',
  userName: '',
  password: '',
  confirmPassword: '',
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
  const userNameError = submitted && !merchant && !form.userName.trim()
  const passwordError = submitted && !merchant && form.password.length < 6
  const confirmPasswordError =
    submitted && !merchant && form.password !== form.confirmPassword

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
            userName: '',
            password: '',
            confirmPassword: '',
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
    if (!merchant && !form.userName.trim()) {
      toast.error('登录用户名不能为空')
      return
    }
    if (!merchant && (form.password.length < 6 || form.password.length > 64)) {
      toast.error('初始密码长度需为 6-64 个字符')
      return
    }
    if (!merchant && form.password !== form.confirmPassword) {
      toast.error('两次输入的密码不一致')
      return
    }
    setSubmitting(true)
    try {
      const saved = merchant
        ? await updateMerchant(merchant.id, {
            name: form.name,
            contactName: form.contactName,
            contactPhone: form.contactPhone,
            remark: form.remark,
          })
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
          <DialogDescription>
            {merchant
              ? '维护商户名称、联系人和备注信息。'
              : '填写商户资料，并创建该商户的首个登录账号。'}
          </DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-1 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="min-h-0 overflow-y-auto pr-1">
            <Field data-invalid={nameError ? true : undefined}>
              <FieldLabel htmlFor="merchant-name">
                商户名称 <span className="text-destructive">*</span>
              </FieldLabel>
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
            {!merchant ? (
              <>
                <div className="border-t pt-5">
                  <p className="text-sm font-medium">登录账号</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    账号创建后将直接归属当前商户。
                  </p>
                </div>
                <Field data-invalid={userNameError ? true : undefined}>
                  <FieldLabel htmlFor="merchant-user-name">
                    登录用户名 <span className="text-destructive">*</span>
                  </FieldLabel>
                  <Input
                    id="merchant-user-name"
                    value={form.userName}
                    aria-invalid={userNameError ? true : undefined}
                    autoComplete="username"
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        userName: event.target.value,
                      }))
                    }
                    maxLength={64}
                  />
                  <FieldDescription>
                    用于商户登录业务端，创建后可在账号管理中修改。
                  </FieldDescription>
                </Field>
                <Field data-invalid={passwordError ? true : undefined}>
                  <FieldLabel htmlFor="merchant-password">
                    初始密码 <span className="text-destructive">*</span>
                  </FieldLabel>
                  <Input
                    id="merchant-password"
                    type="password"
                    value={form.password}
                    aria-invalid={passwordError ? true : undefined}
                    autoComplete="new-password"
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        password: event.target.value,
                      }))
                    }
                    minLength={6}
                    maxLength={64}
                  />
                  <FieldDescription>密码长度为 6-64 个字符。</FieldDescription>
                </Field>
                <Field data-invalid={confirmPasswordError ? true : undefined}>
                  <FieldLabel htmlFor="merchant-confirm-password">
                    确认密码 <span className="text-destructive">*</span>
                  </FieldLabel>
                  <Input
                    id="merchant-confirm-password"
                    type="password"
                    value={form.confirmPassword}
                    aria-invalid={confirmPasswordError ? true : undefined}
                    autoComplete="new-password"
                    onChange={(event) =>
                      setForm((current) => ({
                        ...current,
                        confirmPassword: event.target.value,
                      }))
                    }
                    minLength={6}
                    maxLength={64}
                  />
                  {confirmPasswordError ? (
                    <FieldDescription className="text-destructive">
                      两次输入的密码不一致。
                    </FieldDescription>
                  ) : null}
                </Field>
              </>
            ) : null}
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
