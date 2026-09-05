import assert from "node:assert/strict";
import test from "node:test";
import { hasAtMostDecimalPlaces } from "../src/lib/decimal.ts";

test("checks the decimal scale that will be serialized to JSON", () => {
  assert.equal(hasAtMostDecimalPlaces(12.01, 2), true);
  assert.equal(hasAtMostDecimalPlaces(12.001, 2), false);
  assert.equal(hasAtMostDecimalPlaces(0.000001, 6), true);
  assert.equal(hasAtMostDecimalPlaces(1e-7, 6), false);
  assert.equal(hasAtMostDecimalPlaces(Number.NaN, 2), false);
});
