import assert from 'node:assert/strict'
import test from 'node:test'
import { getOrCreateInvitationRequest } from '../src/lib/invitation-request.ts'

function requestIdSequence() {
  let count = 0
  return () => `invite-${++count}`
}

test('creates a non-empty requestId and reuses it for the same retry', () => {
  const nextRequestId = requestIdSequence()
  const payload = { identifier: '13800138000', phone: '13800138000', role: 'STAFF' }
  const first = getOrCreateInvitationRequest(null, payload, nextRequestId)
  const retry = getOrCreateInvitationRequest(first, payload, nextRequestId)

  assert.equal(first.requestId, 'invite-1')
  assert.strictEqual(retry, first)
})

test('creates a new requestId when the invitation payload changes', () => {
  const nextRequestId = requestIdSequence()
  const first = getOrCreateInvitationRequest(
    null,
    { identifier: '13800138000', phone: '13800138000', role: 'STAFF' },
    nextRequestId,
  )
  const changedPhone = getOrCreateInvitationRequest(
    first,
    { identifier: '13900139000', phone: '13900139000', role: 'STAFF' },
    nextRequestId,
  )
  const changedRole = getOrCreateInvitationRequest(
    changedPhone,
    { identifier: '13900139000', phone: '13900139000', role: 'OWNER' },
    nextRequestId,
  )

  assert.deepEqual(
    [first.requestId, changedPhone.requestId, changedRole.requestId],
    ['invite-1', 'invite-2', 'invite-3'],
  )
})

test('creates a new requestId after a completed request is cleared', () => {
  const nextRequestId = requestIdSequence()
  const payload = { identifier: '13800138000', phone: '13800138000', role: 'MANAGER' }
  const completed = getOrCreateInvitationRequest(null, payload, nextRequestId)
  const nextSubmission = getOrCreateInvitationRequest(null, payload, nextRequestId)

  assert.notEqual(nextSubmission.requestId, completed.requestId)
})

test('switching from a phone to a user code rotates the requestId', () => {
  const nextRequestId = requestIdSequence()
  const byPhone = getOrCreateInvitationRequest(
    null,
    { identifier: '13800138000', phone: '13800138000', role: 'STAFF' },
    nextRequestId,
  )
  // 换成账号就是在邀请「另一种指向」，哪怕最后可能是同一个人，
  // 也必须换 requestId，否则后端会按幂等判成同一次邀请。
  const byCode = getOrCreateInvitationRequest(
    byPhone,
    { identifier: 'R3F9A0C21B7', role: 'STAFF' },
    nextRequestId,
  )
  const codeRetry = getOrCreateInvitationRequest(
    byCode,
    { identifier: 'R3F9A0C21B7', role: 'STAFF' },
    nextRequestId,
  )

  assert.notEqual(byCode.requestId, byPhone.requestId)
  assert.strictEqual(codeRetry, byCode)
})
