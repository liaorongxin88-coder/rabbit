import assert from 'node:assert/strict'
import test from 'node:test'
import {
  BATCH_CODE_MAX_LENGTH,
  batchCodeDraftForDialog,
  defaultBatchCode,
} from '../src/lib/batch-code.ts'

const fixedDate = new Date(2026, 1, 3, 4, 5, 6, 7)

test('uses the selected house and millisecond local time in new batch drafts', () => {
  assert.equal(defaultBatchCode('东一舍', fixedDate), '东一舍-批次-20260203040506007')
  assert.equal(defaultBatchCode('西二舍', fixedDate), '西二舍-批次-20260203040506007')
})

test('keeps a manual batch code while the dialog remains open', () => {
  assert.equal(
    batchCodeDraftForDialog('人工批次-复配', false, '改名后的兔舍', fixedDate),
    '人工批次-复配',
  )
})

test('reserves space for the timestamp when the house name reaches the maximum length', () => {
  const code = defaultBatchCode('兔'.repeat(100), fixedDate)
  assert.equal(code.length, BATCH_CODE_MAX_LENGTH)
  assert.equal(code.endsWith('-批次-20260203040506007'), true)
})
