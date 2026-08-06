import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { updateMerchantAccount } from '@/api/accounts'
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
import type { MerchantAccount } from '@/types/api'

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

export function MerchantAccountFormDialog({
  open,
  account,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  account?: MerchantAccount | null
  onOpenChange: (open: boolean) => void
  onSaved: (account: MerchantAccount) => void
}) {
  const [form, setForm] = useState<FormState>(emptyForm)
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  const changingPassword =
    form.password.trim().length > 0 || form.confirmPassword.trim().length > 0
  const userNameError = submitted && !form.userName.trim()
  const passwordLengthError =
    submitted && changingPassword && form.password.trim().length < 6
  const confirmPasswordError =
    submitted && changingPassword && form.password.trim() !== form.confirmPassword.trim()

  useEffect(() => {
    if (!open) {
      return
    }
    setSubmitted(false)
    setForm(
      account
        ? {
            userName: account.userName,
            password: '',
            confirmPassword: '',
          }
        : emptyForm,
    )
  }, [account, open])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
    const nextUserName = form.userName.trim()
    const nextPassword = form.password.trim()
    const nextConfirmPassword = form.confirmPassword.trim()
    const shouldChangePassword = nextPassword.length > 0 || nextConfirmPassword.length > 0

    if (!account) {
      return
    }
    if (!nextUserName) {
      toast.error('用户名不能为空')
      return
    }
    if (shouldChangePassword && nextPassword.length < 6) {
      toast.error('密码长度至少 6 位')
      return
    }
    if (shouldChangePassword && nextPassword !== nextConfirmPassword) {
      toast.error('两次输入的密码不一致')
      return
    }

    setSubmitting(true)
    try {
      const saved = await updateMerchantAccount(account.userId, {
        userName: nextUserName,
        password: shouldChangePassword ? nextPassword : undefined,
        confirmPassword: shouldChangePassword ? nextConfirmPassword : undefined,
      })
      toast.success('商户账号已更新')
      onSaved(saved)
      onOpenChange(false)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>编辑商户账号</DialogTitle>
          <DialogDescription>查看基础信息，修改用户名或重设登录密码。</DialogDescription>
        </DialogHeader>
        {account ? (
          <form className="flex min-h-0 flex-1 flex-col gap-5" onSubmit={handleSubmit}>
            <FieldGroup className="min-h-0 overflow-y-auto pr-1">
              <Field>
                <FieldLabel>用户 ID</FieldLabel>
                <Input value={String(account.userId)} disabled />
              </Field>
              <Field>
                <FieldLabel>所属商户</FieldLabel>
                <Input value={account.merchantName || '-'} disabled />
                <FieldDescription>
                  商户 ID {account.merchantId}，这里展示账号的默认商户。
                </FieldDescription>
              </Field>
              <Field>
                <FieldLabel>OpenID</FieldLabel>
                <Input value={account.openid || '-'} disabled />
              </Field>
              <Field data-invalid={userNameError ? true : undefined}>
                <FieldLabel htmlFor="merchant-account-user-name">用户名</FieldLabel>
                <Input
                  id="merchant-account-user-name"
                  value={form.userName}
                  aria-invalid={userNameError ? true : undefined}
                  autoComplete="username"
                  maxLength={64}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, userName: event.target.value }))
                  }
                />
                {userNameError ? (
                  <FieldDescription className="text-destructive">请输入用户名。</FieldDescription>
                ) : null}
              </Field>
              <Field data-invalid={passwordLengthError ? true : undefined}>
                <FieldLabel htmlFor="merchant-account-password">新密码</FieldLabel>
                <Input
                  id="merchant-account-password"
                  type="password"
                  value={form.password}
                  aria-invalid={passwordLengthError ? true : undefined}
                  autoComplete="new-password"
                  maxLength={64}
                  placeholder="留空则不修改密码"
                  onChange={(event) =>
                    setForm((current) => ({ ...current, password: event.target.value }))
                  }
                />
                {passwordLengthError ? (
                  <FieldDescription className="text-destructive">
                    密码长度至少 6 位。
                  </FieldDescription>
                ) : (
                  <FieldDescription>密码不可查看，只能通过重设修改。</FieldDescription>
                )}
              </Field>
              <Field data-invalid={confirmPasswordError ? true : undefined}>
                <FieldLabel htmlFor="merchant-account-confirm-password">再次输入新密码</FieldLabel>
                <Input
                  id="merchant-account-confirm-password"
                  type="password"
                  value={form.confirmPassword}
                  aria-invalid={confirmPasswordError ? true : undefined}
                  autoComplete="new-password"
                  maxLength={64}
                  placeholder="需与新密码一致"
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
                保存修改
              </Button>
            </DialogFooter>
          </form>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}
