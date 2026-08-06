import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { updateMerchantHousePolicy } from '@/api/merchants'
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
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import type { MerchantHousePolicy } from '@/types/api'

interface FormState {
  houseCreationEnabled: 'ENABLED' | 'DISABLED'
  houseMemberManagementEnabled: 'ENABLED' | 'DISABLED'
  maxHouseCount: string
  maxMembersPerHouse: string
}

export function MerchantHousePolicyDialog({
  open,
  policy,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  policy: MerchantHousePolicy
  onOpenChange: (open: boolean) => void
  onSaved: (policy: MerchantHousePolicy) => void
}) {
  const [form, setForm] = useState<FormState>(() => toFormState(policy))
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  const maxHouseCount = Number(form.maxHouseCount)
  const maxMembersPerHouse = Number(form.maxMembersPerHouse)
  const houseLimitInvalid = submitted && (!Number.isInteger(maxHouseCount) || maxHouseCount < 1 || maxHouseCount > 1000)
  const memberLimitInvalid = submitted && (!Number.isInteger(maxMembersPerHouse) || maxMembersPerHouse < 1 || maxMembersPerHouse > 500)

  useEffect(() => {
    if (!open) {
      return
    }
    setForm(toFormState(policy))
    setSubmitted(false)
  }, [open, policy])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitted(true)
    if (!Number.isInteger(maxHouseCount) || maxHouseCount < 1 || maxHouseCount > 1000) {
      toast.error('兔场上限需为 1-1000 的整数')
      return
    }
    if (!Number.isInteger(maxMembersPerHouse) || maxMembersPerHouse < 1 || maxMembersPerHouse > 500) {
      toast.error('单兔场成员上限需为 1-500 的整数')
      return
    }

    setSubmitting(true)
    try {
      const saved = await updateMerchantHousePolicy(policy.merchantId, {
        houseCreationEnabled: form.houseCreationEnabled === 'ENABLED',
        houseMemberManagementEnabled: form.houseMemberManagementEnabled === 'ENABLED',
        maxHouseCount,
        maxMembersPerHouse,
      })
      toast.success('兔场权限已更新')
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
          <DialogTitle>配置兔场权限</DialogTitle>
          <DialogDescription>
            控制当前商户创建兔场和维护兔场成员的能力及容量上限。
          </DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-1 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="min-h-0 overflow-y-auto pr-1">
            <Field>
              <FieldLabel htmlFor="house-creation-enabled">创建兔场</FieldLabel>
              <Select
                value={form.houseCreationEnabled}
                onValueChange={(value: 'ENABLED' | 'DISABLED') =>
                  setForm((current) => ({ ...current, houseCreationEnabled: value }))
                }
              >
                <SelectTrigger id="house-creation-enabled">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="ENABLED">允许</SelectItem>
                    <SelectItem value="DISABLED">禁止</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel htmlFor="house-member-management-enabled">管理兔场成员</FieldLabel>
              <Select
                value={form.houseMemberManagementEnabled}
                onValueChange={(value: 'ENABLED' | 'DISABLED') =>
                  setForm((current) => ({ ...current, houseMemberManagementEnabled: value }))
                }
              >
                <SelectTrigger id="house-member-management-enabled">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="ENABLED">允许</SelectItem>
                    <SelectItem value="DISABLED">禁止</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            <Field data-invalid={houseLimitInvalid ? true : undefined}>
              <FieldLabel htmlFor="max-house-count">兔场数量上限</FieldLabel>
              <Input
                id="max-house-count"
                type="number"
                min={1}
                max={1000}
                value={form.maxHouseCount}
                aria-invalid={houseLimitInvalid ? true : undefined}
                onChange={(event) =>
                  setForm((current) => ({ ...current, maxHouseCount: event.target.value }))
                }
              />
              <FieldDescription>当前商户最多可保有的有效兔场数量。</FieldDescription>
            </Field>
            <Field data-invalid={memberLimitInvalid ? true : undefined}>
              <FieldLabel htmlFor="max-members-per-house">单兔场成员上限</FieldLabel>
              <Input
                id="max-members-per-house"
                type="number"
                min={1}
                max={500}
                value={form.maxMembersPerHouse}
                aria-invalid={memberLimitInvalid ? true : undefined}
                onChange={(event) =>
                  setForm((current) => ({ ...current, maxMembersPerHouse: event.target.value }))
                }
              />
              <FieldDescription>包含兔场所有者、管理员、员工和只读成员。</FieldDescription>
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
      </DialogContent>
    </Dialog>
  )
}

function toFormState(policy: MerchantHousePolicy): FormState {
  return {
    houseCreationEnabled: policy.houseCreationEnabled ? 'ENABLED' : 'DISABLED',
    houseMemberManagementEnabled: policy.houseMemberManagementEnabled ? 'ENABLED' : 'DISABLED',
    maxHouseCount: String(policy.maxHouseCount),
    maxMembersPerHouse: String(policy.maxMembersPerHouse),
  }
}
