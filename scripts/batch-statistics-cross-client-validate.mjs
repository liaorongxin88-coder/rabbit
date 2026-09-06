#!/usr/bin/env node

import assert from "node:assert/strict";
import { readFileSync, writeFileSync } from "node:fs";

const expectedMetrics = [
  ["MATING_DATE", null, "2024-04-22", "2024-04-22"],
  ["MATED_DOE_COUNT", 1230, null, "1,230"],
  ["CONCEPTION_RATE", 0.8609756097560975, null, "86.10%"],
  ["DOE_BUCK_RATIO", 20.5, null, "20.50:1"],
  ["PREGNANT_DOE_COUNT", 1059, null, "1,059"],
  ["ABORTION_RATE", 0.019830028328611898, null, "1.98%"],
  ["DELIVERED_LITTER_COUNT", 1004, null, "1,004"],
  ["TOTAL_KIT_COUNT", 10040, null, "10,040"],
  ["AVERAGE_KITS_PER_LITTER", 10, null, "10.00"],
  ["LIVE_KIT_COUNT", 9870, null, "9,870"],
  ["LIVE_BIRTH_RATE", 0.9830677290836654, null, "98.31%"],
  ["KEPT_LITTER_COUNT", 987, null, "987"],
  ["KEPT_KIT_COUNT", 9490, null, "9,490"],
  ["KEPT_LIVE_RATE", 0.961499493414387, null, "96.15%"],
  ["AVERAGE_KEPT_PER_LITTER", 9.614994934143871, null, "9.61"],
  ["WEANED_KIT_COUNT", 8604, null, "8,604"],
  ["AVERAGE_WEANING_WEIGHT", 0.735, null, "0.74 kg"],
  ["WEANING_SURVIVAL_RATE", 0.9066385669125395, null, "90.66%"],
  ["SOLD_RABBIT_COUNT", 6834, null, "6,834"],
  ["OUTBOUND_SURVIVAL_RATE", 0.794281729428173, null, "79.43%"],
  ["SOLD_WEIGHT", 13095, null, "13,095.00 kg"],
  ["AVERAGE_SOLD_WEIGHT", 1.9161545215100966, null, "1.92 kg"],
  ["TOTAL_SALES_AMOUNT", 157140, null, "157,140.00 元"],
  ["SALES_PRICE_PER_KG", 12, null, "12.00 元/kg"],
  ["SALES_PRICE_PER_RABBIT", 22.99385425812116, null, "22.99 元/只"],
  ["FULL_FEED_CONVERSION_RATIO", 3.6846942382467303, null, "3.68"],
  ["FATTENING_FEED_CONVERSION_RATIO", 3.8447473871828115, null, "3.84"],
  ["CARCASS_YIELD_RATE", 0.56, null, "56.00%"],
];

const expectedGroups = [
  "MATING",
  "PREGNANCY",
  "BIRTH",
  "SELECTION",
  "WEANING",
  "OUTBOUND",
  "SALES",
  "FEED_CONVERSION",
];

function readJson(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch (error) {
    throw new Error(`Cannot read JSON input ${file}`, { cause: error });
  }
}

function writeJson(file, value) {
  writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
}

function validateApi(input, batchId, output) {
  const response = readJson(input);
  assert.equal(response.code, 0, "statistics business code");
  const statistics = response.data;
  assert.ok(statistics && typeof statistics === "object", "statistics data");
  assert.equal(statistics.schemaVersion, 1, "schemaVersion");
  assert.equal(statistics.batchId, batchId, "batchId");
  assert.deepEqual(
    {
      totalLitters: statistics.totalLitters,
      totalKits: statistics.totalKits,
      totalLiveKits: statistics.totalLiveKits,
      totalWeaned: statistics.totalWeaned,
    },
    {
      totalLitters: 1004,
      totalKits: 10040,
      totalLiveKits: 9870,
      totalWeaned: 8604,
    },
    "legacy totals",
  );
  assert.equal(
    statistics.metrics?.length,
    expectedMetrics.length,
    "metric count",
  );

  statistics.metrics.forEach((metric, index) => {
    const [code, numericValue, dateValue, displayValue] =
      expectedMetrics[index];
    assert.equal(metric.code, code, `metric ${index + 1} code`);
    assert.equal(metric.order, (index + 1) * 10, `${code} order`);
    assert.equal(metric.status, "AVAILABLE", `${code} status`);
    assert.equal(metric.displayValue, displayValue, `${code} displayValue`);
    assert.deepEqual(metric.missingCauses, [], `${code} missingCauses`);
    if (dateValue) {
      assert.equal(metric.numericValue, null, `${code} numericValue`);
      assert.equal(metric.dateValue?.firstDate, dateValue, `${code} firstDate`);
      assert.equal(metric.dateValue?.lastDate, dateValue, `${code} lastDate`);
      assert.equal(metric.dateValue?.dateCount, 1, `${code} dateCount`);
      assert.deepEqual(
        metric.dateValue?.dailyCycleCounts,
        [{ date: dateValue, cycleCount: 1230 }],
        `${code} dailyCycleCounts`,
      );
    } else {
      assert.equal(metric.numericValue, numericValue, `${code} numericValue`);
      assert.equal(metric.dateValue, null, `${code} dateValue`);
    }
  });

  writeJson(output, {
    passed: true,
    schemaVersion: statistics.schemaVersion,
    batchId,
    metricCount: statistics.metrics.length,
    metricCodes: statistics.metrics.map((metric) => metric.code),
    totals: {
      totalLitters: statistics.totalLitters,
      totalKits: statistics.totalKits,
      totalLiveKits: statistics.totalLiveKits,
      totalWeaned: statistics.totalWeaned,
    },
  });
}

