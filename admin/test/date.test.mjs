import assert from 'node:assert/strict'
import test from 'node:test'
import {
  farmBusinessDateToIso,
  farmBusinessDateToTimestamp,
  formatDateInput,
  formatFarmBusinessDate,
  formatRecordDate,
} from '../src/lib/date.ts'

test('date controls preserve literal calendar dates', () => {
  assert.equal(formatDateInput('2026-03-09'), '2026-03-09')
})

test('date controls format timestamp offsets in the farm business timezone', () => {
  assert.equal(formatDateInput('2026-03-09T00:00:00+08:00'), '2026-03-09')
  assert.equal(formatDateInput('2026-03-08T16:00:00.000Z'), '2026-03-09')
  assert.equal(formatDateInput('2026-03-08T23:30:00-05:00'), '2026-03-09')
})

test('record dates render timestamp instants in the farm business timezone', () => {
  assert.equal(formatRecordDate('2026-03-08T16:00:00.000Z'), '2026-03-09')
  assert.equal(formatRecordDate('2026-03-09'), '2026-03-09')
  assert.equal(formatRecordDate(null), '-')
})

test('farm dates serialize as unambiguous Asia/Shanghai midnight instants', () => {
  assert.equal(farmBusinessDateToIso('2026-03-09'), '2026-03-08T16:00:00.000Z')
  assert.equal(
    farmBusinessDateToTimestamp('2026-03-09'),
    Date.parse('2026-03-09T00:00:00+08:00'),
  )
  assert.equal(
    formatFarmBusinessDate(new Date('2026-03-08T16:00:00.000Z')),
    '2026-03-09',
  )
})

test('invalid farm dates do not produce timestamps', () => {
  assert.equal(farmBusinessDateToTimestamp('2026-02-30'), undefined)
  assert.equal(farmBusinessDateToIso('not-a-date'), undefined)
})
