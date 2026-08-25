import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  ArrowLeftIcon,
  ArrowLeftRightIcon,
  ArrowUpRightIcon,
  Edit3Icon,
  HeartCrackIcon,
  RabbitIcon,
  ShoppingCartIcon,
  SproutIcon,
} from 'lucide-react'
import { getRabbit, listBatches, listCages, listReproStageActions } from '@/api/workspace'
import { PageHeader } from '@/components/page-header'
import { HousePermissionBadge } from '@/components/permission-badge'
import {
  RabbitDepartureDialog,
  RabbitFormDialog,
  RabbitPromotionDialog,
  RabbitReplacementDialog,
  RabbitTransferDialog,
} from '@/components/rabbit-operation-dialogs'
import { RabbitSaleDialog } from '@/components/rabbit-sale-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import { formatRecordDate } from '@/lib/date'
import { isIndividualSaleRabbit } from '@/lib/rabbit-sale'
import { hasPermission, useWorkspace } from '@/lib/workspace'
import {
  rabbitArrivalMethodLabel,
  rabbitGenderLabel,
  rabbitStageSummary,
  rabbitTypeLabel,
} from '@/lib/rabbits'
import type { Cage, ProductionBatch, Rabbit } from '@/types/api'

export function WorkspaceRabbitDetailPage() {
  const workspace = useWorkspace()
  const params = useParams()
  const rabbitId = Number(params.rabbitId)
  const [rabbit, setRabbit] = useState<Rabbit | null>(null)
  const [cages, setCages] = useState<Cage[]>([])
  const [reproStageLabels, setReproStageLabels] = useState<Record<string, string>>({})
  const [batches, setBatches] = useState<ProductionBatch[]>([])
  const [loading, setLoading] = useState(true)
  const [editOpen, setEditOpen] = useState(false)
  const [transferOpen, setTransferOpen] = useState(false)
  const [promotionOpen, setPromotionOpen] = useState(false)
  const [replacementOpen, setReplacementOpen] = useState(false)
  const [saleOpen, setSaleOpen] = useState(false)
  const [departureOpen, setDepartureOpen] = useState(false)
  const canEdit = hasPermission(workspace.permission, 'rabbit:rabbits:edit')
  const canControl = hasPermission(workspace.permission, 'rabbit:rabbits:control')
  const canSell = hasPermission(workspace.permission, 'rabbit:sales:add')
  const canReadRepro = hasPermission(workspace.permission, 'rabbit:batches:query')

  const load = useCallback(async () => {
    if (!workspace.selectedHouse || !Number.isSafeInteger(rabbitId) || rabbitId <= 0) {
      setRabbit(null)
      setCages([])
      setReproStageLabels({})
      setBatches([])
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
          const [stages, nextBatches] = await Promise.all([
            listReproStageActions(workspace.selectedHouse.id),
            listBatches(workspace.selectedHouse.id),
          ])
          setReproStageLabels(Object.fromEntries(stages.map((item) => [item.stage, item.stageLabel])))
          setBatches(nextBatches)
        } catch {
          setReproStageLabels({})
          setBatches([])
        }
      } else {
        setReproStageLabels({})
        setBatches([])
      }
    } catch {
      setRabbit(null)
      setCages([])
      setReproStageLabels({})
      setBatches([])
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
  const isMatureReplacement = rabbit.isActive && rabbit.type === '1' && rabbit.growthStage === 'MATURE'
  const canRetainAsReplacement = canControl && rabbit.isActive && rabbit.type === '2'
  const isIndividualSaleTarget = rabbit.isActive && isIndividualSaleRabbit(rabbit)

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
            {canRetainAsReplacement ? (
              <Button variant="outline" onClick={() => setReplacementOpen(true)}>
                <SproutIcon data-icon="inline-start" />
                留种转后备
              </Button>
            ) : null}
            {isMatureReplacement ? (
              <Button variant="outline" disabled={!canControl} onClick={() => setPromotionOpen(true)}>
                <ArrowUpRightIcon data-icon="inline-start" />
                转为种兔
              </Button>
            ) : null}
            {isIndividualSaleTarget ? (
              <Button variant="outline" disabled={!canSell} onClick={() => setSaleOpen(true)}>
                <ShoppingCartIcon data-icon="inline-start" />
                出售出栏
              </Button>
            ) : null}
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
        batches={batches}
        onSaved={load}
      />
      <RabbitTransferDialog
        rabbit={transferOpen ? rabbit : null}
        cages={cages}
        houseId={workspace.selectedHouse.id}
        onOpenChange={setTransferOpen}
        onSaved={load}
      />
      <RabbitReplacementDialog
        rabbit={replacementOpen ? rabbit : null}
        cages={cages}
        houseId={workspace.selectedHouse.id}
        onOpenChange={setReplacementOpen}
        onSaved={load}
      />
      <RabbitPromotionDialog
        rabbit={promotionOpen ? rabbit : null}
        houseId={workspace.selectedHouse.id}
        onOpenChange={setPromotionOpen}
        onSaved={load}
      />
      <RabbitSaleDialog
        rabbit={saleOpen ? rabbit : null}
        houseId={workspace.selectedHouse.id}
        onOpenChange={setSaleOpen}
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
