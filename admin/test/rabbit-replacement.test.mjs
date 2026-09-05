import assert from "node:assert/strict";
import test from "node:test";
import {
  getOrCreateRabbitReplacementRequest,
  rabbitReplacementPath,
  rabbitReplacementSource,
  rabbitReplacementWeightError,
} from "../src/lib/rabbit-replacement.ts";

test("resolves replacement source from birth batch or active fattening membership", () => {
  const active = (batchId) => ({
    batchId,
    batchRole: "fattening",
    isActive: true,
  });

  assert.deepEqual(
    rabbitReplacementSource({ birthBatchId: 7 }, [active(8)]),
    { status: "ready", batchId: 7 },
  );
  assert.deepEqual(
    rabbitReplacementSource({ birthBatchId: null }, [active(8)]),
    { status: "ready", batchId: 8 },
  );
  assert.deepEqual(
    rabbitReplacementSource({ birthBatchId: null }, []),
    { status: "ready", batchId: null },
  );
  assert.equal(
    rabbitReplacementSource({ birthBatchId: null }, [active(8), active(9)])
      .status,
    "ambiguous",
  );
  assert.equal(
    rabbitReplacementSource({ birthBatchId: null }, null).status,
    "unavailable",
  );
});

test("validates measured replacement weight precision", () => {
  assert.equal(rabbitReplacementWeightError(2.875), null);
  assert.match(rabbitReplacementWeightError(2.8751), /三位小数/);
  assert.match(rabbitReplacementWeightError(0), /大于 0/);
});

test("builds the replacement request contract and reuses its requestId on an unchanged retry", () => {
  let sequence = 0;
  const createRequestId = () => `replacement-${++sequence}`;
  const draft = {
    rabbitIds: [31],
    forceExitBatch: true,
    targetCageId: 18,
    batchAllocations: [{ batchId: 7, rabbitCount: 1, totalWeightKg: 2.875 }],
  };

  const first = getOrCreateRabbitReplacementRequest(
    null,
    draft,
    createRequestId,
  );
  const retry = getOrCreateRabbitReplacementRequest(
    first,
    { ...draft, rabbitIds: [31] },
    createRequestId,
  );
  const changedTarget = getOrCreateRabbitReplacementRequest(
    retry,
    { ...draft, targetCageId: 19 },
    createRequestId,
  );

  assert.equal(rabbitReplacementPath(), "/api/rabbits/replacement");
  assert.deepEqual(first, {
    rabbitIds: [31],
    forceExitBatch: true,
    targetCageId: 18,
    batchAllocations: [{ batchId: 7, rabbitCount: 1, totalWeightKg: 2.875 }],
    requestId: "replacement-1",
  });
  assert.strictEqual(retry, first);
  assert.equal(changedTarget.requestId, "replacement-2");
});
