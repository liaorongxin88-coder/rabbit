import assert from "node:assert/strict";
import test from "node:test";
import {
  parseContentDispositionFilename,
  sanitizeDownloadFilename,
} from "../src/lib/download.ts";

test("prefers the RFC 5987 UTF-8 filename and strips path components", () => {
  assert.equal(
    parseContentDispositionFilename(
      "attachment; filename=\"batch.xlsx\"; filename*=UTF-8''%E6%89%B9%E6%AC%A1-42-%E7%BB%9F%E8%AE%A1.xlsx",
      "fallback.xlsx",
    ),
    "批次-42-统计.xlsx",
  );
  assert.equal(
    parseContentDispositionFilename(
      'attachment; filename="../../batch-42.xlsx"',
      "fallback.xlsx",
    ),
    "batch-42.xlsx",
  );
  assert.equal(
    parseContentDispositionFilename(
      "attachment; filename=fallback.xlsx; filename*=UTF-8'zh-CN'%E6%89%B9%E6%AC%A1.xlsx",
      "fallback.xlsx",
    ),
    "批次.xlsx",
  );
});

test("matches exact parameters and safely handles quoted values", () => {
  assert.equal(
    parseContentDispositionFilename(
      "attachment; xfilename=evil.xlsx; notfilename*=UTF-8''evil.xlsx",
      "fallback.xlsx",
    ),
    "fallback.xlsx",
  );
  assert.equal(
    parseContentDispositionFilename(
      "attachment; filename=ascii.xlsx; filename*=ISO-8859-1''caf%E9.xlsx",
      "fallback.xlsx",
    ),
    "ascii.xlsx",
  );
  assert.equal(
    parseContentDispositionFilename(
      'attachment; filename="batch\\"report.xlsx"',
      "fallback.xlsx",
    ),
    "batch_report.xlsx",
  );
});

test("falls back for missing or unusable filenames", () => {
  assert.equal(
    parseContentDispositionFilename(null, "fallback.xlsx"),
    "fallback.xlsx",
  );
  assert.equal(
    sanitizeDownloadFilename("../", "fallback.xlsx"),
    "fallback.xlsx",
  );
  assert.equal(
    sanitizeDownloadFilename("CON.xlsx", "fallback.xlsx"),
    "_CON.xlsx",
  );
  assert.equal(
    sanitizeDownloadFilename("batch-statistics.xlsx. ", "fallback.xlsx"),
    "batch-statistics.xlsx",
  );
});
