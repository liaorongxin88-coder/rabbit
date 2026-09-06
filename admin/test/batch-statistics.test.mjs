import assert from "node:assert/strict";
import test from "node:test";
import {
  BATCH_METRIC_CODES,
  BATCH_METRIC_CONTRACTS,
  BATCH_METRIC_LAYOUT,
  batchStatisticsContractError,
  metricDisplayValue,
  metricStatusLabel,
} from "../src/lib/batch-statistics.ts";

function metric(contract) {
  const dateMetric = contract.valueType === "DATE_RANGE";
  return {
    ...contract,
    status: "AVAILABLE",
    numericValue: dateMetric ? null : 0,
    displayValue: dateMetric ? "2024-04-22" : "0",
    dateValue: dateMetric
      ? {
          firstDate: "2024-04-22",
          lastDate: "2024-04-22",
          dateCount: 1,
          dailyCycleCounts: [{ date: "2024-04-22", cycleCount: 1 }],
        }
      : null,
    numerator: null,
    denominator: null,
    components: [],
    missingCauses: [],
  };
}

function statistics(metrics = BATCH_METRIC_CONTRACTS.map(metric)) {
  return {
    schemaVersion: 1,
    batchId: 42,
    houseName: "验收兔场",
    batchCode: "B-042",
    calculatedAt: "2026-09-04T03:20:00Z",
    totalLitters: 0,
    totalKits: 0,
    totalLiveKits: 0,
    totalWeaned: 0,
    metrics,
  };
}

test("pins all 28 metrics to the approved eight-group desktop layout", () => {
  const laidOut = BATCH_METRIC_LAYOUT.flatMap((group) => group.rows.flat());

  assert.equal(BATCH_METRIC_CODES.length, 28);
  assert.equal(BATCH_METRIC_LAYOUT.length, 8);
  assert.equal(
    BATCH_METRIC_LAYOUT.reduce((total, group) => total + group.rows.length, 0),
    16,
  );
  assert.deepEqual(laidOut, [...BATCH_METRIC_CODES]);
  assert.deepEqual(
    BATCH_METRIC_LAYOUT.map((group) => group.rows),
    [
      [
        ["MATING_DATE"],
        ["MATED_DOE_COUNT", "CONCEPTION_RATE"],
        ["DOE_BUCK_RATIO"],
      ],
      [["PREGNANT_DOE_COUNT", "ABORTION_RATE"]],
      [
        ["DELIVERED_LITTER_COUNT", "TOTAL_KIT_COUNT"],
        ["AVERAGE_KITS_PER_LITTER"],
        ["LIVE_KIT_COUNT", "LIVE_BIRTH_RATE"],
      ],
      [
        ["KEPT_LITTER_COUNT", "KEPT_KIT_COUNT"],
        ["KEPT_LIVE_RATE", "AVERAGE_KEPT_PER_LITTER"],
      ],
      [
        ["WEANED_KIT_COUNT"],
        ["AVERAGE_WEANING_WEIGHT", "WEANING_SURVIVAL_RATE"],
      ],
      [
        ["SOLD_RABBIT_COUNT", "OUTBOUND_SURVIVAL_RATE"],
        ["SOLD_WEIGHT", "AVERAGE_SOLD_WEIGHT"],
      ],
      [["TOTAL_SALES_AMOUNT", "SALES_PRICE_PER_KG", "SALES_PRICE_PER_RABBIT"]],
      [
        ["FULL_FEED_CONVERSION_RATIO", "FATTENING_FEED_CONVERSION_RATIO"],
        ["CARCASS_YIELD_RATE"],
      ],
    ],
  );

  const salesGroup = BATCH_METRIC_LAYOUT.find(
    (group) => group.stage === "SALES",
  );
  assert.deepEqual(salesGroup?.rows, [
    ["TOTAL_SALES_AMOUNT", "SALES_PRICE_PER_KG", "SALES_PRICE_PER_RABBIT"],
  ]);
});

