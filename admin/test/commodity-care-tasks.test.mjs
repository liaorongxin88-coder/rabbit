import assert from 'node:assert/strict'
import test from 'node:test'
import {
  commodityCareTaskTypes,
  summarizeCommodityCareTasks,
} from '../src/lib/commodity-care-tasks.ts'

test('summarizes daily commodity care tasks without losing their labels or instructions', () => {
  const summary = summarizeCommodityCareTasks([
    {
      total: 2,
      page: 1,
      size: 1,
      items: [{
        id: 12,
        taskType: 'COMMODITY_ADAPTATION_CARE',
        taskLabel: '幼兔适应观察',
        rabbitId: 102,
        dueTime: '2026-04-01T00:05:00+08:00',
        remark: '观察适应情况，按生长和体况分群。',
      }],
    },
    {
      total: 1,
      page: 1,
      size: 1,
      items: [{
        id: 11,
        taskType: 'COMMODITY_GROWING_CARE',
        taskLabel: '生长饲喂观察',
        rabbitId: 101,
        dueTime: '2026-04-01T00:04:00+08:00',
        remark: '观察采食、饮水和投料量。',
      }],
    },
    { total: 0, page: 1, size: 1, items: [] },
  ])

  assert.deepEqual(commodityCareTaskTypes, [
    'COMMODITY_ADAPTATION_CARE',
    'COMMODITY_GROWING_CARE',
    'COMMODITY_FATTENING_CARE',
  ])
  assert.equal(summary.total, 3)
  assert.deepEqual(summary.items.map((task) => task.id), [11, 12])
  assert.equal(summary.items[0].taskLabel, '生长饲喂观察')
  assert.equal(summary.items[0].remark, '观察采食、饮水和投料量。')
})
