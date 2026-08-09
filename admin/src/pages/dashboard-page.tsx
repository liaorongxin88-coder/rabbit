import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  CircleAlertIcon,
  CircleCheckIcon,
  UserCogIcon,
  UsersIcon,
  WarehouseIcon,
  type LucideIcon,
} from 'lucide-react'
import { listFarms } from '@/api/farms'
import { listBusinessUsers } from '@/api/users'
import { FarmStatusBadge } from '@/components/farm-status-badge'
import { PageHeader } from '@/components/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { AdminFarm, AdminSession, PageResult } from '@/types/api'
import { hasPermission } from '@/lib/permissions'

const SNAPSHOT_SIZE = 8

export function DashboardPage({ session }: { session: AdminSession }) {
  const [farms, setFarms] = useState<PageResult<AdminFarm> | null>(null)
  const [enabledTotal, setEnabledTotal] = useState(0)
  const [attentionTotal, setAttentionTotal] = useState(0)
  const [userTotal, setUserTotal] = useState(0)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [allFarms, enabled, suspended, orphaned, users] = await Promise.all([
        listFarms({ pageNum: 1, pageSize: SNAPSHOT_SIZE, status: 'ALL' }),
        listFarms({ pageNum: 1, pageSize: 1, status: 'ENABLED' }),
        listFarms({ pageNum: 1, pageSize: 1, status: 'SUSPENDED' }),
        listFarms({ pageNum: 1, pageSize: 1, status: 'ORPHANED' }),
        listBusinessUsers({ pageNum: 1, pageSize: 1, status: 'ALL' }),
      ])
      setFarms(allFarms)
      setEnabledTotal(enabled.total)
      setAttentionTotal(suspended.total + orphaned.total)
      setUserTotal(users.total)
    } catch {
      setFarms(null)
      setEnabledTotal(0)
      setAttentionTotal(0)
      setUserTotal(0)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <>
      <PageHeader
        title="运营概览"
        description="查看兔场、业务用户和需要平台处理的异常状态。"
        actions={
          <>
            <Button variant="outline" asChild><Link to="/users">业务用户</Link></Button>
            <Button asChild><Link to="/farms">兔场管理</Link></Button>
          </>
        }
      />

      <section className="grid gap-4 md:grid-cols-4">
        <SummaryCard title="兔场总数" value={farms?.total ?? 0} icon={WarehouseIcon} />
        <SummaryCard title="正常兔场" value={enabledTotal} icon={CircleCheckIcon} />
        <SummaryCard title="待处理兔场" value={attentionTotal} icon={CircleAlertIcon} />
        <SummaryCard title="业务用户" value={userTotal} icon={UsersIcon} />
      </section>

      <div className="grid min-w-0 gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
        <Card className="min-w-0">
          <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div><CardTitle>兔场快照</CardTitle><CardDescription>优先检查暂停或缺少所有者的兔场。</CardDescription></div>
            {farms && farms.total > SNAPSHOT_SIZE ? <Badge variant="secondary">显示前 {SNAPSHOT_SIZE} 个</Badge> : null}
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="motion-section flex flex-col gap-2"><Skeleton className="h-12 w-full" /><Skeleton className="h-12 w-full" /><Skeleton className="h-12 w-full" /></div>
            ) : farms && farms.items.length > 0 ? (
              <Table>
                <TableHeader><TableRow><TableHead>兔场</TableHead><TableHead>所有者</TableHead><TableHead>状态</TableHead><TableHead className="text-right">操作</TableHead></TableRow></TableHeader>
                <TableBody>
                  {farms.items.map((farm) => (
                    <TableRow key={farm.id}>
                      <TableCell><div className="min-w-48"><p className="truncate font-medium">{farm.name}</p><p className="text-xs text-muted-foreground">ID {farm.id}</p></div></TableCell>
                      <TableCell><span className="block max-w-64 truncate">{farm.ownerNames?.length ? farm.ownerNames.join('、') : '-'}</span></TableCell>
                      <TableCell><FarmStatusBadge status={farm.status} /></TableCell>
                      <TableCell className="text-right"><Button variant="outline" size="sm" asChild><Link to={`/farms/${farm.id}`}>详情</Link></Button></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : (
              <Empty><EmptyTitle>暂无兔场</EmptyTitle><EmptyDescription>业务用户创建兔场后会显示在这里。</EmptyDescription></Empty>
            )}
          </CardContent>
        </Card>

        <Card className="min-w-0">
          <CardHeader><CardTitle>常用操作</CardTitle><CardDescription>进入兔场和账号管理。</CardDescription></CardHeader>
          <CardContent>
            <div className="flex flex-col gap-2">
              <Button className="justify-start" variant="outline" asChild><Link to="/farms"><WarehouseIcon data-icon="inline-start" />兔场管理</Link></Button>
              <Button className="justify-start" variant="outline" asChild><Link to="/users"><UsersIcon data-icon="inline-start" />业务用户</Link></Button>
              {hasPermission(session, 'platform:accounts:list') ? (
                <Button className="justify-start" variant="outline" asChild><Link to="/accounts"><UserCogIcon data-icon="inline-start" />管理员账号</Link></Button>
              ) : null}
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  )
}

function SummaryCard({ title, value, icon: Icon }: { title: string; value: number; icon: LucideIcon }) {
  return (
    <Card className="motion-section">
      <CardHeader className="flex flex-row items-center justify-between gap-4 pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle><Icon aria-hidden="true" /></CardHeader>
      <CardContent><div className="truncate text-2xl font-semibold">{value}</div></CardContent>
    </Card>
  )
}
