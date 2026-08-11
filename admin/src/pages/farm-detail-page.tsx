import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import {
  ArrowLeftIcon,
  DoorOpenIcon,
  PencilIcon,
  PlusIcon,
  RabbitIcon,
  Rows3Icon,
  ShieldAlertIcon,
  UsersIcon,
} from 'lucide-react'
import {
  addFarmMember,
  getFarmOverview,
  listFarmMembers,
  updateFarm,
  updateFarmStatus,
} from '@/api/farms'
import { FarmStatusBadge } from '@/components/farm-status-badge'
import { MetricCard } from '@/components/metric-card'
import { PageHeader } from '@/components/page-header'
import { houseRoleLabel } from '@/lib/permission-labels'
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import type { AdminFarmMember, FarmOverview, FarmStatus, HouseRole } from '@/types/api'

export function FarmDetailPage() {
  const params = useParams()
  const navigate = useNavigate()
  const farmId = Number(params.farmId)
  const [overview, setOverview] = useState<FarmOverview | null>(null)
  const [members, setMembers] = useState<AdminFarmMember[]>([])
  const [loading, setLoading] = useState(true)
  const [statusOpen, setStatusOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [memberOpen, setMemberOpen] = useState(false)

  const load = useCallback(async () => {
    if (!Number.isFinite(farmId)) {
      navigate('/farms', { replace: true })
      return
    }
    setLoading(true)
    try {
      const [nextOverview, nextMembers] = await Promise.all([
        getFarmOverview(farmId),
        listFarmMembers(farmId),
      ])
      setOverview(nextOverview)
      setMembers(nextMembers)
    } catch {
      setOverview(null)
      setMembers([])
    } finally {
      setLoading(false)
    }
  }, [farmId, navigate])

  useEffect(() => {
    void load()
  }, [load])

  const enabledOwners = useMemo(
    () => members.filter((member) => member.role === 'OWNER' && member.status !== 'DISABLED'),
    [members],
  )

  if (loading) {
    return (
      <div className="motion-page flex flex-col gap-4">
        <Skeleton className="h-20 w-full" />
        <div className="grid gap-4 md:grid-cols-4">
          <Skeleton className="h-28 w-full" />
          <Skeleton className="h-28 w-full" />
          <Skeleton className="h-28 w-full" />
          <Skeleton className="h-28 w-full" />
        </div>
      </div>
    )
  }

  if (!overview) {
    return (
      <Empty>
        <EmptyTitle>兔场不存在</EmptyTitle>
        <EmptyDescription>返回兔场列表后重新选择。</EmptyDescription>
        <Button asChild><Link to="/farms">返回列表</Link></Button>
      </Empty>
    )
  }

  const farm = overview.farm

  return (
    <>
      <PageHeader
        title={farm.name}
        description={`兔场 ID ${farm.id} · ${farm.ownerNames?.length ? `所有者 ${farm.ownerNames.join('、')}` : '尚无有效所有者'}`}
        actions={
          <>
            <Button variant="outline" asChild>
              <Link to="/farms"><ArrowLeftIcon data-icon="inline-start" />返回列表</Link>
            </Button>
            <FarmStatusBadge status={farm.status} />
            <Button variant="outline" onClick={() => setEditOpen(true)}>
              <PencilIcon data-icon="inline-start" />编辑
            </Button>
            <Button
              variant={farm.status === 'ENABLED' ? 'destructive' : 'default'}
              disabled={farm.status !== 'ENABLED' && enabledOwners.length === 0}
              onClick={() => setStatusOpen(true)}
            >
              <ShieldAlertIcon data-icon="inline-start" />
              {farm.status === 'ENABLED' ? '停用兔场' : '启用兔场'}
            </Button>
          </>
        }
      />

      {farm.status === 'ORPHANED' ? (
        <div className="flex flex-col gap-3 border-l-4 border-destructive bg-secondary p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm font-medium">兔场缺少有效所有者</p>
            <p className="mt-1 text-sm text-muted-foreground">先添加至少一名所有者，再显式启用兔场。</p>
          </div>
          <Button onClick={() => setMemberOpen(true)}>
            <PlusIcon data-icon="inline-start" />指定所有者
          </Button>
        </div>
      ) : null}

      <div className="grid gap-4 md:grid-cols-4">
        <MetricCard title="成员" value={overview.memberCount} icon={UsersIcon} />
        <MetricCard title="笼位" value={overview.cageCount} icon={DoorOpenIcon} />
        <MetricCard title="兔只" value={overview.rabbitCount} icon={RabbitIcon} />
        <MetricCard title="生产批次" value={overview.batchCount} icon={Rows3Icon} />
      </div>

      <Tabs defaultValue="members">
        <TabsList className="max-w-full overflow-x-auto">
          <TabsTrigger value="members">兔场成员</TabsTrigger>
          <TabsTrigger value="profile">兔场资料</TabsTrigger>
          <TabsTrigger value="audit">最近审计</TabsTrigger>
        </TabsList>
        <TabsContent value="members">
          <Card>
            <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle>兔场成员</CardTitle>
                <CardDescription>成员关系是兔场访问授权的唯一依据。</CardDescription>
              </div>
              <AddFarmMemberDialog
                open={memberOpen}
                farmId={farm.id}
                defaultRole={farm.status === 'ORPHANED' ? 'OWNER' : 'STAFF'}
                onOpenChange={setMemberOpen}
                onSaved={load}
              />
            </CardHeader>
            <CardContent>
              {members.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>用户</TableHead>
                      <TableHead>手机号</TableHead>
                      <TableHead>角色</TableHead>
                      <TableHead>状态</TableHead>
                      <TableHead>加入时间</TableHead>
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
                        <TableCell className="whitespace-nowrap text-muted-foreground">{formatTime(member.joinTime)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <Empty>
                  <EmptyTitle>暂无成员</EmptyTitle>
                  <EmptyDescription>添加所有者后才能启用并正常访问此兔场。</EmptyDescription>
                </Empty>
              )}
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="profile">
          <Card>
            <CardHeader>
              <CardTitle>兔场资料</CardTitle>
              <CardDescription>名称和备注可维护，布局及生产数据保持只读。</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <ProfileItem label="布局" value={formatLayout(farm)} />
              <ProfileItem label="所有者数量" value={String(enabledOwners.length)} />
              <ProfileItem label="创建时间" value={formatTime(farm.createTime)} />
              <ProfileItem label="最近更新" value={formatTime(farm.updateTime)} />
              <div className="sm:col-span-2 lg:col-span-4">
                <ProfileItem label="备注" value={farm.remark || '-'} />
              </div>
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="audit">
          <Card>
            <CardHeader>
              <CardTitle>最近审计</CardTitle>
              <CardDescription>展示当前兔场最近的接口访问记录。</CardDescription>
            </CardHeader>
            <CardContent>
              {overview.recentAuditLogs.length > 0 ? (
                <Table>
                  <TableHeader><TableRow><TableHead>时间</TableHead><TableHead>用户</TableHead><TableHead>请求</TableHead><TableHead>结果</TableHead><TableHead>耗时</TableHead></TableRow></TableHeader>
                  <TableBody>
                    {overview.recentAuditLogs.map((log) => (
                      <TableRow key={log.id}>
                        <TableCell className="whitespace-nowrap">{formatTime(log.createTime)}</TableCell>
                        <TableCell>{log.userId ?? '-'}</TableCell>
                        <TableCell><span className="block max-w-96 truncate">{log.method || '-'} {log.path || '-'}</span></TableCell>
                        <TableCell>{log.apiCode ?? log.status ?? '-'}</TableCell>
                        <TableCell>{log.costMs == null ? '-' : `${log.costMs} ms`}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <Empty><EmptyTitle>暂无审计记录</EmptyTitle><EmptyDescription>兔场产生接口访问后会显示在这里。</EmptyDescription></Empty>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <FarmStatusDialog
        open={statusOpen}
        farmName={farm.name}
        farmId={farm.id}
        currentStatus={farm.status}
        onOpenChange={setStatusOpen}
        onSaved={load}
      />
      <FarmEditDialog
        open={editOpen}
        farm={farm}
        onOpenChange={setEditOpen}
        onSaved={load}
      />
    </>
  )
}

function FarmEditDialog({
  open,
  farm,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  farm: FarmOverview['farm']
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [name, setName] = useState(farm.name)
  const [remark, setRemark] = useState(farm.remark || '')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setName(farm.name)
      setRemark(farm.remark || '')
    }
  }, [farm.name, farm.remark, open])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    try {
      await updateFarm(farm.id, {
        name: name.trim(),
        remark: remark.trim() || undefined,
      })
      toast.success('兔场资料已更新')
      onOpenChange(false)
      await onSaved()
    } catch {
      // Keep the dialog open; the shared request layer shows the server reason.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>编辑兔场</DialogTitle>
          <DialogDescription>更新兔场名称和备注。</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="edit-farm-name">兔场名称</FieldLabel>
              <Input id="edit-farm-name" maxLength={100} value={name} required onChange={(event) => setName(event.target.value)} />
            </Field>
            <Field>
              <FieldLabel htmlFor="edit-farm-remark">备注</FieldLabel>
              <Textarea id="edit-farm-remark" maxLength={1000} value={remark} onChange={(event) => setRemark(event.target.value)} />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
            <Button type="submit" disabled={saving}>
              {saving ? <Spinner data-icon="inline-start" /> : null}保存
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function AddFarmMemberDialog({
  open,
  farmId,
  defaultRole,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  farmId: number
  defaultRole: HouseRole
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<HouseRole>(defaultRole)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setUserId('')
      setRole(defaultRole)
    }
  }, [defaultRole, open])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const parsedUserId = Number(userId)
    if (!Number.isInteger(parsedUserId) || parsedUserId <= 0) {
      toast.error('请输入有效的用户 ID')
      return
    }
    setSaving(true)
    try {
      await addFarmMember(farmId, { userId: parsedUserId, role })
      toast.success('兔场成员已添加')
      onOpenChange(false)
      await onSaved()
    } catch {
      // Keep the dialog open; the shared request layer shows the server reason.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild><Button><PlusIcon data-icon="inline-start" />添加成员</Button></DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>添加兔场成员</DialogTitle>
          <DialogDescription>按业务用户 ID 建立直接兔场成员关系。</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="farm-member-user-id">用户 ID</FieldLabel>
              <Input id="farm-member-user-id" type="number" min={1} value={userId} required onChange={(event) => setUserId(event.target.value)} />
              <FieldDescription>可在业务用户列表中查询用户 ID。</FieldDescription>
            </Field>
            <Field>
              <FieldLabel htmlFor="farm-member-role">兔场角色</FieldLabel>
              <Select value={role} onValueChange={(value) => setRole(value as HouseRole)}>
                <SelectTrigger id="farm-member-role"><SelectValue /></SelectTrigger>
                <SelectContent><SelectGroup><SelectItem value="OWNER">所有者</SelectItem><SelectItem value="MANAGER">管理员</SelectItem><SelectItem value="STAFF">生产人员</SelectItem><SelectItem value="VIEWER">查看者</SelectItem></SelectGroup></SelectContent>
              </Select>
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
            <Button type="submit" disabled={saving}>{saving ? <Spinner data-icon="inline-start" /> : null}添加</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function FarmStatusDialog({
  open,
  farmName,
  farmId,
  currentStatus,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  farmName: string
  farmId: number
  currentStatus: FarmStatus
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [saving, setSaving] = useState(false)
  const nextStatus: FarmStatus = currentStatus === 'ENABLED' ? 'SUSPENDED' : 'ENABLED'

  async function handleSave() {
    setSaving(true)
    try {
      await updateFarmStatus(farmId, nextStatus)
      toast.success(nextStatus === 'ENABLED' ? '兔场已启用' : '兔场已停用')
      onOpenChange(false)
      await onSaved()
    } catch {
      // Keep the dialog open; the shared request layer shows the server reason.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{nextStatus === 'ENABLED' ? '启用兔场' : '停用兔场'}</DialogTitle>
          <DialogDescription>
            {nextStatus === 'ENABLED'
              ? `启用“${farmName}”后，有效成员可以继续访问。`
              : `停用“${farmName}”后，所有成员将暂时无法访问，业务数据不会删除。`}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button variant={nextStatus === 'ENABLED' ? 'default' : 'destructive'} disabled={saving} onClick={() => void handleSave()}>
            {saving ? <Spinner data-icon="inline-start" /> : null}
            确认{nextStatus === 'ENABLED' ? '启用' : '停用'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function ProfileItem({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0"><p className="text-xs text-muted-foreground">{label}</p><p className="mt-1 break-words text-sm font-medium">{value}</p></div>
}

function formatLayout(farm: FarmOverview['farm']) {
  if (farm.layoutRows == null || farm.layoutCols == null || farm.layoutLayers == null) return '-'
  return `${farm.layoutRows} 排 / ${farm.layoutCols} 列 / ${farm.layoutLayers} 层`
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN')
}
