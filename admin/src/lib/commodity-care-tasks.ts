import type { ReproTask, ReproTaskPage } from '@/types/api'

export const commodityCareTaskTypes = [
  'COMMODITY_ADAPTATION_CARE',
  'COMMODITY_GROWING_CARE',
  'COMMODITY_FATTENING_CARE',
] as const

export function summarizeCommodityCareTasks(pages: ReproTaskPage[]): ReproTaskPage {
  const items = pages.flatMap((page) => page.items).sort(compareTasks)
  return {
    total: pages.reduce((total, page) => total + page.total, 0),
    page: 1,
    size: items.length,
    items,
  }
}

function compareTasks(left: ReproTask, right: ReproTask) {
  const due = dueTimestamp(left) - dueTimestamp(right)
  return due !== 0 ? due : left.id - right.id
}

function dueTimestamp(task: ReproTask) {
  const value = task.dueTime ?? task.dueDate
  if (value == null) return Number.MAX_SAFE_INTEGER
  const timestamp = new Date(value).getTime()
  return Number.isNaN(timestamp) ? Number.MAX_SAFE_INTEGER : timestamp
}
