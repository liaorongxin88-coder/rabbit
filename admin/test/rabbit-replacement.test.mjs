import assert from 'node:assert/strict'
import test from 'node:test'
import {
  getOrCreateRabbitReplacementRequest,
  rabbitReplacementPath,
} from '../src/lib/rabbit-replacement.ts'

test('builds the replacement request contract and reuses its requestId on an unchanged retry', () => {
  let sequence = 0
  const createRequestId = () => `replacement-${++sequence}`
  const draft = {
    rabbitIds: [31],
    forceExitBatch: true,
    targetCageId: 18,
  }

  const first = getOrCreateRabbitReplacementRequest(null, draft, createRequestId)
  const retry = getOrCreateRabbitReplacementRequest(first, { ...draft, rabbitIds: [31] }, createRequestId)
  const changedTarget = getOrCreateRabbitReplacementRequest(
    retry,
    { ...draft, targetCageId: 19 },
    createRequestId,
  )

  assert.equal(rabbitReplacementPath(), '/api/rabbits/replacement')
  assert.deepEqual(first, {
    rabbitIds: [31],
    forceExitBatch: true,
    targetCageId: 18,
    requestId: 'replacement-1',
  })
  assert.strictEqual(retry, first)
  assert.equal(changedTarget.requestId, 'replacement-2')
})
