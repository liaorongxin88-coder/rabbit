import assert from "node:assert/strict";
import test from "node:test";
import {
  buildOutboundAllocationGroups,
  getOrCreateOutboundSubmission,
  normalizeOutboundAllocations,
  outboundAllocationError,
} from "../src/lib/outbound-allocation.ts";

const rabbits = [
  { rabbitId: 1, batchId: 12 },
  { rabbitId: 2, batchId: 12 },
  { rabbitId: 3, batchId: 19 },
  { rabbitId: 4, batchId: null },
];
const selected = rabbits.map((rabbit) => ({
  rabbitId: rabbit.rabbitId,
  stateVersion: 1,
  selectionType: "NORMAL",
}));

test("groups outbound rabbits by batch with unassigned last", () => {
  const groups = buildOutboundAllocationGroups(selected, rabbits);
  assert.deepEqual(groups, [
    { key: "12", batchId: 12, rabbitCount: 2 },
    { key: "19", batchId: 19, rabbitCount: 1 },
    { key: "unassigned", batchId: null, rabbitCount: 1 },
  ]);
  assert.deepEqual(
    normalizeOutboundAllocations(groups, {
      12: "4.125",
      19: "2.500",
      unassigned: "1.375",
    }),
    [
      { batchId: 12, actualWeightKg: 4.125 },
      { batchId: 19, actualWeightKg: 2.5 },
      { batchId: null, actualWeightKg: 1.375 },
    ],
  );
});

test("validates three-decimal weights and a positive unified price", () => {
  const allocations = [
    { batchId: 12, actualWeightKg: 4.125 },
    { batchId: 19, actualWeightKg: 3.875 },
  ];
  assert.equal(outboundAllocationError(8, 12.01, allocations), null);
  assert.match(outboundAllocationError(8.001, 12.01, allocations), /合计/);
  assert.match(outboundAllocationError(8, 0, allocations), /单价/);
  assert.match(outboundAllocationError(8, 12.001, allocations), /两位小数/);
  assert.match(
    outboundAllocationError(8, 12.01, [
      { batchId: 12, actualWeightKg: 4.1251 },
      { batchId: 19, actualWeightKg: 3.8749 },
    ]),
    /三位小数/,
  );
});

test("keeps the outbound requestId for an unchanged submit payload", () => {
  let sequence = 0;
  const payload = {
    taskId: "task-1",
    allocations: [{ batchId: 12, actualWeightKg: 8 }],
  };
  const first = getOrCreateOutboundSubmission(
    null,
    payload,
    () => `out-${++sequence}`,
  );
  const retry = getOrCreateOutboundSubmission(
    first,
    { ...payload },
    () => `out-${++sequence}`,
  );
  const changed = getOrCreateOutboundSubmission(
    retry,
    { ...payload, allocations: [{ batchId: 12, actualWeightKg: 7.5 }] },
    () => `out-${++sequence}`,
  );
  assert.equal(retry.requestId, "out-1");
  assert.equal(changed.requestId, "out-2");
});
