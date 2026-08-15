import { useCallback, useEffect, useRef, useState } from 'react'
import { Edit3Icon, PlusIcon, RefreshCwIcon, Trash2Icon, WarehouseIcon } from 'lucide-react'
import { toast } from 'sonner'
import {
  createHouseInvitation,
  listHouseMembers,
  removeHouseMember,
  requestId as createRequestId,
  updateHouseMember,
} from '@/api/workspace'
import { PageHeader } from '@/components/page-header'
import { HousePermissionBadge } from '@/components/permission-badge'
import { getOrCreateInvitationRequest } from '@/lib/invitation-request'
import { houseRoleLabel } from '@/lib/permission-labels'
import { hasPermission, useWorkspace } from '@/lib/workspace'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
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
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { HouseMember, HouseRole } from '@/types/api'

export function WorkspaceMembersPage() {
  const workspace = useWorkspace()
  const [members, setMembers] = useState<HouseMember[]>([])
  const [loading, setLoading] = useState(false)
  const [inviteOpen, setInviteOpen] = useState(false)
  const [editingMember, setEditingMember] = useState<HouseMember | null>(null)
  const [removalTarget, setRemovalTarget] = useState<HouseMember | null>(null)
  const canManageMembers = hasPermission(workspace.permission, 'rabbit:house-members:list')

  const load = useCallback(async () => {
    if (!workspace.selectedHouse || !canManageMembers) {
      setMembers([])
      return
    }
    setLoading(true)
    try {
      setMembers(await listHouseMembers(workspace.selectedHouse.id))
    } catch {
      setMembers([])
    } finally {
      setLoading(false)
    }
  }, [canManageMembers, workspace.selectedHouse])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <>
      <PageHeader
        title="成员权限"
        description={workspace.selectedHouse?.name ?? '请选择兔场'}
        actions={
          <>
            <HousePermissionBadge permission={workspace.permission} />
            <Button
              variant="outline"
              disabled={loading || !workspace.selectedHouse || !canManageMembers}
              onClick={() => void load()}
            >
              <RefreshCwIcon data-icon="inline-start" />
              刷新
            </Button>
          </>
        }
      />

      {!workspace.selectedHouse ? (
        <Empty>
          <WarehouseIcon aria-hidden="true" />
          <EmptyTitle>请选择兔场</EmptyTitle>
          <EmptyDescription>成员角色只在当前兔场生效。</EmptyDescription>
        </Empty>
      ) : !canManageMembers ? (
        <Empty>
          <EmptyTitle>没有成员管理权限</EmptyTitle>
          <EmptyDescription>仅兔场所有者可以维护成员。</EmptyDescription>
        </Empty>
      ) : (
        <Card>
          <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle>兔场成员</CardTitle>
              <CardDescription>通过手机号邀请用户，并为其设置兔场角色。</CardDescription>
            </div>
            <InviteMemberDialog
              open={inviteOpen}
              houseId={workspace.selectedHouse.id}
              onOpenChange={setInviteOpen}
              onInvited={load}
            />
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="motion-section flex flex-col gap-2">
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
              </div>
            ) : members.length === 0 ? (
              <Empty>
                <EmptyTitle>暂无成员</EmptyTitle>
                <EmptyDescription>邀请成员后，可在这里维护其兔场角色。</EmptyDescription>
              </Empty>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>成员</TableHead>
                    <TableHead>手机号</TableHead>
                    <TableHead>角色</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>加入时间</TableHead>
                    <TableHead className="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {members.map((member) => (
                    <TableRow key={member.userId}>
                      <TableCell>
                        <div className="min-w-44">
                          <p className="truncate font-medium">{member.userName}</p>
                          <p className="text-xs text-muted-foreground">ID {member.userId}</p>
                        </div>
                      </TableCell>
                      <TableCell>{member.phoneMasked || '-'}</TableCell>
                      <TableCell>
                        <Badge variant={member.role === 'OWNER' ? 'default' : 'secondary'}>
                          {houseRoleLabel(member.role)}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant={member.status === 'DISABLED' ? 'outline' : 'secondary'}>
                          {member.status === 'DISABLED' ? '停用' : '启用'}
                        </Badge>
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">
                        {formatDate(member.joinTime)}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="inline-flex items-center gap-2">
                          <Button variant="outline" size="sm" onClick={() => setEditingMember(member)}>
                            <Edit3Icon data-icon="inline-start" />
                            编辑
                          </Button>
                          <Button
                            variant="destructive"
                            size="sm"
                            disabled={member.userId === workspace.session.userId}
                            onClick={() => setRemovalTarget(member)}
                          >
                            <Trash2Icon data-icon="inline-start" />
                            移除
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      )}

      <EditMemberDialog
        member={editingMember}
        houseId={workspace.selectedHouse?.id ?? null}
        onOpenChange={(open) => !open && setEditingMember(null)}
        onSaved={load}
      />
      <RemoveMemberDialog
        member={removalTarget}
        houseId={workspace.selectedHouse?.id ?? null}
        onOpenChange={(open) => !open && setRemovalTarget(null)}
        onRemoved={load}
      />
    </>
  )
}

