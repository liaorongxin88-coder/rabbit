export const BATCH_CODE_MAX_LENGTH = 100

export function defaultBatchCode(houseName: string, date = new Date()) {
  const timestamp = [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
    String(date.getHours()).padStart(2, '0'),
    String(date.getMinutes()).padStart(2, '0'),
    String(date.getSeconds()).padStart(2, '0'),
    String(date.getMilliseconds()).padStart(3, '0'),
  ].join('')
  const suffix = `-批次-${timestamp}`
  const housePrefix = houseName.trim().slice(0, BATCH_CODE_MAX_LENGTH - suffix.length)
  return `${housePrefix}${suffix}`
}

export function batchCodeDraftForDialog(
  currentCode: string,
  isOpening: boolean,
  houseName: string,
  date = new Date(),
) {
  return isOpening ? defaultBatchCode(houseName, date) : currentCode
}
