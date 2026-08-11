export function formatLocalDate(date = new Date()) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function formatDateInput(value?: string | null) {
  if (!value) {
    return formatLocalDate()
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value.slice(0, 10) : formatLocalDate(date)
}
