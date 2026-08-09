import assert from 'node:assert/strict'
import test from 'node:test'
import { getOrCreateInvitationRequest } from '../src/lib/invitation-request.ts'

function requestIdSequence() {
  let count = 0
  return () => `invite-${++count}`
}

test('creates a non-empty requestId and reuses it for the same retry', () => {
  const nextRequestId = requestIdSequence()
  const payload = { phone: '13800138000', role: 'STAFF' }
  const first = getOrCreateInvitationRequest(null, payload, nextRequestId)
  const retry = getOrCreateInvitationRequest(first, payload, nextRequestId)

  assert.equal(first.requestId, 'invite-1')
  assert.strictEqual(retry, first)
})

test('creates a new requestId when the invitation payload changes', () => {
  const nextRequestId = requestIdSequence()
  const first = getOrCreateInvitationRequest(
    null,
    { phone: '13800138000', role: 'STAFF' },
    nextRequestId,
  )
  const changedPhone = getOrCreateInvitationRequest(
    first,
    { phone: '13900139000', role: 'STAFF' },
    nextRequestId,
  )
  const changedRole = getOrCreateInvitationRequest(
    changedPhone,
    { phone: '13900139000', role: 'OWNER' },
    nextRequestId,
  )

  assert.deepEqual(
    [first.requestId, changedPhone.requestId, changedRole.requestId],
    ['invite-1', 'invite-2', 'invite-3'],
  )
})

test('creates a new requestId after a completed request is cleared', () => {
  const nextRequestId = requestIdSequence()
  const payload = { phone: '13800138000', role: 'MANAGER' }
  const completed = getOrCreateInvitationRequest(null, payload, nextRequestId)
  const nextSubmission = getOrCreateInvitationRequest(null, payload, nextRequestId)

  assert.notEqual(nextSubmission.requestId, completed.requestId)
})
