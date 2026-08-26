import assert from "node:assert/strict";
import test from "node:test";
import {
  batchCodeDraftForDialog,
  defaultBatchCode,
} from "../src/lib/batch-code.ts";

const fixedDate = new Date(2026, 1, 3, 4, 5, 6, 7);

test("uses the local date and minute in new batch drafts", () => {
  assert.equal(defaultBatchCode(fixedDate), "批次-20260203-0405");
});

test("keeps a manual batch code while the dialog remains open", () => {
  assert.equal(
    batchCodeDraftForDialog("人工批次-复配", false, fixedDate),
    "人工批次-复配",
  );
});

// 编号要显示在 App 提醒卡片上，和周期号、日期挤一行，所以生成值必须短。
// 旧格式带兔舍名加 17 位毫秒戳，兔舍名一长就被省略号截掉。
test("stays short enough for the reminder chip", () => {
  const code = defaultBatchCode(fixedDate);
  assert.equal(code.length, 16);
  assert.equal(code.startsWith("批次-"), true);
});
