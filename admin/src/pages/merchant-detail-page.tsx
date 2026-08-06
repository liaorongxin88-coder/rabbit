import { type ReactNode, useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import {
  Building2Icon,
  ChevronLeftIcon,
  DoorOpenIcon,
  KeyRoundIcon,
  PencilIcon,
  RabbitIcon,
  Settings2Icon,
  ShieldAlertIcon,
  ShieldCheckIcon,
  UsersIcon,
} from 'lucide-react'
import {
  getMerchant,
  getMerchantHousePolicy,
  getMerchantOverview,
  listMerchantAccounts,
  updateMerchantStatus,
} from '@/api/merchants'
import { MerchantFormDialog } from '@/components/merchant-form-dialog'
import { MerchantAccountCreateDialog } from '@/components/merchant-account-create-dialog'
import { MerchantHousePolicyDialog } from '@/components/merchant-house-policy-dialog'
import { MerchantMembershipDialog } from '@/components/merchant-membership-dialog'
import { MetricCard } from '@/components/metric-card'
import { PageHeader } from '@/components/page-header'
import { StatusBadge } from '@/components/status-badge'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Spinner } from '@/components/ui/spinner'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import type {
  Merchant,
  MerchantAccountSummary,
  MerchantHousePolicy,
  MerchantOverview,
  MerchantRole,
} from '@/types/api'

