import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  ArrowLeftIcon,
  ArrowLeftRightIcon,
  Edit3Icon,
  HeartCrackIcon,
  RabbitIcon,
} from 'lucide-react'
import { getRabbit, listCages, listReproStageActions } from '@/api/workspace'
import { PageHeader } from '@/components/page-header'
import { HousePermissionBadge } from '@/components/permission-badge'
import {
  RabbitDepartureDialog,
  RabbitFormDialog,
  RabbitTransferDialog,
} from '@/components/rabbit-operation-dialogs'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import { hasPermission, useWorkspace } from '@/lib/workspace'
import {
  rabbitArrivalMethodLabel,
  rabbitGenderLabel,
  rabbitStageSummary,
  rabbitTypeLabel,
} from '@/lib/rabbits'
import type { Cage, Rabbit } from '@/types/api'

export function WorkspaceRabbitDetailPage() {
  const workspace = useWorkspace()
  const params = useParams()
  const rabbitId = Number(params.rabbitId)
  const [rabbit, setRabbit] = useState<Rabbit | null>(null)
  const [cages, setCages] = useState<Cage[]>([])
  const [reproStageLabels, setReproStageLabels] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [editOpen, setEditOpen] = useState(false)
  const [transferOpen, setTransferOpen] = useState(false)
  const [departureOpen, setDepartureOpen] = useState(false)
  const canEdit = hasPermission(workspace.permission, 'rabbit:rabbits:edit')
  const canReadRepro = hasPermission(workspace.permission, 'rabbit:batches:query')

  const load = useCallback(async () => {
    if (!workspace.selectedHouse || !Number.isSafeInteger(rabbitId) || rabbitId <= 0) {
      setRabbit(null)
      setCages([])
      setReproStageLabels({})
      setLoading(false)
      return
    }

    setLoading(true)
    try {
      const [nextRabbit, nextCages] = await Promise.all([
        getRabbit(workspace.selectedHouse.id, rabbitId),
        listCages(workspace.selectedHouse.id),
      ])
      setRabbit(nextRabbit)
      setCages(nextCages)

      if (canReadRepro) {
        try {
          const stages = await listReproStageActions(workspace.selectedHouse.id)
          setReproStageLabels(Object.fromEntries(stages.map((item) => [item.stage, item.stageLabel])))
        } catch {
          setReproStageLabels({})
        }
      } else {
        setReproStageLabels({})
      }
    } catch {
      setRabbit(null)
      setCages([])
      setReproStageLabels({})
    } finally {
      setLoading(false)
    }
  }, [canReadRepro, rabbitId, workspace.selectedHouse])

  useEffect(() => {
    void load()
  }, [load])

  if (loading) {
    return (
      <div className="motion-page flex flex-col gap-4">
        <Skeleton className="h-20 w-full" />
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-56 w-full" />
          <Skeleton className="h-56 w-full" />
        </div>
        <Skeleton className="h-44 w-full" />
      </div>
    )
  }

  if (!workspace.selectedHouse) {
    return (
      <Empty>
        <RabbitIcon aria-hidden="true" />
        <EmptyTitle>请选择兔场</EmptyTitle>
        <EmptyDescription>兔只详情始终限定在一个兔场内。</EmptyDescription>
        <Button asChild><Link to="/workspace/livestock">返回兔群管理</Link></Button>
      </Empty>
    )
  }

  if (!rabbit) {
    return (
      <Empty>
        <RabbitIcon aria-hidden="true" />
        <EmptyTitle>兔只不存在</EmptyTitle>
        <EmptyDescription>该兔只不在当前兔场，或记录已不可访问。</EmptyDescription>
        <Button asChild><Link to="/workspace/livestock">返回兔群管理</Link></Button>
      </Empty>
    )
  }

  const cage = cages.find((item) => item.id === rabbit.cageId)
  const stageSummary = rabbitStageSummary(rabbit, reproStageLabels)

  return (
    <>
      <PageHeader
        title={`兔 #${rabbit.id}`}
        description={`${rabbitTypeLabel(rabbit)} · ${rabbitGenderLabel(rabbit.gender)} · ${workspace.selectedHouse.name}`}
        actions={
          <>
            <Button variant="outline" asChild>
              <Link to="/workspace/livestock">
                <ArrowLeftIcon data-icon="inline-start" />
                返回兔群
              </Link>
            </Button>
            <HousePermissionBadge permission={workspace.permission} />
            <Button variant="outline" disabled={!canEdit} onClick={() => setEditOpen(true)}>
              <Edit3Icon data-icon="inline-start" />
              编辑
            </Button>
            <Button
              variant="outline"
              disabled={!canEdit || !rabbit.isActive}
              onClick={() => setTransferOpen(true)}
            >
              <ArrowLeftRightIcon data-icon="inline-start" />
              换笼
            </Button>
            <Button
              variant="destructive"
              disabled={!canEdit || !rabbit.isActive}
              onClick={() => setDepartureOpen(true)}
            >
              <HeartCrackIcon data-icon="inline-start" />
              登记离场
            </Button>
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>当前状态</CardTitle>
            <CardDescription>在栏、笼位和当前阶段。</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-5 sm:grid-cols-2">
            <DetailField label="状态">
              <Badge variant={rabbit.isActive ? 'default' : 'secondary'}>
                {rabbit.isActive ? '在栏' : '离场'}
              </Badge>
            </DetailField>
            <DetailField label={rabbit.isActive ? '当前笼位' : '最后笼位'}>
              {cage?.cageNumber ?? `#${rabbit.cageId}`}
            </DetailField>
            <DetailField label="阶段">{stageSummary}</DetailField>
            <DetailField label="隔离">
              {rabbit.isQuarantined ? (
                <span>隔离中{rabbit.quarantineReason ? ` · ${rabbit.quarantineReason}` : ''}</span>
              ) : '否'}
            </DetailField>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>档案资料</CardTitle>
            <CardDescription>类型、来源和入场信息。</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-5 sm:grid-cols-2">
            <DetailField label="类型">{rabbitTypeLabel(rabbit)}</DetailField>
            <DetailField label="性别">{rabbitGenderLabel(rabbit.gender)}</DetailField>
            <DetailField label="品种">{rabbit.breed || '-'}</DetailField>
            <DetailField label="体重">
              {rabbit.weight == null ? '-' : `${rabbit.weight.toFixed(2)} kg`}
            </DetailField>
            <DetailField label="来源">{rabbitArrivalMethodLabel(rabbit.arrivalMethod)}</DetailField>
            <DetailField label="入场日期">{formatRecordDate(rabbit.arrivalDate)}</DetailField>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>生产关联</CardTitle>
          <CardDescription>血缘、生产周期和离场记录。</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          <DetailField label="母兔">
            {rabbit.motherId ? (
              <Link
                className="font-medium text-primary underline-offset-4 hover:underline"
                to={`/workspace/livestock/rabbits/${rabbit.motherId}`}
              >
                兔 #{rabbit.motherId}
              </Link>
            ) : '-'}
          </DetailField>
          <DetailField label="当前生产周期">
            {rabbit.currentCycleId ? `#${rabbit.currentCycleId}` : '-'}
          </DetailField>
          <DetailField label="进入阶段日期">{formatRecordDate(rabbit.stageEnteredAt)}</DetailField>
          <DetailField label="最近配种日期">{formatRecordDate(rabbit.lastMatingDate)}</DetailField>
          <DetailField label="离场日期">{formatRecordDate(rabbit.departureDate)}</DetailField>
          <DetailField label="离场原因">{rabbit.departureReason || '-'}</DetailField>
          <DetailField label="建档日期">{formatRecordDate(rabbit.createTime)}</DetailField>
          <DetailField label="兔只 ID">{rabbit.id}</DetailField>
        </CardContent>
      </Card>

      <RabbitFormDialog
        open={editOpen}
        rabbit={rabbit}
        onOpenChange={setEditOpen}
        houseId={workspace.selectedHouse.id}
        cages={cages}
        entryPoints={[]}
        onSaved={load}
      />
      <RabbitTransferDialog
        rabbit={transferOpen ? rabbit : null}
        cages={cages}
        houseId={workspace.selectedHouse.id}
        onOpenChange={setTransferOpen}
        onSaved={load}
      />
      <RabbitDepartureDialog
        rabbit={departureOpen ? rabbit : null}
        houseId={workspace.selectedHouse.id}
        onOpenChange={setDepartureOpen}
        onSaved={load}
      />
    </>
  )
}

function DetailField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <p className="text-xs text-muted-foreground">{label}</p>
      <div className="mt-1 break-words text-sm font-medium">{children}</div>
    </div>
  )
}

function formatRecordDate(value?: string | null) {
  if (!value) return '-'
  const date = value.match(/^(\d{4})-(\d{2})-(\d{2})/)
  return date ? `${date[1]}-${date[2]}-${date[3]}` : value
}
