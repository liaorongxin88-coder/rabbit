import assert from 'node:assert/strict'
import test from 'node:test'
import {
  looksLikeUserCode,
  normalizeInviteIdentifier,
  normalizeUserCode,
} from '../src/lib/user-code.ts'

test('accepts a code however it was copied down', () => {
  // 账号靠嘴说、靠手抄传出去，这几种写法必须都认。
  for (const input of [
    'R3F9A0C21B7',
    'r3f9a0c21b7',
    '  R3F9A0C21B7  ',
    'R3F9-A0C2-1B7',
    'R3F9 A0C2 1B7',
  ]) {
    assert.equal(looksLikeUserCode(input), true, `应认出 ${input}`)
  }
})

test('treats O/I/L as the digits they are mistaken for', () => {
  // 十六进制里没有 O、I、L，写成这三个字母时意思一定是 0、1、1。
  assert.equal(normalizeUserCode('rO3f9aOc2lb'), 'R03F9A0C21B')
  assert.equal(looksLikeUserCode('rIf9a0c2lb7'), true)
})

test('rejects things that are neither a phone nor a code', () => {
  for (const input of ['R3F9A0C21', 'R3F9A0C21B77', 'X3F9A0C21B7', 'R3F9A0C21BZ', '隔壁老王', '']) {
    assert.equal(looksLikeUserCode(input), false, `不该认 ${input}`)
  }
})

test('classifies invite input and normalises it', () => {
  assert.deepEqual(normalizeInviteIdentifier('138 0013 8000'), {
    identifier: '13800138000',
    kind: 'phone',
  })
  assert.deepEqual(normalizeInviteIdentifier('+8613800138000'), {
    identifier: '13800138000',
    kind: 'phone',
  })
  assert.deepEqual(normalizeInviteIdentifier(' r3f9-a0c2-1b7 '), {
    identifier: 'R3F9A0C21B7',
    kind: 'code',
  })
  assert.equal(normalizeInviteIdentifier('隔壁老王').kind, 'invalid')
})
