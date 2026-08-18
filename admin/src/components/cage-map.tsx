import { AlertCircleIcon, BanIcon, CircleMinusIcon, CirclePlusIcon, ClockIcon } from 'lucide-react'
import { useEffect, useState, type ComponentType } from 'react'

import { Button } from '@/components/ui/button'
import {
  CAGE_ATTENTIONS,
  cageAlertReason,
  cageAttention,
  cageAttentionHints,
  cageAttentionLabels,
  cageOccupancyText,
  countAttentions,
  type CageAttention,
  type CageLayout,
} from '@/lib/cage-map'
import { cn } from '@/lib/utils'
import type { Cage } from '@/types/api'

/**
 * 笼位分层地图：按「排 → 层 → 位」还原货架，用颜色标关注度。
 *
 * 两条不能破的规则（同样写在 Flutter 端 `app/.rule` 里）：
 * 1. 主色（teal）不表示任何笼位状态，只表示「选中」——这张图同时是换笼的选择器，
 *    如果主色既是状态又是选中，用户就分不清自己点没点中。
 * 2. 颜色永远配图标和文字，不做唯一信号，色觉障碍下也能读（DESIGN.md 无障碍条款）。
 */

const attentionIcons: Record<CageAttention, ComponentType<{ className?: string }>> = {
  alert: AlertCircleIcon,
  disabled: BanIcon,
  needsFeeding: ClockIcon,
  full: CircleMinusIcon,
  vacancy: CirclePlusIcon,
}

// 只用语义 token 的低透明度填充，避免把页面变成彩色块（DESIGN.md：不要大面积色块）。
const attentionCellStyles: Record<CageAttention, string> = {
  alert: 'border-destructive/40 bg-destructive/5 text-destructive',
  disabled: 'border-border bg-secondary text-muted-foreground',
  needsFeeding: 'border-warning/40 bg-warning/5 text-warning',
  full: 'border-border bg-card text-foreground',
  vacancy: 'border-accent/40 bg-accent/5 text-accent',
}

const attentionLegendStyles: Record<CageAttention, string> = {
  alert: 'text-destructive',
  disabled: 'text-muted-foreground',
  needsFeeding: 'text-warning',
  full: 'text-foreground',
  vacancy: 'text-accent',
}

export function CageAttentionLegend({ cages, className }: { cages: Cage[]; className?: string }) {
  const counts = countAttentions(cages)
  return (
    <div className={cn('flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs', className)} data-testid="cage-map-legend">
      {CAGE_ATTENTIONS.map((attention) => {
        const Icon = attentionIcons[attention]
        return (
          <span
            key={attention}
            className={cn('inline-flex items-center gap-1', attentionLegendStyles[attention])}
            title={cageAttentionHints[attention]}
          >
            <Icon className="size-3.5" aria-hidden="true" />
            {cageAttentionLabels[attention]}
            {counts[attention] > 0 ? <span className="tabular-nums">{counts[attention]}</span> : null}
          </span>
        )
      })}
    </div>
  )
}

