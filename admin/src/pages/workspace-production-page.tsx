import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  CalendarClockIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  PlusIcon,
  RefreshCwIcon,
  Rows3Icon,
  SearchIcon,
  WarehouseIcon,
} from 'lucide-react'
import { toast } from 'sonner'
import {
  createBatch,
  listBatchRabbits,
  listBatches,
  listBreedingCycles,
  listCages,
  listRabbits,
  listReproStageActions,
  submitBatchAction,
  submitBulkMating,
  submitRabbitDeparture,
  submitReproAction,
  type ReproActionName,
} from '@/api/workspace'
import { PageHeader } from '@/components/page-header'
import { WorkspaceOutboundDialog } from '@/components/workspace-outbound-dialog'
import { HousePermissionBadge } from '@/components/permission-badge'
import { hasPermission, useWorkspace } from '@/lib/workspace'
import {
  batchStatusLabel,
  BATCH_MOTHER_PAGE_SIZE,
  getOrCreateBatchActionRequest,
  getOrCreateBulkMatingRequest,
  getOrCreateRabbitDepartureRequest,
  isBulkMatingEligible,
  isCompletedBatchStatus,
  MAX_BULK_MATING_MOTHERS,
  normalizeParturitionPayload,
  type PendingBatchActionRequest,
} from '@/lib/batch-workflow'
import { formatLocalDate } from '@/lib/date'
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
import { Textarea } from '@/components/ui/textarea'
import type {
  BatchRabbit,
  BreedingCycle,
  BulkMatingRequest,
  Cage,
  ProductionBatch,
  Rabbit,
  RabbitDepartureRequest,
  RabbitDepartureType,
} from '@/types/api'

/**
 * 工作台上可选的动作。
 *
 * 前六个是生产动作，挂在生产周期上（走 /api/repro/cycles/{id}/actions）；
 * 旧版把它们挂在批次上，且把催情拆成「开始」与「完成」两步——
 * doe-breeding-v2 取消了那个中间态，所以催情只剩一个动作。
 */
type BatchWorkflowAction =
  | 'estrus'
  | 'mating'
  | 'mating/bulk'
  | 'palpation'
  | 'prepartum'
  | 'delivery'
  | 'weaning'
  | 'abortion'
  | 'departure'
  | 'complete'

const actionLabels: Record<BatchWorkflowAction, string> = {
  estrus: '记录催情',
  mating: '记录配种',
  'mating/bulk': '批量配种',
  palpation: '记录摸胎',
  prepartum: '完成备产',
  delivery: '记录分娩',
  weaning: '记录分笼',
  abortion: '记录流产',
  departure: '母兔离场',
  complete: '完成批次',
}

/** 工作台动作 → 服务端生产动作。不在表中的不走生产写入口。 */
const reproActionByWorkflow: Partial<Record<BatchWorkflowAction, ReproActionName>> = {
  estrus: 'ESTRUS',
  mating: 'MATING',
  'mating/bulk': 'MATING',
  palpation: 'PALPATION',
  prepartum: 'PREPARTUM',
  delivery: 'DELIVERY',
  weaning: 'WEANING',
  abortion: 'ABORTION',
}

