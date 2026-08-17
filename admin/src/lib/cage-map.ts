import type { Cage } from '@/types/api'

/**
 * 把一串扁平的笼位还原成「排 → 层 → 位」的分层地图，并按「关注度」归类。
 *
 * 与 Flutter 端 `app/lib/src/domain/models/cage_layout.dart` /
 * `cage_attention.dart` 保持同一套规则：两端看同一批数据，结论必须一致，
 * 否则同一个笼在手机上是「异常」、在后台是「正常」，用户会先不信任后台。
 *
 * 这里只做纯粹的分组、排序与判定，不碰任何 UI，方便用 node:test 钉住边界。
 */

/** 商品兔笼容量上限，对应后端 `app.cage.commodity-capacity` 的默认值。 */
export const COMMODITY_CAPACITY = 10

/**
 * 地图的着色维度是**关注度**，回答「今天该去处理哪些笼」，
 * 而不是「这个笼是什么用途」——用途在格子里以文字呈现，不占颜色。
 *
 * 数组顺序即优先级：一个笼可能同时满足多条（停用却还留着兔、待投喂又满笼），
 * 只呈现最需要人过去处理的那一条，否则地图会变成一堆并列徽标。
 */
export const CAGE_ATTENTIONS = ['alert', 'disabled', 'needsFeeding', 'full', 'vacancy'] as const

export type CageAttention = (typeof CAGE_ATTENTIONS)[number]

export const cageAttentionLabels: Record<CageAttention, string> = {
  alert: '异常',
  disabled: '停用',
  needsFeeding: '待投喂',
  full: '已满',
  vacancy: '有空位',
}

export const cageAttentionHints: Record<CageAttention, string> = {
  alert: '账不平，需要核对',
  disabled: '已停用，不可使用',
  needsFeeding: '有兔但今日未投喂',
  full: '放不下了',
  vacancy: '还能放兔',
}

/** 需要人主动处理的两种状态，用于排头部提示。 */
export function isActionableAttention(attention: CageAttention): boolean {
  return attention === 'alert' || attention === 'needsFeeding'
}

/**
 * 具体说明哪里账不平；没有异常时返回 null。
 *
 * 只依赖笼位列表接口已有的字段，不额外请求——异常要在地图上一眼看到，
 * 而不是点进详情才发现。
 */
export function cageAlertReason(cage: Cage): string | null {
  if (cage.rabbitCount < 0) {
    return `在栏数为负数（${cage.rabbitCount}）`
  }
  if (!cage.isEnabled && cage.rabbitCount > 0) {
    return `已停用但笼内仍有 ${cage.rabbitCount} 只`
  }
  if (cage.status === '0' && cage.rabbitCount > 0) {
    return `标记为空闲却记着 ${cage.rabbitCount} 只`
  }
  if ((cage.status === '1' || cage.status === '2') && cage.rabbitCount > 1) {
    return `单兔笼记着 ${cage.rabbitCount} 只`
  }
  if (cage.status === '3' && cage.rabbitCount > COMMODITY_CAPACITY) {
    return `超出商品兔笼上限（${cage.rabbitCount} / ${COMMODITY_CAPACITY}）`
  }
  return null
}

/** 种兔笼、后备兔笼仅允许存放 1 只；商品兔笼最多 COMMODITY_CAPACITY 只。 */
export function cageAcceptsMoreRabbits(cage: Cage): boolean {
  if (!cage.isEnabled) {
    return false
  }
  if (cage.status === '1' || cage.status === '2') {
    return cage.rabbitCount < 1
  }
  if (cage.status === '3') {
    return cage.rabbitCount < COMMODITY_CAPACITY
  }
  return true
}

export function cageAttention(cage: Cage): CageAttention {
  if (cageAlertReason(cage) !== null) {
    return 'alert'
  }
  if (!cage.isEnabled) {
    return 'disabled'
  }
  if (!cage.isFed && cage.rabbitCount > 0) {
    return 'needsFeeding'
  }
  return cageAcceptsMoreRabbits(cage) ? 'vacancy' : 'full'
}

/** 格子里的占用文字：空笼写「空」，商品笼写「3/10」，单兔笼只写只数。 */
export function cageOccupancyText(cage: Cage): string {
  if (cage.rabbitCount <= 0) {
    return '空'
  }
  if (cage.status === '3') {
    return `${cage.rabbitCount}/${COMMODITY_CAPACITY}`
  }
  return `${cage.rabbitCount} 只`
}

export interface CageMapCell {
  positionIndex: number
  /** 该坐标没有笼位时为 null：留空槽，不把后面的笼往前挤。 */
  cage: Cage | null
}

export interface CageMapLayer {
  layerIndex: number
  /** 位号从小到大。 */
  cells: CageMapCell[]
}

export interface CageMapRow {
  rowCode: string
  /** 层号从大到小。 */
  layers: CageMapLayer[]
  /** 该排最大位号，决定网格列数。 */
  positionSpan: number
  cages: Cage[]
}

