import assert from 'node:assert/strict'
import test from 'node:test'
import {
  COMMODITY_CAPACITY,
  buildCageLayout,
  cageAlertReason,
  cageAttention,
  cageOccupancyText,
  countAttentions,
} from '../src/lib/cage-map.ts'

function cage({ row, layer, position, number, ...overrides } = {}) {
  // 允许 row/layer/position/number 这套简写，省得每个用例都写全字段名。
  if (row !== undefined) overrides.rowCode = row
  if (layer !== undefined) overrides.layerIndex = layer
  if (position !== undefined) overrides.positionIndex = position
  if (number !== undefined) overrides.cageNumber = number
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

test('层是顶层维度，从 1 层往上排', () => {
  const layout = buildCageLayout([
    cage({ id: 1, row: 'R1', layer: 2, position: 1 }),
    cage({ id: 2, row: 'R1', layer: 1, position: 1 }),
    cage({ id: 3, row: 'R1', layer: 3, position: 1 }),
  ])

  assert.deepEqual(
    layout.layers.map((layer) => layer.layerIndex),
    [1, 2, 3],
  )
  assert.deepEqual(
    layout.layers[0].rows[0].cages.map((c) => c.id),
    [2],
  )
})

test('一排就是一条线，位号从左往右递增', () => {
  const layout = buildCageLayout(
    Array.from({ length: 10 }, (_, index) =>
      cage({ id: index + 1, row: 'B', layer: 1, position: index + 1 }),
    ),
  )

  const row = layout.layers[0].rows[0]
  assert.deepEqual(
    row.cells.map((cell) => cell.positionIndex),
    [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
  )
  assert.equal(row.positionSpan, 10)
})

test('排宽取跨层最大位号，切层时网格不跳', () => {
  const layout = buildCageLayout([
    ...Array.from({ length: 6 }, (_, index) =>
      cage({ id: index + 1, row: 'B', layer: 1, position: index + 1 }),
    ),
    cage({ id: 7, row: 'B', layer: 2, position: 1 }),
  ])

  const secondLayerRow = layout.layers[1].rows[0]
  assert.equal(secondLayerRow.positionSpan, 6)
  assert.deepEqual(
    secondLayerRow.cells.map((cell) => cell.positionIndex),
    [1, 2, 3, 4, 5, 6],
  )
})

test('sorts row codes naturally so R2 comes before R10', () => {
  const layout = buildCageLayout([
    cage({ id: 1, row: 'R10', layer: 1, position: 1 }),
    cage({ id: 2, row: 'R2', layer: 1, position: 1 }),
    cage({ id: 3, row: 'R1', layer: 1, position: 1 }),
  ])

  assert.deepEqual(
    layout.layers[0].rows.map((row) => row.rowCode),
    ['R1', 'R2', 'R10'],
  )
})

test('cages without usable coordinates land in the unplaced bucket instead of vanishing', () => {
  const layout = buildCageLayout([
    cage({ id: 1, row: 'R1', layer: 1, position: 1 }),
    cage({ id: 2, row: 'R1', layer: 0, position: 3, number: '缺层' }),
    cage({ id: 3, row: 'LEGACY', layer: 1, position: 1, number: '历史' }),
    cage({ id: 4, row: '', layer: 1, position: 1, number: '无排号' }),
  ])

  assert.equal(layout.layers.length, 1)
  assert.deepEqual(
    layout.layers[0].rows[0].cages.map((c) => c.id),
    [1],
  )
  assert.deepEqual(
    layout.unplaced.map((c) => c.id).sort((a, b) => a - b),
    [2, 3, 4],
  )
})

test('duplicate coordinates displace the later cage rather than overwriting it', () => {
  const layout = buildCageLayout([
    cage({ id: 1, row: 'R1', layer: 1, position: 1, number: '先到' }),
    cage({ id: 2, row: 'R1', layer: 1, position: 1, number: '撞车' }),
  ])

  assert.equal(layout.layers[0].rows[0].cells[0].cage?.id, 1)
  assert.deepEqual(
    layout.unplaced.map((c) => c.id),
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
