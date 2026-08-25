import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildRabbitReproEntryInput,
  inProgressProductionBatches,
  keepValidProductionBatchId,
} from '../src/lib/rabbit-repro-entry.ts'

const batches = [
  { id: 11, batchCode: 'B-11', status: 'ACTIVE' },
  { id: 12, batchCode: 'B-12', status: '进行中' },
  { id: 13, batchCode: 'B-13', status: 'COMPLETED' },
  { id: 14, batchCode: 'B-14', status: 'UNKNOWN' },
]

test('rabbit repro entry only offers in-progress production batches', () => {
  assert.deepEqual(
    inProgressProductionBatches(batches).map((batch) => batch.id),
    [11, 12],
  )
})

test('rabbit repro entry clears a batch selection that is no longer available', () => {
  const available = inProgressProductionBatches(batches)
  assert.equal(keepValidProductionBatchId('12', available), '12')
  assert.equal(keepValidProductionBatchId('13', available), '')
  assert.equal(keepValidProductionBatchId('99', available), '')
})

test('rabbit repro entry payload includes the selected batch and farm dates', () => {
  assert.deepEqual(
    buildRabbitReproEntryInput({
      reproStage: 'AWAIT_PALPATION',
      batchId: 12,
      stageEnteredAt: '2026-03-09',
      matingDate: '2026-03-08',
      birthDate: '',
      liveKits: undefined,
    }),
    {
      reproStage: 'AWAIT_PALPATION',
      batchId: 12,
      stageEnteredAt: '2026-03-08T16:00:00.000Z',
      matingDate: '2026-03-07T16:00:00.000Z',
      birthDate: undefined,
      liveKits: undefined,
    },
  )
})