test("validates the fixed contract while ignoring unknown additive metrics", () => {
  assert.equal(batchStatisticsContractError(statistics()), null);
  assert.match(batchStatisticsContractError(null), /升级服务/);
  assert.match(batchStatisticsContractError("invalid"), /升级服务/);
  assert.equal(
    batchStatisticsContractError(
      statistics([
        ...BATCH_METRIC_CONTRACTS.map(metric),
        {
          ...metric(BATCH_METRIC_CONTRACTS.at(-1)),
          code: "SERVER_ADDED",
          order: 290,
        },
      ]),
    ),
    null,
  );
  assert.match(
    batchStatisticsContractError(
      statistics(BATCH_METRIC_CONTRACTS.slice(1).map(metric)),
    ),
    /缺少 1 项/,
  );
  assert.match(
    batchStatisticsContractError({ ...statistics(), schemaVersion: 2 }),
    /升级服务/,
  );
});

test("rejects fixed metadata, order, UTC, and status-shape drift", () => {
  const metadata = statistics();
  metadata.metrics[3].formula = "错误口径";
  assert.match(batchStatisticsContractError(metadata), /元数据不一致/);

  const order = statistics();
  [order.metrics[1], order.metrics[2]] = [order.metrics[2], order.metrics[1]];
  assert.match(batchStatisticsContractError(order), /顺序不正确/);

  assert.match(
    batchStatisticsContractError({
      ...statistics(),
      calculatedAt: "2026-09-04T11:20:00+08:00",
    }),
    /取数信息/,
  );

  const missingCause = statistics();
  Object.assign(missingCause.metrics[20], {
    status: "DATA_MISSING",
    numericValue: null,
    displayValue: null,
    missingCauses: [],
  });
  assert.match(batchStatisticsContractError(missingCause), /值或状态/);

  const availableWithCause = statistics();
  availableWithCause.metrics[20].missingCauses = [
    { code: "MISSING_BATCH_SALE_ALLOCATION", message: "缺少批次重量" },
  ];
  assert.match(batchStatisticsContractError(availableWithCause), /值或状态/);
});

test("rejects malformed nested values and impossible dates without throwing", () => {
  for (const [field, value] of [
    ["numerator", undefined],
    ["components", [42]],
    ["missingCauses", [null]],
  ]) {
    const malformed = statistics();
    malformed.metrics[0][field] = value;
    assert.doesNotThrow(() => batchStatisticsContractError(malformed));
    assert.match(batchStatisticsContractError(malformed), /值或状态/);
  }

  const impossibleDate = statistics();
  impossibleDate.metrics[0].dateValue = {
    firstDate: "2024-02-31",
    lastDate: "2024-02-31",
    dateCount: 1,
    dailyCycleCounts: [{ date: "2024-02-31", cycleCount: 1 }],
  };
  assert.match(batchStatisticsContractError(impossibleDate), /值或状态/);

  const invalidPercentage = statistics();
  invalidPercentage.metrics[2].numericValue = 1.01;
  assert.match(batchStatisticsContractError(invalidPercentage), /值或状态/);

  const fractionalCount = statistics();
  fractionalCount.metrics[1].numericValue = 1.5;
  assert.match(batchStatisticsContractError(fractionalCount), /值或状态/);
});

test("keeps real zero values distinct from unavailable statuses", () => {
  const available = metric(BATCH_METRIC_CONTRACTS[1]);
  assert.equal(metricDisplayValue(available), "0");
  assert.equal(
    metricDisplayValue({
      ...available,
      status: "NOT_APPLICABLE",
      numericValue: null,
      displayValue: null,
      missingCauses: [{ code: "ZERO_DENOMINATOR", message: "计算分母为零" }],
    }),
    "暂无可计算数据",
  );
  assert.equal(metricStatusLabel("NOT_RECORDED"), "未录入");
  assert.equal(metricStatusLabel("DATA_MISSING"), "历史数据缺失");
});