function InviteMemberDialog({
  open,
  houseId,
  onOpenChange,
  onInvited,
}: {
  open: boolean
  houseId: number
  onOpenChange: (open: boolean) => void
  onInvited: () => Promise<void>
}) {
  const [phone, setPhone] = useState('')
  const [role, setRole] = useState<HouseRole>('STAFF')
  const [saving, setSaving] = useState(false)
  const pendingRequest = useRef<{
    phone: string
    role: HouseRole
    requestId: string
  } | null>(null)

  useEffect(() => {
    if (open) {
      setPhone('')
      setRole('STAFF')
      pendingRequest.current = null
    }
  }, [open])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalizedPhone = normalizeMainlandPhone(phone)
    if (!/^1[3-9]\d{9}$/.test(normalizedPhone)) {
      toast.error('请输入有效的中国大陆手机号')
      return
    }
    const invitationRequest = getOrCreateInvitationRequest(
      pendingRequest.current,
      { phone: normalizedPhone, role },
      createRequestId,
    )
    pendingRequest.current = invitationRequest
    setSaving(true)
    try {
      await createHouseInvitation(houseId, invitationRequest)
      pendingRequest.current = null
      toast.success('邀请已提交')
      onOpenChange(false)
      await onInvited()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        <Button>
          <PlusIcon data-icon="inline-start" />
          邀请成员
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>邀请兔场成员</DialogTitle>
          <DialogDescription>邀请提交后，对方下次使用该手机号验证登录时加入。</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="invite-phone">手机号</FieldLabel>
              <Input
                id="invite-phone"
                value={phone}
                inputMode="tel"
                autoComplete="tel"
                placeholder="请输入 11 位手机号"
                required
                onChange={(event) => setPhone(event.target.value)}
              />
              <FieldDescription>完整号码仅用于发起邀请，成员列表始终脱敏展示。</FieldDescription>
            </Field>
            <RoleField
              id="invite-role"
              role={role}
              allowOwner={false}
              onRoleChange={setRole}
            />
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
            <Button type="submit" disabled={saving}>
              {saving ? <Spinner data-icon="inline-start" /> : null}
              发送邀请
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function EditMemberDialog({
  member,
  houseId,
  onOpenChange,
  onSaved,
}: {
  member: HouseMember | null
  houseId: number | null
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [role, setRole] = useState<HouseRole>('STAFF')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (member) {
      setRole(member.role)
    }
  }, [member])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!member || !houseId) return
    setSaving(true)
    try {
      await updateHouseMember(houseId, member.userId, role)
      toast.success('兔场成员角色已更新')
      onOpenChange(false)
      await onSaved()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={Boolean(member)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>编辑兔场成员</DialogTitle>
          <DialogDescription>调整 {member?.userName ?? '当前成员'} 在此兔场的角色。</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
          <RoleField
            id="edit-member-role"
            role={role}
            allowOwner
            onRoleChange={setRole}
          />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
            <Button type="submit" disabled={saving}>
              {saving ? <Spinner data-icon="inline-start" /> : null}
              保存
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function RoleField({
  id,
  role,
  allowOwner,
  onRoleChange,
}: {
  id: string
  role: HouseRole
  allowOwner: boolean
  onRoleChange: (role: HouseRole) => void
}) {
  return (
    <Field>
      <FieldLabel htmlFor={id}>兔场角色</FieldLabel>
      <Select value={role} onValueChange={(value) => onRoleChange(value as HouseRole)}>
        <SelectTrigger id={id}><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {allowOwner ? <SelectItem value="OWNER">所有者</SelectItem> : null}
            <SelectItem value="MANAGER">管理员</SelectItem>
            <SelectItem value="STAFF">生产人员</SelectItem>
            <SelectItem value="VIEWER">查看者</SelectItem>
          </SelectGroup>
        </SelectContent>
      </Select>
    </Field>
  )
}

function RemoveMemberDialog({
  member,
  houseId,
  onOpenChange,
  onRemoved,
}: {
  member: HouseMember | null
  houseId: number | null
  onOpenChange: (open: boolean) => void
  onRemoved: () => Promise<void>
}) {
  const [saving, setSaving] = useState(false)

  async function handleRemove() {
    if (!member || !houseId) return
    setSaving(true)
    try {
      await removeHouseMember(houseId, member.userId)
      toast.success('成员已移出兔场')
      onOpenChange(false)
      await onRemoved()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={Boolean(member)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>确认移除成员</DialogTitle>
          <DialogDescription>将“{member?.userName ?? ''}”移出当前兔场。</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button variant="destructive" disabled={saving} onClick={() => void handleRemove()}>
            {saving ? <Spinner data-icon="inline-start" /> : <Trash2Icon data-icon="inline-start" />}
            确认移除
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function normalizeMainlandPhone(value: string) {
  const digits = value.replace(/\D/g, '')
  if (digits.startsWith('0086')) return digits.slice(4)
  if (digits.startsWith('86') && digits.length === 13) return digits.slice(2)
  return digits
}

function formatDate(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleDateString('zh-CN')
}
