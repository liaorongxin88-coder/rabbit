import assert from 'node:assert/strict'
import test from 'node:test'
import {
  batchStatusLabel,
  batchActionPath,
  getOrCreateBatchActionRequest,
  getOrCreateRabbitDepartureRequest,
  isCompletedBatchStatus,
  normalizeBatchStatus,
  normalizeBatchActionPayload,
  normalizeParturitionCounts,
  normalizeParturitionPayload,
  pendingWeaningRecordsPath,
  rabbitEventPath,
  weaningSeparationPath,
} from '../src/lib/batch-workflow.ts'

test('normalizes active and completed batch statuses only', () => {
  assert.equal(normalizeBatchStatus(' ACTIVE '), '进行中')
  assert.equal(normalizeBatchStatus('COMPLETED'), '已完成')
  assert.equal(normalizeBatchStatus('UNKNOWN'), 'UNKNOWN')
  assert.equal(batchStatusLabel('   '), '-')
  assert.equal(isCompletedBatchStatus(' 已完成 '), true)
  assert.equal(isCompletedBatchStatus('ACTIVE'), false)
})

test('forces failed parturition counts to zero', () => {
  assert.deepEqual(normalizeParturitionCounts(true, 8, 6), { totalKits: 0, liveKits: 0 })
  assert.deepEqual(normalizeParturitionCounts(false, 8, 6), { totalKits: 8, liveKits: 6 })
  assert.deepEqual(normalizeParturitionPayload(true, 8, 6), { failed: true, totalKits: 0, liveKits: 0 })
  assert.deepEqual(normalizeParturitionPayload(false, 8, 6), { failed: false, totalKits: 8, liveKits: 6 })
})

test('reuses rabbit departure request only while its draft is unchanged', () => {
  let sequence = 0
  const createRequestId = () => `departure-${++sequence}`
  const payload = {
    rabbitId: 12,
    eventType: 'cull',
    actionDate: 2_000_000,
    reason: '繁殖效率下降',
    remark: '现场复核',
    forceExitBatch: true,
  }
  const first = getOrCreateRabbitDepartureRequest(null, payload, createRequestId)
  const retry = getOrCreateRabbitDepartureRequest(first, { ...payload }, createRequestId)
  const changed = getOrCreateRabbitDepartureRequest(retry, { ...payload, eventType: 'death' }, createRequestId)

  assert.equal(retry.requestId, 'departure-1')
  assert.equal(changed.requestId, 'departure-2')
  assert.equal(rabbitEventPath(), '/api/rabbits/events')
})

test('normalizes generic batch action payloads before comparing retries', () => {
  assert.deepEqual(normalizeBatchActionPayload({
    remark: undefined,
    triggerHardware: false,
    rabbitIds: [8, 3, 8],
  }), {
    rabbitIds: [3, 8],
    triggerHardware: false,
  })
  assert.equal(batchActionPath(21, 'aphrodisiac/start'), '/api/batches/21/aphrodisiac/start')
})

test('builds deferred weaning separation paths', () => {
  assert.equal(pendingWeaningRecordsPath(21), '/api/batches/21/weaning-records')
  assert.equal(
    weaningSeparationPath(21, 34),
    '/api/batches/21/weaning-records/34/separation',
  )
})

test('reuses generic batch action requestId only for the same batch action draft', () => {
  let sequence = 0
  const createRequestId = () => `action-${++sequence}`
  const first = getOrCreateBatchActionRequest(null, {
    batchId: 21,
    action: 'weaning',
    payload: { rabbitId: 7, remark: undefined, rabbitIds: [9, 2, 9] },
  }, createRequestId)
  const retry = getOrCreateBatchActionRequest(first, {
    batchId: 21,
    action: 'weaning',
    payload: { rabbitIds: [2, 9], rabbitId: 7 },
  }, createRequestId)
  const changedPayload = getOrCreateBatchActionRequest(retry, {
    batchId: 21,
    action: 'weaning',
    payload: { rabbitIds: [2, 9], rabbitId: 8 },
  }, createRequestId)
  const changedAction = getOrCreateBatchActionRequest(changedPayload, {
    batchId: 21,
    action: 'parturition',
    payload: changedPayload.payload,
  }, createRequestId)
  const changedBatch = getOrCreateBatchActionRequest(changedAction, {
    batchId: 22,
    action: 'parturition',
    payload: changedAction.payload,
  }, createRequestId)
  const afterSuccessClear = getOrCreateBatchActionRequest(null, {
    batchId: 22,
    action: 'parturition',
    payload: changedAction.payload,
  }, createRequestId)

  assert.equal(first.requestId, 'action-1')
  assert.equal(retry.requestId, 'action-1')
  assert.equal(changedPayload.requestId, 'action-2')
  assert.equal(changedAction.requestId, 'action-3')
  assert.equal(changedBatch.requestId, 'action-4')
  assert.equal(afterSuccessClear.requestId, 'action-5')

  const separation = getOrCreateBatchActionRequest(null, {
    batchId: 34,
    action: 'separation',
    payload: { allocations: [{ cageId: 8, count: 3 }] },
  }, createRequestId)
  const separationRetry = getOrCreateBatchActionRequest(separation, {
    batchId: 34,
    action: 'separation',
    payload: { allocations: [{ count: 3, cageId: 8 }] },
  }, createRequestId)
  const separationChanged = getOrCreateBatchActionRequest(separationRetry, {
    batchId: 34,
    action: 'separation',
    payload: { allocations: [{ cageId: 8, count: 2 }] },
  }, createRequestId)

  assert.equal(separation.requestId, 'action-6')
  assert.equal(separationRetry.requestId, 'action-6')
  assert.equal(separationChanged.requestId, 'action-7')
})
