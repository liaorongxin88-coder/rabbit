import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { createMerchantAccount } from '@/api/merchants'
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

interface FormState {
  userName: string
  password: string
  confirmPassword: string
}

const emptyForm: FormState = {
  userName: '',
  password: '',
  confirmPassword: '',
}

export function MerchantAccountCreateDialog({
  open,
  merchantId,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  merchantId: number
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const [form, setForm] = useState<FormState>(emptyForm)
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  const userNameError = submitted && !form.userName.trim()
  const passwordError =
    submitted && (form.password.length < 6 || form.password.length > 64)
  const confirmPasswordError = submitted && form.password !== form.confirmPassword

  useEffect(() => {
    if (!open) {
      return
    }
    setForm(emptyForm)
    setSubmitted(false)
  }, [open])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
    if (!form.userName.trim()) {
      toast.error('登录用户名不能为空')
      return
    }
    if (form.password.length < 6 || form.password.length > 64) {
      toast.error('初始密码长度需为 6-64 个字符')
      return
    }
    if (form.password !== form.confirmPassword) {
      toast.error('两次输入的密码不一致')
      return
    }

    setSubmitting(true)
    try {
      await createMerchantAccount(merchantId, {
        ...form,
        userName: form.userName.trim(),
      })
      toast.success('商户账号已创建')
      onSaved()
      onOpenChange(false)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>新增商户账号</DialogTitle>
          <DialogDescription>
            创建业务端登录账号，账号将直接归属当前商户。
          </DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-1 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="min-h-0 overflow-y-auto pr-1">
            <Field data-invalid={userNameError ? true : undefined}>
              <FieldLabel htmlFor="new-merchant-user-name">
                登录用户名 <span className="text-destructive">*</span>
              </FieldLabel>
              <Input
                id="new-merchant-user-name"
                value={form.userName}
                aria-invalid={userNameError ? true : undefined}
                autoComplete="username"
                maxLength={64}
                onChange={(event) =>
                  setForm((current) => ({ ...current, userName: event.target.value }))
                }
              />
              {userNameError ? (
                <FieldDescription className="text-destructive">
                  请输入登录用户名。
                </FieldDescription>
              ) : (
                <FieldDescription>用户名需在所有业务账号中保持唯一。</FieldDescription>
              )}
            </Field>
            <Field data-invalid={passwordError ? true : undefined}>
              <FieldLabel htmlFor="new-merchant-password">
                初始密码 <span className="text-destructive">*</span>
              </FieldLabel>
              <Input
                id="new-merchant-password"
                type="password"
                value={form.password}
                aria-invalid={passwordError ? true : undefined}
                autoComplete="new-password"
                minLength={6}
                maxLength={64}
                onChange={(event) =>
                  setForm((current) => ({ ...current, password: event.target.value }))
                }
              />
              <FieldDescription>密码长度为 6-64 个字符。</FieldDescription>
            </Field>
            <Field data-invalid={confirmPasswordError ? true : undefined}>
              <FieldLabel htmlFor="new-merchant-confirm-password">
                确认密码 <span className="text-destructive">*</span>
              </FieldLabel>
              <Input
                id="new-merchant-confirm-password"
                type="password"
                value={form.confirmPassword}
                aria-invalid={confirmPasswordError ? true : undefined}
                autoComplete="new-password"
                minLength={6}
                maxLength={64}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    confirmPassword: event.target.value,
                  }))
                }
              />
              {confirmPasswordError ? (
                <FieldDescription className="text-destructive">
                  两次输入的密码不一致。
                </FieldDescription>
              ) : null}
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              取消
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? <Spinner /> : null}
              创建账号
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
