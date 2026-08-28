import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ArrowLeftRightIcon, ArrowUpRightIcon, HeartCrackIcon, SproutIcon } from 'lucide-react'
import { toast } from 'sonner'
import {
  createRabbit,
  createRabbitBatch,
  promoteReplacementRabbit,
  retainRabbitsAsReplacement,
  requestId,
  submitRabbitDeparture,
  transferRabbitCage,
  updateRabbit,
  type ReproEntryPoint,
} from '@/api/workspace'
import { CageAttentionLegend, CageMap } from '@/components/cage-map'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'
import { getOrCreateRabbitDepartureRequest } from '@/lib/batch-workflow'
import { buildCageLayout, cageAcceptsMoreRabbits } from '@/lib/cage-map'
import { farmBusinessDateToIso, formatDateInput } from '@/lib/date'
import {
  buildRabbitReproEntryInput,
  inProgressProductionBatches,
  keepValidProductionBatchId,
} from '@/lib/rabbit-repro-entry'
import {
  isReplacementTargetCage,
  preferredRabbitTypeForCage,
  rabbitCageValidationMessage,
} from '@/lib/rabbit-cage'
import { getOrCreateRabbitReplacementRequest } from '@/lib/rabbit-replacement'
import {
  defaultReproductiveStage,
  growthStageOptions,
  rabbitTypeLabels,
  reproductiveOptions,
} from '@/lib/rabbits'
import type {
  BatchRabbitEntryResult,
  Cage,
  Rabbit,
  ProductionBatch,
  RabbitDepartureRequest,
  RabbitDepartureType,
  RabbitReplacementRequest,
} from '@/types/api'

const cageStatusLabels: Record<string, string> = {
  '0': '空闲',
  '1': '种兔',
  '2': '后备兔',
  '3': '商品兔',
}

const transferModeMessages: Record<string, string> = {
  MOVE: '已移入目标笼位',
  APPEND: '已并入目标商品兔笼',
  REPLAY: '该换笼请求之前已完成',
}

/** Radix Select 不接受空字符串选项，“不入轨”需要一个显式哨兵值。 */
const NO_REPRO_ENTRY = 'NONE'

function cageUsageLabel(cage: Cage) {
  if (cage.status === '1' && cage.breedingOccupantGender === '0') return '种母兔笼'
  if (cage.status === '1' && cage.breedingOccupantGender === '1') return '种公兔笼'
  return cageStatusLabels[cage.status ?? ''] ?? cage.status ?? '-'
}

