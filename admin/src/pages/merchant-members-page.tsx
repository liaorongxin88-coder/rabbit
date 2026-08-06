import { useCallback, useEffect, useState } from 'react'
import { Edit3Icon, PlusIcon, RefreshCwIcon, Trash2Icon, WarehouseIcon } from 'lucide-react'
import { toast } from 'sonner'
import {
  addHouseMember,
  addMerchantMember,
  listHouseMembers,
  listMerchantMembers,
  removeHouseMember,
  removeMerchantMember,
  updateHouseMember,
  updateMerchantMember,
} from '@/api/workspace'
import { PageHeader } from '@/components/page-header'
import { houseRoleLabel, merchantRoleLabel } from '@/lib/permission-labels'
import { hasPermission, useMerchantWorkspace } from '@/lib/merchant-workspace'
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
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import type {
  HouseMember,
  HouseRole,
  MerchantMember,
  MerchantRole,
  MembershipStatus,
} from '@/types/api'

type RemovalTarget =
  | { scope: 'merchant'; userId: number; userName: string }
  | { scope: 'house'; userId: number; userName: string }
  | null

export function MerchantMembersPage() {
  const workspace = useMerchantWorkspace()
  const [merchantMembers, setMerchantMembers] = useState<MerchantMember[]>([])
  const [houseMembers, setHouseMembers] = useState<HouseMember[]>([])
  const [loading, setLoading] = useState(false)
  const [merchantDialog, setMerchantDialog] = useState<{ open: boolean; member: MerchantMember | null }>({ open: false, member: null })
  const [houseDialog, setHouseDialog] = useState<{ open: boolean; member: HouseMember | null }>({ open: false, member: null })
  const [removalTarget, setRemovalTarget] = useState<RemovalTarget>(null)
  const isMerchantOwner = hasPermission(workspace.selectedMerchant, 'merchant:members:list')
  const isHouseAdmin = hasPermission(workspace.permission, 'rabbit:house-members:list')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [nextMerchantMembers, nextHouseMembers] = await Promise.all([
        workspace.selectedMerchant && isMerchantOwner
          ? listMerchantMembers(workspace.selectedMerchant.merchantId)
          : Promise.resolve([]),
        workspace.selectedHouse && isHouseAdmin
          ? listHouseMembers(workspace.selectedHouse.id)
          : Promise.resolve([]),
      ])
      setMerchantMembers(nextMerchantMembers)
      setHouseMembers(nextHouseMembers)
    } catch {
      setMerchantMembers([])
      setHouseMembers([])
    } finally {
      setLoading(false)
    }
  }, [isHouseAdmin, isMerchantOwner, workspace.selectedHouse, workspace.selectedMerchant])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <>
      <PageHeader
        title="成员权限"
        description={`${workspace.selectedMerchant?.merchantName ?? '未选择商户'} · ${workspace.selectedHouse?.name ?? '未选择兔场'}`}
        actions={
          <Button variant="outline" onClick={() => void load()} disabled={loading}>
            <RefreshCwIcon data-icon="inline-start" />
            刷新
          </Button>
        }
      />

      <Tabs defaultValue="house">
        <TabsList>
          <TabsTrigger value="house">兔场成员</TabsTrigger>
          <TabsTrigger value="merchant">商户账号</TabsTrigger>
        </TabsList>
        <TabsContent value="house">
          {!workspace.selectedHouse ? (
            <Empty>
              <WarehouseIcon aria-hidden="true" />
              <EmptyTitle>请选择兔场</EmptyTitle>
              <EmptyDescription>兔场角色只在当前兔场生效。</EmptyDescription>
            </Empty>
          ) : !isHouseAdmin ? (
            <Empty>
              <EmptyTitle>仅兔场所有者可管理成员</EmptyTitle>
              <EmptyDescription>管理员、生产人员和查看者不能修改成员范围。</EmptyDescription>
            </Empty>
          ) : (
            <MemberCard
              title="兔场成员"
              description="为已加入当前商户的账号分配兔场角色。"
              action={
                <HouseMemberDialog
                  state={houseDialog}
                  onOpenChange={(open) =>
                    setHouseDialog((current) => ({
                      open,
                      member: open ? current.member : null,
                    }))
                  }
                  houseId={workspace.selectedHouse.id}
                  onSaved={load}
                />
              }
            >
              <Table>
                <TableHeader><TableRow><TableHead>账号</TableHead><TableHead>角色</TableHead><TableHead>权限</TableHead><TableHead>加入时间</TableHead><TableHead className="text-right">操作</TableHead></TableRow></TableHeader>
                <TableBody>
                  {houseMembers.map((member) => (
                    <TableRow key={member.userId}>
                      <TableCell><div className="flex flex-col gap-1"><span className="font-medium">{member.userName}</span><span className="text-xs text-muted-foreground">ID {member.userId}</span></div></TableCell>
                      <TableCell><Badge variant={member.isAdmin ? 'default' : 'secondary'}>{houseRoleLabel(member.role)}</Badge></TableCell>
                      <TableCell>{permissionLabel(member.perms)}</TableCell>
                      <TableCell>{formatDate(member.joinTime)}</TableCell>
                      <TableCell><div className="flex justify-end gap-2"><Button variant="outline" size="sm" onClick={() => setHouseDialog({ open: true, member })}><Edit3Icon data-icon="inline-start" />编辑</Button><Button variant="destructive" size="sm" disabled={member.role === 'OWNER' || member.role === 'MERCHANT_OWNER' || member.userId === workspace.session.userId} onClick={() => setRemovalTarget({ scope: 'house', userId: member.userId, userName: member.userName })}><Trash2Icon data-icon="inline-start" />移除</Button></div></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </MemberCard>
          )}
        </TabsContent>

        <TabsContent value="merchant">
          {!workspace.selectedMerchant ? (
            <Empty><EmptyTitle>请选择商户</EmptyTitle><EmptyDescription>商户账号关系不会跨商户共享。</EmptyDescription></Empty>
          ) : !isMerchantOwner ? (
            <Empty>
              <EmptyTitle>仅商户所有者可管理商户账号</EmptyTitle>
              <EmptyDescription>商户管理员可以管理业务，但不能改变商户账号归属。</EmptyDescription>
            </Empty>
          ) : (
            <MemberCard
              title="商户账号"
              description="商户角色决定建场和账号治理能力。"
              action={
                <MerchantMemberDialog
                  state={merchantDialog}
                  onOpenChange={(open) =>
                    setMerchantDialog((current) => ({
                      open,
                      member: open ? current.member : null,
                    }))
                  }
                  merchantId={workspace.selectedMerchant.merchantId}
                  onSaved={load}
                />
              }
            >
              <Table>
                <TableHeader><TableRow><TableHead>账号</TableHead><TableHead>角色</TableHead><TableHead>状态</TableHead><TableHead>加入时间</TableHead><TableHead className="text-right">操作</TableHead></TableRow></TableHeader>
                <TableBody>
                  {merchantMembers.map((member) => (
                    <TableRow key={member.userId}>
                      <TableCell><div className="flex flex-col gap-1"><span className="font-medium">{member.userName}</span><span className="text-xs text-muted-foreground">{member.phoneMasked || `ID ${member.userId}`}</span></div></TableCell>
                      <TableCell><Badge variant={member.role === 'OWNER' ? 'default' : 'secondary'}>{merchantRoleLabel(member.role)}</Badge></TableCell>
                      <TableCell><Badge variant={member.status === 'ENABLED' ? 'default' : 'secondary'}>{member.status === 'ENABLED' ? '启用' : '停用'}</Badge></TableCell>
                      <TableCell>{formatDate(member.joinTime)}</TableCell>
                      <TableCell><div className="flex justify-end gap-2"><Button variant="outline" size="sm" onClick={() => setMerchantDialog({ open: true, member })}><Edit3Icon data-icon="inline-start" />编辑</Button><Button variant="destructive" size="sm" disabled={member.role === 'OWNER'} onClick={() => setRemovalTarget({ scope: 'merchant', userId: member.userId, userName: member.userName })}><Trash2Icon data-icon="inline-start" />移除</Button></div></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </MemberCard>
          )}
        </TabsContent>
      </Tabs>

      <RemoveMemberDialog
        target={removalTarget}
        merchantId={workspace.selectedMerchant?.merchantId ?? null}
        houseId={workspace.selectedHouse?.id ?? null}
        onOpenChange={(open) => !open && setRemovalTarget(null)}
        onRemoved={load}
      />
    </>
  )
}

function MemberCard({ title, description, action, children }: { title: string; description: string; action: React.ReactNode; children: React.ReactNode }) {
  return (
    <Card>
      <CardHeader className="sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-col gap-1.5"><CardTitle>{title}</CardTitle><CardDescription>{description}</CardDescription></div>
        {action}
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}

function MerchantMemberDialog({
  state,
  onOpenChange,
  merchantId,
  onSaved,
}: {
  state: { open: boolean; member: MerchantMember | null }
  onOpenChange: (open: boolean) => void
  merchantId: number | null
  onSaved: () => Promise<void>
}) {
  const [userName, setUserName] = useState('')
  const [role, setRole] = useState<MerchantRole>('MEMBER')
  const [status, setStatus] = useState<MembershipStatus>('ENABLED')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!state.open) return
    setUserName(state.member?.userName ?? '')
    setRole(state.member?.role ?? 'MEMBER')
    setStatus(state.member?.status ?? 'ENABLED')
  }, [state.member, state.open])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!merchantId) return
    setSaving(true)
    try {
      if (state.member) {
        await updateMerchantMember(merchantId, state.member.userId, { role, status })
        toast.success('商户账号权限已更新')
      } else {
        await addMerchantMember(merchantId, {
          userName: userName.trim(),
          role: role === 'ADMIN' ? 'ADMIN' : 'MEMBER',
        })
        toast.success('账号已加入商户')
      }
      onOpenChange(false)
      await onSaved()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }

  const content = (
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{state.member ? '编辑商户账号' : '添加商户账号'}</DialogTitle>
        <DialogDescription>{state.member ? '设为所有者将转让商户所有权。' : '账号必须已在 Rabbit 注册。'}</DialogDescription>
      </DialogHeader>
      <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
        <FieldGroup>
          <Field><FieldLabel htmlFor="merchant-member-name">用户名</FieldLabel><Input id="merchant-member-name" value={userName} disabled={Boolean(state.member)} required onChange={(event) => setUserName(event.target.value)} /></Field>
          <Field><FieldLabel htmlFor="merchant-member-role">商户角色</FieldLabel><Select value={role} onValueChange={(value) => setRole(value as MerchantRole)}><SelectTrigger id="merchant-member-role"><SelectValue /></SelectTrigger><SelectContent><SelectGroup>{state.member ? <SelectItem value="OWNER">所有者</SelectItem> : null}<SelectItem value="ADMIN">管理员</SelectItem><SelectItem value="MEMBER">成员</SelectItem></SelectGroup></SelectContent></Select></Field>
          {state.member ? <Field><FieldLabel htmlFor="merchant-member-status">状态</FieldLabel><Select value={status} onValueChange={(value) => setStatus(value as MembershipStatus)}><SelectTrigger id="merchant-member-status"><SelectValue /></SelectTrigger><SelectContent><SelectGroup><SelectItem value="ENABLED">启用</SelectItem><SelectItem value="DISABLED">停用</SelectItem></SelectGroup></SelectContent></Select></Field> : null}
        </FieldGroup>
        <DialogFooter><Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button><Button type="submit" disabled={saving}>{saving ? <Spinner data-icon="inline-start" /> : null}保存</Button></DialogFooter>
      </form>
    </DialogContent>
  )

  return (
    <Dialog open={state.open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild><Button><PlusIcon data-icon="inline-start" />添加账号</Button></DialogTrigger>
      {content}
    </Dialog>
  )
}

function HouseMemberDialog({
  state,
  onOpenChange,
  houseId,
  onSaved,
}: {
  state: { open: boolean; member: HouseMember | null }
  onOpenChange: (open: boolean) => void
  houseId: number | null
  onSaved: () => Promise<void>
}) {
  const [userName, setUserName] = useState('')
  const [role, setRole] = useState<Exclude<HouseRole, 'MERCHANT_OWNER'>>('STAFF')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!state.open) return
    setUserName(state.member?.userName ?? '')
    setRole(state.member?.role === 'MERCHANT_OWNER' ? 'OWNER' : state.member?.role ?? 'STAFF')
  }, [state.member, state.open])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!houseId) return
    setSaving(true)
    try {
      if (state.member) {
        await updateHouseMember(houseId, state.member.userId, role)
        toast.success('兔场成员角色已更新')
      } else {
        await addHouseMember(houseId, {
          userName: userName.trim(),
          role: role === 'MANAGER' ? 'MANAGER' : role === 'VIEWER' ? 'VIEWER' : 'STAFF',
        })
        toast.success('成员已加入兔场')
      }
      onOpenChange(false)
      await onSaved()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={state.open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild><Button><PlusIcon data-icon="inline-start" />添加成员</Button></DialogTrigger>
      <DialogContent>
        <DialogHeader><DialogTitle>{state.member ? '编辑兔场成员' : '添加兔场成员'}</DialogTitle><DialogDescription>{state.member ? '设为所有者将转让兔场所有权。' : '只能添加已加入当前商户的账号。'}</DialogDescription></DialogHeader>
        <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup>
            <Field><FieldLabel htmlFor="house-member-name">用户名</FieldLabel><Input id="house-member-name" value={userName} disabled={Boolean(state.member)} required onChange={(event) => setUserName(event.target.value)} /></Field>
            <Field><FieldLabel htmlFor="house-member-role">兔场角色</FieldLabel><Select value={role} onValueChange={(value) => setRole(value as Exclude<HouseRole, 'MERCHANT_OWNER'>)}><SelectTrigger id="house-member-role"><SelectValue /></SelectTrigger><SelectContent><SelectGroup>{state.member ? <SelectItem value="OWNER">所有者</SelectItem> : null}<SelectItem value="MANAGER">管理员</SelectItem><SelectItem value="STAFF">生产人员</SelectItem><SelectItem value="VIEWER">查看者</SelectItem></SelectGroup></SelectContent></Select></Field>
          </FieldGroup>
          <DialogFooter><Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button><Button type="submit" disabled={saving}>{saving ? <Spinner data-icon="inline-start" /> : null}保存</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function RemoveMemberDialog({
  target,
  merchantId,
  houseId,
  onOpenChange,
  onRemoved,
}: {
  target: RemovalTarget
  merchantId: number | null
  houseId: number | null
  onOpenChange: (open: boolean) => void
  onRemoved: () => Promise<void>
}) {
  const [saving, setSaving] = useState(false)
  async function handleRemove() {
    if (!target) return
    setSaving(true)
    try {
      if (target.scope === 'merchant' && merchantId) {
        await removeMerchantMember(merchantId, target.userId)
      } else if (target.scope === 'house' && houseId) {
        await removeHouseMember(houseId, target.userId)
      }
      toast.success(target.scope === 'merchant' ? '账号已移出商户' : '成员已移出兔场')
      onOpenChange(false)
      await onRemoved()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }
  return (
    <Dialog open={Boolean(target)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader><DialogTitle>确认移除成员</DialogTitle><DialogDescription>将“{target?.userName ?? ''}”移出{target?.scope === 'merchant' ? '当前商户' : '当前兔场'}。</DialogDescription></DialogHeader>
        <DialogFooter><Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button><Button variant="destructive" disabled={saving} onClick={() => void handleRemove()}>{saving ? <Spinner data-icon="inline-start" /> : <Trash2Icon data-icon="inline-start" />}确认移除</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function permissionLabel(perms: HouseMember['perms']) {
  if (perms === 'control') return '控制与维护'
  if (perms === 'edit') return '生产维护'
  return '仅查看'
}

function formatDate(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleDateString('zh-CN')
}
