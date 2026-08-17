import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  ArrowLeftRightIcon,
  Edit3Icon,
  HeartCrackIcon,
  PlusIcon,
  RabbitIcon,
  RefreshCwIcon,
  SearchIcon,
  Trash2Icon,
  WarehouseIcon,
} from 'lucide-react'
import { toast } from 'sonner'
import {
  createCage,
  createRabbit,
  deleteCage,
  listCages,
  listRabbits,
  listReproEntryPoints,
  listReproStageActions,
  submitRabbitDeparture,
  transferRabbitCage,
  updateCage,
  updateRabbit,
} from '@/api/workspace'
import type { ReproEntryPoint } from '@/api/workspace'
import { PageHeader } from '@/components/page-header'
import { HousePermissionBadge } from '@/components/permission-badge'
import { hasPermission, useWorkspace } from '@/lib/workspace'
import { getOrCreateRabbitDepartureRequest } from '@/lib/batch-workflow'
import { formatDateInput } from '@/lib/date'
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
} from '@/components/ui/dialog'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import type { Cage, Rabbit, RabbitDepartureRequest, RabbitDepartureType } from '@/types/api'

const rabbitTypeLabels: Record<string, string> = {
  '0': '种兔',
  '1': '后备兔',
  '2': '商品兔',
}

const cageStatusLabels: Record<string, string> = {
  '0': '空闲',
  '1': '种兔',
  '2': '后备兔',
  '3': '商品兔',
}

const growthStageLabels: Record<string, string> = {
  JUVENILE: '幼兔',
  GROWING: '成长期',
  FATTENING: '育肥期',
  MATURE: '成熟',
}

const reproductiveStageLabels: Record<string, string> = {
  RESERVE: '后备',
  EMPTY: '空怀',
  MATED: '已配种',
  PREGNANT: '妊娠',
  LACTATING: '哺乳',
  RESTING: '休整',
  READY: '可配',
}

const growthStageOptions = Object.entries(growthStageLabels)

const buckReproductiveStageOptions = [
  ['READY', '可配'],
  ['RESTING', '休整'],
] as const

const replacementReproductiveStageOptions = [['RESERVE', '后备']] as const

type BreedingCageFilter = 'all' | 'doe' | 'buck'

/** 换笼位的三种结局对用户是不同的事实，不能统一提示“已换笼”。 */
const transferModeMessages: Record<string, string> = {
  MOVE: '已移入目标笼位',
  APPEND: '已并入目标商品兔笼',
  REPLAY: '该换笼请求之前已完成',
}

