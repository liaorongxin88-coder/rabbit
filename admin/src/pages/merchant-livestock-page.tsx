import { useCallback, useEffect, useMemo, useState } from 'react'
import { Edit3Icon, PlusIcon, RabbitIcon, RefreshCwIcon, SearchIcon, Trash2Icon, WarehouseIcon } from 'lucide-react'
import { toast } from 'sonner'
import {
  createCage,
  createRabbit,
  deleteCage,
  listCages,
  listRabbits,
  updateCage,
  updateRabbit,
} from '@/api/workspace'
import { PageHeader } from '@/components/page-header'
import { HousePermissionBadge } from '@/components/permission-badge'
import { hasPermission, useMerchantWorkspace } from '@/lib/merchant-workspace'
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
import type { Cage, Rabbit } from '@/types/api'

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

export function MerchantLivestockPage() {
  const workspace = useMerchantWorkspace()
  const [cages, setCages] = useState<Cage[]>([])
  const [rabbits, setRabbits] = useState<Rabbit[]>([])
  const [loading, setLoading] = useState(false)
  const [queryInput, setQueryInput] = useState('')
  const [query, setQuery] = useState('')
  const [cageDialog, setCageDialog] = useState<{ open: boolean; cage: Cage | null }>({ open: false, cage: null })
  const [rabbitDialog, setRabbitDialog] = useState<{ open: boolean; rabbit: Rabbit | null }>({ open: false, rabbit: null })
  const [deleteTarget, setDeleteTarget] = useState<Cage | null>(null)
  const canEdit = hasPermission(workspace.permission, 'rabbit:rabbits:edit')
  const canControl = hasPermission(workspace.permission, 'rabbit:cages:edit')

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

  useEffect(() => {
    void load()
  }, [load])

  const filteredCages = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) {
      return cages
    }
    return cages.filter((cage) =>
      [cage.cageNumber, cage.rowCode, cage.remark, cage.id.toString()].some((value) =>
        value?.toLowerCase().includes(normalized),
      ),
    )
  }, [cages, query])

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
                            <Badge variant={rabbit.isActive ? 'default' : 'secondary'}>{rabbit.isActive ? '在栏' : '离场'}</Badge>
                          </TableCell>
                          <TableCell className="text-right">
                            <Button variant="outline" size="sm" disabled={!canEdit} onClick={() => setRabbitDialog({ open: true, rabbit })}>
                              <Edit3Icon data-icon="inline-start" />
                              编辑
                            </Button>
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
                          <TableCell>{cageStatusLabels[cage.status ?? ''] ?? cage.status ?? '-'}</TableCell>
                          <TableCell>{cage.rabbitCount} 只</TableCell>
                          <TableCell><Badge variant={cage.isEnabled ? 'default' : 'secondary'}>{cage.isEnabled ? '启用' : '停用'}</Badge></TableCell>
                          <TableCell>
                            <div className="flex justify-end gap-2">
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
      <RabbitDialog state={rabbitDialog} onOpenChange={(open) => setRabbitDialog((current) => ({ ...current, open }))} houseId={workspace.selectedHouse?.id ?? null} cages={cages} onSaved={load} />
      <DeleteCageDialog cage={deleteTarget} houseId={workspace.selectedHouse?.id ?? null} onOpenChange={(open) => !open && setDeleteTarget(null)} onDeleted={load} />
    </>
  )
}

function rabbitTypeLabel(rabbit: Rabbit) {
  if (rabbit.type === '0') {
    return rabbit.gender === '0' ? '种母兔' : rabbit.gender === '1' ? '种公兔' : '种兔'
  }
  return rabbitTypeLabels[rabbit.type] ?? rabbit.type ?? '未分类'
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

function RabbitDialog({
  state,
  onOpenChange,
  houseId,
  cages,
  onSaved,
}: {
  state: { open: boolean; rabbit: Rabbit | null }
  onOpenChange: (open: boolean) => void
  houseId: number | null
  cages: Cage[]
  onSaved: () => Promise<void>
}) {
  const [cageId, setCageId] = useState('')
  const [type, setType] = useState('0')
  const [gender, setGender] = useState('0')
  const [breed, setBreed] = useState('')
  const [arrivalMethod, setArrivalMethod] = useState('0')
  const [arrivalDate, setArrivalDate] = useState('')
  const [weight, setWeight] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!state.open) return
    setCageId(String(state.rabbit?.cageId ?? cages.find((cage) => cage.isEnabled)?.id ?? ''))
    setType(state.rabbit?.type ?? '0')
    setGender(state.rabbit?.gender ?? '0')
    setBreed(state.rabbit?.breed ?? '')
    setArrivalMethod(state.rabbit?.arrivalMethod ?? '0')
    setArrivalDate(formatDateInput(state.rabbit?.arrivalDate))
    setWeight(state.rabbit?.weight?.toString() ?? '')
  }, [cages, state.open, state.rabbit])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!houseId || !cageId) return
    setSaving(true)
    const data = {
      cageId: Number(cageId),
      type,
      gender,
      breed: breed.trim(),
      arrivalMethod,
      arrivalDate: arrivalDate ? new Date(`${arrivalDate}T00:00:00`).toISOString() : undefined,
      weight: weight ? Number(weight) : undefined,
    }
    try {
      if (state.rabbit) {
        await updateRabbit(houseId, state.rabbit.id, data)
        toast.success('兔只资料已更新')
      } else {
        await createRabbit(houseId, data)
        toast.success('兔只已录入')
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
                <Select value={type} onValueChange={setType} disabled={Boolean(state.rabbit)}>
                  <SelectTrigger id="rabbit-type"><SelectValue /></SelectTrigger>
                  <SelectContent><SelectGroup>{Object.entries(rabbitTypeLabels).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}</SelectGroup></SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="rabbit-gender">性别</FieldLabel>
                <Select value={gender} onValueChange={setGender} disabled={Boolean(state.rabbit)}>
                  <SelectTrigger id="rabbit-gender"><SelectValue /></SelectTrigger>
                  <SelectContent><SelectGroup><SelectItem value="0">母</SelectItem><SelectItem value="1">公</SelectItem></SelectGroup></SelectContent>
                </Select>
              </Field>
            </div>
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
