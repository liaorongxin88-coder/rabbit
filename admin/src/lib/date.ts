const DATE_ONLY_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const FARM_MIDNIGHT_OFFSET = '+08:00'
const farmDateFormatter = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

export function formatLocalDate(date = new Date()) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function formatFarmBusinessDate(date = new Date()) {
  const parts = farmDateFormatter.formatToParts(date).reduce<Record<string, string>>(
    (result, part) => {
      if (part.type !== 'literal') {
        result[part.type] = part.value
      }
      return result
    },
    {},
  )
  return `${parts.year}-${parts.month}-${parts.day}`
}

export function formatDateInput(value?: string | null) {
  if (!value) {
    return formatFarmBusinessDate()
  }
  if (DATE_ONLY_PATTERN.test(value)) {
    return value
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value.slice(0, 10) : formatFarmBusinessDate(date)
}

export function farmBusinessDateToTimestamp(value: string) {
  if (!DATE_ONLY_PATTERN.test(value)) {
    return undefined
  }
  const timestamp = Date.parse(`${value}T00:00:00${FARM_MIDNIGHT_OFFSET}`)
  if (!Number.isFinite(timestamp) || formatFarmBusinessDate(new Date(timestamp)) !== value) {
    return undefined
  }
  return timestamp
}

export function farmBusinessDateToIso(value: string) {
  const timestamp = farmBusinessDateToTimestamp(value)
  return timestamp === undefined ? undefined : new Date(timestamp).toISOString()
}

export function formatRecordDate(value?: string | null) {
  return value ? formatDateInput(value) : '-'
}
