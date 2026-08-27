import assert from "node:assert/strict";
import test from "node:test";
import {
  awaitsNextDose,
  sortVaccinationRecords,
  vaccinationDetailSummary,
  vaccinationStatusLabel,
  vaccinationStatusVariant,
} from "../src/lib/rabbit-vaccination.ts";

function record(overrides) {
  return {
    id: 1,
    rabbitId: 31,
    vaccineName: "兔瘟疫苗",
    status: "DONE",
    ...overrides,
  };
}

test("a scheduled record only counts as outstanding when it carries a next due date", () => {
  const outstanding = record({
    status: "SCHEDULED",
    nextDueDate: "2026-09-04",
  });
  const closed = record({ status: "DONE", nextDueDate: "2026-09-04" });
  // SCHEDULED 但没有下次日期是脏数据，不该显示成待补种
  const dirty = record({ status: "SCHEDULED", nextDueDate: null });

  assert.equal(awaitsNextDose(outstanding), true);
  assert.equal(awaitsNextDose(closed), false);
  assert.equal(awaitsNextDose(dirty), false);

  assert.equal(vaccinationStatusLabel(outstanding), "待补种");
  assert.equal(vaccinationStatusLabel(closed), "已完成");
  assert.equal(vaccinationStatusVariant(outstanding), "default");
  assert.equal(vaccinationStatusVariant(closed), "secondary");
});

test("detail summary joins the filled fields and falls back to a dash", () => {
  assert.equal(
    vaccinationDetailSummary(
      record({ vaccineBatchNo: "B20260301", dose: "1ml", route: "皮下注射" }),
    ),
    "批号 B20260301 · 剂量 1ml · 皮下注射",
  );
  assert.equal(
    vaccinationDetailSummary(
      record({ vaccineBatchNo: "B20260301", dose: null }),
    ),
    "批号 B20260301",
  );
  assert.equal(vaccinationDetailSummary(record({})), "-");
  // 空白串等同于没填，不该渲染成孤零零的分隔点
  assert.equal(vaccinationDetailSummary(record({ dose: "   " })), "-");
});

test("records sort newest first and stay stable without a vaccination time", () => {
  const rows = [
    record({ id: 1, vaccinatedAt: "2026-07-01T00:00:00.000Z" }),
    record({ id: 2, vaccinatedAt: "2026-08-14T00:00:00.000Z" }),
    record({ id: 3, vaccinatedAt: null }),
    record({ id: 4, vaccinatedAt: null }),
  ];

  const sorted = sortVaccinationRecords(rows);

  assert.deepEqual(
    sorted.map((item) => item.id),
    [2, 1, 4, 3],
  );
  // 不得原地改动入参，页面 state 要保持不可变
  assert.deepEqual(
    rows.map((item) => item.id),
    [1, 2, 3, 4],
  );
});
