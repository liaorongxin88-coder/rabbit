import assert from 'node:assert/strict'
import test from 'node:test'
import {
  isReplacementTargetCage,
  preferredRabbitTypeForCage,
  rabbitCageValidationMessage,
} from '../src/lib/rabbit-cage.ts'

function cage(overrides = {}) {
  return {
    id: 1,
    houseId: 8,
    cageNumber: '1-1-1',
    status: '0',
    rabbitCount: 0,
    isFed: true,
    isEnabled: true,
    ...overrides,
  }
}

test('prefers the rabbit type implied by the selected cage usage', () => {
  assert.equal(preferredRabbitTypeForCage(cage({ status: '0' })), '0')
  assert.equal(preferredRabbitTypeForCage(cage({ status: '1' })), '0')
  assert.equal(preferredRabbitTypeForCage(cage({ status: '2' })), '1')
  assert.equal(preferredRabbitTypeForCage(cage({ status: '3' })), '2')
})

test('blocks a cage whose usage does not match the rabbit type', () => {
  assert.equal(
    rabbitCageValidationMessage(cage({ status: '1', rabbitCount: 1 }), '2', 8),
    '笼位用途与兔只类型不匹配',
  )
  assert.equal(
    rabbitCageValidationMessage(cage({ status: '3', rabbitCount: 2 }), '2', 8),
    null,
  )
  assert.equal(
    rabbitCageValidationMessage(cage({ status: '2', rabbitCount: 0 }), '2', 8),
    '笼位用途与兔只类型不匹配',
  )
  assert.equal(
    rabbitCageValidationMessage(cage({ status: '2', rabbitCount: 0 }), '1', 8),
    null,
  )
})

test('enforces single-rabbit and commodity cage capacities', () => {
  assert.equal(
    rabbitCageValidationMessage(cage({ status: '1', rabbitCount: 1 }), '0', 8),
    '笼位已满，最多容纳 1 只',
  )
  assert.equal(
    rabbitCageValidationMessage(cage({ status: '2', rabbitCount: 1 }), '1', 8),
    '笼位已满，最多容纳 1 只',
  )
  assert.equal(
    rabbitCageValidationMessage(cage({ status: '3', rabbitCount: 10 }), '2', 8),
    '笼位已满，最多容纳 10 只',
  )
})

test('blocks cages from another house and disabled cages', () => {
  assert.equal(
    rabbitCageValidationMessage(cage({ houseId: 9 }), '0', 8),
    '笼位不属于当前兔场',
  )
  assert.equal(
    rabbitCageValidationMessage(cage({ isEnabled: false }), '0', 8),
    '笼位已停用',
  )
})

test('replacement targets are empty enabled replacement or unused cages in the current house', () => {
  assert.equal(isReplacementTargetCage(cage({ status: '0' }), 8), true)
  assert.equal(isReplacementTargetCage(cage({ status: '2' }), 8), true)
  assert.equal(isReplacementTargetCage(cage({ status: '1' }), 8), false)
  assert.equal(isReplacementTargetCage(cage({ status: '2', rabbitCount: 1 }), 8), false)
  assert.equal(isReplacementTargetCage(cage({ status: '2', houseId: 9 }), 8), false)
})