export function MerchantDetailPage() {
  const params = useParams()
  const navigate = useNavigate()
  const merchantId = Number(params.merchantId)
  const [merchant, setMerchant] = useState<Merchant | null>(null)
  const [users, setUsers] = useState<MerchantAccountSummary[]>([])
  const [overview, setOverview] = useState<MerchantOverview | null>(null)
  const [policy, setPolicy] = useState<MerchantHousePolicy | null>(null)
  const [loading, setLoading] = useState(true)
  const [editOpen, setEditOpen] = useState(false)
  const [createAccountOpen, setCreateAccountOpen] = useState(false)
  const [statusDialogOpen, setStatusDialogOpen] = useState(false)
  const [policyDialogOpen, setPolicyDialogOpen] = useState(false)
  const [membershipDialogOpen, setMembershipDialogOpen] = useState(false)
  const [selectedAccount, setSelectedAccount] = useState<MerchantAccountSummary | null>(null)
  const [saving, setSaving] = useState(false)

  const loadDetail = useCallback(async () => {
    if (!Number.isFinite(merchantId)) {
      navigate('/merchants', { replace: true })
      return
    }
    setLoading(true)
    try {
      const [merchantResult, usersResult, overviewResult, policyResult] = await Promise.all([
        getMerchant(merchantId),
        listMerchantAccounts(merchantId),
        getMerchantOverview(merchantId),
        getMerchantHousePolicy(merchantId),
      ])
      setMerchant(merchantResult)
      setUsers(usersResult)
      setOverview(overviewResult)
      setPolicy(policyResult)
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

  function openMembershipDialog(account: MerchantAccountSummary) {
    setSelectedAccount(account)
    setMembershipDialogOpen(true)
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
          <MetricCard title="商户账号" value={overview.userCount} icon={UsersIcon} />
          <MetricCard title="笼位" value={overview.cageCount} icon={DoorOpenIcon} />
          <MetricCard title="兔只" value={overview.rabbitCount} icon={RabbitIcon} />
        </div>
      ) : null}

      <Tabs defaultValue="overview">
        <TabsList className="max-w-full overflow-x-auto">
          <TabsTrigger value="overview">数据概览</TabsTrigger>
          <TabsTrigger value="users">商户账号</TabsTrigger>
          <TabsTrigger value="permissions">兔场权限</TabsTrigger>
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
                    商户账号在业务端创建兔舍后，相关数据会出现在这里。
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
                <CardTitle>商户账号</CardTitle>
                <CardDescription>
                  管理账号在当前商户内的角色和状态；同一账号可加入多个商户。
                </CardDescription>
              </div>
              <Button onClick={() => setCreateAccountOpen(true)}>
                <KeyRoundIcon data-icon="inline-start" />
                新增账号
              </Button>
            </CardHeader>
            <CardContent>
              {users.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>用户</TableHead>
                      <TableHead>角色</TableHead>
                      <TableHead>状态</TableHead>
                      <TableHead>账号标识</TableHead>
                      <TableHead>创建时间</TableHead>
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
                        <TableCell>
                          <Badge variant={user.role === 'OWNER' ? 'default' : 'secondary'}>
                            {merchantRoleLabel(user.role)}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant={user.membershipStatus === 'ENABLED' ? 'secondary' : 'outline'}>
                            {user.membershipStatus === 'ENABLED' ? '启用' : '停用'}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          <span className="block max-w-72 truncate">
                            {user.phoneMasked || user.openid || '-'}
                          </span>
                        </TableCell>
                        <TableCell className="whitespace-nowrap">
                          {formatTime(user.createTime)}
                        </TableCell>
                        <TableCell className="text-right">
                          <Button variant="outline" size="sm" onClick={() => openMembershipDialog(user)}>
                            <ShieldCheckIcon data-icon="inline-start" />
                            配置权限
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <Empty>
                  <EmptyTitle>暂无商户账号</EmptyTitle>
                  <EmptyDescription>
                    新增账号后，会为该账号创建当前商户的成员关系。
                  </EmptyDescription>
                </Empty>
              )}
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="permissions">
          <Card>
            <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle>兔场管理权限</CardTitle>
                <CardDescription>平台为当前商户配置建场、成员维护和容量边界。</CardDescription>
              </div>
              <Button variant="outline" disabled={!policy} onClick={() => setPolicyDialogOpen(true)}>
                <Settings2Icon data-icon="inline-start" />
                配置权限
              </Button>
            </CardHeader>
            <CardContent>
              {policy ? (
                <>
                  <dl className="grid grid-cols-2 gap-x-5 gap-y-5 sm:hidden">
                    <PolicySummary label="创建兔场">
                      {permissionBadge(policy.houseCreationEnabled)}
                    </PolicySummary>
                    <PolicySummary label="管理兔场成员">
                      {permissionBadge(policy.houseMemberManagementEnabled)}
                    </PolicySummary>
                    <PolicySummary label="兔场上限">
                      {policy.maxHouseCount}
                    </PolicySummary>
                    <PolicySummary label="单兔场成员上限">
                      {policy.maxMembersPerHouse}
                    </PolicySummary>
                    <PolicySummary label="更新时间" className="col-span-2">
                      {formatTime(policy.updateTime)}
                    </PolicySummary>
                  </dl>
                  <div className="hidden sm:block">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>创建兔场</TableHead>
                          <TableHead>管理兔场成员</TableHead>
                          <TableHead>兔场上限</TableHead>
                          <TableHead>单兔场成员上限</TableHead>
                          <TableHead>更新时间</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        <TableRow>
                          <TableCell>{permissionBadge(policy.houseCreationEnabled)}</TableCell>
                          <TableCell>{permissionBadge(policy.houseMemberManagementEnabled)}</TableCell>
                          <TableCell>{policy.maxHouseCount}</TableCell>
                          <TableCell>{policy.maxMembersPerHouse}</TableCell>
                          <TableCell className="whitespace-nowrap text-muted-foreground">
                            {formatTime(policy.updateTime)}
                          </TableCell>
                        </TableRow>
                      </TableBody>
                    </Table>
                  </div>
                </>
              ) : (
                <Empty>
                  <EmptyTitle>权限配置不可用</EmptyTitle>
                  <EmptyDescription>刷新商户详情后重试。</EmptyDescription>
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
                    商户账号使用客户端后，相关记录会出现在这里。
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

      <MerchantAccountCreateDialog
        open={createAccountOpen}
        merchantId={merchantId}
        onOpenChange={setCreateAccountOpen}
        onSaved={() => void loadDetail()}
      />

      {policy ? (
        <MerchantHousePolicyDialog
          open={policyDialogOpen}
          policy={policy}
          onOpenChange={setPolicyDialogOpen}
          onSaved={setPolicy}
        />
      ) : null}

      <MerchantMembershipDialog
        open={membershipDialogOpen}
        merchantId={merchantId}
        account={selectedAccount}
        onOpenChange={setMembershipDialogOpen}
        onSaved={() => void loadDetail()}
      />

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

function merchantRoleLabel(role: MerchantRole) {
  if (role === 'OWNER') {
    return '所有者'
  }
  if (role === 'ADMIN') {
    return '管理员'
  }
  return '成员'
}

function permissionBadge(enabled: boolean) {
  return enabled ? <Badge>允许</Badge> : <Badge variant="secondary">禁止</Badge>
}

function PolicySummary({
  label,
  className,
  children,
}: {
  label: string
  className?: string
  children: ReactNode
}) {
  return (
    <div className={className}>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-2 text-sm font-medium">{children}</dd>
    </div>
  )
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
