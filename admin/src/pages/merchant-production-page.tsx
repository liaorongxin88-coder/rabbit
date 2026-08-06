import { useCallback, useEffect, useMemo, useState } from 'react'
import { CalendarClockIcon, PlusIcon, RefreshCwIcon, Rows3Icon, WarehouseIcon } from 'lucide-react'
import { toast } from 'sonner'
import {
  createBatch,
  listBatchRabbits,
  listBatches,
  listCages,
  listRabbits,
  submitBatchAction,
  type BatchAction,
} from '@/api/workspace'
import { PageHeader } from '@/components/page-header'
import { MerchantOutboundDialog } from '@/components/merchant-outbound-dialog'
import { HousePermissionBadge } from '@/components/permission-badge'
import { hasPermission, useMerchantWorkspace } from '@/lib/merchant-workspace'
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
import type { BatchRabbit, Cage, ProductionBatch, Rabbit } from '@/types/api'

type BatchWorkflowAction = Exclude<BatchAction, 'sale'>

const actionLabels: Record<BatchWorkflowAction, string> = {
  'aphrodisiac/start': '开始催情',
  'aphrodisiac/finish': '完成催情',
  mating: '记录配种',
  'pregnancy-check': '记录孕检',
  'prepartum/finish': '完成产前准备',
  parturition: '记录分娩',
  weaning: '记录断奶',
  complete: '完成批次',
}

