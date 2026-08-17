import assert from 'node:assert/strict'
import test from 'node:test'
import {
  batchStatusLabel,
  batchActionPath,
  getOrCreateBulkMatingRequest,
  getOrCreateBatchActionRequest,
  getOrCreateRabbitDepartureRequest,
  isBulkMatingEligible,
  isCompletedBatchStatus,
  normalizeBatchStatus,
  normalizeBatchActionPayload,
  normalizeParturitionCounts,
  normalizeParturitionPayload,
  rabbitEventPath,
} from '../src/lib/batch-workflow.ts'

test('normalizes trimmed Chinese and legacy batch statuses', () => {
  assert.equal(normalizeBatchStatus(' 计划中 '), '计划中')
  assert.equal(normalizeBatchStatus(' 进行中\n'), '进行中')
  assert.equal(normalizeBatchStatus('COMPLETED'), '已完成')
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

test('keeps only active breeding females in mating-ready statuses', () => {
  const ready = { isActive: true, rabbitType: '0', rabbitGender: '0', currentStatus: '待配种' }
  assert.equal(isBulkMatingEligible(ready), true)
  assert.equal(isBulkMatingEligible({ ...ready, currentStatus: '哺乳中' }), true)
  assert.equal(isBulkMatingEligible({ ...ready, currentStatus: '已配种' }), false)
  assert.equal(isBulkMatingEligible({ ...ready, rabbitGender: '1' }), false)
  assert.equal(isBulkMatingEligible({ ...ready, isActive: false }), false)
})

test('normalizes bulk mating order and reuses requestId for the same retry', () => {
  let sequence = 0
  const createRequestId = () => `bulk-${++sequence}`
  const first = getOrCreateBulkMatingRequest(null, {
    femaleRabbitIds: [9, 3, 9, 5],
    maleRabbitId: 2,
    matingDate: 1_800_000,
  }, createRequestId)
  const retry = getOrCreateBulkMatingRequest(first, {
    femaleRabbitIds: [5, 9, 3],
    maleRabbitId: 2,
    matingDate: 1_800_000,
  }, createRequestId)
  const changed = getOrCreateBulkMatingRequest(retry, {
    femaleRabbitIds: [5, 9],
    maleRabbitId: 2,
    matingDate: 1_800_000,
  }, createRequestId)

  assert.deepEqual(first.femaleRabbitIds, [3, 5, 9])
  assert.equal(retry.requestId, 'bulk-1')
  assert.equal(changed.requestId, 'bulk-2')
  // 批量配种现在走通用的批次动作路径（旧的 bulkMatingPath 已在 doe-breeding-v2 里移除）。
  assert.equal(batchActionPath(17, 'mating/bulk'), '/api/batches/17/mating/bulk')
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
})
