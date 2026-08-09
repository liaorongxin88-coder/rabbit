import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ActivityIcon,
  BabyIcon,
  MarsIcon,
  RabbitIcon,
  RefreshCwIcon,
  VenusIcon,
  WarehouseIcon,
} from 'lucide-react'
import { getDashboard } from '@/api/workspace'
import { MetricCard } from '@/components/metric-card'
import { PageHeader } from '@/components/page-header'
import { HousePermissionBadge } from '@/components/permission-badge'
import { useWorkspace } from '@/lib/workspace'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import type { DashboardSummary } from '@/types/api'

export function WorkspaceDashboardPage() {
  const workspace = useWorkspace()
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    if (!workspace.selectedHouse) {
      setSummary(null)
      return
    }
    setLoading(true)
    try {
      setSummary(await getDashboard(workspace.selectedHouse.id))
    } catch {
      setSummary(null)
    } finally {
      setLoading(false)
    }
  }, [workspace.selectedHouse])

  useEffect(() => {
    void load()
  }, [load])

  const maxMonthly = useMemo(() => {
    if (!summary) {
      return 1
    }
    return Math.max(1, ...summary.monthlyBirths, ...summary.monthlyWeaned)
  }, [summary])

  return (
    <>
      <PageHeader
        title="工作概览"
        description={workspace.selectedHouse ? workspace.selectedHouse.name : '请选择兔场'}
        actions={
          <>
            <HousePermissionBadge permission={workspace.permission} />
            <Button variant="outline" onClick={() => void load()} disabled={loading || !workspace.selectedHouse}>
              <RefreshCwIcon data-icon="inline-start" />
              刷新
            </Button>
          </>
        }
      />

      {!workspace.selectedHouse ? (
        <Empty>
          <WarehouseIcon aria-hidden="true" />
          <EmptyTitle>还没有可访问的兔场</EmptyTitle>
          <EmptyDescription>创建兔场或通过手机号邀请加入已有兔场。</EmptyDescription>
        </Empty>
      ) : loading && !summary ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 8 }, (_, index) => (
            <Skeleton key={index} className="h-28" />
          ))}
        </div>
      ) : summary ? (
        <>
          <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard title="兔只总数" value={summary.totalRabbits} icon={RabbitIcon} />
            <MetricCard title="种兔" value={summary.seedRabbits} icon={ActivityIcon} />
            <MetricCard title="公兔" value={summary.maleRabbits} icon={MarsIcon} />
            <MetricCard title="母兔" value={summary.femaleRabbits} icon={VenusIcon} />
            <MetricCard title="已配种" value={summary.bredRabbits} icon={ActivityIcon} />
            <MetricCard title="待配种" value={summary.readyForBreeding} icon={ActivityIcon} />
            <MetricCard title="窝数" value={summary.litters} icon={BabyIcon} />
            <MetricCard title="哺乳仔兔" value={summary.nursingKits} icon={BabyIcon} />
          </section>

          <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_20rem]">
            <Card>
              <CardHeader>
                <CardTitle>{summary.year} 年生产趋势</CardTitle>
                <CardDescription>出生与断奶数量按月汇总。</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid min-h-52 grid-cols-12 items-end gap-2" aria-label="月度生产趋势">
                  {summary.monthlyBirths.map((births, index) => {
                    const weaned = summary.monthlyWeaned[index] ?? 0
                    return (
                      <div key={index} className="flex min-w-0 flex-col items-center gap-2">
                        <div className="flex h-40 w-full items-end justify-center gap-1">
                          <div
                            className="w-2 rounded-sm bg-primary"
                            style={{ height: `${Math.max(3, (births / maxMonthly) * 100)}%` }}
                            title={`${index + 1}月出生 ${births}`}
                          />
                          <div
                            className="w-2 rounded-sm bg-accent"
                            style={{ height: `${Math.max(3, (weaned / maxMonthly) * 100)}%` }}
                            title={`${index + 1}月断奶 ${weaned}`}
                          />
                        </div>
                        <span className="text-xs text-muted-foreground">{index + 1}</span>
                      </div>
                    )
                  })}
                </div>
                <div className="mt-4 flex flex-wrap gap-4 text-xs text-muted-foreground">
                  <span className="flex items-center gap-2"><span className="size-2 rounded-sm bg-primary" />出生</span>
                  <span className="flex items-center gap-2"><span className="size-2 rounded-sm bg-accent" />断奶</span>
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>兔群结构</CardTitle>
                <CardDescription>当前兔场在栏结构。</CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-4">
                <StructureRow label="商品兔" value={summary.commodityRabbits} total={summary.totalRabbits} />
                <StructureRow label="后备兔" value={summary.replacementRabbits} total={summary.totalRabbits} />
                <StructureRow label="种兔" value={summary.seedRabbits} total={summary.totalRabbits} />
                <div className="rounded-md bg-secondary p-4">
                  <p className="text-xs text-muted-foreground">成活率</p>
                  <p className="mt-1 text-2xl font-semibold">{(summary.liveRate * 100).toFixed(1)}%</p>
                </div>
              </CardContent>
            </Card>
          </section>
        </>
      ) : (
        <Empty>
          <EmptyTitle>概览暂时无法加载</EmptyTitle>
          <EmptyDescription>请刷新重试或切换兔场。</EmptyDescription>
        </Empty>
      )}
    </>
  )
}

function StructureRow({ label, value, total }: { label: string; value: number; total: number }) {
  const percent = total > 0 ? Math.min(100, (value / total) * 100) : 0
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-3 text-sm">
        <span>{label}</span>
        <span className="font-medium">{value}</span>
      </div>
      <div className="h-2 overflow-hidden rounded-sm bg-secondary">
        <div className="h-full bg-primary" style={{ width: `${percent}%` }} />
      </div>
    </div>
  )
}