export function WorkspaceLivestockPage() {
  const workspace = useWorkspace()
  const [cages, setCages] = useState<Cage[]>([])
  const [rabbits, setRabbits] = useState<Rabbit[]>([])
  const [loading, setLoading] = useState(false)
  const [queryInput, setQueryInput] = useState('')
  const [query, setQuery] = useState('')
  const [breedingCageFilter, setBreedingCageFilter] = useState<BreedingCageFilter>('all')
  const [cageDialog, setCageDialog] = useState<{ open: boolean; cage: Cage | null }>({ open: false, cage: null })
  const [rabbitDialog, setRabbitDialog] = useState<{ open: boolean; rabbit: Rabbit | null }>({ open: false, rabbit: null })
  const [deleteTarget, setDeleteTarget] = useState<Cage | null>(null)
  const [transferTarget, setTransferTarget] = useState<Rabbit | null>(null)
  const [departureTarget, setDepartureTarget] = useState<Rabbit | null>(null)
  const [cageDetail, setCageDetail] = useState<Cage | null>(null)
  /** 阶段→中文名。服务端下发，客户端不再自带一张会漂移的对照表。 */
  const [reproStageLabels, setReproStageLabels] = useState<Record<string, string>>({})
  const [entryPoints, setEntryPoints] = useState<ReproEntryPoint[]>([])
  const canEdit = hasPermission(workspace.permission, 'rabbit:rabbits:edit')
  const canControl = hasPermission(workspace.permission, 'rabbit:cages:edit')
  const canReadRepro = hasPermission(workspace.permission, 'rabbit:batches:query')

  const load = useCallback(async () => {
    if (!workspace.selectedHouse) {
      setCages([])
      setRabbits([])
      return
    }
    setLoading(true)
    try {
      const [nextCages, nextRabbits] = await Promise.all([
        listCages(workspace.selectedHouse.id),
        listRabbits(workspace.selectedHouse.id),
      ])
      setCages(nextCages)
      setRabbits(nextRabbits)
    } catch {
      setCages([])
      setRabbits([])
    } finally {
      setLoading(false)
    }
  }, [workspace.selectedHouse])

  const loadReproDictionaries = useCallback(async () => {
    if (!workspace.selectedHouse || !canReadRepro) {
      setReproStageLabels({})
      setEntryPoints([])
      return
    }
    try {
      const [stages, entries] = await Promise.all([
        listReproStageActions(workspace.selectedHouse.id),
        listReproEntryPoints(workspace.selectedHouse.id),
      ])
      setReproStageLabels(Object.fromEntries(stages.map((item) => [item.stage, item.stageLabel])))
      setEntryPoints(entries)
    } catch {
      // 字典拿不到时退回英文枚举与旧阶段字段，列表不应该因此变成空页。
      setReproStageLabels({})
      setEntryPoints([])
    }
  }, [canReadRepro, workspace.selectedHouse])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    void loadReproDictionaries()
  }, [loadReproDictionaries])

  const filteredCages = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    return cages.filter((cage) => {
      const matchesQuery = !normalized || [cage.cageNumber, cage.rowCode, cage.remark, cage.id.toString()].some((value) =>
        value?.toLowerCase().includes(normalized),
      )
      if (!matchesQuery) return false
      if (breedingCageFilter === 'doe') {
        return cage.status === '1' && cage.breedingOccupantGender === '0'
      }
      if (breedingCageFilter === 'buck') {
        return cage.status === '1' && cage.breedingOccupantGender === '1'
      }
      return true
    })
  }, [breedingCageFilter, cages, query])

  const filteredRabbits = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) {
      return rabbits
    }
    return rabbits.filter((rabbit) =>
      [rabbit.id.toString(), rabbit.breed, rabbitTypeLabel(rabbit)].some((value) =>
        value?.toLowerCase().includes(normalized),
      ),
    )
  }, [query, rabbits])

  return (
    <>
      <PageHeader
        title="兔群管理"
        description={workspace.selectedHouse?.name ?? '请选择兔场'}
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
          <EmptyTitle>请选择兔场</EmptyTitle>
          <EmptyDescription>兔群数据始终限定在一个兔场内。</EmptyDescription>
        </Empty>
      ) : (
        <Tabs defaultValue="rabbits">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <TabsList>
              <TabsTrigger value="rabbits">兔只 {rabbits.length}</TabsTrigger>
              <TabsTrigger value="cages">笼位 {cages.length}</TabsTrigger>
            </TabsList>
            <form
              className="flex w-full gap-2 sm:max-w-sm"
              onSubmit={(event) => {
                event.preventDefault()
                setQuery(queryInput)
              }}
            >
              <Input value={queryInput} placeholder="ID、品种或笼位编号" aria-label="搜索兔群" onChange={(event) => setQueryInput(event.target.value)} />
              <Button type="submit" variant="outline" size="icon" aria-label="查询">
                <SearchIcon aria-hidden="true" />
              </Button>
            </form>
          </div>

          <TabsContent value="rabbits">
            <Card>
              <CardHeader className="sm:flex-row sm:items-center sm:justify-between">
                <div className="flex flex-col gap-1.5">
                  <CardTitle>兔只</CardTitle>
                  <CardDescription>维护入场信息、笼位、品种和体重。</CardDescription>
                </div>
                <Button onClick={() => setRabbitDialog({ open: true, rabbit: null })} disabled={!canEdit || cages.length === 0}>
                  <PlusIcon data-icon="inline-start" />
                  录入兔只
                </Button>
              </CardHeader>
              <CardContent>
                {filteredRabbits.length === 0 ? (
                  <Empty>
                    <RabbitIcon aria-hidden="true" />
                    <EmptyTitle>没有匹配的兔只</EmptyTitle>
                    <EmptyDescription>清除查询条件或录入第一只兔。</EmptyDescription>
                  </Empty>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>兔只</TableHead>
                        <TableHead>笼位</TableHead>
                        <TableHead>品种</TableHead>
                        <TableHead>体重</TableHead>
                        <TableHead>状态</TableHead>
                        <TableHead className="text-right">操作</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredRabbits.map((rabbit) => (
                        <TableRow key={rabbit.id}>
                          <TableCell>
                            <div className="flex min-w-32 flex-col gap-1">
                              <span className="font-medium">兔 #{rabbit.id}</span>
                              <span className="text-xs text-muted-foreground">{rabbitTypeLabel(rabbit)} · {rabbit.gender === '0' ? '母' : rabbit.gender === '1' ? '公' : '未知'}</span>
                            </div>
                          </TableCell>
                          <TableCell>{cages.find((cage) => cage.id === rabbit.cageId)?.cageNumber ?? `#${rabbit.cageId}`}</TableCell>
                          <TableCell>{rabbit.breed || '-'}</TableCell>
                          <TableCell>{rabbit.weight ? `${rabbit.weight.toFixed(2)} kg` : '-'}</TableCell>
                          <TableCell>
                            <div className="flex min-w-28 flex-col items-start gap-1">
                              <Badge variant={rabbit.isActive ? 'default' : 'secondary'}>{rabbit.isActive ? '在栏' : '离场'}</Badge>
                              <span className="text-xs text-muted-foreground">{rabbitStageSummary(rabbit, reproStageLabels)}</span>
                            </div>
                          </TableCell>
                          <TableCell>
                            <RabbitRowActions
                              rabbit={rabbit}
                              canEdit={canEdit}
                              onEdit={() => setRabbitDialog({ open: true, rabbit })}
                              onTransfer={() => setTransferTarget(rabbit)}
                              onDeparture={() => setDepartureTarget(rabbit)}
                            />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="cages">
            <Card>
              <CardHeader className="sm:flex-row sm:items-center sm:justify-between">
                <div className="flex flex-col gap-1.5">
                  <CardTitle>笼位</CardTitle>
                  <CardDescription>新增笼位和启停笼位需要控制权限。</CardDescription>
                </div>
                <Button onClick={() => setCageDialog({ open: true, cage: null })} disabled={!canControl}>
                  <PlusIcon data-icon="inline-start" />
                  新增笼位
                </Button>
              </CardHeader>
              <CardContent>
                <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                  <p className="text-sm text-muted-foreground">种兔笼按实际在栏种兔性别筛选。</p>
                  <Select value={breedingCageFilter} onValueChange={(value) => setBreedingCageFilter(value as BreedingCageFilter)}>
                    <SelectTrigger className="w-full sm:w-44" aria-label="筛选种兔笼">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value="all">全部笼位</SelectItem>
                        <SelectItem value="doe">种母兔笼</SelectItem>
                        <SelectItem value="buck">种公兔笼</SelectItem>
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </div>
                {filteredCages.length === 0 ? (
                  <Empty>
                    <EmptyTitle>没有匹配的笼位</EmptyTitle>
                    <EmptyDescription>清除查询条件或新增笼位。</EmptyDescription>
                  </Empty>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>笼位</TableHead>
                        <TableHead>位置</TableHead>
                        <TableHead>用途</TableHead>
                        <TableHead>兔只</TableHead>
                        <TableHead>状态</TableHead>
                        <TableHead className="text-right">操作</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredCages.map((cage) => (
                        <TableRow key={cage.id}>
                          <TableCell>
                            <div className="flex min-w-32 flex-col gap-1">
                              <span className="font-medium">{cage.cageNumber}</span>
                              <span className="text-xs text-muted-foreground">ID {cage.id}</span>
                            </div>
                          </TableCell>
                          <TableCell>{cage.rowCode || '-'} / {cage.positionIndex ?? '-'} / {cage.layerIndex ?? '-'}</TableCell>
                          <TableCell>{cageUsageLabel(cage)}</TableCell>
                          <TableCell>{cage.rabbitCount} 只</TableCell>
                          <TableCell><Badge variant={cage.isEnabled ? 'default' : 'secondary'}>{cage.isEnabled ? '启用' : '停用'}</Badge></TableCell>
                          <TableCell>
                            <div className="flex flex-wrap justify-end gap-2">
                              <Button variant="outline" size="sm" onClick={() => setCageDetail(cage)}>
                                <RabbitIcon data-icon="inline-start" />
                                笼内兔只
                              </Button>
                              <Button variant="outline" size="sm" disabled={!canControl} onClick={() => setCageDialog({ open: true, cage })}>
                                <Edit3Icon data-icon="inline-start" />
                                编辑
                              </Button>
                              <Button variant="destructive" size="sm" disabled={!canControl || cage.rabbitCount > 0} onClick={() => setDeleteTarget(cage)}>
                                <Trash2Icon data-icon="inline-start" />
                                删除
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      )}

      <CageDialog state={cageDialog} onOpenChange={(open) => setCageDialog((current) => ({ ...current, open }))} houseId={workspace.selectedHouse?.id ?? null} onSaved={load} />
      <RabbitDialog
        state={rabbitDialog}
        onOpenChange={(open) => setRabbitDialog((current) => ({ ...current, open }))}
        houseId={workspace.selectedHouse?.id ?? null}
        cages={cages}
        entryPoints={entryPoints}
        onSaved={load}
      />
      <DeleteCageDialog cage={deleteTarget} houseId={workspace.selectedHouse?.id ?? null} onOpenChange={(open) => !open && setDeleteTarget(null)} onDeleted={load} />
      <RabbitTransferDialog
        rabbit={transferTarget}
        cages={cages}
        houseId={workspace.selectedHouse?.id ?? null}
        onOpenChange={(open) => !open && setTransferTarget(null)}
        onSaved={load}
      />
      <RabbitDepartureDialog
        rabbit={departureTarget}
        houseId={workspace.selectedHouse?.id ?? null}
        onOpenChange={(open) => !open && setDepartureTarget(null)}
        onSaved={load}
      />
      <CageRabbitsDialog
        cage={cageDetail}
        rabbits={rabbits}
        stageLabels={reproStageLabels}
        canEdit={canEdit}
        onOpenChange={(open) => !open && setCageDetail(null)}
        onEdit={(rabbit) => {
          setCageDetail(null)
          setRabbitDialog({ open: true, rabbit })
        }}
        onTransfer={(rabbit) => {
          setCageDetail(null)
          setTransferTarget(rabbit)
        }}
        onDeparture={(rabbit) => {
          setCageDetail(null)
          setDepartureTarget(rabbit)
        }}
      />
    </>
  )
}

/**
 * 兔只行上的三个动作。
 *
 * 离场之前只挂在批次详情的“母兔离场”里，笼内的商品兔根本无处登记死亡（飞书 recvrpTL16SBwu）。
 */
function RabbitRowActions({
  rabbit,
  canEdit,
  onEdit,
  onTransfer,
  onDeparture,
}: {
  rabbit: Rabbit
  canEdit: boolean
  onEdit: () => void
  onTransfer: () => void
  onDeparture: () => void
}) {
  return (
    <div className="flex flex-wrap justify-end gap-2">
      <Button variant="outline" size="sm" disabled={!canEdit} onClick={onEdit}>
        <Edit3Icon data-icon="inline-start" />
        编辑
      </Button>
      <Button variant="outline" size="sm" disabled={!canEdit || !rabbit.isActive} onClick={onTransfer}>
        <ArrowLeftRightIcon data-icon="inline-start" />
        换笼
      </Button>
      <Button variant="destructive" size="sm" disabled={!canEdit || !rabbit.isActive} onClick={onDeparture}>
        <HeartCrackIcon data-icon="inline-start" />
        登记离场
      </Button>
    </div>
  )
}

function rabbitTypeLabel(rabbit: Rabbit) {
  if (rabbit.type === '0') {
    return rabbit.gender === '0' ? '种母兔' : rabbit.gender === '1' ? '种公兔' : '种兔'
  }
  return rabbitTypeLabels[rabbit.type] ?? rabbit.type ?? '未分类'
}

function cageUsageLabel(cage: Cage) {
  if (cage.status === '1' && cage.breedingOccupantGender === '0') return '种母兔笼'
  if (cage.status === '1' && cage.breedingOccupantGender === '1') return '种公兔笼'
  return cageStatusLabels[cage.status ?? ''] ?? cage.status ?? '-'
}

/**
 * 可手工录入的旧繁殖阶段。
 *
 * 种母兔刻意返回空：它的阶段由生产流程状态机单写，后端也已拒收手录的
 * `reproductiveStage`。这里再给一份下拉，只会让用户填完才吃 400（飞书 recvsrpMlvu2SC）。
 */
function reproductiveOptions(type: string, gender: string) {
  if (type === '2') return []
  if (type === '1') return replacementReproductiveStageOptions
  return gender === '1' ? buckReproductiveStageOptions : []
}

function defaultReproductiveStage(type: string, gender: string) {
  return reproductiveOptions(type, gender)[0]?.[0] ?? ''
}

function stageLabel(value: string | null | undefined, labels: Record<string, string>) {
  return value ? labels[value] ?? value : null
}

/** Radix Select 不接受空字符串选项，“不入轨”需要一个显式哨兵值。 */
const NO_REPRO_ENTRY = 'NONE'

function toIsoDate(value: string) {
  return value ? new Date(`${value}T00:00:00`).toISOString() : undefined
}

/**
 * 兔只阶段摘要。
 *
 * 种母兔以生产阶段投影 `currentStage` 为准（它由生产流程状态机单写），
 * 旧的 `reproductiveStage` 只在没有投影时兽底，否则两套词汇会同时显示、互相矛盾。
 */
function rabbitStageSummary(rabbit: Rabbit, reproStageLabels: Record<string, string>) {
  const repro = rabbit.currentStage
    ? reproStageLabels[rabbit.currentStage] ?? rabbit.currentStage
    : stageLabel(rabbit.reproductiveStage, reproductiveStageLabels)
  const labels = [stageLabel(rabbit.growthStage, growthStageLabels), repro].filter(Boolean)
  return labels.length > 0 ? labels.join(' · ') : '阶段未填写'
}

function CageDialog({
  state,
  onOpenChange,
  houseId,
  onSaved,
}: {
  state: { open: boolean; cage: Cage | null }
  onOpenChange: (open: boolean) => void
  houseId: number | null
  onSaved: () => Promise<void>
}) {
  const [number, setNumber] = useState('')
  const [rowCode, setRowCode] = useState('')
  const [position, setPosition] = useState('')
  const [layer, setLayer] = useState('')
  const [remark, setRemark] = useState('')
  const [enabled, setEnabled] = useState(true)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!state.open) return
    setNumber(state.cage?.cageNumber ?? '')
    setRowCode(state.cage?.rowCode ?? '')
    setPosition(state.cage?.positionIndex?.toString() ?? '')
    setLayer(state.cage?.layerIndex?.toString() ?? '')
    setRemark(state.cage?.remark ?? '')
    setEnabled(state.cage?.isEnabled ?? true)
  }, [state.cage, state.open])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!houseId) return
    setSaving(true)
    const data = {
      cageNumber: number.trim(),
      rowCode: rowCode.trim() || undefined,
      positionIndex: position ? Number(position) : undefined,
      layerIndex: layer ? Number(layer) : undefined,
      remark: remark.trim(),
      isEnabled: enabled,
    }
    try {
      if (state.cage) {
        await updateCage(houseId, state.cage.id, data)
        toast.success('笼位已更新')
      } else {
        await createCage(houseId, data)
        toast.success('笼位已新增')
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
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{state.cage ? '编辑笼位' : '新增笼位'}</DialogTitle>
          <DialogDescription>维护笼位编号、物理位置和启用状态。</DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="overflow-y-auto pr-1">
            <Field>
              <FieldLabel htmlFor="cage-number">笼位编号</FieldLabel>
              <Input id="cage-number" value={number} required maxLength={50} onChange={(event) => setNumber(event.target.value)} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-3">
              <Field>
                <FieldLabel htmlFor="cage-row">排号</FieldLabel>
                <Input id="cage-row" value={rowCode} maxLength={40} onChange={(event) => setRowCode(event.target.value)} />
              </Field>
              <Field>
                <FieldLabel htmlFor="cage-position">列位</FieldLabel>
                <Input id="cage-position" type="number" min={1} value={position} onChange={(event) => setPosition(event.target.value)} />
              </Field>
              <Field>
                <FieldLabel htmlFor="cage-layer">层位</FieldLabel>
                <Input id="cage-layer" type="number" min={1} value={layer} onChange={(event) => setLayer(event.target.value)} />
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="cage-remark">备注</FieldLabel>
              <Textarea id="cage-remark" value={remark} onChange={(event) => setRemark(event.target.value)} />
            </Field>
            <Field>
              <label className="flex items-center gap-3 text-sm font-medium" htmlFor="cage-enabled">
                <input id="cage-enabled" type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />
                启用笼位
              </label>
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
            <Button type="submit" disabled={saving}>{saving ? <Spinner data-icon="inline-start" /> : null}保存</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/**
 * 换笼位。走专用端点而不是编辑表单里的笼位下拉：只有专用端点会在目标笼已有
 * 种兔/后备兔时执行对调，编辑路径只会报“该繁殖笼已有在栏种兔”。
 */
function RabbitTransferDialog({
  rabbit,
  cages,
  houseId,
  onOpenChange,
  onSaved,
}: {
  rabbit: Rabbit | null
  cages: Cage[]
  houseId: number | null
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [targetCageId, setTargetCageId] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!rabbit) return
    setTargetCageId('')
  }, [rabbit])

  const options = useMemo(
    () => cages.filter((cage) => cage.isEnabled && cage.id !== rabbit?.cageId),
    [cages, rabbit?.cageId],
  )

  async function handleSubmit() {
    if (!rabbit || !houseId || !targetCageId) return
    setSaving(true)
    try {
      const result = await transferRabbitCage(houseId, rabbit.id, Number(targetCageId))
      const target = cages.find((cage) => cage.id === result.toCageId)?.cageNumber ?? `#${result.toCageId}`
      toast.success(
        result.mode === 'SWAP'
          ? `已与兔 #${result.swappedRabbitId} 对调笼位`
          : `${transferModeMessages[result.mode] ?? '已换笼'} ${target}`,
      )
      onOpenChange(false)
      await onSaved()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={Boolean(rabbit)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>换笼位</DialogTitle>
          <DialogDescription>
            目标笼位为空时直接入笼；商品兔仅能并入商品兔笼；种兔与后备兔遇到已占用的非商品兔笼时会两笼对调。
          </DialogDescription>
        </DialogHeader>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="transfer-target-cage">目标笼位</FieldLabel>
            <Select value={targetCageId} onValueChange={setTargetCageId}>
              <SelectTrigger id="transfer-target-cage"><SelectValue placeholder="选择目标笼位" /></SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {options.map((cage) => (
                    <SelectItem key={cage.id} value={String(cage.id)}>
                      {cage.cageNumber} · {cageUsageLabel(cage)} · {cage.rabbitCount} 只
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </Field>
        </FieldGroup>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button disabled={saving || !targetCageId} onClick={() => void handleSubmit()}>
            {saving ? <Spinner data-icon="inline-start" /> : <ArrowLeftRightIcon data-icon="inline-start" />}
            确认换笼
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/**
 * 登记离场（死亡 / 淘汰）。
 *
 * 对任意在栏兔都适用，不再需要先找到它所在的生产批次：后端的
 * `POST /api/rabbits/events` 本来就不收 batchId，只需 rabbitId。
 */
function RabbitDepartureDialog({
  rabbit,
  houseId,
  onOpenChange,
  onSaved,
}: {
  rabbit: Rabbit | null
  houseId: number | null
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [departureType, setDepartureType] = useState<RabbitDepartureType>('death')
  const [reason, setReason] = useState('')
  const [remark, setRemark] = useState('')
  const [confirmed, setConfirmed] = useState(false)
  const [saving, setSaving] = useState(false)
  const pendingRequest = useRef<RabbitDepartureRequest | null>(null)

  useEffect(() => {
    if (!rabbit) return
    setDepartureType('death')
    setReason('')
    setRemark('')
    setConfirmed(false)
    pendingRequest.current = null
  }, [rabbit])

  async function handleSubmit() {
    if (!rabbit || !houseId) return
    const trimmedReason = reason.trim()
    if (!trimmedReason) {
      toast.error('请填写离场原因')
      return
    }
    if (!confirmed) {
      toast.error('请确认退出活跃批次及生产周期')
      return
    }
    const request = getOrCreateRabbitDepartureRequest(
      pendingRequest.current,
      {
        rabbitId: rabbit.id,
        eventType: departureType,
        actionDate: Date.now(),
        reason: trimmedReason,
        remark: remark.trim() || undefined,
        forceExitBatch: true,
      },
      () => crypto.randomUUID(),
    )
    pendingRequest.current = request
    setSaving(true)
    try {
      await submitRabbitDeparture(houseId, request)
      pendingRequest.current = null
      toast.success(`兔 #${rabbit.id} 已${departureType === 'cull' ? '淘汰' : '登记死亡'}`)
      onOpenChange(false)
      await onSaved()
    } catch {
      // 保留 requestId，参数未改的重试仍然幂等。
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={Boolean(rabbit)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>登记离场</DialogTitle>
          <DialogDescription>兔 #{rabbit?.id ?? ''} 将标记为离场，同时退出活跃批次并关闭进行中的生产周期。</DialogDescription>
        </DialogHeader>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="livestock-departure-type">离场类型</FieldLabel>
            <Select value={departureType} onValueChange={(value) => setDepartureType(value as RabbitDepartureType)}>
              <SelectTrigger id="livestock-departure-type"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectItem value="death">死亡</SelectItem>
                  <SelectItem value="cull">淘汰</SelectItem>
                </SelectGroup>
              </SelectContent>
            </Select>
          </Field>
          <Field>
            <FieldLabel htmlFor="livestock-departure-reason">离场原因</FieldLabel>
            <Input
              id="livestock-departure-reason"
              value={reason}
              maxLength={200}
              placeholder="例如：病死、腐蹄淘汰"
              onChange={(event) => setReason(event.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="livestock-departure-remark">备注</FieldLabel>
            <Textarea id="livestock-departure-remark" value={remark} onChange={(event) => setRemark(event.target.value)} />
          </Field>
          <Field>
            <label className="flex items-start gap-3 text-sm" htmlFor="livestock-departure-confirm">
              <input
                id="livestock-departure-confirm"
                type="checkbox"
                className="mt-1"
                checked={confirmed}
                onChange={(event) => setConfirmed(event.target.checked)}
              />
              <span>
                <span className="font-medium text-destructive">确认强制离场</span>
                <br />
                <span className="text-muted-foreground">将同时退出活跃批次关系并关闭进行中的生产周期。</span>
              </span>
            </label>
          </Field>
        </FieldGroup>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button variant="destructive" disabled={saving} onClick={() => void handleSubmit()}>
            {saving ? <Spinner data-icon="inline-start" /> : <HeartCrackIcon data-icon="inline-start" />}
            确认离场
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/**
 * 笼内兔只清单（飞书 recvsrEA6TRuK6）。
 *
 * 现场人员是按笼子找兔的，没有这个入口就只能回到兔只列表里逐条对笼位号。
 */
function CageRabbitsDialog({
  cage,
  rabbits,
  stageLabels,
  canEdit,
  onOpenChange,
  onEdit,
  onTransfer,
  onDeparture,
}: {
  cage: Cage | null
  rabbits: Rabbit[]
  stageLabels: Record<string, string>
  canEdit: boolean
  onOpenChange: (open: boolean) => void
  onEdit: (rabbit: Rabbit) => void
  onTransfer: (rabbit: Rabbit) => void
  onDeparture: (rabbit: Rabbit) => void
}) {
  const members = useMemo(
    () => rabbits.filter((rabbit) => rabbit.cageId === cage?.id && rabbit.isActive),
    [cage?.id, rabbits],
  )

  return (
    <Dialog open={Boolean(cage)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{cage?.cageNumber ?? ''} 笼内兔只</DialogTitle>
          <DialogDescription>在栏 {members.length} 只。可直接对单只兔编辑、换笼或登记离场。</DialogDescription>
        </DialogHeader>
        {members.length === 0 ? (
          <Empty>
            <RabbitIcon aria-hidden="true" />
            <EmptyTitle>笼内没有在栏兔</EmptyTitle>
            <EmptyDescription>录入兔只或把其它笼位的兔换过来。</EmptyDescription>
          </Empty>
        ) : (
          <div className="flex max-h-96 flex-col gap-3 overflow-y-auto pr-1">
            {members.map((rabbit) => (
              <div key={rabbit.id} className="flex flex-col gap-2 rounded-md border p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium">兔 #{rabbit.id}</span>
                  <Badge variant="secondary">{rabbitTypeLabel(rabbit)}</Badge>
                </div>
                <span className="text-xs text-muted-foreground">
                  {rabbit.breed || '未填品种'} · {rabbitStageSummary(rabbit, stageLabels)}
                </span>
                <RabbitRowActions
                  rabbit={rabbit}
                  canEdit={canEdit}
                  onEdit={() => onEdit(rabbit)}
                  onTransfer={() => onTransfer(rabbit)}
                  onDeparture={() => onDeparture(rabbit)}
                />
              </div>
            ))}
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>关闭</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function RabbitDialog({
  state,
  onOpenChange,
  houseId,
  cages,
  entryPoints,
  onSaved,
}: {
  state: { open: boolean; rabbit: Rabbit | null }
  onOpenChange: (open: boolean) => void
  houseId: number | null
  cages: Cage[]
  entryPoints: ReproEntryPoint[]
  onSaved: () => Promise<void>
}) {
  const [cageId, setCageId] = useState('')
  const [type, setType] = useState('0')
  const [gender, setGender] = useState('0')
  const [breed, setBreed] = useState('')
  const [arrivalMethod, setArrivalMethod] = useState('0')
  const [arrivalDate, setArrivalDate] = useState('')
  const [weight, setWeight] = useState('')
  const [growthStage, setGrowthStage] = useState('')
  const [reproductiveStage, setReproductiveStage] = useState('')
  const [reproStage, setReproStage] = useState(NO_REPRO_ENTRY)
  const [stageEnteredAt, setStageEnteredAt] = useState('')
  const [matingDate, setMatingDate] = useState('')
  const [birthDate, setBirthDate] = useState('')
  const [liveKits, setLiveKits] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!state.open) return
    setCageId(String(state.rabbit?.cageId ?? cages.find((cage) => cage.isEnabled)?.id ?? ''))
    const nextType = state.rabbit?.type ?? '0'
    const nextGender = state.rabbit?.gender ?? '0'
    setType(nextType)
    setGender(nextGender)
    setBreed(state.rabbit?.breed ?? '')
    setArrivalMethod(state.rabbit?.arrivalMethod ?? '0')
    setArrivalDate(formatDateInput(state.rabbit?.arrivalDate))
    setWeight(state.rabbit?.weight?.toString() ?? '')
    setGrowthStage(state.rabbit?.growthStage ?? '')
    setReproductiveStage(
      state.rabbit?.reproductiveStage ?? (state.rabbit ? '' : defaultReproductiveStage(nextType, nextGender)),
    )
    setReproStage(NO_REPRO_ENTRY)
    setStageEnteredAt(formatDateInput(new Date().toISOString()))
    setMatingDate('')
    setBirthDate('')
    setLiveKits('')
  }, [cages, state.open, state.rabbit])

  const reproductiveStageOptions = reproductiveOptions(type, gender)
  /** 只有新录入的种母兔能在这里入轨；已存在的母兔要改阶段得走生产动作。 */
  const canOpenReproEntry = !state.rabbit && type === '0' && gender === '0'
  const selectedEntry = entryPoints.find((entry) => entry.stage === reproStage) ?? null
  const requiredFacts = new Set(selectedEntry?.requiredFacts.map((fact) => fact.fact) ?? [])
  const needsMatingDate = requiredFacts.has('MATING_DATE') || requiredFacts.has('GESTATION_ANCHOR')

  function resetReproductiveStage(nextType: string, nextGender: string) {
    const options = reproductiveOptions(nextType, nextGender)
    setReproductiveStage((current) => options.some(([value]) => value === current) ? current : options[0]?.[0] ?? '')
  }

  function handleTypeChange(nextType: string) {
    setType(nextType)
    resetReproductiveStage(nextType, gender)
  }

  function handleGenderChange(nextGender: string) {
    setGender(nextGender)
    resetReproductiveStage(type, nextGender)
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!houseId || !cageId) return
    const data = {
      cageId: Number(cageId),
      type,
      gender,
      breed: breed.trim(),
      arrivalMethod,
      arrivalDate: arrivalDate ? new Date(`${arrivalDate}T00:00:00`).toISOString() : undefined,
      weight: weight ? Number(weight) : undefined,
      growthStage: growthStage || undefined,
      // 种母兔不能带旧的 reproductiveStage：后端会直接拒收。
      reproductiveStage: reproductiveStageOptions.length === 0 ? undefined : reproductiveStage || undefined,
    }
    const entry = canOpenReproEntry && selectedEntry
      ? {
        reproStage: selectedEntry.stage,
        stageEnteredAt: toIsoDate(stageEnteredAt),
        matingDate: toIsoDate(matingDate),
        birthDate: toIsoDate(birthDate),
        liveKits: liveKits ? Number(liveKits) : undefined,
      }
      : {}
    if (selectedEntry) {
      const missing = selectedEntry.requiredFacts.find((fact) => {
        if (fact.fact === 'STAGE_ENTERED_AT') return !stageEnteredAt
        if (fact.fact === 'MATING_DATE' || fact.fact === 'GESTATION_ANCHOR') return !matingDate
        if (fact.fact === 'BIRTH_DATE') return !birthDate
        if (fact.fact === 'LIVE_KITS') return !liveKits
        return false
      })
      if (missing) {
        toast.error(`从【${selectedEntry.stageLabel}】入轨需要补录${missing.label}`)
        return
      }
    }
    setSaving(true)
    try {
      if (state.rabbit) {
        await updateRabbit(houseId, state.rabbit.id, data)
        toast.success('兔只资料已更新')
      } else {
        await createRabbit(houseId, { ...data, ...entry })
        toast.success(selectedEntry ? `兔只已录入，并从【${selectedEntry.stageLabel}】入轨` : '兔只已录入')
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
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{state.rabbit ? `编辑兔 #${state.rabbit.id}` : '录入兔只'}</DialogTitle>
          <DialogDescription>类型和性别在录入后需通过生产业务动作调整。</DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="overflow-y-auto pr-1">
            <Field>
              <FieldLabel htmlFor="rabbit-cage">笼位</FieldLabel>
              <Select value={cageId} onValueChange={setCageId}>
                <SelectTrigger id="rabbit-cage"><SelectValue placeholder="选择笼位" /></SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {cages.filter((cage) => cage.isEnabled).map((cage) => (
                      <SelectItem key={cage.id} value={String(cage.id)}>{cage.cageNumber} · {cage.rabbitCount} 只</SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="rabbit-type">类型</FieldLabel>
                <Select value={type} onValueChange={handleTypeChange} disabled={Boolean(state.rabbit)}>
                  <SelectTrigger id="rabbit-type"><SelectValue /></SelectTrigger>
                  <SelectContent><SelectGroup>{Object.entries(rabbitTypeLabels).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}</SelectGroup></SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="rabbit-gender">性别</FieldLabel>
                <Select value={gender} onValueChange={handleGenderChange} disabled={Boolean(state.rabbit)}>
                  <SelectTrigger id="rabbit-gender"><SelectValue /></SelectTrigger>
                  <SelectContent><SelectGroup><SelectItem value="0">母</SelectItem><SelectItem value="1">公</SelectItem></SelectGroup></SelectContent>
                </Select>
              </Field>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="rabbit-growth-stage">生长阶段</FieldLabel>
                <Select value={growthStage} onValueChange={setGrowthStage}>
                  <SelectTrigger id="rabbit-growth-stage"><SelectValue placeholder="未填写" /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>{growthStageOptions.map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}</SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
              {reproductiveStageOptions.length === 0 ? (
                <Field>
                  <FieldLabel>繁殖阶段</FieldLabel>
                  <p className="min-h-9 rounded-md border bg-muted px-3 py-2 text-sm text-muted-foreground">
                    {type === '2' ? '商品兔不记录繁殖阶段' : '种母兔阶段由生产流程维护'}
                  </p>
                </Field>
              ) : (
                <Field>
                  <FieldLabel htmlFor="rabbit-reproductive-stage">繁殖阶段</FieldLabel>
                  <Select value={reproductiveStage} onValueChange={setReproductiveStage}>
                    <SelectTrigger id="rabbit-reproductive-stage"><SelectValue placeholder="未填写" /></SelectTrigger>
                    <SelectContent>
                      <SelectGroup>{reproductiveStageOptions.map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}</SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
              )}
            </div>
            {canOpenReproEntry ? (
              <>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field>
                    <FieldLabel htmlFor="rabbit-repro-stage">生产阶段</FieldLabel>
                    <Select value={reproStage} onValueChange={setReproStage}>
                      <SelectTrigger id="rabbit-repro-stage"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          <SelectItem value={NO_REPRO_ENTRY}>暂不入轨</SelectItem>
                          {entryPoints.map((entry) => (
                            <SelectItem key={entry.stage} value={entry.stage}>{entry.stageLabel}</SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </Field>
                  <Field>
                    <FieldLabel htmlFor="rabbit-stage-entered-at">进入该阶段日期</FieldLabel>
                    <Input
                      id="rabbit-stage-entered-at"
                      type="date"
                      value={stageEnteredAt}
                      disabled={!selectedEntry}
                      onChange={(event) => setStageEnteredAt(event.target.value)}
                    />
                  </Field>
                </div>
                {selectedEntry ? (
                  <div className="grid gap-4 sm:grid-cols-2">
                    {needsMatingDate ? (
                      <Field>
                        <FieldLabel htmlFor="rabbit-mating-date">配种日期</FieldLabel>
                        <Input id="rabbit-mating-date" type="date" value={matingDate} onChange={(event) => setMatingDate(event.target.value)} />
                      </Field>
                    ) : null}
                    {requiredFacts.has('BIRTH_DATE') ? (
                      <Field>
                        <FieldLabel htmlFor="rabbit-birth-date">分娩日期</FieldLabel>
                        <Input id="rabbit-birth-date" type="date" value={birthDate} onChange={(event) => setBirthDate(event.target.value)} />
                      </Field>
                    ) : null}
                    {requiredFacts.has('LIVE_KITS') ? (
                      <Field>
                        <FieldLabel htmlFor="rabbit-live-kits">活仔数</FieldLabel>
                        <Input id="rabbit-live-kits" type="number" min={0} value={liveKits} onChange={(event) => setLiveKits(event.target.value)} />
                      </Field>
                    ) : null}
                  </div>
                ) : null}
              </>
            ) : null}
            <Field>
              <FieldLabel htmlFor="rabbit-breed">品种</FieldLabel>
              <Input id="rabbit-breed" value={breed} maxLength={100} onChange={(event) => setBreed(event.target.value)} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="rabbit-source">来源</FieldLabel>
                <Select value={arrivalMethod} onValueChange={setArrivalMethod}>
                  <SelectTrigger id="rabbit-source"><SelectValue /></SelectTrigger>
                  <SelectContent><SelectGroup><SelectItem value="0">购入</SelectItem><SelectItem value="1">出生</SelectItem></SelectGroup></SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="rabbit-date">入场日期</FieldLabel>
                <Input id="rabbit-date" type="date" value={arrivalDate} onChange={(event) => setArrivalDate(event.target.value)} />
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="rabbit-weight">体重（kg）</FieldLabel>
              <Input id="rabbit-weight" type="number" min={0} step="0.01" value={weight} onChange={(event) => setWeight(event.target.value)} />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
            <Button type="submit" disabled={saving || !cageId}>{saving ? <Spinner data-icon="inline-start" /> : null}保存</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function DeleteCageDialog({
  cage,
  houseId,
  onOpenChange,
  onDeleted,
}: {
  cage: Cage | null
  houseId: number | null
  onOpenChange: (open: boolean) => void
  onDeleted: () => Promise<void>
}) {
  const [saving, setSaving] = useState(false)

  async function handleDelete() {
    if (!cage || !houseId) return
    setSaving(true)
    try {
      await deleteCage(houseId, cage.id)
      toast.success('笼位已删除')
      onOpenChange(false)
      await onDeleted()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={Boolean(cage)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>确认删除笼位</DialogTitle>
          <DialogDescription>仅空笼位可以删除。将删除“{cage?.cageNumber ?? ''}”。</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button variant="destructive" disabled={saving} onClick={() => void handleDelete()}>{saving ? <Spinner data-icon="inline-start" /> : <Trash2Icon data-icon="inline-start" />}删除笼位</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
