import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Building2Icon,
  CheckCircle2Icon,
  CircleOffIcon,
  Clock3Icon,
  PlusIcon,
  UserCogIcon,
  type LucideIcon,
} from 'lucide-react'
import { listMerchants } from '@/api/merchants'
import { MerchantFormDialog } from '@/components/merchant-form-dialog'
import { PageHeader } from '@/components/page-header'
import { StatusBadge } from '@/components/status-badge'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { AdminSession, Merchant, PageResult } from '@/types/api'

const DASHBOARD_PAGE_SIZE = 8

export function DashboardPage({ session }: { session: AdminSession }) {
  const [data, setData] = useState<PageResult<Merchant> | null>(null)
  const [enabledTotal, setEnabledTotal] = useState(0)
  const [disabledTotal, setDisabledTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)

  const loadDashboard = useCallback(async () => {
    setLoading(true)
    try {
      const [result, enabled, disabled] = await Promise.all([
        listMerchants({
          page: 1,
          pageSize: DASHBOARD_PAGE_SIZE,
          status: 'ALL',
        }),
        listMerchants({
          page: 1,
          pageSize: 1,
          status: 'ENABLED',
        }),
        listMerchants({
          page: 1,
          pageSize: 1,
          status: 'DISABLED',
        }),
      ])
      setData(result)
      setEnabledTotal(enabled.total)
      setDisabledTotal(disabled.total)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadDashboard()
  }, [loadDashboard])

  const stats = useMemo(() => {
    const items = data?.items ?? []
    return {
      total: data?.total ?? 0,
      enabled: enabledTotal,
      disabled: disabledTotal,
      recent: items[0]?.createTime ? formatTime(items[0].createTime) : '-',
    }
  }, [data, disabledTotal, enabledTotal])

  return (
    <>
      <PageHeader
        title="运营概览"
        description="查看平台商户状态，进入商户管理，并快速创建新商户。"
        actions={
          <>
            <Button variant="outline" asChild>
              <Link to="/merchants">查看全部商户</Link>
            </Button>
            <Button onClick={() => setDialogOpen(true)}>
              <PlusIcon data-icon="inline-start" />
              新增商户
            </Button>
          </>
        }
      />

      <section className="grid gap-4 md:grid-cols-4">
        <SummaryCard title="商户总数" value={stats.total} icon={Building2Icon} />
        <SummaryCard title="启用商户" value={stats.enabled} icon={CheckCircle2Icon} />
        <SummaryCard title="停用商户" value={stats.disabled} icon={CircleOffIcon} />
        <SummaryCard title="最近记录" value={stats.recent} icon={Clock3Icon} />
      </section>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
        <Card>
          <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle>商户快照</CardTitle>
              <CardDescription>展示当前列表入口中最先返回的一组商户。</CardDescription>
            </div>
            {data && data.total > DASHBOARD_PAGE_SIZE ? (
              <Badge variant="secondary">显示前 {DASHBOARD_PAGE_SIZE} 个</Badge>
            ) : null}
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="motion-section flex flex-col gap-2">
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
              </div>
            ) : data && data.items.length > 0 ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>商户</TableHead>
                    <TableHead>联系人</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead className="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((merchant) => (
                    <TableRow key={merchant.id}>
                      <TableCell>
                        <div className="min-w-48">
                          <p className="truncate font-medium">{merchant.name}</p>
                          <p className="text-xs text-muted-foreground">ID {merchant.id}</p>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="min-w-40">
                          <p className="truncate">{merchant.contactName || '-'}</p>
                          <p className="truncate text-xs text-muted-foreground">
                            {merchant.contactPhone || '-'}
                          </p>
                        </div>
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={merchant.status} />
                      </TableCell>
                      <TableCell className="text-right">
                        <Button variant="outline" size="sm" asChild>
                          <Link to={`/merchants/${merchant.id}`}>详情</Link>
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : (
              <Empty>
                <EmptyTitle>暂无商户</EmptyTitle>
                <EmptyDescription>创建商户后，这里会显示商户状态快照。</EmptyDescription>
                <Button size="sm" onClick={() => setDialogOpen(true)}>
                  <PlusIcon data-icon="inline-start" />
                  新增商户
                </Button>
              </Empty>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>常用操作</CardTitle>
            <CardDescription>进入商户和账号管理。</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col gap-2">
              <Button className="justify-start" variant="outline" asChild>
                <Link to="/merchants">
                  <Building2Icon data-icon="inline-start" />
                  商户管理
                </Link>
              </Button>
              {session.role === 'SUPER_ADMIN' ? (
                <Button className="justify-start" variant="outline" asChild>
                  <Link to="/accounts">
                    <UserCogIcon data-icon="inline-start" />
                    账号管理
                  </Link>
                </Button>
              ) : null}
              <Button className="justify-start" onClick={() => setDialogOpen(true)}>
                <PlusIcon data-icon="inline-start" />
                新增商户
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      <MerchantFormDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        onSaved={() => void loadDashboard()}
      />
    </>
  )
}

function SummaryCard({
  title,
  value,
  icon: Icon,
}: {
  title: string
  value: number | string
  icon: LucideIcon
}) {
  return (
    <Card className="motion-section">
      <CardHeader className="flex flex-row items-center justify-between gap-4 pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">
          {title}
        </CardTitle>
        <Icon aria-hidden="true" />
      </CardHeader>
      <CardContent>
        <div className="truncate text-2xl font-semibold">{value}</div>
      </CardContent>
    </Card>
  )
}

function formatTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString()
}