export interface CageLayout {
  rows: CageMapRow[]
  /**
   * 没有层/位坐标、排号为空或 `LEGACY` 的笼位，以及坐标撞车被挤出来的笼位。
   * 它们放不进网格，但绝不能从界面上消失。
   */
  unplaced: Cage[]
}

function normalizeIndex(value: number | null | undefined): number | null {
  // 后端历史数据里 0 表示「没有坐标」，负数是脏数据，都不该被当成第 0 位。
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
    return null
  }
  return Math.trunc(value)
}

function isPlaceable(cage: Cage): boolean {
  // 'LEGACY' 是历史数据的占位排号，不是真实排，进「未编排」。
  const rowCode = (cage.rowCode ?? '').trim()
  return (
    normalizeIndex(cage.layerIndex) !== null &&
    normalizeIndex(cage.positionIndex) !== null &&
    rowCode.length > 0 &&
    rowCode !== 'LEGACY'
  )
}

/** R2 要排在 R10 前面：按「数字段按数值、其余按字符」比较。 */
export function compareRowCodes(a: string, b: string): number {
  const left = segments(a)
  const right = segments(b)
  for (let index = 0; index < left.length && index < right.length; index += 1) {
    const l = left[index]
    const r = right[index]
    if (typeof l === 'number' && typeof r === 'number') {
      if (l !== r) {
        return l - r
      }
      continue
    }
    const compared = String(l).localeCompare(String(r))
    if (compared !== 0) {
      return compared
    }
  }
  return left.length - right.length
}

function segments(value: string): Array<string | number> {
  const result: Array<string | number> = []
  let buffer = ''
  let bufferIsDigit = false

  const flush = () => {
    if (buffer.length === 0) return
    result.push(bufferIsDigit ? Number(buffer) : buffer.toUpperCase())
    buffer = ''
  }

  for (const char of value) {
    const isDigit = char >= '0' && char <= '9'
    if (buffer.length > 0 && isDigit !== bufferIsDigit) {
      flush()
    }
    bufferIsDigit = isDigit
    buffer += char
  }
  flush()
  return result
}

export function buildCageLayout(cages: Cage[]): CageLayout {
  const byRow = new Map<string, Cage[]>()
  const unplaced: Cage[] = []

  for (const cage of cages) {
    if (isPlaceable(cage)) {
      const rowCode = (cage.rowCode ?? '').trim()
      const bucket = byRow.get(rowCode)
      if (bucket) {
        bucket.push(cage)
      } else {
        byRow.set(rowCode, [cage])
      }
    } else {
      unplaced.push(cage)
    }
  }

  const rows: CageMapRow[] = []
  for (const rowCode of [...byRow.keys()].sort(compareRowCodes)) {
    const built = buildRow(rowCode, byRow.get(rowCode) ?? [])
    rows.push(built.row)
    unplaced.push(...built.displaced)
  }

  unplaced.sort((a, b) => a.cageNumber.localeCompare(b.cageNumber))
  return { rows, unplaced }
}

function buildRow(rowCode: string, cages: Cage[]): { row: CageMapRow; displaced: Cage[] } {
  const displaced: Cage[] = []
  const slots = new Map<number, Map<number, Cage>>()
  let positionSpan = 0

  for (const cage of cages) {
    const layer = normalizeIndex(cage.layerIndex) as number
    const position = normalizeIndex(cage.positionIndex) as number
    let layerSlots = slots.get(layer)
    if (!layerSlots) {
      layerSlots = new Map<number, Cage>()
      slots.set(layer, layerSlots)
    }
    if (layerSlots.has(position)) {
      // 同一坐标出现两个笼位属于数据问题。保留先到的那个，另一个挪到
      // 「未编排」而不是覆盖掉，否则界面上会凭空少一个笼。
      displaced.push(cage)
      continue
    }
    layerSlots.set(position, cage)
    if (position > positionSpan) {
      positionSpan = position
    }
  }

  // 层号从大到小：现实里最上层在最上面，地图跟着物理货架走。
  const layerIndexes = [...slots.keys()].sort((a, b) => b - a)
  const layers: CageMapLayer[] = layerIndexes.map((layerIndex) => {
    const layerSlots = slots.get(layerIndex) as Map<number, Cage>
    return {
      layerIndex,
      // 补齐空槽，让每层的第 N 列在屏幕上真的对齐；缺笼的位置留白。
      cells: Array.from({ length: positionSpan }, (_, index) => ({
        positionIndex: index + 1,
        cage: layerSlots.get(index + 1) ?? null,
      })),
    }
  })

  const placed: Cage[] = []
  for (const layer of layers) {
    for (const cell of layer.cells) {
      if (cell.cage) placed.push(cell.cage)
    }
  }

  return { row: { rowCode, layers, positionSpan, cages: placed }, displaced }
}

/** 按关注度统计，用于图例与每排概览。 */
export function countAttentions(cages: Cage[]): Record<CageAttention, number> {
  const counts: Record<CageAttention, number> = {
    alert: 0,
    disabled: 0,
    needsFeeding: 0,
    full: 0,
    vacancy: 0,
  }
  for (const cage of cages) {
    counts[cageAttention(cage)] += 1
  }
  return counts
}
