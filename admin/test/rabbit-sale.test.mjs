import assert from "node:assert/strict";
import test from "node:test";
import {
  getOrCreateRabbitSaleRequest,
  isIndividualSaleRabbit,
  rabbitSaleValidationError,
  rabbitSalesPath,
} from "../src/lib/rabbit-sale.ts";

test("only breeder and replacement rabbits qualify for individual sale", () => {
  assert.equal(isIndividualSaleRabbit({ type: "0" }), true);
  assert.equal(isIndividualSaleRabbit({ type: "1" }), true);
  assert.equal(isIndividualSaleRabbit({ type: "2" }), false);
});

test("validates sale weight and price precision before submission", () => {
  assert.equal(rabbitSaleValidationError(3.125, 12.01), null);
  assert.match(rabbitSaleValidationError(3.1251, 12.01), /三位小数/);
  assert.match(rabbitSaleValidationError(3.125, 12.001), /两位小数/);
});

test("keeps the sale request ID only while the one-rabbit draft is unchanged", () => {
  let sequence = 0;
  const createRequestId = () => `sale-${++sequence}`;
  const draft = {
    rabbitIds: [31],
    saleTime: 1_700_000_000_000,
    totalWeight: 3.2,
    unitPrice: 20,
    unitPricePerKg: 20,
    batchAllocations: [],
    customer: "采购方",
    remark: "种兔更新",
  };

  const first = getOrCreateRabbitSaleRequest(null, draft, createRequestId);
  const retry = getOrCreateRabbitSaleRequest(
    first,
    { ...draft },
    createRequestId,
  );
  const changed = getOrCreateRabbitSaleRequest(
    retry,
    { ...draft, totalWeight: 3.1 },
    createRequestId,
  );

  assert.equal(first.requestId, "sale-1");
  assert.equal(retry.requestId, "sale-1");
  assert.equal(changed.requestId, "sale-2");
  assert.equal(rabbitSalesPath(), "/api/sales");
});
