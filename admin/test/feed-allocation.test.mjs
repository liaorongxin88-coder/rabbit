import assert from "node:assert/strict";
import test from "node:test";
import {
  canAutoAssignFeedGroup,
  feedAllocationError,
  getOrCreateFeedRequest,
  normalizeFeedAllocations,
} from "../src/lib/feed-allocation-request.ts";

const groups = [
  { batchId: 12, phase: "BREEDING", rabbitCount: 4 },
  { batchId: 12, phase: "FATTENING", rabbitCount: 8 },
];

test("auto-assigns only one concrete batch group", () => {
  assert.equal(
    canAutoAssignFeedGroup([
      { batchId: 12, phase: "FATTENING", rabbitCount: 2 },
    ]),
    true,
  );
  assert.equal(
    canAutoAssignFeedGroup([
      { batchId: null, phase: "UNASSIGNED", rabbitCount: 2 },
    ]),
    false,
  );
  assert.equal(canAutoAssignFeedGroup(groups), false);
});

test("normalizes two-decimal feed allocations and requires an exact total", () => {
  const allocations = normalizeFeedAllocations(groups, {
    "12:BREEDING": "4.25",
    "12:FATTENING": "5.75",
  });
  assert.deepEqual(allocations, [
    { batchId: 12, phase: "BREEDING", amountKg: 4.25 },
    { batchId: 12, phase: "FATTENING", amountKg: 5.75 },
  ]);
  assert.equal(feedAllocationError(10, allocations), null);
  assert.match(feedAllocationError(9.99, allocations), /合计/);
  assert.match(
    feedAllocationError(10, [
      { batchId: 12, phase: "BREEDING", amountKg: 4.255 },
      { batchId: 12, phase: "FATTENING", amountKg: 5.745 },
    ]),
    /两位小数/,
  );
});

test("reuses a feed requestId only for an unchanged logical draft", () => {
  let sequence = 0;
  const draft = {
    rabbitIds: [1, 2],
    feedTime: 1_700_000_000_000,
    amount: 10,
    unit: "kg",
    allocations: normalizeFeedAllocations(groups, {
      "12:BREEDING": "4.25",
      "12:FATTENING": "5.75",
    }),
  };
  const first = getOrCreateFeedRequest(null, draft, () => `feed-${++sequence}`);
  const retry = getOrCreateFeedRequest(
    first,
    { ...draft },
    () => `feed-${++sequence}`,
  );
  const changed = getOrCreateFeedRequest(
    first,
    { ...draft, amount: 11 },
    () => `feed-${++sequence}`,
  );
  assert.equal(retry.request.requestId, "feed-1");
  assert.equal(changed.request.requestId, "feed-2");
});
