import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import {
  Building2Icon,
  ChevronLeftIcon,
  DoorOpenIcon,
  PencilIcon,
  RabbitIcon,
  ShieldAlertIcon,
  UserPlusIcon,
  UsersIcon,
} from 'lucide-react'
import { addMerchantUser, getMerchant, getMerchantOverview, listMerchantUsers, removeMerchantUser, updateMerchantStatus } from '@/api/merchants'
import { MerchantFormDialog } from '@/components/merchant-form-dialog'
import { MetricCard } from '@/components/metric-card'
import { PageHeader } from '@/components/page-header'
import { StatusBadge } from '@/components/status-badge'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import type { Merchant, MerchantOverview, MerchantUser } from '@/types/api'

export function MerchantDetailPage() {
  const params = useParams()
  const navigate = useNavigate()
  const merchantId = Number(params.merchantId)
  const [merchant, setMerchant] = useState<Merchant | null>(null)
  const [users, setUsers] = useState<MerchantUser[]>([])
  const [overview, setOverview] = useState<MerchantOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [editOpen, setEditOpen] = useState(false)
  const [bindOpen, setBindOpen] = useState(false)
  const [statusDialogOpen, setStatusDialogOpen] = useState(false)
  const [removeUser, setRemoveUser] = useState<MerchantUser | null>(null)
  const [userIdInput, setUserIdInput] = useState('')
  const [bindSubmitted, setBindSubmitted] = useState(false)
  const [saving, setSaving] = useState(false)

  const bindUserId = Number(userIdInput)
  const bindUserIdInvalid =
    bindSubmitted && (!Number.isFinite(bindUserId) || bindUserId <= 0)

  const loadDetail = useCallback(async () => {
    if (!Number.isFinite(merchantId)) {
      navigate('/merchants', { replace: true })
      return
    }
    setLoading(true)
    try {
      const [merchantResult, usersResult, overviewResult] = await Promise.all([
        getMerchant(merchantId),
        listMerchantUsers(merchantId),
        getMerchantOverview(merchantId),
      ])
      setMerchant(merchantResult)
      setUsers(usersResult)
      setOverview(overviewResult)
    } finally {
      setLoading(false)
    }
  }, [merchantId, navigate])

  useEffect(() => {
    void loadDetail()
  }, [loadDetail])

  async function handleToggleStatus() {
    if (!merchant) {
      return
    }
    setSaving(true)
    try {
      const nextStatus = merchant.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
      const updated = await updateMerchantStatus(merchant.id, nextStatus)
      setMerchant(updated)
      toast.success(nextStatus === 'ENABLED' ? '商户已启用' : '商户已停用')
      setStatusDialogOpen(false)
    } finally {
      setSaving(false)
    }
  }

  async function handleBindUser(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBindSubmitted(true)
    if (!Number.isFinite(bindUserId) || bindUserId <= 0) {
      toast.error('请输入有效的用户 ID')
      return
    }
    setSaving(true)
    try {
      await addMerchantUser(merchantId, bindUserId)
      toast.success('用户已绑定')
      setBindOpen(false)
      setUserIdInput('')
      setBindSubmitted(false)
      await loadDetail()
    } finally {
      setSaving(false)
    }
  }

  async function handleRemoveUser(userId: number) {
    setSaving(true)
    try {
      await removeMerchantUser(merchantId, userId)
      toast.success('用户已解绑')
      setRemoveUser(null)
      await loadDetail()
    } finally {
      setSaving(false)
    }
  }

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

  if (!merchant) {
    return (
      <Empty>
        <EmptyTitle>商户不存在</EmptyTitle>
        <EmptyDescription>返回商户列表后重新选择。</EmptyDescription>
        <Button asChild>
          <Link to="/merchants">返回列表</Link>
        </Button>
      </Empty>
    )
  }

  return (
    <>
      <PageHeader
        title={merchant.name}
        description={`商户 ID ${merchant.id}，联系人 ${merchant.contactName || '-'}，电话 ${merchant.contactPhone || '-'}`}
        actions={
          <>
            <Button variant="outline" asChild>
              <Link to="/merchants">
                <ChevronLeftIcon data-icon="inline-start" />
                返回列表
              </Link>
            </Button>
            <StatusBadge status={merchant.status} />
            <Button variant="outline" onClick={() => setEditOpen(true)}>
              <PencilIcon data-icon="inline-start" />
              编辑
            </Button>
            <Button
              variant={merchant.status === 'ENABLED' ? 'destructive' : 'default'}
              disabled={saving}
              onClick={() => setStatusDialogOpen(true)}
            >
              <ShieldAlertIcon data-icon="inline-start" />
              {merchant.status === 'ENABLED' ? '停用商户' : '启用商户'}
            </Button>
          </>
        }
      />

      {overview ? (
        <div className="grid gap-4 md:grid-cols-4">
          <MetricCard title="兔舍" value={overview.houseCount} icon={Building2Icon} />
          <MetricCard title="商户用户" value={overview.userCount} icon={UsersIcon} />
          <MetricCard title="笼位" value={overview.cageCount} icon={DoorOpenIcon} />
          <MetricCard title="兔只" value={overview.rabbitCount} icon={RabbitIcon} />
        </div>
      ) : null}

      <Tabs defaultValue="overview">
        <TabsList className="max-w-full overflow-x-auto">
          <TabsTrigger value="overview">数据概览</TabsTrigger>
          <TabsTrigger value="users">商户用户</TabsTrigger>
          <TabsTrigger value="audit">最近审计</TabsTrigger>
        </TabsList>
        <TabsContent value="overview">
          <Card>
            <CardHeader>
              <CardTitle>兔舍概览</CardTitle>
              <CardDescription>展示商户下最近创建的兔舍。</CardDescription>
            </CardHeader>
            <CardContent>
              {overview && overview.houses.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>兔舍</TableHead>
                      <TableHead>布局</TableHead>
                      <TableHead>创建时间</TableHead>
                      <TableHead>备注</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {overview.houses.map((house) => (
                      <TableRow key={house.id}>
                        <TableCell>
                          <div className="min-w-44">
                            <p className="truncate font-medium">{house.name}</p>
                            <p className="text-xs text-muted-foreground">ID {house.id}</p>
                          </div>
                        </TableCell>
                        <TableCell className="whitespace-nowrap">
                          {house.layoutRows} 排 / {house.layoutCols} 列 /{' '}
                          {house.layoutLayers} 层
                        </TableCell>
                        <TableCell className="whitespace-nowrap">
                          {formatTime(house.createTime)}
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          <span className="line-clamp-2 min-w-48">{house.remark || '-'}</span>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <Empty>
                  <EmptyTitle>暂无兔舍</EmptyTitle>
                  <EmptyDescription>
                    商户绑定用户后，业务端创建的兔舍会出现在这里。
                  </EmptyDescription>
                </Empty>
              )}
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="users">
          <Card>
            <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle>商户用户</CardTitle>
                <CardDescription>通过用户 ID 绑定已有业务用户。</CardDescription>
              </div>
              <Button onClick={() => setBindOpen(true)}>
                <UserPlusIcon data-icon="inline-start" />
                绑定用户
              </Button>
            </CardHeader>
            <CardContent>
              {users.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>用户</TableHead>
                      <TableHead>OpenID</TableHead>
                      <TableHead>绑定时间</TableHead>
                      <TableHead className="text-right">操作</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {users.map((user) => (
                      <TableRow key={user.userId}>
                        <TableCell>
                          <div className="min-w-40">
                            <p className="truncate font-medium">{user.userName}</p>
                            <p className="text-xs text-muted-foreground">
                              ID {user.userId}
                            </p>
                          </div>
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          <span className="block max-w-72 truncate">{user.openid || '-'}</span>
                        </TableCell>
                        <TableCell className="whitespace-nowrap">
                          {formatTime(user.bindTime)}
                        </TableCell>
                        <TableCell className="text-right">
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={saving}
                            onClick={() => setRemoveUser(user)}
                          >
                            解绑
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <Empty>
                  <EmptyTitle>暂无绑定用户</EmptyTitle>
                  <EmptyDescription>
                    绑定用户后，新建兔舍会自动归属当前商户。
                  </EmptyDescription>
                </Empty>
              )}
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="audit">
          <Card>
            <CardHeader>
              <CardTitle>最近审计</CardTitle>
              <CardDescription>展示商户兔舍下最近的接口访问记录。</CardDescription>
            </CardHeader>
            <CardContent>
              {overview && overview.recentAuditLogs.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>时间</TableHead>
                      <TableHead>接口</TableHead>
                      <TableHead>状态</TableHead>
                      <TableHead>耗时</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {overview.recentAuditLogs.map((log) => (
                      <TableRow key={log.id}>
                        <TableCell className="whitespace-nowrap">
                          {formatTime(log.createTime)}
                        </TableCell>
                        <TableCell>
                          <div className="min-w-72">
                            <span className="font-medium">{log.method || '-'}</span>{' '}
                            <span className="text-muted-foreground">{log.path || '-'}</span>
                            {log.apiMessage ? (
                              <p className="truncate text-xs text-muted-foreground">
                                {log.apiMessage}
                              </p>
                            ) : null}
                          </div>
                        </TableCell>
                        <TableCell>{renderAuditStatus(log)}</TableCell>
                        <TableCell className="whitespace-nowrap">
                          {formatCost(log.costMs)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <Empty>
                  <EmptyTitle>暂无审计记录</EmptyTitle>
                  <EmptyDescription>
                    商户用户使用客户端后，相关记录会出现在这里。
                  </EmptyDescription>
                </Empty>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <MerchantFormDialog
        open={editOpen}
        merchant={merchant}
        onOpenChange={setEditOpen}
        onSaved={(saved) => setMerchant(saved)}
      />

      <Dialog
        open={bindOpen}
        onOpenChange={(open) => {
          setBindOpen(open)
          if (open) {
            setBindSubmitted(false)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>绑定商户用户</DialogTitle>
            <DialogDescription>
              输入已有业务用户 ID。用户注册仍通过客户端或现有接口完成。
            </DialogDescription>
          </DialogHeader>
          <form className="flex flex-col gap-5" onSubmit={handleBindUser}>
            <FieldGroup>
              <Field data-invalid={bindUserIdInvalid ? true : undefined}>
                <FieldLabel htmlFor="bind-user-id">用户 ID</FieldLabel>
                <Input
                  id="bind-user-id"
                  inputMode="numeric"
                  value={userIdInput}
                  aria-invalid={bindUserIdInvalid ? true : undefined}
                  onChange={(event) => setUserIdInput(event.target.value)}
                />
                {bindUserIdInvalid ? (
                  <FieldDescription className="text-destructive">
                    请输入大于 0 的用户 ID。
                  </FieldDescription>
                ) : (
                  <FieldDescription>仅支持绑定已经存在的业务用户。</FieldDescription>
                )}
              </Field>
            </FieldGroup>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setBindOpen(false)}
              >
                取消
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? <Spinner /> : null}
                绑定
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={statusDialogOpen} onOpenChange={setStatusDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {merchant.status === 'ENABLED' ? '停用商户' : '启用商户'}
            </DialogTitle>
            <DialogDescription>
              {merchant.status === 'ENABLED'
                ? '停用后，该商户下业务用户将不能继续使用商户相关能力。业务数据不会被删除。'
                : '启用后，该商户下业务用户可以继续使用商户相关能力。'}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setStatusDialogOpen(false)}
            >
              取消
            </Button>
            <Button
              variant={merchant.status === 'ENABLED' ? 'destructive' : 'default'}
              disabled={saving}
              onClick={() => void handleToggleStatus()}
            >
              {saving ? <Spinner /> : <ShieldAlertIcon data-icon="inline-start" />}
              {merchant.status === 'ENABLED' ? '确认停用' : '确认启用'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(removeUser)} onOpenChange={(open) => !open && setRemoveUser(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>解绑商户用户</DialogTitle>
            <DialogDescription>
              解绑后，该用户不再归属当前商户。用户账号本身不会被删除。
            </DialogDescription>
          </DialogHeader>
          {removeUser ? (
            <div className="rounded-md border bg-secondary p-3 text-sm">
              <p className="font-medium">{removeUser.userName}</p>
              <p className="text-muted-foreground">ID {removeUser.userId}</p>
            </div>
          ) : null}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setRemoveUser(null)}
            >
              取消
            </Button>
            <Button
              variant="destructive"
              disabled={saving || !removeUser}
              onClick={() => removeUser && void handleRemoveUser(removeUser.userId)}
            >
              {saving ? <Spinner /> : null}
              确认解绑
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

function formatTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString()
}

function formatCost(value?: number | null) {
  if (value == null) {
    return '-'
  }
  return `${value} ms`
}

function renderAuditStatus(log: { apiCode?: number | null; status?: number | null }) {
  if (log.apiCode != null) {
    return log.apiCode === 0 ? (
      <Badge>成功</Badge>
    ) : (
      <Badge variant="secondary">业务码 {log.apiCode}</Badge>
    )
  }
  if (log.status == null) {
    return '-'
  }
  return log.status >= 200 && log.status < 400 ? (
    <Badge>HTTP {log.status}</Badge>
  ) : (
    <Badge variant="secondary">HTTP {log.status}</Badge>
  )
}
