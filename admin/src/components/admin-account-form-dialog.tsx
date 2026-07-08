import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { createAdminAccount, updateAdminAccount } from '@/api/accounts'
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
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import type { AdminAccount, AdminRole } from '@/types/api'

interface FormState {
  userName: string
  password: string
  role: AdminRole
  enabled: 'true' | 'false'
}

const emptyForm: FormState = {
  userName: '',
  password: '',
  role: 'ADMIN',
  enabled: 'true',
}

export function AdminAccountFormDialog({
  open,
  account,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  account?: AdminAccount | null
  onOpenChange: (open: boolean) => void
  onSaved: (account: AdminAccount) => void
}) {
  const [form, setForm] = useState<FormState>(emptyForm)
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  const userNameError = submitted && !form.userName.trim()
  const passwordError = submitted && !account && form.password.trim().length < 6
  const editPasswordError =
    submitted &&
    Boolean(account) &&
    form.password.trim().length > 0 &&
    form.password.trim().length < 6

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
            role: account.role,
            enabled: account.enabled ? 'true' : 'false',
          }
        : emptyForm,
    )
  }, [account, open])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
    const nextPassword = form.password.trim()
    if (!form.userName.trim()) {
      toast.error('用户名不能为空')
      return
    }
    if (!account && nextPassword.length < 6) {
      toast.error('密码长度至少 6 位')
      return
    }
    if (account && nextPassword && nextPassword.length < 6) {
      toast.error('密码长度至少 6 位')
      return
    }

    setSubmitting(true)
    try {
      const payload = {
        userName: form.userName.trim(),
        role: form.role,
        enabled: form.enabled === 'true',
      }
      const saved = account
        ? await updateAdminAccount(account.id, {
            ...payload,
            password: nextPassword || undefined,
          })
        : await createAdminAccount({
            ...payload,
            password: nextPassword,
          })
      toast.success(account ? '账号已更新' : '账号已创建')
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
          <DialogTitle>{account ? '编辑管理员账号' : '新增管理员账号'}</DialogTitle>
          <DialogDescription>维护平台管理端登录账号和权限级别。</DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-1 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="min-h-0 overflow-y-auto pr-1">
            <Field data-invalid={userNameError ? true : undefined}>
              <FieldLabel htmlFor="account-user-name">用户名</FieldLabel>
              <Input
                id="account-user-name"
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
            <Field data-invalid={passwordError || editPasswordError ? true : undefined}>
              <FieldLabel htmlFor="account-password">密码</FieldLabel>
              <Input
                id="account-password"
                type="password"
                value={form.password}
                aria-invalid={passwordError || editPasswordError ? true : undefined}
                autoComplete="new-password"
                maxLength={64}
                placeholder={account ? '留空则不修改密码' : '至少 6 位'}
                onChange={(event) =>
                  setForm((current) => ({ ...current, password: event.target.value }))
                }
              />
              {passwordError || editPasswordError ? (
                <FieldDescription className="text-destructive">
                  密码长度至少 6 位。
                </FieldDescription>
              ) : account ? (
                <FieldDescription>不填写则保留原密码。</FieldDescription>
              ) : null}
            </Field>
            <Field>
              <FieldLabel>角色</FieldLabel>
              <Select
                value={form.role}
                onValueChange={(value) =>
                  setForm((current) => ({ ...current, role: value as AdminRole }))
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="SUPER_ADMIN">超级管理员</SelectItem>
                    <SelectItem value="ADMIN">管理员</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel>状态</FieldLabel>
              <Select
                value={form.enabled}
                onValueChange={(value) =>
                  setForm((current) => ({
                    ...current,
                    enabled: value as 'true' | 'false',
                  }))
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="true">启用</SelectItem>
                    <SelectItem value="false">停用</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              取消
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? <Spinner /> : null}
              {account ? '保存修改' : '创建账号'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