export function CageMap({
  layout,
  selectedCageId,
  isSelectable,
  cellNote,
  isMatch,
  visibleRowLimit,
  onShowMoreRows,
  onSelectCage,
}: {
  layout: CageLayout
  selectedCageId?: number | null
  /** 返回 false 的笼位会变淡且不可点，例如换笼时放不下的目标。 */
  isSelectable?: (cage: Cage) => boolean
  /** 格子底部的一行小字，例如换笼时的「对调」「当前」。 */
  cellNote?: (cage: Cage) => string | null
  /** 命中筛选条件的笼位；未命中的只变淡，不从图上移除，否则坐标会错位。 */
  isMatch?: (cage: Cage) => boolean
  visibleRowLimit?: number
  onShowMoreRows?: () => void
  onSelectCage?: (cage: Cage) => void
}) {
  // 层是切换出来的空间：现场的多层笼是错位阶梯，人站在某一层前面时
  // 眼里只有这一层的那几排，所以一次只画一层。
  const layers = layout.layers
  const [activeLayer, setActiveLayer] = useState<number | null>(layers[0]?.layerIndex ?? null)

  // 换笼时可以直接输入编号选中一个笼，它可能不在当前层。不跟着切的话，
  // 用户看到「已选中 B7」，图上却没有任何一格是高亮的。
  const selectedLayer = selectedCageId
    ? (layers.find((layer) => layer.cages.some((cage) => cage.id === selectedCageId))?.layerIndex ?? null)
    : null
  useEffect(() => {
    if (selectedLayer !== null) setActiveLayer(selectedLayer)
  }, [selectedLayer])

  const active = layers.find((layer) => layer.layerIndex === activeLayer) ?? layers[0] ?? null
  const rows = active?.rows ?? []
  const limit = visibleRowLimit ?? rows.length
  const visibleRows = rows.slice(0, limit)
  const hiddenRowCount = rows.length - visibleRows.length

  return (
    <div className="flex flex-col gap-3" data-testid="cage-map">
      {layers.length > 1 ? (
        <div className="flex flex-wrap gap-2" data-testid="cage-map-layer-switcher">
          {layers.map((layer) => {
            // 切层会把别的层整个藏起来，所以层签上带该层「要处理的笼」数量，
            // 否则站在 1 层永远不知道 3 层有笼子等着喂。
            const counts = countAttentions(layer.cages)
            const todo = counts.alert + counts.needsFeeding
            const selected = layer.layerIndex === active?.layerIndex
            return (
              <button
                key={layer.layerIndex}
                type="button"
                data-testid={`cage-map-layer-${layer.layerIndex}`}
                aria-pressed={selected}
                onClick={() => setActiveLayer(layer.layerIndex)}
                className={cn(
                  'inline-flex min-h-9 items-center gap-2 rounded-md border px-3 text-sm',
                  selected ? 'border-primary bg-primary/10 font-medium text-primary' : 'hover:bg-muted',
                )}
              >
                {layer.layerIndex} 层
                {todo > 0 ? (
                  <span className="rounded-full bg-warning/15 px-1.5 text-xs tabular-nums text-warning">{todo}</span>
                ) : null}
              </button>
            )
          })}
        </div>
      ) : null}

      {visibleRows.map((row) => {
        const counts = countAttentions(row.cages)
        return (
          <div key={row.rowCode} className="rounded-md border" data-testid={`cage-map-row-${row.rowCode}`}>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 border-b px-3 py-2 text-xs">
              <span className="text-sm font-medium">{row.rowCode} 排</span>
              <span className="text-muted-foreground">{row.cages.length} 笼</span>
              {counts.vacancy > 0 ? <span className="text-accent">空位 {counts.vacancy}</span> : null}
              {counts.needsFeeding > 0 ? <span className="text-warning">待投喂 {counts.needsFeeding}</span> : null}
              {counts.alert > 0 ? <span className="text-destructive">异常 {counts.alert}</span> : null}
            </div>
            {/* 一排一个横向滚动区：列数由该排最大位号决定，窄屏下横向滚动
                比换行更可信——换行会让「第几位」错位。
                一排是双面笼架，位号绕着架子走，所以折成两行、回程那行反着排。 */}
            <div className="overflow-x-auto px-3 py-2">
              <div className="flex min-w-max flex-col gap-1.5">
                {row.lines.map((line, lineIndex) => (
                  <div key={lineIndex} className="flex items-stretch gap-1.5">
                    {line.cells.map((cell, cellIndex) =>
                      cell.positionIndex === null ? (
                        // 折角对齐用的留白：不是笼位，也不是缺笼，什么都不画。
                        <span key={`pad-${cellIndex}`} aria-hidden="true" className="h-16 w-16 shrink-0" />
                      ) : cell.cage ? (
                        <CageMapCellButton
                          key={cell.positionIndex}
                          cage={cell.cage}
                          positionIndex={cell.positionIndex}
                          selected={selectedCageId === cell.cage.id}
                          selectable={isSelectable ? isSelectable(cell.cage) : true}
                          dimmed={isMatch ? !isMatch(cell.cage) : false}
                          note={cellNote?.(cell.cage) ?? null}
                          onSelect={onSelectCage}
                        />
                      ) : (
                        <span
                          key={cell.positionIndex}
                          aria-label={`第 ${cell.positionIndex} 位，缺笼`}
                          className="flex h-16 w-16 shrink-0 flex-col items-center justify-center rounded-md border border-dashed text-xs text-muted-foreground/60"
                        >
                          <span className="self-start pl-1.5 text-[10px]">{cell.positionIndex}</span>
                          <span className="grow content-center">-</span>
                        </span>
                      ),
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        )
      })}

      {hiddenRowCount > 0 ? (
        <Button variant="outline" size="sm" className="self-start" onClick={onShowMoreRows} data-testid="cage-map-more-rows">
          显示更多排（还有 {hiddenRowCount} 排）
        </Button>
      ) : null}

      {layout.unplaced.length > 0 ? (
        <div className="rounded-md border border-dashed p-3" data-testid="cage-map-unplaced">
          <p className="mb-2 text-xs text-muted-foreground">
            未编排 {layout.unplaced.length} 笼：缺少排 / 层 / 位坐标，编辑笼位补上后会回到地图。
          </p>
          <div className="flex flex-wrap gap-1.5">
            {layout.unplaced.map((cage) => (
              <CageMapCellButton
                key={cage.id}
                cage={cage}
                selected={selectedCageId === cage.id}
                selectable={isSelectable ? isSelectable(cage) : true}
                dimmed={isMatch ? !isMatch(cage) : false}
                note={cellNote?.(cage) ?? null}
                onSelect={onSelectCage}
                showCageNumber
              />
            ))}
          </div>
        </div>
      ) : null}
    </div>
  )
}

function CageMapCellButton({
  cage,
  positionIndex,
  selected,
  selectable,
  dimmed,
  note,
  onSelect,
  showCageNumber = false,
}: {
  cage: Cage
  /** 未编排的笼位没有位号。 */
  positionIndex?: number
  selected: boolean
  selectable: boolean
  dimmed: boolean
  note: string | null
  onSelect?: (cage: Cage) => void
  showCageNumber?: boolean
}) {
  const attention = cageAttention(cage)
  const Icon = attentionIcons[attention]
  const reason = cageAlertReason(cage)
  const description = [
    cage.cageNumber,
    positionIndex ? `第 ${positionIndex} 位` : null,
    cageOccupancyText(cage),
    cageAttentionLabels[attention],
    reason,
    selectable ? null : '不可选择',
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <button
      type="button"
      // 完整编号写不进格子，格子上只写位号（排号在排头、层号在切换器，
      // 三者拼起来就是一个笼位）；完整编号走 title/aria-label。
      title={description}
      aria-label={description}
      aria-pressed={selected}
      disabled={!selectable}
      data-testid={`cage-map-cell-${cage.id}`}
      onClick={() => onSelect?.(cage)}
      className={cn(
        'motion-press flex h-16 shrink-0 flex-col items-center justify-center gap-0.5 rounded-md border px-1 text-xs',
        showCageNumber ? 'w-24' : 'w-16',
        attentionCellStyles[attention],
        // 主色只表示选中，不表示任何笼位状态。
        selected ? 'border-primary ring-2 ring-primary/40' : null,
        dimmed ? 'opacity-40' : null,
        selectable ? 'cursor-pointer hover:border-primary/60' : 'cursor-not-allowed opacity-50',
      )}
    >
      <span className="flex w-full items-center justify-between gap-1">
        <span className="text-[10px] tabular-nums text-muted-foreground">{positionIndex ?? ''}</span>
        <Icon className="size-4 shrink-0" aria-hidden="true" />
      </span>
      {showCageNumber ? <span className="w-full truncate font-medium">{cage.cageNumber}</span> : null}
      <span className="w-full truncate tabular-nums">{cageOccupancyText(cage)}</span>
      {note ? <span className="w-full truncate text-[10px]">{note}</span> : null}
    </button>
  )
}
