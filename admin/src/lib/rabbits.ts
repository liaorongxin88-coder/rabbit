import type { Rabbit } from '@/types/api'

export const rabbitTypeLabels: Record<string, string> = {
  '0': '种兔',
  '1': '后备兔',
  '2': '商品兔',
}

export const growthStageLabels: Record<string, string> = {
  JUVENILE: '适应期',
  ADAPTATION: '适应期',
  GROWING: '成长期',
  FATTENING: '育肥期',
  MATURE: '成熟可售',
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

export const growthStageOptions = [
  ['ADAPTATION', growthStageLabels.ADAPTATION],
  ['GROWING', growthStageLabels.GROWING],
  ['FATTENING', growthStageLabels.FATTENING],
  ['MATURE', growthStageLabels.MATURE],
] as const

const buckReproductiveStageOptions = [
  ['READY', '可配'],
  ['RESTING', '休整'],
] as const

const replacementReproductiveStageOptions = [['RESERVE', '后备']] as const

export function rabbitTypeLabel(rabbit: Pick<Rabbit, 'type' | 'gender'>) {
  if (rabbit.type === '0') {
    return rabbit.gender === '0' ? '种母兔' : rabbit.gender === '1' ? '种公兔' : '种兔'
  }
  return rabbitTypeLabels[rabbit.type] ?? rabbit.type ?? '未分类'
}

export function isReplacementPromotionTarget(
  rabbit: Pick<Rabbit, 'isActive' | 'type'>,
) {
  return rabbit.isActive && rabbit.type === '1'
}

export function rabbitGenderLabel(gender: string) {
  return gender === '0' ? '母' : gender === '1' ? '公' : '未知'
}

export function rabbitArrivalMethodLabel(arrivalMethod?: string | null) {
  if (arrivalMethod === '0') return '购入'
  if (arrivalMethod === '1') return '场内生产'
  return arrivalMethod || '-'
}

/**
 * 可手工录入的旧繁殖阶段。种母兔阶段由生产流程状态机维护，不能在资料表单里手改。
 */
export function reproductiveOptions(type: string, gender: string) {
  if (type === '2') return []
  if (type === '1') return replacementReproductiveStageOptions
  return gender === '1' ? buckReproductiveStageOptions : []
}

export function defaultReproductiveStage(type: string, gender: string) {
  return reproductiveOptions(type, gender)[0]?.[0] ?? ''
}

function stageLabel(value: string | null | undefined, labels: Record<string, string>) {
  return value ? labels[value] ?? value : null
}

/** 种母兔优先显示生产流程投影，避免和旧繁殖阶段口径冲突。 */
export function rabbitStageSummary(
  rabbit: Pick<Rabbit, 'currentStage' | 'reproductiveStage' | 'growthStage'>,
  reproStageLabels: Record<string, string>,
) {
  const repro = rabbit.currentStage
    ? reproStageLabels[rabbit.currentStage] ?? rabbit.currentStage
    : stageLabel(rabbit.reproductiveStage, reproductiveStageLabels)
  const labels = [stageLabel(rabbit.growthStage, growthStageLabels), repro].filter(Boolean)
  return labels.length > 0 ? labels.join(' · ') : '阶段未填写'
}