function validateDatabase(input, runId, output) {
  const actual = readJson(input);
  assert.equal(actual.run_id, runId, "database run_id");
  assert.deepEqual(actual.cycle, {
    count: 1230,
    mating_first: "2024-04-22",
    mating_last: "2024-04-22",
  });
  assert.deepEqual(actual.pregnancy, { cycle_count: 1059, doe_count: 1059 });
  assert.deepEqual(actual.buck, { distinct_count: 60 });
  assert.deepEqual(actual.abortion, { cycle_count: 21, event_count: 21 });
  assert.deepEqual(actual.litter, {
    count: 1004,
    total_kits: 10040,
    live_kits: 9870,
    kept_kits: 9490,
    weaned_count: 8604,
    weaning_total_weight_kg: 6323.94,
  });
  assert.deepEqual(actual.sold, {
    allocation_rows: 1,
    rabbit_count: 6834,
    actual_weight_kg: 13095,
    amount: 157140,
    item_count: 6834,
  });
  assert.deepEqual(actual.replacement, {
    allocation_rows: 1,
    rabbit_count: 600,
    total_weight_kg: 1050,
  });
  assert.deepEqual(actual.feed, { breeding_kg: 22050, fattening_kg: 30070 });
  assert.deepEqual(actual.carcass, {
    version_count: 1,
    yield_rate: 0.56,
    source_unit: "测试屠宰场",
    measured_date: "2024-08-01",
  });
  assert.deepEqual(actual.tenant_ownership, {
    owner_memberships: 2,
    target_batch_owned: 1,
    isolation_batch_owned: 1,
  });
  assert.deepEqual(actual.isolation, {
    house_rows: 1,
    batch_rows: 1,
    rabbit_rows: 0,
    business_rows: 0,
  });
  writeJson(output, actual);
}

function validateAndroid(input, runId, houseId, batchId, output) {
  const result = readJson(input);
  assert.equal(result.runId, runId, "Android runId");
  assert.equal(result.houseId, houseId, "Android houseId");
  assert.equal(result.batchId, batchId, "Android batchId");
  assert.equal(result.fixtureMutated, false, "Android fixture mutation");
  assert.deepEqual(
    result.metricCodes,
    expectedMetrics.map(([code]) => code),
  );
  assert.deepEqual(
    result.metricDisplayValues,
    Object.fromEntries(
      expectedMetrics.map(([code, , , displayValue]) => [code, displayValue]),
    ),
  );
  assert.deepEqual(result.groupStages, expectedGroups);
  assert.equal(result.exportEntranceVisible, true, "Android export entrance");
  assert.equal(result.carcassYieldFormInspected, true, "Android carcass form");
  assert.equal(
    result.carcassYieldHistoryInspected,
    true,
    "Android carcass history",
  );
  writeJson(output, {
    passed: true,
    resultFile: "android_e2e_result.json",
    metricCount: result.metricCodes.length,
    metricDisplayValues: result.metricDisplayValues,
    groupStages: result.groupStages,
  });
}

const [mode, ...args] = process.argv.slice(2);
if (mode === "api" && args.length === 3) {
  validateApi(args[0], Number(args[1]), args[2]);
} else if (mode === "database" && args.length === 3) {
  validateDatabase(args[0], args[1], args[2]);
} else if (mode === "android" && args.length === 5) {
  validateAndroid(args[0], args[1], Number(args[2]), Number(args[3]), args[4]);
} else {
  process.stderr.write(
    "Usage: batch-statistics-cross-client-validate.mjs api <input> <batch-id> <output> | " +
      "database <input> <run-id> <output> | " +
      "android <input> <run-id> <house-id> <batch-id> <output>\n",
  );
  process.exit(64);
}