export function WorkspaceProductionPage() {
  const workspace = useWorkspace()
  const [batches, setBatches] = useState<ProductionBatch[]>([])
  const [batchMotherCounts, setBatchMotherCounts] = useState<Record<number, number | null>>({})
  const [rabbits, setRabbits] = useState<Rabbit[]>([])
  const [cages, setCages] = useState<Cage[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [actionBatch, setActionBatch] = useState<ProductionBatch | null>(null)
  const canEdit = hasPermission(workspace.permission, 'rabbit:batches:edit')
  const canRabbitEdit = hasPermission(workspace.permission, 'rabbit:rabbits:edit')
  const canOutboundEdit = hasPermission(workspace.permission, 'rabbit:outbound:edit')
  const canControl = hasPermission(workspace.permission, 'rabbit:rabbits:control')

  const load = useCallback(async () => {
    if (!workspace.selectedHouse) {
      setBatches([])
      setBatchMotherCounts({})
      setRabbits([])
      setCages([])
      return
    }
    setLoading(true)
    try {
      const [nextBatches, nextRabbits, nextCages] = await Promise.all([
        listBatches(workspace.selectedHouse.id),
        listRabbits(workspace.selectedHouse.id),
        listCages(workspace.selectedHouse.id),
      ])
      setBatches(nextBatches)
      setBatchMotherCounts({})
      setRabbits(nextRabbits)
      setCages(nextCages)

      const batchRabbitResults = await Promise.allSettled(
        nextBatches.map((batch) => listBatchRabbits(workspace.selectedHouse!.id, batch.id)),
      )
      setBatchMotherCounts(Object.fromEntries(
        batchRabbitResults.map((result, index) => [
          nextBatches[index].id,
          result.status === 'fulfilled'
            ? result.value.filter((item) => item.batchRole === 'breeding').length
            : null,
        ]),
      ))
    } catch {
      setBatches([])
      setBatchMotherCounts({})
      setRabbits([])
      setCages([])
    } finally {
      setLoading(false)
    }
  }, [workspace.selectedHouse])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <>
      <PageHeader
        title="生产批次"
        description={workspace.selectedHouse?.name ?? '请选择兔场'}
        actions={
          <>
            <HousePermissionBadge permission={workspace.permission} />
            <Button variant="outline" onClick={() => void load()} disabled={loading || !workspace.selectedHouse}>
              <RefreshCwIcon data-icon="inline-start" />
              刷新
            </Button>
            <WorkspaceOutboundDialog
              houseId={workspace.selectedHouse?.id ?? null}
              disabled={!canOutboundEdit}
              canControl={canControl}
              onSaved={load}
            />
            <CreateBatchDialog
              open={createOpen}
              onOpenChange={setCreateOpen}
              houseId={workspace.selectedHouse?.id ?? null}
              rabbits={rabbits}
              disabled={!canEdit}
              onSaved={load}
            />
          </>
        }
      />

      {!workspace.selectedHouse ? (
        <Empty>
          <WarehouseIcon aria-hidden="true" />
          <EmptyTitle>请选择兔场</EmptyTitle>
          <EmptyDescription>生产批次不会跨兔场合并。</EmptyDescription>
        </Empty>
      ) : batches.length === 0 ? (
        <Empty>
          <Rows3Icon aria-hidden="true" />
          <EmptyTitle>还没有生产批次</EmptyTitle>
          <EmptyDescription>从当前兔场的种母兔中建立第一个批次。</EmptyDescription>
        </Empty>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>批次列表</CardTitle>
            <CardDescription>生产记录会沿用客户端的批次状态机。</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="divide-y md:hidden">
              {batches.map((batch) => (
                <article key={batch.id} className="py-4 first:pt-0 last:pb-0" aria-label={`批次 ${batch.batchCode}`}>
                  <div className="flex min-w-0 items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <h3 className="break-all text-sm font-semibold leading-5">{batch.batchCode}</h3>
                      <p className="mt-0.5 text-xs text-muted-foreground">ID {batch.id}</p>
                    </div>
                    <Badge className="shrink-0" variant={isCompletedBatchStatus(batch.status) ? 'secondary' : 'default'}>
                      {batchStatusLabel(batch.status)}
                    </Badge>
                  </div>

                  <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
                    <div className="min-w-0">
                      <dt className="text-xs text-muted-foreground">开始日期</dt>
                      <dd className="mt-1 font-medium tabular-nums">{formatDate(batch.startDate)}</dd>
                    </div>
                    <div className="min-w-0">
                      <dt className="text-xs text-muted-foreground">结束日期</dt>
                      <dd className="mt-1 font-medium tabular-nums">{formatDate(batch.endDate)}</dd>
                    </div>
                    <div className="min-w-0">
                      <dt className="text-xs text-muted-foreground">母兔数</dt>
                      <dd className="mt-1 font-medium tabular-nums">{formatMotherCount(batchMotherCounts[batch.id])}</dd>
                    </div>
                    <div className="col-span-2 min-w-0">
                      <dt className="text-xs text-muted-foreground">备注</dt>
                      <dd className="mt-1 break-words leading-5">{batch.remark || '-'}</dd>
                    </div>
                  </dl>

                  <div className="mt-4">
                    {isCompletedBatchStatus(batch.status) ? (
                      <p className="text-right text-xs text-muted-foreground">已闭环</p>
                    ) : (
                      <Button className="w-full" variant="outline" disabled={!canEdit} onClick={() => setActionBatch(batch)}>
                        <CalendarClockIcon data-icon="inline-start" />
                        生产操作
                      </Button>
                    )}
                  </div>
                </article>
              ))}
            </div>

            <div className="hidden md:block">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>批次</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>开始日期</TableHead>
                    <TableHead>结束日期</TableHead>
                    <TableHead>母兔数</TableHead>
                    <TableHead>备注</TableHead>
                    <TableHead className="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {batches.map((batch) => (
                    <TableRow key={batch.id}>
                      <TableCell>
                        <div className="flex min-w-36 flex-col gap-1">
                          <span className="font-medium">{batch.batchCode}</span>
                          <span className="text-xs text-muted-foreground">ID {batch.id}</span>
                        </div>
                      </TableCell>
                      <TableCell><Badge variant={isCompletedBatchStatus(batch.status) ? 'secondary' : 'default'}>{batchStatusLabel(batch.status)}</Badge></TableCell>
                      <TableCell>{formatDate(batch.startDate)}</TableCell>
                      <TableCell>{formatDate(batch.endDate)}</TableCell>
                      <TableCell className="tabular-nums">{formatMotherCount(batchMotherCounts[batch.id])}</TableCell>
                      <TableCell className="max-w-64 truncate">{batch.remark || '-'}</TableCell>
                      <TableCell className="text-right">
                        {isCompletedBatchStatus(batch.status) ? (
                          <span className="text-xs text-muted-foreground">已闭环</span>
                        ) : (
                          <Button variant="outline" size="sm" disabled={!canEdit} onClick={() => setActionBatch(batch)}>
                            <CalendarClockIcon data-icon="inline-start" />
                            生产操作
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>
      )}

      <BatchActionDialog
        batch={actionBatch}
        houseId={workspace.selectedHouse?.id ?? null}
        rabbits={rabbits}
        cages={cages}
        canRabbitEdit={canRabbitEdit}
        onOpenChange={(open) => !open && setActionBatch(null)}
        onSaved={load}
      />
    </>
  )
}

function CreateBatchDialog({
  open,
  onOpenChange,
  houseId,
  rabbits,
  disabled,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  houseId: number | null
  rabbits: Rabbit[]
  disabled: boolean
  onSaved: () => Promise<void>
}) {
  const femaleRabbits = useMemo(
    () => rabbits.filter((rabbit) => rabbit.isActive && rabbit.type === '0' && rabbit.gender === '0'),
    [rabbits],
  )
  const [code, setCode] = useState('')
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [rabbitSearch, setRabbitSearch] = useState('')
  const [rabbitPage, setRabbitPage] = useState(1)
  const [remark, setRemark] = useState('')
  const [saving, setSaving] = useState(false)

  const filteredFemaleRabbits = useMemo(() => {
    const keyword = rabbitSearch.trim().toLowerCase()
    if (!keyword) return femaleRabbits
    return femaleRabbits.filter((rabbit) => (
      String(rabbit.id).includes(keyword)
      || String(rabbit.cageId).includes(keyword)
      || rabbit.breed?.toLowerCase().includes(keyword)
    ))
  }, [femaleRabbits, rabbitSearch])
  const rabbitPageCount = Math.max(1, Math.ceil(filteredFemaleRabbits.length / BATCH_MOTHER_PAGE_SIZE))
  const visibleFemaleRabbits = filteredFemaleRabbits.slice(
    (rabbitPage - 1) * BATCH_MOTHER_PAGE_SIZE,
    rabbitPage * BATCH_MOTHER_PAGE_SIZE,
  )

  useEffect(() => {
    if (!open) return
    setCode(`PC-${formatLocalDate().replaceAll('-', '')}`)
    setSelectedIds([])
    setRabbitSearch('')
    setRabbitPage(1)
    setRemark('')
  }, [open])

  function toggleRabbit(id: number) {
    setSelectedIds((current) => {
      if (current.includes(id)) return current.filter((item) => item !== id)
      if (current.length >= 5000) {
        toast.error('单个批次最多选择 5000 只母兔')
        return current
      }
      return [...current, id]
    })
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!houseId || selectedIds.length === 0) return
    setSaving(true)
    try {
      await createBatch(houseId, {
        batchCode: code.trim(),
        femaleRabbitIds: selectedIds,
        remark: remark.trim(),
      })
      toast.success('生产批次已创建')
      onOpenChange(false)
      await onSaved()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogTrigger asChild>
        <Button disabled={disabled || femaleRabbits.length === 0}>
          <PlusIcon data-icon="inline-start" />
          新建批次
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>新建生产批次</DialogTitle>
          <DialogDescription>选择当前兔场内尚可用的种母兔。</DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="overflow-y-auto pr-1">
            <Field>
              <FieldLabel htmlFor="batch-code">批次编号</FieldLabel>
              <Input id="batch-code" value={code} required maxLength={100} onChange={(event) => setCode(event.target.value)} />
            </Field>
            <Field>
              <FieldLabel>种母兔</FieldLabel>
              <div className="flex flex-col gap-3 rounded-md border p-3">
                <div className="relative">
                  <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
                  <Input
                    className="pl-9"
                    value={rabbitSearch}
                    placeholder="搜索兔只 ID、笼位或品种"
                    aria-label="搜索种母兔"
                    onChange={(event) => {
                      setRabbitSearch(event.target.value)
                      setRabbitPage(1)
                    }}
                  />
                </div>
                <div className="grid max-h-48 gap-2 overflow-y-auto sm:grid-cols-2">
                  {visibleFemaleRabbits.map((rabbit) => (
                    <label key={rabbit.id} className="flex items-center gap-2 text-sm">
                      <input className="size-5 shrink-0" type="checkbox" checked={selectedIds.includes(rabbit.id)} onChange={() => toggleRabbit(rabbit.id)} />
                      兔 #{rabbit.id} · 笼位 #{rabbit.cageId}
                    </label>
                  ))}
                </div>
                {visibleFemaleRabbits.length === 0 ? <p className="text-sm text-muted-foreground">没有匹配的种母兔</p> : null}
                <div className="flex flex-wrap items-center justify-between gap-2 border-t pt-3">
                  <span className="text-xs text-muted-foreground">筛选 {filteredFemaleRabbits.length} 只 · 已选 {selectedIds.length}/5000 · 第 {rabbitPage}/{rabbitPageCount} 页</span>
                  <div className="flex flex-wrap gap-2">
                    <Button type="button" variant="outline" size="sm" disabled={filteredFemaleRabbits.length === 0} onClick={() => {
                      if (filteredFemaleRabbits.length > 5000) toast.error('单个批次最多选择 5000 只母兔')
                      setSelectedIds(filteredFemaleRabbits.slice(0, 5000).map((rabbit) => rabbit.id))
                    }}>选择筛选结果</Button>
                    <Button type="button" variant="ghost" size="sm" disabled={selectedIds.length === 0} onClick={() => setSelectedIds([])}>清空已选</Button>
                    <Button type="button" variant="outline" size="sm" disabled={rabbitPage <= 1} onClick={() => setRabbitPage((current) => Math.max(1, current - 1))}>
                      <ChevronLeftIcon data-icon="inline-start" />上一页
                    </Button>
                    <Button type="button" variant="outline" size="sm" disabled={rabbitPage >= rabbitPageCount} onClick={() => setRabbitPage((current) => Math.min(rabbitPageCount, current + 1))}>
                      下一页<ChevronRightIcon data-icon="inline-end" />
                    </Button>
                  </div>
                </div>
              </div>
            </Field>
            <Field>
              <FieldLabel htmlFor="batch-remark">备注</FieldLabel>
              <Textarea id="batch-remark" value={remark} onChange={(event) => setRemark(event.target.value)} />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
            <Button type="submit" disabled={saving || selectedIds.length === 0}>{saving ? <Spinner data-icon="inline-start" /> : null}创建批次</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function BatchActionDialog({
  batch,
  houseId,
  rabbits,
  cages,
  canRabbitEdit,
  onOpenChange,
  onSaved,
}: {
  batch: ProductionBatch | null
  houseId: number | null
  rabbits: Rabbit[]
  cages: Cage[]
  canRabbitEdit: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [action, setAction] = useState<BatchWorkflowAction>('mating')
  const [batchRabbits, setBatchRabbits] = useState<BatchRabbit[]>([])
  const [breedingCycles, setBreedingCycles] = useState<BreedingCycle[]>([])
  const [breedingCycleId, setBreedingCycleId] = useState('')
  const [stillbirthCount, setStillbirthCount] = useState('')
  /** 阶段→可执行动作，服务端下发；决定「记录流产」是否出现。 */
  const [stageActions, setStageActions] = useState<Record<string, string[]>>({})
  const [rabbitId, setRabbitId] = useState('')
  const [motherSearch, setMotherSearch] = useState('')
  const [motherPage, setMotherPage] = useState(1)
  const [maleRabbitId, setMaleRabbitId] = useState('')
  const [bulkSelectedIds, setBulkSelectedIds] = useState<number[]>([])
  const [bulkSearch, setBulkSearch] = useState('')
  const [bulkStatus, setBulkStatus] = useState<'all' | '待配种' | '哺乳中'>('all')
  const [bulkPage, setBulkPage] = useState(1)
  const [date, setDate] = useState(formatLocalDate())
  const [result, setResult] = useState('怀孕')
  const [totalKits, setTotalKits] = useState('0')
  const [liveKits, setLiveKits] = useState('0')
  const [parturitionFailed, setParturitionFailed] = useState(false)
  const [weaningCount, setWeaningCount] = useState('0')
  const [maleCount, setMaleCount] = useState('0')
  const [femaleCount, setFemaleCount] = useState('0')
  const [targetCageId, setTargetCageId] = useState('')
  const [avgWeight, setAvgWeight] = useState('')
  const [force, setForce] = useState(false)
  const [departureType, setDepartureType] = useState<RabbitDepartureType>('cull')
  const [departureReason, setDepartureReason] = useState('')
  const [departureConfirmed, setDepartureConfirmed] = useState(false)
  const [remark, setRemark] = useState('')
  const [saving, setSaving] = useState(false)
  const pendingBulkRequest = useRef<BulkMatingRequest | null>(null)
  const pendingDepartureRequest = useRef<RabbitDepartureRequest | null>(null)
  const pendingBatchActionRequest = useRef<PendingBatchActionRequest | null>(null)

  useEffect(() => {
    setAction('mating')
    setBatchRabbits([])
    setBreedingCycles([])
    setBreedingCycleId('')
    setRabbitId('')
    setMotherSearch('')
    setMotherPage(1)
    setMaleRabbitId('')
    setBulkSelectedIds([])
    setBulkSearch('')
    setBulkStatus('all')
    setBulkPage(1)
    setDate(formatLocalDate())
    setResult('怀孕')
    setTotalKits('0')
    setLiveKits('0')
    setParturitionFailed(false)
    setWeaningCount('0')
    setMaleCount('0')
    setFemaleCount('0')
    setTargetCageId('')
    setAvgWeight('')
    setForce(false)
    setDepartureType('cull')
    setDepartureReason('')
    setDepartureConfirmed(false)
    setRemark('')
    pendingBulkRequest.current = null
    pendingDepartureRequest.current = null
    pendingBatchActionRequest.current = null

    if (!batch || !houseId) {
      return
    }

    let active = true
    void Promise.allSettled([
      listBatchRabbits(houseId, batch.id),
      listBreedingCycles(houseId, batch.id),
    ])
      .then(([itemsResult, cyclesResult]) => {
        if (!active) return
        if (itemsResult.status === 'fulfilled') {
          setBatchRabbits(itemsResult.value)
          setRabbitId(String(itemsResult.value.find((item) => item.isActive && item.batchRole === 'breeding')?.rabbitId ?? ''))
        }
        if (cyclesResult.status === 'fulfilled') setBreedingCycles(cyclesResult.value)
      })
    return () => {
      active = false
    }
  }, [batch, houseId])

  const maleRabbits = useMemo(
    () => rabbits.filter((rabbit) => rabbit.isActive && rabbit.type === '0' && rabbit.gender === '1'),
    [rabbits],
  )
  const activeBatchRabbits = useMemo(
    () => batchRabbits.filter((item) => item.isActive && item.batchRole === 'breeding'),
    [batchRabbits],
  )
  // 阶段字典是业务常量，开一次弹窗拉一次即可。
  useEffect(() => {
    if (!houseId) return
    let cancelled = false
    listReproStageActions(houseId)
      .then((rows) => {
        if (cancelled) return
        const next: Record<string, string[]> = {}
        for (const row of rows ?? []) {
          next[row.stage] = (row.actions ?? []).map((item) => item.action)
        }
        setStageActions(next)
      })
      .catch(() => {
        // 拉不到就不提供流产入口，宁可少给也不给一个点下去必定 409 的选项
      })
    return () => {
      cancelled = true
    }
  }, [houseId])

  const selectedMotherCycles = useMemo(
    () => breedingCycles
      .filter((cycle) => String(cycle.motherRabbitId) === rabbitId)
      .sort((left, right) => right.cycleNo - left.cycleNo),
    [breedingCycles, rabbitId],
  )
  /**
   * 流产只在孕期三个阶段成立，且必须落到具体周期上。
   * 判据来自服务端字典，不在前端写死阶段名。
   */
  const abortionAllowed = useMemo(() => {
    const cycle = selectedMotherCycles.find(
      (item) => String(item.id) === breedingCycleId,
    )
    const stage = cycle?.stage ?? ''
    return Boolean(stage) && (stageActions[stage] ?? []).includes('ABORTION')
  }, [selectedMotherCycles, breedingCycleId, stageActions])

  const filteredActiveMothers = useMemo(() => {
    const keyword = motherSearch.trim().toLowerCase()
    if (!keyword) return activeBatchRabbits
    return activeBatchRabbits.filter((item) => (
      String(item.rabbitId).includes(keyword)
      || String(item.cageId ?? '').includes(keyword)
      || item.currentStatus?.toLowerCase().includes(keyword)
    ))
  }, [activeBatchRabbits, motherSearch])
  const motherPageCount = Math.max(1, Math.ceil(filteredActiveMothers.length / BATCH_MOTHER_PAGE_SIZE))
  const visibleActiveMothers = filteredActiveMothers.slice(
    (motherPage - 1) * BATCH_MOTHER_PAGE_SIZE,
    motherPage * BATCH_MOTHER_PAGE_SIZE,
  )
  const eligibleBulkMothers = useMemo(
    () => activeBatchRabbits.filter(isBulkMatingEligible),
    [activeBatchRabbits],
  )
  const filteredBulkMothers = useMemo(() => {
    const keyword = bulkSearch.trim().toLowerCase()
    return eligibleBulkMothers.filter((item) => {
      if (bulkStatus !== 'all' && item.currentStatus?.trim() !== bulkStatus) return false
      if (!keyword) return true
      return String(item.rabbitId).includes(keyword)
        || String(item.cageId ?? '').includes(keyword)
        || item.currentStatus?.toLowerCase().includes(keyword)
    })
  }, [bulkSearch, bulkStatus, eligibleBulkMothers])
  const bulkPageCount = Math.max(1, Math.ceil(filteredBulkMothers.length / BATCH_MOTHER_PAGE_SIZE))
  const visibleBulkMothers = filteredBulkMothers.slice(
    (bulkPage - 1) * BATCH_MOTHER_PAGE_SIZE,
    bulkPage * BATCH_MOTHER_PAGE_SIZE,
  )

  function toggleBulkMother(rabbitId: number) {
    setBulkSelectedIds((current) => {
      if (current.includes(rabbitId)) return current.filter((id) => id !== rabbitId)
      if (current.length >= MAX_BULK_MATING_MOTHERS) {
        toast.error(`单次最多选择 ${MAX_BULK_MATING_MOTHERS} 只母兔`)
        return current
      }
      return [...current, rabbitId]
    })
  }

  function toggleVisibleBulkMothers() {
    const visibleIds = visibleBulkMothers.map((item) => item.rabbitId)
    const allVisibleSelected = visibleIds.length > 0 && visibleIds.every((id) => bulkSelectedIds.includes(id))
    if (allVisibleSelected) {
      setBulkSelectedIds((current) => current.filter((id) => !visibleIds.includes(id)))
      return
    }
    setBulkSelectedIds((current) => {
      const newIds = visibleIds.filter((id) => !current.includes(id))
      const available = MAX_BULK_MATING_MOTHERS - current.length
      if (newIds.length > available) {
        toast.error(`已达到单次 ${MAX_BULK_MATING_MOTHERS} 只上限`)
      }
      return [...current, ...newIds.slice(0, available)]
    })
  }

  function selectAllFilteredBulkMothers() {
    setBulkSelectedIds((current) => {
      const merged = [...new Set([...current, ...filteredBulkMothers.map((item) => item.rabbitId)])]
      if (merged.length > MAX_BULK_MATING_MOTHERS) {
        toast.error(`单次最多选择 ${MAX_BULK_MATING_MOTHERS} 只母兔`)
      }
      return merged.slice(0, MAX_BULK_MATING_MOTHERS)
    })
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!batch || !houseId || isCompletedBatchStatus(batch.status)) return
    const timestamp = new Date(`${date}T00:00:00`).getTime()
    if (action === 'mating/bulk') {
      if (bulkSelectedIds.length === 0 || !maleRabbitId) return
      const request = getOrCreateBulkMatingRequest(
        pendingBulkRequest.current,
        {
          femaleRabbitIds: bulkSelectedIds,
          maleRabbitId: Number(maleRabbitId),
          matingDate: timestamp,
        },
        () => crypto.randomUUID(),
      )
      pendingBulkRequest.current = request
      setSaving(true)
      try {
        const result = await submitBulkMating(houseId, batch.id, request)
        pendingBulkRequest.current = null
        toast.success(`批量配种已保存，共 ${result.count} 只母兔`)
        onOpenChange(false)
        await onSaved()
      } catch {
        // Keep the requestId so an unchanged retry remains idempotent.
      } finally {
        setSaving(false)
      }
      return
    }
    if (action === 'departure') {
      if (!canRabbitEdit) return
      const reason = departureReason.trim()
      if (!reason) {
        toast.error('请填写母兔离场原因')
        return
      }
      if (!departureConfirmed) {
        toast.error('请确认退出活跃批次及繁殖周期')
        return
      }
      const request = getOrCreateRabbitDepartureRequest(
        pendingDepartureRequest.current,
        {
          rabbitId: Number(rabbitId),
          eventType: departureType,
          actionDate: timestamp,
          reason,
          remark: remark.trim() || undefined,
          forceExitBatch: true,
        },
        () => crypto.randomUUID(),
      )
      pendingDepartureRequest.current = request
      setSaving(true)
      try {
        await submitRabbitDeparture(houseId, request)
        pendingDepartureRequest.current = null
        toast.success(`母兔 #${rabbitId} 已${departureType === 'cull' ? '淘汰' : '登记死亡'}`)
        onOpenChange(false)
        await onSaved()
      } catch {
        // Keep the requestId so an unchanged retry remains idempotent.
      } finally {
        setSaving(false)
      }
      return
    }
    const reproAction = reproActionByWorkflow[action]
    let data: Record<string, unknown>
    if (reproAction) {
      // 生产动作必须落在具体周期上。旧版允许「自动选择」并在服务端猜，
      // 但一头母兔可能同时持有哺乳周期与新怀孕周期（血配），猜错就是静默写错周期。
      const cycleId = Number(breedingCycleId)
      if (!cycleId) {
        toast.error('请先选择要推进的生产周期')
        return
      }
      switch (action) {
        case 'estrus':
        case 'prepartum':
          data = { action: reproAction, occurredAt: timestamp, remark: remark.trim() }
          break
        case 'abortion':
          data = {
            action: reproAction,
            occurredAt: timestamp,
            // 死胎数可不填；填了才能统计流产损失
            ...(stillbirthCount.trim() ? { stillbirthCount: Number(stillbirthCount) } : {}),
            remark: remark.trim(),
          }
          break
        case 'mating':
          data = {
            action: reproAction,
            occurredAt: timestamp,
            maleRabbitId: Number(maleRabbitId),
            matingMethod: 'NATURAL',
            remark: remark.trim(),
          }
          break
        case 'palpation':
          data = {
            action: reproAction,
            occurredAt: timestamp,
            palpationResult: result,
            remark: remark.trim(),
          }
          break
        case 'delivery': {
          const parturition = normalizeParturitionPayload(
            parturitionFailed,
            Number(totalKits),
            Number(liveKits),
          )
          data = {
            action: reproAction,
            outcome: parturitionFailed ? 'FAILED' : 'BORN',
            occurredAt: timestamp,
            ...(parturitionFailed
              ? {}
              : { totalKits: parturition.totalKits, liveKits: parturition.liveKits }),
            remark: remark.trim(),
          }
          break
        }
        case 'weaning':
          data = {
            action: reproAction,
            occurredAt: timestamp,
            weanedCount: Number(weaningCount),
            maleCount: Number(maleCount),
            femaleCount: Number(femaleCount),
            targetCageId: targetCageId ? Number(targetCageId) : undefined,
            avgWeaningWeight: avgWeight ? Number(avgWeight) : undefined,
            remark: remark.trim(),
          }
          break
        default:
          return
      }
      const reproRequest = getOrCreateBatchActionRequest(
        pendingBatchActionRequest.current,
        { batchId: cycleId, action, payload: data },
        () => crypto.randomUUID(),
      )
      pendingBatchActionRequest.current = reproRequest
      setSaving(true)
      try {
        await submitReproAction(houseId, cycleId, reproRequest.payload, reproRequest.requestId)
        pendingBatchActionRequest.current = null
        toast.success(`${actionLabels[action]}已保存`)
        onOpenChange(false)
        await onSaved()
      } catch {
        // 保留 requestId，让未改动载荷的重试保持幂等。
      } finally {
        setSaving(false)
      }
      return
    }

    switch (action) {
      case 'complete':
        data = { endDate: timestamp, force, remark: remark.trim() }
        break
      default:
        return
    }
    const request = getOrCreateBatchActionRequest(
      pendingBatchActionRequest.current,
      { batchId: batch.id, action, payload: data },
      () => crypto.randomUUID(),
    )
    pendingBatchActionRequest.current = request
    setSaving(true)
    try {
      await submitBatchAction(houseId, batch.id, action, request.payload, request.requestId)
      pendingBatchActionRequest.current = null
      toast.success(`${actionLabels[action]}已保存`)
      onOpenChange(false)
      await onSaved()
    } catch {
      // Keep the requestId so an unchanged retry remains idempotent.
    } finally {
      setSaving(false)
    }
  }

  const requiresRabbit = action !== 'complete' && action !== 'mating/bulk'
  // 所有生产动作现在都记录发生时间（occurredAt），不再有无日期的动作。
  const usesDate = true
  const batchCompleted = isCompletedBatchStatus(batch?.status)

  return (
    <Dialog open={Boolean(batch)} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>生产操作 · {batch?.batchCode ?? ''}</DialogTitle>
          <DialogDescription>生产记录归属当前兔场。</DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="overflow-y-auto pr-1">
            <Field>
              <FieldLabel htmlFor="batch-action">操作类型</FieldLabel>
              <Select value={action} onValueChange={(value) => setAction(value as BatchWorkflowAction)}>
                <SelectTrigger id="batch-action"><SelectValue /></SelectTrigger>
                <SelectContent><SelectGroup>{Object.entries(actionLabels).filter(([value]) => (value !== 'departure' || canRabbitEdit) && (value !== 'abortion' || abortionAllowed)).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}</SelectGroup></SelectContent>
              </Select>
            </Field>
            {requiresRabbit ? (
              <Field>
                <FieldLabel htmlFor="batch-mother-search">活跃繁殖母兔</FieldLabel>
                <div className="mt-2 overflow-hidden rounded-md border">
                  <div className="border-b p-3">
                    <div className="relative">
                      <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
                      <Input
                        id="batch-mother-search"
                        className="pl-9"
                        value={motherSearch}
                        placeholder="搜索兔只 ID、笼位或状态"
                        onChange={(event) => {
                          setMotherSearch(event.target.value)
                          setMotherPage(1)
                        }}
                      />
                    </div>
                  </div>
                  <div className="max-h-52 divide-y overflow-y-auto">
                    {visibleActiveMothers.map((item) => (
                      <label key={item.id} className="flex min-h-12 cursor-pointer items-center gap-3 px-3 py-2 text-sm hover:bg-secondary/50">
                        <input
                          className="size-5 shrink-0"
                          type="radio"
                          name="batch-rabbit"
                          checked={rabbitId === String(item.rabbitId)}
                          onChange={() => {
                            setRabbitId(String(item.rabbitId))
                            setBreedingCycleId('')
                          }}
                        />
                        <span className="min-w-0 flex-1 truncate">兔 #{item.rabbitId} · 笼位 #{item.cageId ?? '-'}</span>
                        <Badge variant="secondary">{item.currentStatus || '批次中'}</Badge>
                        {(item.currentNursingKits ?? 0) > 0 ? <span className="hidden text-xs text-muted-foreground sm:inline">哺乳 {item.currentNursingKits} 只/{item.nursingLitterCount ?? 1} 窝</span> : null}
                      </label>
                    ))}
                    {visibleActiveMothers.length === 0 ? <p className="px-3 py-6 text-center text-sm text-muted-foreground">没有匹配的活跃母兔</p> : null}
                  </div>
                  <div className="flex flex-wrap items-center justify-between gap-2 border-t px-3 py-2">
                    <span className="text-xs text-muted-foreground">已选 {rabbitId ? `兔 #${rabbitId}` : '0 只'} · 第 {motherPage}/{motherPageCount} 页</span>
                    <div className="flex gap-2">
                      <Button type="button" variant="outline" size="sm" disabled={motherPage <= 1} onClick={() => setMotherPage((current) => Math.max(1, current - 1))}>
                        <ChevronLeftIcon data-icon="inline-start" />上一页
                      </Button>
                      <Button type="button" variant="outline" size="sm" disabled={motherPage >= motherPageCount} onClick={() => setMotherPage((current) => Math.min(motherPageCount, current + 1))}>
                        下一页<ChevronRightIcon data-icon="inline-end" />
                      </Button>
                    </div>
                  </div>
                </div>
              </Field>
            ) : null}
            {['palpation', 'prepartum', 'delivery', 'weaning', 'estrus', 'mating', 'abortion'].includes(action) && selectedMotherCycles.length > 0 ? (
              <Field>
                <FieldLabel htmlFor="breeding-cycle">繁殖周期</FieldLabel>
                <Select value={breedingCycleId || 'auto'} onValueChange={(value) => setBreedingCycleId(value === 'auto' ? '' : value)}>
                  <SelectTrigger id="breeding-cycle"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="auto">自动匹配当前业务周期</SelectItem>
                      {selectedMotherCycles.map((cycle) => (
                        <SelectItem key={cycle.id} value={String(cycle.id)}>
                          第 {cycle.cycleNo} 周期 · {cycle.status} · 配种 {formatDate(cycle.matingDate)}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">共 {selectedMotherCycles.length} 个周期；重叠哺乳与妊娠时可明确指定。</p>
              </Field>
            ) : null}
            {action === 'mating/bulk' ? (
              <Field>
                <div className="flex flex-wrap items-end gap-3">
                  <div className="min-w-52 flex-1">
                    <FieldLabel htmlFor="bulk-mating-search">搜索可配种母兔</FieldLabel>
                    <div className="relative mt-2">
                      <SearchIcon className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
                      <Input
                        id="bulk-mating-search"
                        className="pl-9"
                        value={bulkSearch}
                        placeholder="兔只 ID、笼位或状态"
                        onChange={(event) => {
                          setBulkSearch(event.target.value)
                          setBulkPage(1)
                        }}
                      />
                    </div>
                  </div>
                  <div className="w-full sm:w-40">
                    <FieldLabel htmlFor="bulk-mating-status">繁殖状态</FieldLabel>
                    <Select
                      value={bulkStatus}
                      onValueChange={(value) => {
                        setBulkStatus(value as typeof bulkStatus)
                        setBulkPage(1)
                      }}
                    >
                      <SelectTrigger id="bulk-mating-status" className="mt-2"><SelectValue /></SelectTrigger>
                      <SelectContent><SelectGroup><SelectItem value="all">全部可配种</SelectItem><SelectItem value="待配种">待配种</SelectItem><SelectItem value="哺乳中">哺乳中</SelectItem></SelectGroup></SelectContent>
                    </Select>
                  </div>
                </div>
                <div className="mt-3 overflow-hidden rounded-md border">
                  <div className="flex flex-wrap items-center justify-between gap-2 border-b bg-secondary/50 px-3 py-2">
                    <span className="text-sm text-muted-foreground">
                      可选 {filteredBulkMothers.length} 只 · 已选 {bulkSelectedIds.length}/{MAX_BULK_MATING_MOTHERS}
                    </span>
                    <div className="flex flex-wrap gap-2">
                      <Button type="button" variant="outline" size="sm" disabled={filteredBulkMothers.length === 0} onClick={selectAllFilteredBulkMothers}>选择筛选结果</Button>
                      <Button type="button" variant="outline" size="sm" disabled={visibleBulkMothers.length === 0} onClick={toggleVisibleBulkMothers}>
                        {visibleBulkMothers.length > 0 && visibleBulkMothers.every((item) => bulkSelectedIds.includes(item.rabbitId)) ? '取消本页' : '选择本页'}
                      </Button>
                      <Button type="button" variant="ghost" size="sm" disabled={bulkSelectedIds.length === 0} onClick={() => setBulkSelectedIds([])}>清空已选</Button>
                    </div>
                  </div>
                  <div className="max-h-64 divide-y overflow-y-auto">
                    {visibleBulkMothers.map((item) => (
                      <label key={item.id} className="flex min-h-12 cursor-pointer items-center gap-3 px-3 py-2 text-sm hover:bg-secondary/50">
                        <input
                          className="size-5 shrink-0"
                          type="checkbox"
                          checked={bulkSelectedIds.includes(item.rabbitId)}
                          onChange={() => toggleBulkMother(item.rabbitId)}
                        />
                        <span className="min-w-0 flex-1 truncate">兔 #{item.rabbitId} · 笼位 #{item.cageId ?? '-'}</span>
                        <Badge variant="secondary">{item.currentStatus?.trim() || '批次中'}</Badge>
                        {(item.currentNursingKits ?? 0) > 0 ? <span className="hidden text-xs text-muted-foreground sm:inline">哺乳 {item.currentNursingKits} 只</span> : null}
                      </label>
                    ))}
                    {visibleBulkMothers.length === 0 ? <p className="px-3 py-6 text-center text-sm text-muted-foreground">没有匹配的可配种母兔</p> : null}
                  </div>
                  <div className="flex flex-wrap items-center justify-between gap-2 border-t px-3 py-2">
                    <span className="text-xs text-muted-foreground">第 {bulkPage}/{bulkPageCount} 页，每页最多 {BATCH_MOTHER_PAGE_SIZE} 只</span>
                    <div className="flex gap-2">
                      <Button type="button" variant="outline" size="sm" disabled={bulkPage <= 1} onClick={() => setBulkPage((current) => Math.max(1, current - 1))}>
                        <ChevronLeftIcon data-icon="inline-start" />上一页
                      </Button>
                      <Button type="button" variant="outline" size="sm" disabled={bulkPage >= bulkPageCount} onClick={() => setBulkPage((current) => Math.min(bulkPageCount, current + 1))}>
                        下一页<ChevronRightIcon data-icon="inline-end" />
                      </Button>
                    </div>
                  </div>
                </div>
              </Field>
            ) : null}
            {action === 'mating' || action === 'mating/bulk' ? (
              <Field>
                <FieldLabel htmlFor="male-rabbit">种公兔</FieldLabel>
                <Select value={maleRabbitId} onValueChange={setMaleRabbitId}>
                  <SelectTrigger id="male-rabbit"><SelectValue placeholder="选择种公兔" /></SelectTrigger>
                  <SelectContent><SelectGroup>{maleRabbits.map((rabbit) => <SelectItem key={rabbit.id} value={String(rabbit.id)}>兔 #{rabbit.id} · 笼位 #{rabbit.cageId}</SelectItem>)}</SelectGroup></SelectContent>
                </Select>
              </Field>
            ) : null}
            {action === 'departure' ? (
              <>
                <Field>
                  <FieldLabel htmlFor="departure-type">离场类型</FieldLabel>
                  <Select value={departureType} onValueChange={(value) => setDepartureType(value as RabbitDepartureType)}>
                    <SelectTrigger id="departure-type"><SelectValue /></SelectTrigger>
                    <SelectContent><SelectGroup><SelectItem value="cull">淘汰</SelectItem><SelectItem value="death">死亡</SelectItem></SelectGroup></SelectContent>
                  </Select>
                </Field>
                <Field>
                  <FieldLabel htmlFor="departure-reason">离场原因</FieldLabel>
                  <Input id="departure-reason" value={departureReason} required maxLength={255} placeholder="例如：繁殖效率下降" onChange={(event) => setDepartureReason(event.target.value)} />
                </Field>
                <Field>
                  <label className="flex items-start gap-3 rounded-md border border-destructive/40 p-3 text-sm" htmlFor="departure-force-confirm">
                    <input
                      id="departure-force-confirm"
                      className="mt-0.5 size-5 shrink-0"
                      type="checkbox"
                      checked={departureConfirmed}
                      onChange={(event) => setDepartureConfirmed(event.target.checked)}
                    />
                    <span><span className="font-medium text-destructive">确认强制离场</span><br /><span className="text-muted-foreground">将同时退出该母兔的活跃批次关系并关闭进行中的繁殖周期。</span></span>
                  </label>
                </Field>
              </>
            ) : null}
            {action === 'palpation' ? (
              <Field>
                <FieldLabel htmlFor="pregnancy-result">孕检结果</FieldLabel>
                <Select value={result} onValueChange={setResult}>
                  <SelectTrigger id="pregnancy-result"><SelectValue /></SelectTrigger>
                  <SelectContent><SelectGroup><SelectItem value="怀孕">怀孕</SelectItem><SelectItem value="空怀">空怀</SelectItem><SelectItem value="不确定">不确定</SelectItem></SelectGroup></SelectContent>
                </Select>
              </Field>
            ) : null}
            {action === 'abortion' ? (
              <Field>
                <FieldLabel htmlFor="abortion-stillbirth">死胎数</FieldLabel>
                <Input
                  id="abortion-stillbirth"
                  type="number"
                  min={0}
                  value={stillbirthCount}
                  placeholder="可不填；填了才能统计流产损失"
                  onChange={(event) => setStillbirthCount(event.target.value)}
                />
                <p className="text-xs text-muted-foreground">本轮周期将结束，母兔复旧后重新进入待催情。</p>
              </Field>
            ) : null}
            {action === 'delivery' ? (
              <>
                <Field>
                  <label className="flex items-center gap-3 text-sm font-medium" htmlFor="parturition-failed">
                    <input
                      id="parturition-failed"
                      type="checkbox"
                      checked={parturitionFailed}
                      onChange={(event) => {
                        const failed = event.target.checked
                        setParturitionFailed(failed)
                        if (failed) {
                          setTotalKits('0')
                          setLiveKits('0')
                        }
                      }}
                    />
                    失败分娩（流产）
                  </label>
                </Field>
                <div className="grid gap-4 sm:grid-cols-2">
                  <NumberInput id="total-kits" label="产仔数" value={totalKits} onChange={setTotalKits} disabled={parturitionFailed} />
                  <NumberInput id="live-kits" label="活仔数" value={liveKits} onChange={setLiveKits} disabled={parturitionFailed} />
                </div>
              </>
            ) : null}
            {action === 'weaning' ? (
              <>
                <div className="grid gap-4 sm:grid-cols-3">
                  <NumberInput id="weaning-count" label="断奶数" value={weaningCount} onChange={setWeaningCount} />
                  <NumberInput id="weaning-male" label="公兔数" value={maleCount} onChange={setMaleCount} />
                  <NumberInput id="weaning-female" label="母兔数" value={femaleCount} onChange={setFemaleCount} />
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field>
                    <FieldLabel htmlFor="target-cage">目标笼位</FieldLabel>
                    <Select value={targetCageId} onValueChange={setTargetCageId}>
                      <SelectTrigger id="target-cage"><SelectValue placeholder="不调整" /></SelectTrigger>
                      <SelectContent><SelectGroup>{cages.filter((cage) => cage.isEnabled).map((cage) => <SelectItem key={cage.id} value={String(cage.id)}>{cage.cageNumber}</SelectItem>)}</SelectGroup></SelectContent>
                    </Select>
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="average-weight">平均体重（kg）</FieldLabel>
                    <Input id="average-weight" type="number" min={0} step="0.01" value={avgWeight} onChange={(event) => setAvgWeight(event.target.value)} />
                  </Field>
                </div>
              </>
            ) : null}
            {action === 'complete' ? (
              <Field>
                <label className="flex items-center gap-3 text-sm font-medium" htmlFor="force-complete">
                  <input id="force-complete" type="checkbox" checked={force} onChange={(event) => setForce(event.target.checked)} />
                  强制退出仍在批次中的兔只
                </label>
              </Field>
            ) : null}
            {usesDate ? (
              <Field>
                <FieldLabel htmlFor="batch-action-date">业务日期</FieldLabel>
                <Input id="batch-action-date" type="date" value={date} required onChange={(event) => setDate(event.target.value)} />
              </Field>
            ) : null}
            {!['mating', 'mating/bulk'].includes(action) ? (
              <Field>
                <FieldLabel htmlFor="batch-action-remark">备注</FieldLabel>
                <Textarea id="batch-action-remark" value={remark} onChange={(event) => setRemark(event.target.value)} />
              </Field>
            ) : null}
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
            <Button
              type="submit"
              disabled={
                saving ||
                batchCompleted ||
                (requiresRabbit && !rabbitId) ||
                ((action === 'mating' || action === 'mating/bulk') && !maleRabbitId) ||
                (action === 'mating/bulk' && bulkSelectedIds.length === 0) ||
                (action === 'departure' && (!departureReason.trim() || !departureConfirmed))
              }
            >
              {saving ? <Spinner data-icon="inline-start" /> : null}
              {action === 'mating/bulk' ? `提交 ${bulkSelectedIds.length} 只配种` : action === 'departure' ? '确认母兔离场' : '保存记录'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function NumberInput({
  id,
  label,
  value,
  onChange,
  disabled = false,
}: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  disabled?: boolean
}) {
  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Input id={id} type="number" min={0} value={value} required disabled={disabled} onChange={(event) => onChange(event.target.value)} />
    </Field>
  )
}

function formatDate(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleDateString('zh-CN')
}

function formatMotherCount(value?: number | null) {
  if (value === undefined) return '待加载'
  return value === null ? '暂不可用' : `${value.toLocaleString('zh-CN')} 只`
}
