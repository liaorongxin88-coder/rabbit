import assert from "node:assert/strict";
import test from "node:test";
import {
  appendEvents,
  operationLabel,
  operatorLabel,
  stageTransition,
  targetLabel,
} from "../src/lib/operation-events.ts";

function event(overrides) {
  return {
    id: 1,
    occurredAt: "2026-08-29T10:00:00.000Z",
    operationCode: "feed:add",
    eventType: "FEED_RECORDED",
    eventLabel: "投喂",
    targetType: "RABBIT",
    targetId: 31,
    cageId: null,
    batchId: null,
    rabbitId: 31,
    cycleId: null,
    litterId: null,
    fromStage: null,
    toStage: null,
    operatorId: 7,
    operatorName: "张三",
    ...overrides,
  };
}

test("已知操作码翻成中文", () => {
  assert.equal(operationLabel(event()), "投喂");
  assert.equal(
    operationLabel(event({ operationCode: "repro:state-machine" })),
    "繁育流程",
  );
});

test("未知操作码原样显示而不是「未知」", () => {
  // 后端会不断上新操作码，显示原码至少还能搜到出处。
  assert.equal(
    operationLabel(event({ operationCode: "brandnew:action" })),
    "brandnew:action",
  );
});

test("带方向后缀的操作码回退到前两段", () => {
  // 库存出入库会拼成 inventory:tx:OUT / :IN，不能为每个方向各配一条文案。
  assert.equal(
    operationLabel(event({ operationCode: "inventory:tx:OUT" })),
    "库存出入",
  );
  assert.equal(
    operationLabel(event({ operationCode: "inventory:tx:IN" })),
    "库存出入",
  );
});

test("目标带类型和编号", () => {
  assert.equal(targetLabel(event()), "兔只 #31");
  assert.equal(
    targetLabel(event({ targetType: "BATCH", targetId: 9 })),
    "批次 #9",
  );
  assert.equal(targetLabel(event({ targetId: null })), "兔只");
});

test("只有繁育事件才有阶段迁移", () => {
  assert.equal(stageTransition(event()), "");
  assert.equal(
    stageTransition(event({ fromStage: "待催情", toStage: "待配种" })),
    "待催情 → 待配种",
  );
  assert.equal(stageTransition(event({ toStage: "待催情" })), "待催情");
});

test("操作人缺失时不退回 userId", () => {
  // 一串数字对复盘的人没有意义。
  assert.equal(operatorLabel(event({ operatorName: null })), "未记录");
  assert.equal(operatorLabel(event({ operatorName: "  " })), "未记录");
  assert.equal(operatorLabel(event()), "张三");
});

test("追加下一页时按 id 去重", () => {
  // 游标边界在并发写入下可能把同一行带回来，重复渲染会让人以为操作发生了两次。
  const first = [event({ id: 3 }), event({ id: 2 })];
  const second = [event({ id: 2 }), event({ id: 1 })];
  assert.deepEqual(
    appendEvents(first, second).map((item) => item.id),
    [3, 2, 1],
  );
});