export function MerchantProductionPage() {
  const workspace = useMerchantWorkspace()
  const [batches, setBatches] = useState<ProductionBatch[]>([])
  const [rabbits, setRabbits] = useState<Rabbit[]>([])
  const [cages, setCages] = useState<Cage[]>([])
  const [loading, setLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [actionBatch, setActionBatch] = useState<ProductionBatch | null>(null)
  const canEdit = hasPermission(workspace.permission, 'rabbit:batches:edit')
  const canControl = hasPermission(workspace.permission, 'rabbit:rabbits:control')

  const load = useCallback(async () => {
    if (!workspace.selectedHouse) {
      setBatches([])
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
      setRabbits(nextRabbits)
      setCages(nextCages)
    } catch {
      setBatches([])
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
            <MerchantOutboundDialog
              houseId={workspace.selectedHouse?.id ?? null}
              disabled={!canEdit}
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
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>批次</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>开始日期</TableHead>
                  <TableHead>结束日期</TableHead>
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
                    <TableCell><Badge variant={batch.status === 'COMPLETED' ? 'secondary' : 'default'}>{batchStatusLabel(batch.status)}</Badge></TableCell>
                    <TableCell>{formatDate(batch.startDate)}</TableCell>
                    <TableCell>{formatDate(batch.endDate)}</TableCell>
                    <TableCell className="max-w-64 truncate">{batch.remark || '-'}</TableCell>
                    <TableCell className="text-right">
                      <Button variant="outline" size="sm" disabled={!canEdit || batch.status === 'COMPLETED'} onClick={() => setActionBatch(batch)}>
                        <CalendarClockIcon data-icon="inline-start" />
                        生产操作
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <BatchActionDialog
        batch={actionBatch}
        houseId={workspace.selectedHouse?.id ?? null}
        rabbits={rabbits}
        cages={cages}
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
  const [remark, setRemark] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    setCode(`PC-${formatLocalDate().replaceAll('-', '')}`)
    setSelectedIds([])
    setRemark('')
  }, [open])

  function toggleRabbit(id: number) {
    setSelectedIds((current) =>
      current.includes(id) ? current.filter((item) => item !== id) : [...current, id],
    )
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
              <div className="grid max-h-48 gap-2 overflow-y-auto rounded-md border p-3 sm:grid-cols-2">
                {femaleRabbits.map((rabbit) => (
                  <label key={rabbit.id} className="flex items-center gap-2 text-sm">
                    <input type="checkbox" checked={selectedIds.includes(rabbit.id)} onChange={() => toggleRabbit(rabbit.id)} />
                    兔 #{rabbit.id} · 笼位 #{rabbit.cageId}
                  </label>
                ))}
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
  onOpenChange,
  onSaved,
}: {
  batch: ProductionBatch | null
  houseId: number | null
  rabbits: Rabbit[]
  cages: Cage[]
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [action, setAction] = useState<BatchWorkflowAction>('mating')
  const [batchRabbits, setBatchRabbits] = useState<BatchRabbit[]>([])
  const [rabbitId, setRabbitId] = useState('')
  const [maleRabbitId, setMaleRabbitId] = useState('')
  const [date, setDate] = useState(formatLocalDate())
  const [result, setResult] = useState('怀孕')
  const [totalKits, setTotalKits] = useState('0')
  const [liveKits, setLiveKits] = useState('0')
  const [weaningCount, setWeaningCount] = useState('0')
  const [maleCount, setMaleCount] = useState('0')
  const [femaleCount, setFemaleCount] = useState('0')
  const [targetCageId, setTargetCageId] = useState('')
  const [avgWeight, setAvgWeight] = useState('')
  const [force, setForce] = useState(false)
  const [remark, setRemark] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!batch || !houseId) {
      setBatchRabbits([])
      return
    }
    setAction('mating')
    setDate(formatLocalDate())
    setRemark('')
    void listBatchRabbits(houseId, batch.id)
      .then((items) => {
        setBatchRabbits(items)
        setRabbitId(String(items.find((item) => item.isActive)?.rabbitId ?? ''))
      })
      .catch(() => setBatchRabbits([]))
  }, [batch, houseId])

  const maleRabbits = rabbits.filter(
    (rabbit) => rabbit.isActive && rabbit.type === '0' && rabbit.gender === '1',
  )
  const activeBatchRabbits = batchRabbits.filter((item) => item.isActive)
  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!batch || !houseId) return
    const timestamp = new Date(`${date}T00:00:00`).getTime()
    let data: Record<string, unknown>
    switch (action) {
      case 'aphrodisiac/start':
      case 'aphrodisiac/finish':
        data = { rabbitIds: [Number(rabbitId)], triggerHardware: false }
        break
      case 'mating':
        data = { femaleRabbitId: Number(rabbitId), maleRabbitId: Number(maleRabbitId), matingDate: timestamp }
        break
      case 'pregnancy-check':
        data = { rabbitId: Number(rabbitId), checkDate: timestamp, result, remark: remark.trim() }
        break
      case 'prepartum/finish':
        data = { rabbitId: Number(rabbitId), actionDate: timestamp, remark: remark.trim() }
        break
      case 'parturition':
        data = { rabbitId: Number(rabbitId), birthDate: timestamp, totalKits: Number(totalKits), liveKits: Number(liveKits), failed: false, remark: remark.trim() }
        break
      case 'weaning':
        data = { rabbitId: Number(rabbitId), weaningDate: timestamp, weaningCount: Number(weaningCount), maleCount: Number(maleCount), femaleCount: Number(femaleCount), targetCageId: targetCageId ? Number(targetCageId) : undefined, avgWeight: avgWeight ? Number(avgWeight) : undefined, remark: remark.trim() }
        break
      case 'complete':
        data = { endDate: timestamp, force, remark: remark.trim() }
        break
    }
    setSaving(true)
    try {
      await submitBatchAction(houseId, batch.id, action, data)
      toast.success(`${actionLabels[action]}已保存`)
      onOpenChange(false)
      await onSaved()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }

  const requiresRabbit = action !== 'complete'
  const usesDate = !['aphrodisiac/start', 'aphrodisiac/finish'].includes(action)

  return (
    <Dialog open={Boolean(batch)} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
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
                <SelectContent><SelectGroup>{Object.entries(actionLabels).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}</SelectGroup></SelectContent>
              </Select>
            </Field>
            {requiresRabbit ? (
              <Field>
                <FieldLabel htmlFor="batch-rabbit">批次兔只</FieldLabel>
                <Select value={rabbitId} onValueChange={setRabbitId}>
                  <SelectTrigger id="batch-rabbit"><SelectValue placeholder="选择兔只" /></SelectTrigger>
                  <SelectContent><SelectGroup>{activeBatchRabbits.map((item) => <SelectItem key={item.id} value={String(item.rabbitId)}>兔 #{item.rabbitId} · {item.currentStatus || '批次中'}</SelectItem>)}</SelectGroup></SelectContent>
                </Select>
              </Field>
            ) : null}
            {action === 'mating' ? (
              <Field>
                <FieldLabel htmlFor="male-rabbit">种公兔</FieldLabel>
                <Select value={maleRabbitId} onValueChange={setMaleRabbitId}>
                  <SelectTrigger id="male-rabbit"><SelectValue placeholder="选择种公兔" /></SelectTrigger>
                  <SelectContent><SelectGroup>{maleRabbits.map((rabbit) => <SelectItem key={rabbit.id} value={String(rabbit.id)}>兔 #{rabbit.id} · 笼位 #{rabbit.cageId}</SelectItem>)}</SelectGroup></SelectContent>
                </Select>
              </Field>
            ) : null}
            {action === 'pregnancy-check' ? (
              <Field>
                <FieldLabel htmlFor="pregnancy-result">孕检结果</FieldLabel>
                <Select value={result} onValueChange={setResult}>
                  <SelectTrigger id="pregnancy-result"><SelectValue /></SelectTrigger>
                  <SelectContent><SelectGroup><SelectItem value="怀孕">怀孕</SelectItem><SelectItem value="空怀">空怀</SelectItem><SelectItem value="不确定">不确定</SelectItem></SelectGroup></SelectContent>
                </Select>
              </Field>
            ) : null}
            {action === 'parturition' ? (
              <div className="grid gap-4 sm:grid-cols-2">
                <NumberInput id="total-kits" label="产仔数" value={totalKits} onChange={setTotalKits} />
                <NumberInput id="live-kits" label="活仔数" value={liveKits} onChange={setLiveKits} />
              </div>
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
            {!['mating', 'aphrodisiac/start', 'aphrodisiac/finish'].includes(action) ? (
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
                (requiresRabbit && !rabbitId) ||
                (action === 'mating' && !maleRabbitId)
              }
            >
              {saving ? <Spinner data-icon="inline-start" /> : null}
              保存记录
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function NumberInput({ id, label, value, onChange }: { id: string; label: string; value: string; onChange: (value: string) => void }) {
  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Input id={id} type="number" min={0} value={value} required onChange={(event) => onChange(event.target.value)} />
    </Field>
  )
}

function formatDate(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleDateString('zh-CN')
}

function batchStatusLabel(status: string) {
  const labels: Record<string, string> = {
    ACTIVE: '进行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
  }
  return labels[status] ?? status
}