export function RabbitFormDialog({
  open,
  rabbit,
  onOpenChange,
  houseId,
  cages,
  entryPoints,
  batches,
  initialCageId,
  onSaved,
}: {
  open: boolean
  rabbit: Rabbit | null
  onOpenChange: (open: boolean) => void
  houseId: number | null
  cages: Cage[]
  entryPoints: ReproEntryPoint[]
  batches: ProductionBatch[]
  initialCageId?: number | null
  onSaved: () => Promise<void>
}) {
  const [cageId, setCageId] = useState('')
  const [type, setType] = useState('0')
  const [gender, setGender] = useState('0')
  const [breed, setBreed] = useState('')
  const [arrivalMethod, setArrivalMethod] = useState('0')
  const [sourceSeller, setSourceSeller] = useState('')
  const [motherId, setMotherId] = useState('')
  const [arrivalDate, setArrivalDate] = useState('')
  const [weight, setWeight] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [totalWeight, setTotalWeight] = useState('')
  const [growthStage, setGrowthStage] = useState('')
  const [reproductiveStage, setReproductiveStage] = useState('')
  const [reproStage, setReproStage] = useState(NO_REPRO_ENTRY)
  const [batchId, setBatchId] = useState('')
  const [stageEnteredAt, setStageEnteredAt] = useState('')
  const [matingDate, setMatingDate] = useState('')
  const [birthDate, setBirthDate] = useState('')
  const [liveKits, setLiveKits] = useState('')
  const [batchEntryResult, setBatchEntryResult] = useState<BatchRabbitEntryResult | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    const initialCage = cages.find(
      (cage) => cage.id === initialCageId && cage.isEnabled && cage.houseId === houseId,
    )
    const defaultCage = initialCage ?? cages.find(
      (cage) => cage.isEnabled && cage.houseId === houseId,
    )
    setCageId(String(rabbit?.cageId ?? defaultCage?.id ?? ''))
    const nextType = rabbit?.type ?? preferredRabbitTypeForCage(defaultCage)
    const nextGender = rabbit?.gender ?? '0'
    setType(nextType)
    setGender(nextGender)
    setBreed(rabbit?.breed ?? '')
    setArrivalMethod(rabbit?.arrivalMethod ?? '0')
    setSourceSeller(rabbit?.sourceSeller ?? '')
    setMotherId(rabbit?.motherId?.toString() ?? '')
    setArrivalDate(formatDateInput(rabbit?.arrivalDate))
    setWeight(rabbit?.weight?.toString() ?? '')
    setQuantity('1')
    setTotalWeight('')
    setGrowthStage(rabbit?.growthStage ?? '')
    setReproductiveStage(
      rabbit?.reproductiveStage ?? (rabbit ? '' : defaultReproductiveStage(nextType, nextGender)),
    )
    setReproStage(NO_REPRO_ENTRY)
    setBatchId('')
    setStageEnteredAt(formatDateInput(new Date().toISOString()))
    setMatingDate('')
    setBirthDate('')
    setLiveKits('')
    setBatchEntryResult(null)
  }, [cages, houseId, initialCageId, open, rabbit])

  const availableBatches = useMemo(() => inProgressProductionBatches(batches), [batches])

  useEffect(() => {
    if (!open) return
    setBatchId((current) => keepValidProductionBatchId(current, availableBatches))
  }, [availableBatches, houseId, open])

  const reproductiveStageOptions = reproductiveOptions(type, gender)
  const canOpenReproEntry = !rabbit && type === '0' && gender === '0'
  const selectedEntry = canOpenReproEntry
    ? entryPoints.find((entry) => entry.stage === reproStage) ?? null
    : null
  const requiredFacts = new Set(selectedEntry?.requiredFacts.map((fact) => fact.fact) ?? [])
  const needsMatingDate = requiredFacts.has('MATING_DATE') || requiredFacts.has('GESTATION_ANCHOR')
  const currentCage = cages.find((cage) => cage.id === rabbit?.cageId)
  const selectedCage = cages.find((cage) => cage.id === Number(cageId))
  const cageValidationMessage = !rabbit && cageId && houseId
    ? rabbitCageValidationMessage(selectedCage, type, houseId)
    : null
  const quantityValue = Number(quantity)
  const isBatchEntry = !rabbit && Number.isInteger(quantityValue) && quantityValue > 1

  function resetReproductiveStage(nextType: string, nextGender: string) {
    const options = reproductiveOptions(nextType, nextGender)
    setReproductiveStage((current) =>
      options.some((option) => option[0] === current) ? current : options[0]?.[0] ?? '',
    )
  }

  function handleTypeChange(nextType: string) {
    setType(nextType)
    resetReproductiveStage(nextType, gender)
    if (nextType !== '0' || gender !== '0') {
      setReproStage(NO_REPRO_ENTRY)
      setBatchId('')
    }
  }

  function handleGenderChange(nextGender: string) {
    setGender(nextGender)
    resetReproductiveStage(type, nextGender)
    if (type !== '0' || nextGender !== '0') {
      setReproStage(NO_REPRO_ENTRY)
      setBatchId('')
    }
  }

  function handleReproStageChange(nextStage: string) {
    setReproStage(nextStage)
    if (nextStage === NO_REPRO_ENTRY) {
      setBatchId('')
    }
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (batchEntryResult) {
      onOpenChange(false)
      return
    }
    if (!houseId || !cageId || cageValidationMessage) return
    if (!rabbit && (!Number.isInteger(quantityValue) || quantityValue < 1 || quantityValue > 10)) {
      toast.error('数量必须在 1 到 10 之间')
      return
    }
    if (isBatchEntry && type !== '2') {
      toast.error('种兔和后备兔一次只能录入 1 只')
      return
    }
    if (isBatchEntry && (!Number.isFinite(Number(totalWeight)) || Number(totalWeight) <= 0 || Number(totalWeight) > 100)) {
      toast.error('总重量必须在 0.01 到 100 kg 之间')
      return
    }
    if (motherId && (!Number.isInteger(Number(motherId)) || Number(motherId) <= 0)) {
      toast.error('请输入有效的母兔 ID')
      return
    }
    if (selectedEntry && arrivalMethod === '0' && !sourceSeller.trim()) {
      toast.error('购入种母兔请填写供应方')
      return
    }
    const data = {
      cageId: Number(cageId),
      type,
      gender,
      breed: breed.trim(),
      arrivalMethod,
      sourceSeller: sourceSeller.trim() || undefined,
      motherId: motherId ? Number(motherId) : undefined,
      arrivalDate: farmBusinessDateToIso(arrivalDate),
      weight: weight ? Number(weight) : undefined,
      growthStage: growthStage || undefined,
      reproductiveStage: reproductiveStageOptions.length === 0
        ? undefined
        : reproductiveStage || undefined,
    }
    if (selectedEntry && !batchId) {
      toast.error('选择生产阶段后，请选择当前进行中的批次')
      return
    }
    const entry = canOpenReproEntry && selectedEntry
      ? buildRabbitReproEntryInput({
        reproStage: selectedEntry.stage,
        batchId: Number(batchId),
        stageEnteredAt,
        matingDate,
        birthDate,
        liveKits: liveKits ? Number(liveKits) : undefined,
      })
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
      if (rabbit) {
        await updateRabbit(houseId, rabbit.id, data)
        toast.success('兔只资料已更新')
        onOpenChange(false)
      } else if (isBatchEntry) {
        const result = await createRabbitBatch(houseId, {
          cageId: data.cageId,
          type: data.type,
          gender: data.gender,
          breed: data.breed || undefined,
          arrivalMethod: data.arrivalMethod,
          sourceSeller: data.sourceSeller,
          motherId: data.motherId,
          arrivalDate: data.arrivalDate ?? '',
          growthStage: data.growthStage,
          reproductiveStage: data.reproductiveStage,
          quantity: quantityValue,
          totalWeight: Number(totalWeight),
        })
        await onSaved()
        if (result.skippedCages.length > 0) {
          setBatchEntryResult(result)
          if (result.enteredRabbitCount > 0) {
            toast.success(`已录入 ${result.enteredRabbitCount} 只，未录入项见表单内结果`)
          } else {
            toast.error('没有兔只录入，请查看未录入原因')
          }
          return
        }
        toast.success(`已录入 ${result.enteredRabbitCount} 只`)
        onOpenChange(false)
      } else {
        await createRabbit(houseId, { ...data, ...entry })
        toast.success(selectedEntry ? `兔只已录入，并从【${selectedEntry.stageLabel}】入轨` : '兔只已录入')
        onOpenChange(false)
      }
      await onSaved()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{rabbit ? `编辑兔 #${rabbit.id}` : '录入兔只'}</DialogTitle>
          <DialogDescription>类型和性别在录入后需通过生产业务动作调整。</DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="overflow-y-auto pr-1">
            <Field>
              <FieldLabel htmlFor={rabbit ? undefined : 'rabbit-cage'}>笼位</FieldLabel>
              {rabbit ? (
                <p className="min-h-9 rounded-md border bg-muted px-3 py-2 text-sm text-muted-foreground">
                  {currentCage?.cageNumber ?? `#${rabbit.cageId}`}
                </p>
              ) : (
                <Select value={cageId} onValueChange={setCageId}>
                  <SelectTrigger id="rabbit-cage"><SelectValue placeholder="选择笼位" /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {cages.filter(
                        (cage) => cage.isEnabled && cage.houseId === houseId,
                      ).map((cage) => (
                        <SelectItem key={cage.id} value={String(cage.id)}>
                          {cage.cageNumber} · {cage.rabbitCount} 只
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              )}
              {cageValidationMessage ? (
                <FieldDescription className="text-destructive">
                  {cageValidationMessage}
                </FieldDescription>
              ) : null}
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="rabbit-type">类型</FieldLabel>
                <Select value={type} onValueChange={handleTypeChange} disabled={Boolean(rabbit)}>
                  <SelectTrigger id="rabbit-type"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {Object.entries(rabbitTypeLabels).map(([value, label]) => (
                        <SelectItem key={value} value={value}>{label}</SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="rabbit-gender">性别</FieldLabel>
                <Select value={gender} onValueChange={handleGenderChange} disabled={Boolean(rabbit)}>
                  <SelectTrigger id="rabbit-gender"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="0">母</SelectItem>
                      <SelectItem value="1">公</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="rabbit-growth-stage">生长阶段</FieldLabel>
                <Select value={growthStage} onValueChange={setGrowthStage}>
                  <SelectTrigger id="rabbit-growth-stage"><SelectValue placeholder="未填写" /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {growthStageOptions.map(([value, label]) => (
                        <SelectItem key={value} value={value}>{label}</SelectItem>
                      ))}
                    </SelectGroup>
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
                      <SelectGroup>
                        {reproductiveStageOptions.map(([value, label]) => (
                          <SelectItem key={value} value={value}>{label}</SelectItem>
                        ))}
                      </SelectGroup>
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
                    <Select value={reproStage} onValueChange={handleReproStageChange}>
                      <SelectTrigger id="rabbit-repro-stage"><SelectValue /></SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          <SelectItem value={NO_REPRO_ENTRY}>暂不入轨</SelectItem>
                          {entryPoints.map((entryPoint) => (
                            <SelectItem key={entryPoint.stage} value={entryPoint.stage}>
                              {entryPoint.stageLabel}
                            </SelectItem>
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
                  <>
                    <Field>
                      <FieldLabel htmlFor="rabbit-production-batch">生产批次</FieldLabel>
                      <Select
                        value={batchId}
                        onValueChange={setBatchId}
                        disabled={availableBatches.length === 0}
                      >
                        <SelectTrigger id="rabbit-production-batch">
                          <SelectValue placeholder={availableBatches.length === 0 ? '当前没有进行中的批次' : '选择批次'} />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectGroup>
                            {availableBatches.map((batch) => (
                              <SelectItem key={batch.id} value={String(batch.id)}>
                                {batch.batchCode}
                              </SelectItem>
                            ))}
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                      <FieldDescription>
                        {availableBatches.length === 0
                          ? '请先到生产管理创建批次，或选择“暂不入轨”。'
                          : '选择生产阶段后必须绑定进行中的批次；“暂不入轨”不需要选择。'}
                      </FieldDescription>
                    </Field>
                    <div className="grid gap-4 sm:grid-cols-2">
                    {needsMatingDate ? (
                      <Field>
                        <FieldLabel htmlFor="rabbit-mating-date">配种日期</FieldLabel>
                        <Input
                          id="rabbit-mating-date"
                          type="date"
                          value={matingDate}
                          onChange={(event) => setMatingDate(event.target.value)}
                        />
                      </Field>
                    ) : null}
                    {requiredFacts.has('BIRTH_DATE') ? (
                      <Field>
                        <FieldLabel htmlFor="rabbit-birth-date">分娩日期</FieldLabel>
                        <Input
                          id="rabbit-birth-date"
                          type="date"
                          value={birthDate}
                          onChange={(event) => setBirthDate(event.target.value)}
                        />
                      </Field>
                    ) : null}
                    {requiredFacts.has('LIVE_KITS') ? (
                      <Field>
                        <FieldLabel htmlFor="rabbit-live-kits">活仔数</FieldLabel>
                        <Input
                          id="rabbit-live-kits"
                          type="number"
                          min={0}
                          value={liveKits}
                          onChange={(event) => setLiveKits(event.target.value)}
                        />
                      </Field>
                    ) : null}
                    </div>
                  </>
                ) : null}
              </>
            ) : null}
            <Field>
              <FieldLabel htmlFor="rabbit-breed">品种</FieldLabel>
              <Input
                id="rabbit-breed"
                value={breed}
                maxLength={100}
                onChange={(event) => setBreed(event.target.value)}
              />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field>
                <FieldLabel htmlFor="rabbit-source">来源</FieldLabel>
                <Select
                  value={arrivalMethod}
                  onValueChange={(value) => {
                    setArrivalMethod(value)
                    setBatchEntryResult(null)
                  }}
                >
                  <SelectTrigger id="rabbit-source"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="0">购入</SelectItem>
                      <SelectItem value="1">出生</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="rabbit-date">入场日期</FieldLabel>
                <Input
                  id="rabbit-date"
                  type="date"
                  value={arrivalDate}
                  onChange={(event) => setArrivalDate(event.target.value)}
                />
              </Field>
            </div>
            {!rabbit && arrivalMethod === '0' ? (
              <Field>
                <FieldLabel htmlFor="rabbit-source-seller">供应方</FieldLabel>
                <Input
                  id="rabbit-source-seller"
                  maxLength={120}
                  value={sourceSeller}
                  onChange={(event) => {
                    setSourceSeller(event.target.value)
                    setBatchEntryResult(null)
                  }}
                />
              </Field>
            ) : null}
            {!rabbit && arrivalMethod === '1' ? (
              <Field>
                <FieldLabel htmlFor="rabbit-mother-id">母兔 ID（可选）</FieldLabel>
                <Input
                  id="rabbit-mother-id"
                  type="number"
                  min={1}
                  inputMode="numeric"
                  value={motherId}
                  onChange={(event) => {
                    setMotherId(event.target.value)
                    setBatchEntryResult(null)
                  }}
                />
                <FieldDescription>请填写当前兔场内在栏种母兔的 ID。</FieldDescription>
              </Field>
            ) : null}
            <Field>
              <FieldLabel htmlFor="rabbit-weight">体重（kg）</FieldLabel>
              <Input
                id="rabbit-weight"
                type="number"
                min={0}
                step="0.01"
                value={weight}
                onChange={(event) => {
                  setWeight(event.target.value)
                  setBatchEntryResult(null)
                }}
              />
            </Field>
            {!rabbit ? (
              <div className="grid gap-4 sm:grid-cols-2">
                <Field>
                  <FieldLabel htmlFor="rabbit-quantity">数量</FieldLabel>
                  <Input
                    id="rabbit-quantity"
                    type="number"
                    min={1}
                    max={10}
                    inputMode="numeric"
                    value={quantity}
                    onChange={(event) => {
                      setQuantity(event.target.value)
                      setBatchEntryResult(null)
                    }}
                  />
                  <FieldDescription>单次最多 10 只。</FieldDescription>
                </Field>
                {isBatchEntry ? (
                  <Field>
                    <FieldLabel htmlFor="rabbit-total-weight">总重量（kg）</FieldLabel>
                    <Input
                      id="rabbit-total-weight"
                      type="number"
                      min={0.01}
                      max={100}
                      step="0.01"
                      value={totalWeight}
                      onChange={(event) => {
                        setTotalWeight(event.target.value)
                        setBatchEntryResult(null)
                      }}
                    />
                    <FieldDescription>将按数量折算为每只体重。</FieldDescription>
                  </Field>
                ) : null}
              </div>
            ) : null}
          </FieldGroup>
          {batchEntryResult ? (
            <div className="border bg-secondary p-3 text-sm">
              <p>已录入 {batchEntryResult.enteredRabbitCount} 只。</p>
              {batchEntryResult.skippedCages.length > 0 ? (
                <p className="mt-1 text-muted-foreground">
                  未录入：{batchEntryResult.skippedCages
                    .map((item) => `${item.cageNumber || `#${item.cageId}`}：${item.rabbitCount}只，${item.reason}`)
                    .join('；')}
                </p>
              ) : null}
            </div>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
            <Button
              type={batchEntryResult ? 'button' : 'submit'}
              onClick={batchEntryResult ? () => onOpenChange(false) : undefined}
              disabled={saving || !cageId || Boolean(cageValidationMessage) || Boolean(selectedEntry && !batchId)}
            >
              {saving ? <Spinner data-icon="inline-start" /> : null}
              {batchEntryResult ? '关闭' : '保存'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** 换笼必须走专用端点，种兔和后备兔才能在目标笼已占用时正确对调。 */
export function RabbitTransferDialog({
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
  const [cageNumberInput, setCageNumberInput] = useState('')
  const [numberHint, setNumberHint] = useState<string | null>(null)

  useEffect(() => {
    if (!rabbit) return
    setTargetCageId('')
    setCageNumberInput('')
    setNumberHint(null)
  }, [rabbit])

  const layout = useMemo(() => buildCageLayout(cages), [cages])

  const acceptsTarget = useCallback(
    (cage: Cage) => {
      if (!rabbit || !cage.isEnabled || cage.id === rabbit.cageId) return false
      if (cage.rabbitCount <= 0) return true
      if (rabbit.type === '2') {
        return cage.status === '3' && cageAcceptsMoreRabbits(cage)
      }
      return cage.status === '1' || cage.status === '2'
    },
    [rabbit],
  )

  const isSwapTarget = useCallback(
    (cage: Cage) => Boolean(
      rabbit &&
      cage.id !== rabbit.cageId &&
      cage.rabbitCount > 0 &&
      rabbit.type !== '2' &&
      (cage.status === '1' || cage.status === '2'),
    ),
    [rabbit],
  )

  const options = useMemo(
    () => cages.filter((cage) => acceptsTarget(cage)),
    [acceptsTarget, cages],
  )

  function selectByExactNumber(value: string) {
    setCageNumberInput(value)
    const keyword = value.trim().toLowerCase()
    if (!keyword) {
      setNumberHint(null)
      return
    }
    const matches = cages.filter(
      (cage) => cage.cageNumber.toLowerCase() === keyword || String(cage.id) === keyword,
    )
    if (matches.length !== 1) {
      setNumberHint(matches.length > 1 ? '笼位编号不唯一，请在地图上选' : null)
      return
    }
    const matched = matches[0]
    if (!acceptsTarget(matched)) {
      setTargetCageId('')
      setNumberHint(`${matched.cageNumber} 不能接收该兔`)
      return
    }
    setTargetCageId(String(matched.id))
    setNumberHint(
      isSwapTarget(matched)
        ? `已选中 ${matched.cageNumber}，将与笼内兔只对调`
        : `已选中 ${matched.cageNumber}`,
    )
  }

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
            <Select
              value={targetCageId}
              onValueChange={(value) => {
                setTargetCageId(value)
                setNumberHint(null)
              }}
            >
              <SelectTrigger id="transfer-target-cage"><SelectValue placeholder="选择目标笼位" /></SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {options.map((cage) => (
                    <SelectItem key={cage.id} value={String(cage.id)}>
                      {cage.cageNumber} · {cageUsageLabel(cage)} · {cage.rabbitCount} 只
                      {isSwapTarget(cage) ? ' · 对调' : ''}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </Field>
          <Field>
            <FieldLabel htmlFor="transfer-cage-number">输入笼位编号</FieldLabel>
            <Input
              id="transfer-cage-number"
              value={cageNumberInput}
              placeholder="完整对上就直接选中，如 2-3-1"
              onChange={(event) => selectByExactNumber(event.target.value)}
            />
            {numberHint ? (
              <p className="text-xs text-muted-foreground" data-testid="transfer-number-hint">
                {numberHint}
              </p>
            ) : null}
          </Field>
          {layout.layers.length > 0 || layout.unplaced.length > 0 ? (
            <Field>
              <FieldLabel>在地图上选</FieldLabel>
              <CageAttentionLegend cages={cages} />
              <div className="max-h-72 overflow-y-auto">
                <CageMap
                  layout={layout}
                  selectedCageId={targetCageId ? Number(targetCageId) : null}
                  isSelectable={acceptsTarget}
                  cellNote={(cage) => {
                    if (cage.id === rabbit?.cageId) return '当前'
                    return isSwapTarget(cage) ? '对调' : null
                  }}
                  onSelectCage={(cage) => {
                    setTargetCageId(String(cage.id))
                    setCageNumberInput('')
                    setNumberHint(
                      isSwapTarget(cage)
                        ? `已选中 ${cage.cageNumber}，将与笼内兔只对调`
                        : `已选中 ${cage.cageNumber}`,
                    )
                  }}
                />
              </div>
            </Field>
          ) : null}
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

export function RabbitReplacementDialog({
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
  const pendingRequest = useRef<RabbitReplacementRequest | null>(null)
  const targetCages = useMemo(
    () => houseId ? cages.filter((cage) => isReplacementTargetCage(cage, houseId)) : [],
    [cages, houseId],
  )

  useEffect(() => {
    if (!rabbit) {
      setTargetCageId('')
      pendingRequest.current = null
      return
    }
    setTargetCageId((current) => targetCages.some(
      (cage) => cage.id === Number(current),
    ) ? current : String(targetCages[0]?.id ?? ''))
    pendingRequest.current = null
  }, [rabbit, targetCages])

  async function handleSubmit() {
    if (!rabbit || !houseId || !targetCageId || saving) return
    const request = getOrCreateRabbitReplacementRequest(
      pendingRequest.current,
      {
        rabbitIds: [rabbit.id],
        forceExitBatch: true,
        targetCageId: Number(targetCageId),
      },
      requestId,
    )
    pendingRequest.current = request
    setSaving(true)
    try {
      await retainRabbitsAsReplacement(houseId, request)
      pendingRequest.current = null
      toast.success(`兔 #${rabbit.id} 已留种并转为后备兔`)
      onOpenChange(false)
      await onSaved()
    } catch {
      // 共享请求层会展示失败原因；草稿未变化时保留 requestId 以支持幂等重试。
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={Boolean(rabbit)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>留种转后备</DialogTitle>
          <DialogDescription>
            兔 #{rabbit?.id ?? ''} 会退出当前活跃批次、转为后备兔，并移入所选后备兔笼。
          </DialogDescription>
        </DialogHeader>
        <Field>
          <FieldLabel htmlFor="replacement-target-cage">目标笼位</FieldLabel>
          <Select
            value={targetCageId}
            onValueChange={setTargetCageId}
            disabled={saving || targetCages.length === 0}
          >
            <SelectTrigger id="replacement-target-cage">
              <SelectValue placeholder="选择空闲笼位" />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {targetCages.map((cage) => (
                  <SelectItem key={cage.id} value={String(cage.id)}>
                    {cage.cageNumber} · {cageUsageLabel(cage)}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
          {targetCages.length === 0 ? (
            <FieldDescription>当前兔场没有启用且空闲的后备兔笼或空笼。</FieldDescription>
          ) : null}
        </Field>
        <DialogFooter>
          <Button variant="outline" disabled={saving} onClick={() => onOpenChange(false)}>取消</Button>
          <Button disabled={saving || !targetCageId} onClick={() => void handleSubmit()}>
            {saving ? <Spinner data-icon="inline-start" /> : <SproutIcon data-icon="inline-start" />}
            确认留种
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function RabbitPromotionDialog({
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
  const [saving, setSaving] = useState(false)
  const pendingRequestId = useRef<string | null>(null)

  useEffect(() => {
    if (!rabbit) return
    pendingRequestId.current = null
  }, [rabbit])

  async function handleSubmit() {
    if (!rabbit || !houseId) return
    const promotionRequestId = pendingRequestId.current ?? requestId()
    pendingRequestId.current = promotionRequestId
    setSaving(true)
    try {
      await promoteReplacementRabbit(houseId, rabbit.id, promotionRequestId)
      pendingRequestId.current = null
      toast.success(`兔 #${rabbit.id} 已转为种兔`)
      onOpenChange(false)
      await onSaved()
    } catch {
      // 共享请求层会展示失败原因；参数未变化时保留 requestId 以支持幂等重试。
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={Boolean(rabbit)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>后备兔转种兔</DialogTitle>
          <DialogDescription>
            兔 #{rabbit?.id ?? ''} 将转为种兔，当前笼位会同步设为种兔笼。符合条件的母兔会进入待催情生产周期。
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" disabled={saving} onClick={() => onOpenChange(false)}>取消</Button>
          <Button disabled={saving} onClick={() => void handleSubmit()}>
            {saving ? <Spinner data-icon="inline-start" /> : <ArrowUpRightIcon data-icon="inline-start" />}
            确认转种
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** 对任意在栏兔登记死亡或淘汰，并显式退出活跃批次和生产周期。 */
export function RabbitDepartureDialog({
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
          <DialogDescription>
            兔 #{rabbit?.id ?? ''} 将标记为离场，同时退出活跃批次并关闭进行中的生产周期。
          </DialogDescription>
        </DialogHeader>
        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="livestock-departure-type">离场类型</FieldLabel>
            <Select
              value={departureType}
              onValueChange={(value) => setDepartureType(value as RabbitDepartureType)}
            >
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
            <Textarea
              id="livestock-departure-remark"
              value={remark}
              onChange={(event) => setRemark(event.target.value)}
            />
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
                <span className="text-muted-foreground">
                  将同时退出活跃批次关系并关闭进行中的生产周期。
                </span>
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
