import assert from 'node:assert/strict'
import test from 'node:test'
import {
  COMMODITY_CAPACITY,
  buildCageLayout,
  cageAlertReason,
  cageAttention,
  cageOccupancyText,
  compareRowCodes,
  countAttentions,
} from '../src/lib/cage-map.ts'

function cage(overrides = {}) {
  return {
    id: 1,
    houseId: 8,
    cageNumber: 'R1-C1-L1',
    rowCode: 'R1',
    layerIndex: 1,
    positionIndex: 1,
    status: '0',
    rabbitCount: 0,
    isFed: true,
    isEnabled: true,
    ...overrides,
  }
}

test('groups cages by row, stacks layers top-first and keeps positions ascending', () => {
  const layout = buildCageLayout([
    cage({ id: 3, cageNumber: 'R1-C2-L2', layerIndex: 2, positionIndex: 2 }),
    cage({ id: 1, cageNumber: 'R1-C1-L1', layerIndex: 1, positionIndex: 1 }),
    cage({ id: 2, cageNumber: 'R1-C2-L1', layerIndex: 1, positionIndex: 2 }),
  ])

  assert.equal(layout.rows.length, 1)
  const row = layout.rows[0]
  // 最上层排在最前面，跟物理货架一致。
  assert.deepEqual(
    row.layers.map((layer) => layer.layerIndex),
    [2, 1],
  )
  assert.equal(row.positionSpan, 2)
  // 第 2 层只有第 2 位有笼，第 1 位必须留空槽，否则列对不齐。
  assert.deepEqual(
    row.layers[0].cells.map((cell) => cell.cage?.id ?? null),
    [null, 3],
  )
  assert.deepEqual(
    row.layers[1].cells.map((cell) => cell.cage?.id ?? null),
    [1, 2],
  )
})

test('sorts row codes naturally so R2 comes before R10', () => {
  const layout = buildCageLayout([
    cage({ id: 1, rowCode: 'R10', cageNumber: 'R10-C1-L1' }),
    cage({ id: 2, rowCode: 'R2', cageNumber: 'R2-C1-L1' }),
    cage({ id: 3, rowCode: 'R1', cageNumber: 'R1-C1-L1' }),
  ])

  assert.deepEqual(
    layout.rows.map((row) => row.rowCode),
    ['R1', 'R2', 'R10'],
  )
  assert.ok(compareRowCodes('R2', 'R10') < 0)
})

test('cages without usable coordinates land in the unplaced bucket instead of vanishing', () => {
  const layout = buildCageLayout([
    cage({ id: 1, rowCode: 'LEGACY', cageNumber: 'OLD-1' }),
    cage({ id: 2, rowCode: '', cageNumber: 'OLD-2' }),
    cage({ id: 3, layerIndex: null, cageNumber: 'OLD-3' }),
    cage({ id: 4, positionIndex: 0, cageNumber: 'OLD-4' }),
    cage({ id: 5, cageNumber: 'R1-C1-L1' }),
  ])

  assert.equal(layout.rows.length, 1)
  assert.deepEqual(
    layout.unplaced.map((item) => item.cageNumber),
    ['OLD-1', 'OLD-2', 'OLD-3', 'OLD-4'],
  )
})

test('duplicate coordinates displace the later cage rather than overwriting it', () => {
  const layout = buildCageLayout([
    cage({ id: 1, cageNumber: 'R1-C1-L1' }),
    cage({ id: 2, cageNumber: 'R1-C1-L1-DUP' }),
  ])

  assert.equal(layout.rows[0].layers[0].cells[0].cage?.id, 1)
  // 覆盖掉就等于界面上凭空少一个笼，必须挪到「未编排」。
  assert.deepEqual(
    layout.unplaced.map((item) => item.id),
    [2],
  )
})

test('attention priority puts inconsistent bookkeeping ahead of everything else', () => {
  // 标记为空闲却记着兔：账不平，最先看见。
  assert.equal(cageAttention(cage({ status: '0', rabbitCount: 2 })), 'alert')
  // 单兔笼记着两只。
  assert.equal(cageAttention(cage({ status: '1', rabbitCount: 2 })), 'alert')
  // 商品笼超上限。
  assert.equal(
    cageAttention(cage({ status: '3', rabbitCount: COMMODITY_CAPACITY + 1 })),
    'alert',
  )
  // 停用却还留着兔，也是账不平，不能只显示「停用」。
  assert.equal(cageAttention(cage({ isEnabled: false, status: '1', rabbitCount: 1 })), 'alert')

  assert.equal(cageAttention(cage({ isEnabled: false })), 'disabled')
  assert.equal(cageAttention(cage({ status: '3', rabbitCount: 2, isFed: false })), 'needsFeeding')
  // 没有兔就没有投喂待办，空笼不该报「待投喂」。
  assert.equal(cageAttention(cage({ status: '0', rabbitCount: 0, isFed: false })), 'vacancy')
  assert.equal(cageAttention(cage({ status: '1', rabbitCount: 1 })), 'full')
  assert.equal(cageAttention(cage({ status: '3', rabbitCount: COMMODITY_CAPACITY })), 'full')
  assert.equal(cageAttention(cage({ status: '3', rabbitCount: 2 })), 'vacancy')
})

test('alert reason names the actual inconsistency', () => {
  assert.equal(cageAlertReason(cage({ status: '0', rabbitCount: 2 })), '标记为空闲却记着 2 只')
  assert.equal(
    cageAlertReason(cage({ isEnabled: false, status: '1', rabbitCount: 1 })),
    '已停用但笼内仍有 1 只',
  )
  assert.equal(cageAlertReason(cage({ rabbitCount: -1 })), '在栏数为负数（-1）')
  assert.equal(cageAlertReason(cage()), null)
})

test('occupancy text keeps commodity cages on the n/capacity form', () => {
  assert.equal(cageOccupancyText(cage({ rabbitCount: 0 })), '空')
  assert.equal(cageOccupancyText(cage({ status: '3', rabbitCount: 3 })), '3/10')
  assert.equal(cageOccupancyText(cage({ status: '1', rabbitCount: 1 })), '1 只')
})

test('counts every attention bucket for the legend', () => {
  const counts = countAttentions([
    cage({ id: 1, status: '0', rabbitCount: 2 }),
    cage({ id: 2, isEnabled: false }),
    cage({ id: 3, status: '3', rabbitCount: 2, isFed: false }),
    cage({ id: 4, status: '1', rabbitCount: 1 }),
    cage({ id: 5 }),
    cage({ id: 6 }),
  ])

  assert.deepEqual(counts, { alert: 1, disabled: 1, needsFeeding: 1, full: 1, vacancy: 2 })
})
