import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { updateMerchantMembership } from '@/api/merchants'
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
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import type { MembershipStatus, MerchantAccountSummary, MerchantRole } from '@/types/api'

export function MerchantMembershipDialog({
  open,
  merchantId,
  account,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  merchantId: number
  account?: MerchantAccountSummary | null
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}) {
  const [role, setRole] = useState<MerchantRole>('MEMBER')
  const [status, setStatus] = useState<MembershipStatus>('ENABLED')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (!open || !account) {
      return
    }
    setRole(account.role)
    setStatus(account.membershipStatus)
  }, [account, open])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!account) {
      return
    }
    setSubmitting(true)
    try {
      await updateMerchantMembership(merchantId, account.userId, { role, status })
      toast.success('商户成员权限已更新')
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
          <DialogTitle>配置商户成员</DialogTitle>
          <DialogDescription>
            调整 {account?.userName ?? '当前账号'} 在此商户内的管理角色和可用状态。
          </DialogDescription>
        </DialogHeader>
        {account ? (
          <form className="flex min-h-0 flex-1 flex-col gap-5" onSubmit={handleSubmit}>
            <FieldGroup className="min-h-0 overflow-y-auto pr-1">
              <Field>
                <FieldLabel htmlFor="merchant-membership-role">商户角色</FieldLabel>
                <Select value={role} onValueChange={(value: MerchantRole) => setRole(value)}>
                  <SelectTrigger id="merchant-membership-role">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="OWNER">所有者</SelectItem>
                      <SelectItem value="ADMIN">管理员</SelectItem>
                      <SelectItem value="MEMBER">成员</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
                <FieldDescription>设置为所有者会将原所有者调整为管理员。</FieldDescription>
              </Field>
              <Field>
                <FieldLabel htmlFor="merchant-membership-status">成员状态</FieldLabel>
                <Select
                  value={status}
                  onValueChange={(value: MembershipStatus) => setStatus(value)}
                >
                  <SelectTrigger id="merchant-membership-status">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="ENABLED">启用</SelectItem>
                      <SelectItem value="DISABLED">停用</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
                <FieldDescription>停用只影响该商户，不影响账号加入的其他商户。</FieldDescription>
              </Field>
            </FieldGroup>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                取消
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitting ? <Spinner /> : null}
                保存权限
              </Button>
            </DialogFooter>
          </form>
        ) : null}
      </DialogContent>
    </Dialog>
  )
}
