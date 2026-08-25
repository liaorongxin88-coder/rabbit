import assert from 'node:assert/strict'
import test from 'node:test'
import {
  rabbitArrivalMethodLabel,
  rabbitGenderLabel,
  rabbitStageSummary,
  rabbitTypeLabel,
  reproductiveOptions,
} from '../src/lib/rabbits.ts'

test('rabbit identity labels distinguish breeding gender and other types', () => {
  assert.equal(rabbitTypeLabel({ type: '0', gender: '0' }), '种母兔')
  assert.equal(rabbitTypeLabel({ type: '0', gender: '1' }), '种公兔')
  assert.equal(rabbitTypeLabel({ type: '1', gender: '0' }), '后备兔')
  assert.equal(rabbitTypeLabel({ type: '2', gender: '1' }), '商品兔')
  assert.equal(rabbitGenderLabel('0'), '母')
  assert.equal(rabbitGenderLabel('1'), '公')
  assert.equal(rabbitGenderLabel('9'), '未知')
})

test('current production stage takes precedence over the legacy reproductive stage', () => {
  assert.equal(
    rabbitStageSummary(
      {
        growthStage: 'MATURE',
        currentStage: 'AWAIT_MATING',
        reproductiveStage: 'PREGNANT',
      },
      { AWAIT_MATING: '待配种' },
    ),
    '成熟可售 · 待配种',
  )
})

test('stage summary falls back to legacy labels and a stable empty value', () => {
  assert.equal(
    rabbitStageSummary(
      { growthStage: 'GROWING', currentStage: null, reproductiveStage: 'READY' },
      {},
    ),
    '成长期 · 可配',
  )
  assert.equal(
    rabbitStageSummary(
      { growthStage: null, currentStage: null, reproductiveStage: null },
      {},
    ),
    '阶段未填写',
  )
})

test('commodity growth-stage labels remain compatible with canonical and legacy values', () => {
  assert.equal(
    rabbitStageSummary(
      { growthStage: 'ADAPTATION', currentStage: null, reproductiveStage: null },
      {},
    ),
    '适应期',
  )
  assert.equal(
    rabbitStageSummary(
      { growthStage: 'JUVENILE', currentStage: null, reproductiveStage: null },
      {},
    ),
    '适应期',
  )
  assert.equal(
    rabbitStageSummary(
      { growthStage: 'FATTENING', currentStage: null, reproductiveStage: null },
      {},
    ),
    '育肥期',
  )
  assert.equal(
    rabbitStageSummary(
      { growthStage: 'MATURE', currentStage: null, reproductiveStage: null },
      {},
    ),
    '成熟可售',
  )
  assert.equal(
    rabbitStageSummary(
      { growthStage: 'MARKET_READY', currentStage: null, reproductiveStage: null },
      {},
    ),
    'MARKET_READY',
  )
})

test('editable legacy reproductive stages stay limited by rabbit type and gender', () => {
  assert.deepEqual(reproductiveOptions('0', '0'), [])
  assert.deepEqual(reproductiveOptions('0', '1'), [['READY', '可配'], ['RESTING', '休整']])
  assert.deepEqual(reproductiveOptions('1', '0'), [['RESERVE', '后备']])
  assert.deepEqual(reproductiveOptions('2', '0'), [])
})

test('arrival method labels preserve unknown server values', () => {
  assert.equal(rabbitArrivalMethodLabel('0'), '购入')
  assert.equal(rabbitArrivalMethodLabel('1'), '场内生产')
  assert.equal(rabbitArrivalMethodLabel('legacy'), 'legacy')
  assert.equal(rabbitArrivalMethodLabel(null), '-')
})
