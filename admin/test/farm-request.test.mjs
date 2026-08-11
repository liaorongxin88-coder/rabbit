import assert from 'node:assert/strict'
import test from 'node:test'
import { getOrCreateFarmRequest } from '../src/lib/farm-request.ts'

function requestIdSequence() {
  let count = 0
  return () => `farm-${++count}`
}

test('reuses requestId when a farm creation is retried unchanged', () => {
  const nextRequestId = requestIdSequence()
  const payload = {
    name: '东区兔场',
    layoutRows: 2,
    layoutCols: 3,
    layoutLayers: 2,
    remark: '首期',
    ownerUserId: 42,
  }
  const first = getOrCreateFarmRequest(null, payload, nextRequestId)
  const retry = getOrCreateFarmRequest(first, payload, nextRequestId)

  assert.equal(first.requestId, 'farm-1')
  assert.strictEqual(retry, first)
})

test('uses a new requestId when farm fields or owner change', () => {
  const nextRequestId = requestIdSequence()
  const first = getOrCreateFarmRequest(
    null,
    { name: '东区兔场', layoutRows: 1, layoutCols: 1, layoutLayers: 1, ownerUserId: 42 },
    nextRequestId,
  )
  const edited = getOrCreateFarmRequest(
    first,
    { name: '西区兔场', layoutRows: 1, layoutCols: 1, layoutLayers: 1, ownerUserId: 42 },
    nextRequestId,
  )
  const invited = getOrCreateFarmRequest(
    edited,
    { name: '西区兔场', layoutRows: 1, layoutCols: 1, layoutLayers: 1, ownerPhone: '13800138000' },
    nextRequestId,
  )

  assert.deepEqual(
    [first.requestId, edited.requestId, invited.requestId],
    ['farm-1', 'farm-2', 'farm-3'],
  )
})
