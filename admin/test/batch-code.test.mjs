import assert from "node:assert/strict";
import test from "node:test";
import {
  batchCodeDraftForDialog,
  defaultBatchCode,
} from "../src/lib/batch-code.ts";

const fixedDate = new Date("2026-02-03T04:05:06.007Z");

test("uses the house name and farm-local minute in new batch drafts", () => {
  assert.equal(defaultBatchCode("东一舍", fixedDate), "东一舍-20260203-1205");
});

test("keeps a manual batch code while the dialog remains open", () => {
  assert.equal(
    batchCodeDraftForDialog("人工批次-复配", false, "东一舍", fixedDate),
    "人工批次-复配",
  );
});

test("normalizes separators and falls back for a blank house name", () => {
  assert.equal(
    defaultBatchCode("  东一 / 舍--A  ", fixedDate),
    "东一-舍-A-20260203-1205",
  );
  assert.equal(defaultBatchCode(" /_- ", fixedDate), "兔舍-20260203-1205");
});

test("truncates long names to the backend batch-code limit", () => {
  const code = defaultBatchCode("超长兔舍".repeat(30), fixedDate);
  assert.equal(Array.from(code).length, 100);
  assert.equal(code.endsWith("-20260203-1205"), true);
});
