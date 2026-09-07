#!/usr/bin/env node

import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

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

const complexPrimaryScenarioIds = [
  "complex-available",
  "mixed-data-quality",
  "mixed-batch-rounding",
  "time-and-cycle-boundaries",
  "security-and-retry",
];

const complexSupportScenarioId = "mixed-batch-rounding-support";
const complexScenarioIds = [
  ...complexPrimaryScenarioIds,
  complexSupportScenarioId,
];
const complexUserRoles = ["OWNER", "READ_ONLY", "UNRELATED_HOUSE"];
const metricCodes = expectedMetrics.map(([code]) => code);
const validStatuses = new Set([
  "AVAILABLE",
  "NOT_APPLICABLE",
  "NOT_RECORDED",
  "DATA_MISSING",
]);
const statusDisplayValues = new Map([
  ["NOT_APPLICABLE", "暂无可计算数据"],
  ["NOT_RECORDED", "未录入"],
  ["DATA_MISSING", "历史数据缺失"],
]);
const xlsxMediaType =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
const causeMessages = new Map([
  ["MISSING_WEANING_WEIGHT", "断奶记录缺少总重快照"],
  ["MISSING_BATCH_SALE_ALLOCATION", "销售记录缺少批次重量分配"],
  ["MISSING_SALE_UNIT_PRICE", "销售记录缺少重量单价或金额快照"],
  ["MISSING_FEED_ALLOCATION", "投喂记录缺少批次阶段分配"],
  ["MISSING_FEED_UNIT", "投喂记录单位不是kg"],
  ["MISSING_REPLACEMENT_WEIGHT", "转后备记录缺少实测重量快照"],
  ["CARCASS_YIELD_NOT_RECORDED", "未录入出肉率"],
  ["ZERO_DENOMINATOR", "计算分母为零"],
]);

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

function requireObject(value, label) {
  assert.ok(value && typeof value === "object" && !Array.isArray(value), label);
  return value;
}

function valueOf(object, snakeName, camelName = snakeName) {
  return object[snakeName] ?? object[camelName];
}

function positiveInteger(value, label) {
  assert.ok(
    Number.isSafeInteger(value) && value > 0,
    `${label} must be a positive integer`,
  );
  return value;
}

function nonEmptyString(value, label) {
  assert.ok(
    typeof value === "string" && value.trim(),
    `${label} must be a non-empty string`,
  );
  return value.trim();
}

function assertPasswordFree(value, label = "fixture manifest") {
  const forbiddenKey =
    /(?:password|passwd|token|secret|authorization|login.?body)/i;
  const forbiddenValue =
    /(?:Bearer\s+[A-Za-z0-9._~-]+|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)/;
  const visit = (entry, location) => {
    if (Array.isArray(entry)) {
      entry.forEach((item, index) => visit(item, `${location}[${index}]`));
      return;
    }
    if (!entry || typeof entry !== "object") {
      if (typeof entry === "string") {
        assert.doesNotMatch(
          entry,
          forbiddenValue,
          `${location} contains a bearer token or JWT`,
        );
      }
      return;
    }
    for (const [key, child] of Object.entries(entry)) {
      assert.doesNotMatch(
        key,
        forbiddenKey,
        `${location}.${key} is a forbidden secret field`,
      );
      visit(child, `${location}.${key}`);
    }
  };
  visit(value, label);
}

function normalizeCause(cause, label) {
  if (typeof cause === "string") {
    const code = nonEmptyString(cause, label);
    const message = causeMessages.get(code);
    assert.ok(message, `${label} uses an unknown missing-cause code: ${code}`);
    return { code, message };
  }
  const object = requireObject(cause, `${label} must be a string or object`);
  return {
    code: nonEmptyString(object.code, `${label}.code`),
    message: nonEmptyString(object.message, `${label}.message`),
  };
}

function normalizeExpectedMetric(metric, index, scenarioId) {
  const object = requireObject(
    metric,
    `${scenarioId}.metrics[${index}] must be an object`,
  );
  const expectedCode = metricCodes[index];
  assert.equal(
    object.code,
    expectedCode,
    `${scenarioId} metric ${index + 1} code`,
  );
  if (object.order !== undefined) {
    assert.equal(
      object.order,
      (index + 1) * 10,
      `${scenarioId}.${expectedCode}.order`,
    );
  }
  assert.ok(
    validStatuses.has(object.status),
    `${scenarioId}.${expectedCode}.status is invalid`,
  );
  assert.ok(
    Object.hasOwn(object, "numericValue"),
    `${scenarioId}.${expectedCode}.numericValue is required`,
  );
  assert.ok(
    Object.hasOwn(object, "dateValue"),
    `${scenarioId}.${expectedCode}.dateValue is required`,
  );
  assert.ok(
    Object.hasOwn(object, "displayValue"),
    `${scenarioId}.${expectedCode}.displayValue is required`,
  );
  assert.ok(
    Array.isArray(object.missingCauses),
    `${scenarioId}.${expectedCode}.missingCauses must be an array`,
  );
  const causes = object.missingCauses.map((cause, causeIndex) =>
    normalizeCause(
      cause,
      `${scenarioId}.${expectedCode}.missingCauses[${causeIndex}]`,
    ),
  );
  if (object.status === "AVAILABLE") {
    assert.equal(
      typeof object.displayValue,
      "string",
      `${scenarioId}.${expectedCode}.displayValue`,
    );
    assert.deepEqual(causes, [], `${scenarioId}.${expectedCode}.missingCauses`);
    if (expectedCode === "MATING_DATE") {
      requireObject(
        object.dateValue,
        `${scenarioId}.${expectedCode}.dateValue`,
      );
      assert.equal(
        object.numericValue,
        null,
        `${scenarioId}.${expectedCode}.numericValue`,
      );
    } else {
      assert.ok(
        Number.isFinite(object.numericValue),
        `${scenarioId}.${expectedCode}.numericValue`,
      );
      assert.equal(
        object.dateValue,
        null,
        `${scenarioId}.${expectedCode}.dateValue`,
      );
    }
  } else {
    assert.equal(
      object.numericValue,
      null,
      `${scenarioId}.${expectedCode}.numericValue`,
    );
    assert.equal(
      object.dateValue,
      null,
      `${scenarioId}.${expectedCode}.dateValue`,
    );
    assert.equal(
      object.displayValue,
      null,
      `${scenarioId}.${expectedCode}.displayValue`,
    );
    assert.ok(
      causes.length > 0,
      `${scenarioId}.${expectedCode}.missingCauses must not be empty`,
    );
  }
  return {
    code: expectedCode,
    order: (index + 1) * 10,
    status: object.status,
    numericValue: object.numericValue,
    dateValue: object.dateValue,
    displayValue: object.displayValue,
    missingCauses: causes,
  };
}

function normalizeCatalog(input) {
  const catalog = requireObject(
    readJson(input),
    "complex catalog must be an object",
  );
  assert.equal(catalog.schemaVersion, 1, "complex catalog schemaVersion");
  assert.ok(Array.isArray(catalog.scenarios), "complex catalog scenarios");
  assert.equal(
    catalog.scenarios.length,
    complexScenarioIds.length,
    "complex catalog scenario count",
  );
  const byId = new Map();
  for (const entry of catalog.scenarios) {
    const object = requireObject(entry, "complex catalog scenario");
    const id = nonEmptyString(object.id, "complex catalog scenario id");
    assert.ok(
      complexScenarioIds.includes(id),
      `complex catalog scenario id is not allowlisted: ${id}`,
    );
    assert.equal(
      byId.has(id),
      false,
      `duplicate complex catalog scenario: ${id}`,
    );
    const expectedRole =
      id === complexSupportScenarioId ? "support" : "primary";
    if (object.batchRole !== undefined) {
      assert.equal(object.batchRole, expectedRole, `${id}.batchRole`);
    }
    assert.ok(Array.isArray(object.metrics), `${id}.metrics must be an array`);
    assert.equal(
      object.metrics.length,
      metricCodes.length,
      `${id}.metric count`,
    );
    const metrics = object.metrics.map((metric, index) =>
      normalizeExpectedMetric(metric, index, id),
    );
    const legacyTotals = requireObject(
      object.legacyTotals,
      `${id}.legacyTotals must be an object`,
    );
    byId.set(id, {
      id,
      batchRole: expectedRole,
      legacyTotals,
      metrics,
    });
  }
  assert.deepEqual(
    [...byId.keys()].sort(),
    [...complexScenarioIds].sort(),
    "complex catalog scenario ids",
  );
  return { schemaVersion: 1, byId };
}

function normalizeRole(value) {
  const normalized = String(value ?? "")
    .trim()
    .toUpperCase()
    .replaceAll("-", "_");
  if (normalized === "READONLY" || normalized === "VIEWER") return "READ_ONLY";
  if (
    normalized === "ISOLATED" ||
    normalized === "UNRELATED" ||
    normalized === "OUTSIDER"
  )
    return "UNRELATED_HOUSE";
  return normalized;
}

function normalizeComplexManifest(input, catalogInput, runId) {
  const manifest = requireObject(
    readJson(input),
    "complex fixture manifest must be an object",
  );
  assertPasswordFree(manifest);
  const catalog = normalizeCatalog(catalogInput);
  assert.equal(
    valueOf(manifest, "run_id", "runId"),
    runId,
    "complex fixture run_id",
  );
  const manifestSchemaVersion = valueOf(
    manifest,
    "schema_version",
    "schemaVersion",
  );
  assert.equal(manifestSchemaVersion, 1, "complex fixture schemaVersion");
  const houseId = positiveInteger(
    valueOf(manifest, "target_house_id", "houseId"),
    "complex fixture target_house_id",
  );
  const isolationHouseId = positiveInteger(
    valueOf(manifest, "isolation_house_id", "isolationHouseId"),
    "complex fixture isolation_house_id",
  );
  assert.notEqual(houseId, isolationHouseId, "target and isolation house ids");
  const houseName = nonEmptyString(
    valueOf(manifest, "house_name", "houseName"),
    "complex fixture house_name",
  );
  const isolationHouseName = nonEmptyString(
    valueOf(manifest, "isolation_house_name", "isolationHouseName"),
    "complex fixture isolation_house_name",
  );
  const credentialProfile = nonEmptyString(
    valueOf(manifest, "credential_profile", "credentialProfile"),
    "complex fixture credential_profile",
  );
  assert.ok(Array.isArray(manifest.scenarios), "complex fixture scenarios");
  assert.equal(
    manifest.scenarios.length,
    complexScenarioIds.length,
    "complex fixture scenario count",
  );
  const scenarioById = new Map();
  for (const entry of manifest.scenarios) {
    const object = requireObject(entry, "complex fixture scenario");
    const id = nonEmptyString(
      valueOf(object, "scenario_id", "id"),
      "complex fixture scenario id",
    );
    assert.ok(
      complexScenarioIds.includes(id),
      `complex fixture scenario id is not allowlisted: ${id}`,
    );
    assert.equal(
      scenarioById.has(id),
      false,
      `duplicate complex fixture scenario: ${id}`,
    );
    const batchId = positiveInteger(
      valueOf(object, "batch_id", "batchId"),
      `${id}.batch_id`,
    );
    const batchCode = nonEmptyString(
      valueOf(object, "batch_code", "batchCode"),
      `${id}.batch_code`,
    );
    const scenarioHouseId = valueOf(object, "house_id", "houseId") ?? houseId;
    assert.equal(scenarioHouseId, houseId, `${id}.house_id`);
    const expectedRole =
      id === complexSupportScenarioId ? "support" : "primary";
    const suppliedRole = valueOf(object, "batch_role", "batchRole");
    if (suppliedRole !== undefined)
      assert.equal(suppliedRole, expectedRole, `${id}.batch_role`);
    scenarioById.set(id, {
      id,
      batchRole: expectedRole,
      batchId,
      batchCode,
      houseId,
      legacyTotals: catalog.byId.get(id).legacyTotals,
      metrics: catalog.byId.get(id).metrics,
    });
  }
  assert.deepEqual(
    [...scenarioById.keys()].sort(),
    [...complexScenarioIds].sort(),
    "complex fixture scenario ids",
  );
  assert.equal(
    new Set([...scenarioById.values()].map((entry) => entry.batchId)).size,
    6,
    "complex batch ids must be unique",
  );
  assert.equal(
    new Set([...scenarioById.values()].map((entry) => entry.batchCode)).size,
    6,
    "complex batch codes must be unique",
  );

  const manifestUsers = manifest.accounts ?? manifest.users;
  assert.ok(Array.isArray(manifestUsers), "complex fixture accounts");
  assert.equal(
    manifestUsers.length,
    complexUserRoles.length,
    "complex fixture account count",
  );
  const userByRole = new Map();
  for (const entry of manifestUsers) {
    const object = requireObject(entry, "complex fixture user");
    const role = normalizeRole(valueOf(object, "role", "role"));
    assert.ok(
      complexUserRoles.includes(role),
      `complex fixture user role is invalid: ${role}`,
    );
    assert.equal(
      userByRole.has(role),
      false,
      `duplicate complex fixture user role: ${role}`,
    );
    const userHouseId = positiveInteger(
      valueOf(object, "house_id", "houseId"),
      `${role}.house_id`,
    );
    assert.equal(
      userHouseId,
      role === "UNRELATED_HOUSE" ? isolationHouseId : houseId,
      `${role}.house_id topology`,
    );
    const expectedHouseName =
      role === "UNRELATED_HOUSE" ? isolationHouseName : houseName;
    const userHouseName = nonEmptyString(
      valueOf(object, "house_name", "houseName"),
      `${role}.house_name`,
    );
    assert.equal(
      userHouseName,
      expectedHouseName,
      `${role}.house_name topology`,
    );
    userByRole.set(role, {
      role,
      userId: positiveInteger(
        valueOf(object, "user_id", "userId"),
        `${role}.user_id`,
      ),
      userName: nonEmptyString(
        valueOf(object, "user_name", "userName"),
        `${role}.user_name`,
      ),
      houseId: userHouseId,
      houseName: userHouseName,
      credentialProfile: nonEmptyString(
        valueOf(object, "credential_profile", "credentialProfile") ??
          credentialProfile,
        `${role}.credential_profile`,
      ),
    });
  }
  assert.deepEqual(
    [...userByRole.keys()].sort(),
    [...complexUserRoles].sort(),
    "complex fixture user roles",
  );
  assert.equal(
    new Set([...userByRole.values()].map((entry) => entry.userId)).size,
    3,
    "complex user ids must be unique",
  );
  assert.equal(
    new Set([...userByRole.values()].map((entry) => entry.userName)).size,
    3,
    "complex usernames must be unique",
  );

  return {
    schemaVersion: 1,
    runId,
    credentialProfile,
    houseId,
    houseName,
    isolationHouseId,
    isolationHouseName,
    scenarios: complexScenarioIds.map((id) => scenarioById.get(id)),
    users: complexUserRoles.map((role) => userByRole.get(role)),
  };
}

function validateComplexManifest(input, catalog, runId, output) {
  const normalized = normalizeComplexManifest(input, catalog, runId);
  writeJson(output, normalized);
}

function scenarioFromCatalog(catalogInput, scenarioId) {
  assert.ok(
    complexScenarioIds.includes(scenarioId),
    `scenario id is not allowlisted: ${scenarioId}`,
  );
  const scenario = normalizeCatalog(catalogInput).byId.get(scenarioId);
  assert.ok(scenario, `scenario is absent from catalog: ${scenarioId}`);
  return scenario;
}

function assertMetricMatches(metric, expected, label) {
  assert.equal(metric.code, expected.code, `${label}.code`);
  assert.equal(metric.order, expected.order, `${label}.order`);
  assert.equal(metric.status, expected.status, `${label}.status`);
  assert.equal(
    metric.numericValue,
    expected.numericValue,
    `${label}.numericValue`,
  );
  assert.deepEqual(metric.dateValue, expected.dateValue, `${label}.dateValue`);
  assert.equal(
    metric.displayValue,
    expected.displayValue,
    `${label}.displayValue`,
  );
  assert.deepEqual(
    metric.missingCauses,
    expected.missingCauses,
    `${label}.missingCauses`,
  );
}

function validateComplexApi(input, scenarioId, batchId, catalogInput, output) {
  const expected = scenarioFromCatalog(catalogInput, scenarioId);
  const response = readJson(input);
  assert.equal(response.code, 0, `${scenarioId} statistics business code`);
  const statistics = requireObject(
    response.data,
    `${scenarioId} statistics data`,
  );
  assert.equal(statistics.schemaVersion, 1, `${scenarioId} schemaVersion`);
  assert.equal(statistics.batchId, batchId, `${scenarioId} batchId`);
  nonEmptyString(statistics.houseName, `${scenarioId} houseName`);
  nonEmptyString(statistics.batchCode, `${scenarioId} batchCode`);
  assert.match(
    statistics.calculatedAt,
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/,
    `${scenarioId} calculatedAt`,
  );
  assert.ok(Array.isArray(statistics.metrics), `${scenarioId} metrics`);
  assert.equal(
    statistics.metrics.length,
    metricCodes.length,
    `${scenarioId} metric count`,
  );
  statistics.metrics.forEach((metric, index) =>
    assertMetricMatches(
      metric,
      expected.metrics[index],
      `${scenarioId}.${metricCodes[index]}`,
    ),
  );
  assert.deepEqual(
    {
      totalLitters: statistics.totalLitters,
      totalKits: statistics.totalKits,
      totalLiveKits: statistics.totalLiveKits,
      totalWeaned: statistics.totalWeaned,
    },
    expected.legacyTotals,
    `${scenarioId} legacy totals`,
  );
  writeJson(output, {
    passed: true,
    scenarioId,
    batchId,
    metricCount: statistics.metrics.length,
    metricCodes: statistics.metrics.map((metric) => metric.code),
    metricStatuses: Object.fromEntries(
      statistics.metrics.map((metric) => [metric.code, metric.status]),
    ),
    metricDisplayValues: Object.fromEntries(
      statistics.metrics.map((metric) => [metric.code, metric.displayValue]),
    ),
    missingCauses: Object.fromEntries(
      statistics.metrics.map((metric) => [metric.code, metric.missingCauses]),
    ),
  });
}

function unzipText(file, member) {
  const result = spawnSync("unzip", ["-p", file, member], { encoding: "utf8" });
  assert.equal(
    result.status,
    0,
    `cannot read ${member} from ${file}: ${result.stderr.trim()}`,
  );
  return result.stdout;
}

function decodeXml(value) {
  return value
    .replaceAll("&lt;", "<")
    .replaceAll("&gt;", ">")
    .replaceAll("&quot;", '"')
    .replaceAll("&apos;", "'")
    .replaceAll("&amp;", "&#38;")
    .replaceAll("&#38;", "&")
    .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number(code)))
    .replace(/&#x([0-9a-f]+);/gi, (_, code) =>
      String.fromCodePoint(Number.parseInt(code, 16)),
    );
}

function sharedStrings(file) {
  const listing = spawnSync("unzip", ["-Z1", file], { encoding: "utf8" });
  assert.equal(
    listing.status,
    0,
    `cannot list ${file}: ${listing.stderr.trim()}`,
  );
  if (!listing.stdout.split("\n").includes("xl/sharedStrings.xml")) return [];
  const xml = unzipText(file, "xl/sharedStrings.xml");
  return [...xml.matchAll(/<si(?:\s[^>]*)?>([\s\S]*?)<\/si>/g)].map((match) =>
    decodeXml(
      [...match[1].matchAll(/<t(?:\s[^>]*)?>([\s\S]*?)<\/t>/g)]
        .map((entry) => entry[1])
        .join(""),
    ),
  );
}

function columnIndex(reference) {
  const letters = /^[A-Z]+/.exec(reference)?.[0];
  if (!letters) return null;
  let value = 0;
  for (const letter of letters) value = value * 26 + letter.charCodeAt(0) - 64;
  return value - 1;
}

function sheetRows(file, member, strings) {
  const xml = unzipText(file, member);
  return [...xml.matchAll(/<row(?:\s[^>]*)?>([\s\S]*?)<\/row>/g)].map(
    (rowMatch) => {
      const cells = [];
      let sequentialIndex = 0;
      for (const cellMatch of rowMatch[1].matchAll(
        /<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g,
      )) {
        const attributes = cellMatch[1];
        const body = cellMatch[2] ?? "";
        const reference = /\br="([A-Z]+\d+)"/.exec(attributes)?.[1];
        const index = reference ? columnIndex(reference) : sequentialIndex;
        assert.ok(index !== null, `invalid cell reference in ${member}`);
        const type = /\bt="([^"]+)"/.exec(attributes)?.[1] ?? "n";
        let value;
        if (type === "inlineStr") {
          value = decodeXml(
            [...body.matchAll(/<t(?:\s[^>]*)?>([\s\S]*?)<\/t>/g)]
              .map((entry) => entry[1])
              .join(""),
          );
        } else {
          const raw = /<v>([\s\S]*?)<\/v>/.exec(body)?.[1] ?? "";
          value = type === "s" ? (strings[Number(raw)] ?? "") : decodeXml(raw);
        }
        cells[index] = value;
        sequentialIndex = index + 1;
      }
      return Array.from(
        { length: cells.length },
        (_, index) => cells[index] ?? "",
      );
    },
  );
}

function excelDateSerial(date) {
  const [year, month, day] = date.split("-").map(Number);
  return (
    (Date.UTC(year, month - 1, day) - Date.UTC(1899, 11, 30)) /
    (24 * 60 * 60 * 1000)
  );
}

function headerValue(headers, name) {
  const prefix = `${name.toLowerCase()}:`;
  return (
    headers
      .split(/\r?\n/)
      .filter((line) => line.toLowerCase().startsWith(prefix))
      .map((line) => line.slice(line.indexOf(":") + 1).trim())
      .at(-1) ?? ""
  );
}

function validateComplexXlsx(
  file,
  headersFile,
  scenarioId,
  batchCode,
  catalogInput,
  output,
) {
  const expected = scenarioFromCatalog(catalogInput, scenarioId);
  const headers = readFileSync(headersFile, "utf8");
  assert.equal(
    headerValue(headers, "content-type").split(";")[0],
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    `${scenarioId} workbook content type`,
  );
  const disposition = headerValue(headers, "content-disposition");
  assert.match(
    disposition,
    /\.xlsx(?:"|$)/,
    `${scenarioId} workbook content disposition`,
  );
  assert.ok(
    disposition.includes(batchCode),
    `${scenarioId} workbook filename must contain batch code`,
  );
  const workbook = unzipText(file, "xl/workbook.xml");
  const names = [...workbook.matchAll(/<sheet\s[^>]*name="([^"]+)"/g)].map(
    (match) => decodeXml(match[1]),
  );
  assert.deepEqual(
    names,
    ["批次统计", "口径与状态"],
    `${scenarioId} workbook sheets`,
  );
  const strings = sharedStrings(file);
  const summaryRows = sheetRows(file, "xl/worksheets/sheet1.xml", strings);
  const detailRows = sheetRows(file, "xl/worksheets/sheet2.xml", strings);
  assert.equal(summaryRows.length, 2, `${scenarioId} summary row count`);
  assert.equal(summaryRows[0].length, 31, `${scenarioId} summary column count`);
  assert.equal(summaryRows[1].length, 31, `${scenarioId} summary value count`);
  assert.equal(
    summaryRows[1][1],
    batchCode,
    `${scenarioId} workbook batch code`,
  );
  assert.equal(detailRows.length, 29, `${scenarioId} detail row count`);
  expected.metrics.forEach((metric, index) => {
    const row = detailRows[index + 1];
    const summaryValue = summaryRows[1][index + 3];
    assert.equal(
      row[1],
      metric.code,
      `${scenarioId}.${metric.code} workbook code`,
    );
    assert.equal(
      row[7],
      metric.status,
      `${scenarioId}.${metric.code} workbook status`,
    );
    if (metric.status === "AVAILABLE") {
      assert.equal(
        row[6],
        metric.displayValue,
        `${scenarioId}.${metric.code} workbook display`,
      );
      if (metric.dateValue) {
        const expectedRawDate =
          metric.dateValue.dateCount === 1
            ? metric.dateValue.firstDate
            : `${metric.dateValue.firstDate}/${metric.dateValue.lastDate}`;
        assert.equal(
          row[5],
          expectedRawDate,
          `${scenarioId}.${metric.code} workbook detail raw date`,
        );
        if (metric.dateValue.dateCount === 1) {
          assert.equal(
            Number(summaryValue),
            excelDateSerial(metric.dateValue.firstDate),
            `${scenarioId}.${metric.code} workbook summary date`,
          );
        } else {
          assert.equal(
            summaryValue,
            metric.displayValue,
            `${scenarioId}.${metric.code} workbook summary date range`,
          );
        }
      } else {
        assert.equal(
          Number(row[5]),
          metric.numericValue,
          `${scenarioId}.${metric.code} workbook detail raw numeric value`,
        );
        assert.equal(
          Number(summaryValue),
          metric.numericValue,
          `${scenarioId}.${metric.code} workbook summary numeric value`,
        );
      }
    } else {
      assert.equal(
        row[5],
        "",
        `${scenarioId}.${metric.code} workbook raw value`,
      );
      assert.equal(
        row[6],
        "",
        `${scenarioId}.${metric.code} workbook display value`,
      );
      assert.equal(
        summaryValue,
        statusDisplayValues.get(metric.status),
        `${scenarioId}.${metric.code} workbook summary status`,
      );
    }
    let offset = -1;
    for (const cause of metric.missingCauses) {
      const next = row[12].indexOf(cause.code, offset + 1);
      assert.ok(
        next > offset,
        `${scenarioId}.${metric.code} workbook missing cause order`,
      );
      offset = next;
    }
  });
  writeJson(output, {
    passed: true,
    scenarioId,
    batchCode,
    sheetNames: names,
    metricCount: expected.metrics.length,
  });
}

function validateComplexSecurity(
  input,
  runId,
  scenarioId,
  expectedHouseId,
  artifactRoot,
  output,
) {
  assert.equal(scenarioId, "security-and-retry", "security scenario id");
  const result = requireObject(readJson(input), "security preflight result");
  assert.equal(result.runId, runId, "security preflight runId");
  assert.equal(result.scenarioId, scenarioId, "security preflight scenarioId");
  assert.equal(result.ownerStatistics?.code, 0, "owner statistics access");
  assert.equal(
    result.readOnlyStatistics?.code,
    0,
    "read-only statistics access",
  );
  const readOnlyExport = requireObject(
    result.readOnlyExport,
    "read-only export evidence",
  );
  assert.equal(readOnlyExport.allowed, true, "read-only export permission");
  assert.equal(readOnlyExport.httpStatus, 200, "read-only export HTTP status");
  assert.equal(
    readOnlyExport.houseId,
    expectedHouseId,
    "read-only export house scope",
  );
  assert.equal(
    readOnlyExport.mediaType,
    xlsxMediaType,
    "read-only export media type",
  );
  assert.equal(
    readOnlyExport.zipSignature,
    "PK",
    "read-only export ZIP signature evidence",
  );
  const exportFile = assertArtifactFile(
    artifactRoot,
    readOnlyExport.file,
    "read-only export workbook",
  );
  assert.equal(
    statSync(path.resolve(artifactRoot, exportFile)).size,
    readOnlyExport.byteLength,
    "read-only export byte length",
  );
  assert.equal(
    readFileSync(path.resolve(artifactRoot, exportFile))
      .subarray(0, 2)
      .toString("ascii"),
    "PK",
    "read-only export workbook signature",
  );
  const exportHeaders = assertArtifactFile(
    artifactRoot,
    readOnlyExport.headersFile,
    "read-only export headers",
  );
  assert.equal(
    headerValue(
      readFileSync(path.resolve(artifactRoot, exportHeaders), "utf8"),
      "content-type",
    ).split(";")[0],
    xlsxMediaType,
    "read-only export header media type",
  );
  for (const [name, response] of [
    ["read-only edit", result.readOnlyEdit],
    ["read-only history", result.readOnlyHistory],
    ["unrelated-house statistics", result.unrelatedStatistics],
  ]) {
    assert.equal(response?.code, 403, `${name} must be denied with code 403`);
  }
  assert.equal(result.replay?.first?.code, 0, "carcass replay first response");
  assert.equal(
    result.replay?.second?.code,
    0,
    "carcass replay second response",
  );
  assert.equal(
    result.replay.first.data?.id,
    result.replay.second.data?.id,
    "carcass replay version id",
  );
  assert.ok(
    result.replay?.conflict?.code !== 0,
    "changed-payload retry must conflict",
  );
  assert.equal(
    result.replay.beforeVersionCount,
    0,
    "initial security version count",
  );
  assert.equal(
    result.replay.beforeDedupCount,
    0,
    "initial security dedup count",
  );
  assert.equal(
    result.replay.firstVersionCount,
    result.replay.beforeVersionCount + 1,
    "first write version count",
  );
  assert.equal(
    result.replay.firstDedupCount,
    result.replay.beforeDedupCount + 1,
    "first write dedup count",
  );
  assert.equal(
    result.replay.afterVersionCount,
    result.replay.firstVersionCount,
    "replay and conflict must not add versions",
  );
  assert.equal(
    result.replay.afterDedupCount,
    result.replay.firstDedupCount,
    "replay and conflict must not add dedup rows",
  );
  assert.equal(
    result.replay.afterVersionCount,
    1,
    "security scenario version count",
  );
  assert.equal(
    result.replay.afterDedupCount,
    1,
    "security scenario dedup count",
  );
  writeJson(output, {
    passed: true,
    runId,
    scenarioId,
    versionId: result.replay.first.data.id,
    versionCount: result.replay.afterVersionCount,
    dedupCount: result.replay.afterDedupCount,
    allowedChecks: 2,
    deniedChecks: 3,
    readOnlyExport,
  });
}

function validateComplexDatabase(input, runId, manifestInput, output) {
  const actual = requireObject(readJson(input), "complex database assertions");
  const manifest = requireObject(
    readJson(manifestInput),
    "normalized complex manifest",
  );
  assert.equal(actual.run_id, runId, "complex database run_id");
  assert.equal(actual.target_house_rows, 1, "complex target house rows");
  assert.equal(actual.isolation_house_rows, 1, "complex isolation house rows");
  assert.equal(actual.scenario_batch_rows, 6, "complex scenario batch rows");
  assert.equal(actual.fixture_user_rows, 3, "complex fixture user rows");
  assert.equal(actual.owner_memberships, 1, "complex owner memberships");
  assert.equal(
    actual.read_only_memberships,
    1,
    "complex read-only memberships",
  );
  assert.equal(
    actual.unrelated_memberships,
    1,
    "complex unrelated memberships",
  );
  assert.equal(
    actual.security_version_rows,
    1,
    "complex security version rows",
  );
  assert.equal(actual.security_dedup_rows, 1, "complex security dedup rows");
  assert.deepEqual(
    [...actual.batch_ids].sort((left, right) => left - right),
    manifest.scenarios
      .map((scenario) => scenario.batchId)
      .sort((left, right) => left - right),
    "complex database batch ids",
  );
  writeJson(output, actual);
}

function assertArtifactFile(root, relative, label) {
  const normalized = nonEmptyString(relative, label).replaceAll("\\", "/");
  assert.doesNotMatch(
    normalized,
    /(?:^|\/)\.\.(?:\/|$)/,
    `${label} traverses directories`,
  );
  const resolvedRoot = path.resolve(root);
  const resolved = path.resolve(root, normalized);
  assert.ok(
    resolved.startsWith(`${resolvedRoot}${path.sep}`),
    `${label} leaves artifact root`,
  );
  const stat = statSync(resolved);
  assert.ok(
    stat.isFile() && stat.size > 0,
    `${label} is missing or empty: ${resolved}`,
  );
  return normalized;
}

function expectedClientMetrics(expected) {
  return expected.metrics.map(
    ({ code, status, displayValue, missingCauses }) => ({
      code,
      status,
      displayValue,
      visibleValue:
        status === "AVAILABLE" ? displayValue : statusDisplayValues.get(status),
      missingCauses,
    }),
  );
}

function validateComplexClient(
  kind,
  input,
  runId,
  manifestInput,
  artifactRoot,
  output,
) {
  const result = requireObject(readJson(input), `${kind} result`);
  const manifest = requireObject(
    readJson(manifestInput),
    "normalized complex manifest",
  );
  assert.equal(result.suite, "complex", `${kind} suite`);
  const scenarios =
    kind === "admin" ? result.scenarios : result.scenarioResults;
  assert.ok(Array.isArray(scenarios), `${kind} scenario results`);
  assert.equal(scenarios.length, 6, `${kind} scenario count`);
  if (kind === "android") {
    assert.equal(result.runId, runId, "android runId");
    assert.equal(result.fixtureMutated, false, "android fixture mutation");
    assert.equal(
      Object.hasOwn(result, "screenshots"),
      false,
      "android result must not embed screenshots",
    );
  } else {
    assert.equal(result.passed, true, "admin passed");
    assert.equal(result.scenarioCount, 6, "admin scenarioCount");
    assert.equal(
      result.scenarioWorkbookCount,
      6,
      "admin scenarioWorkbookCount",
    );
    assert.equal(result.roleWorkbookCount, 1, "admin roleWorkbookCount");
    assert.equal(result.workbookCount, 7, "admin workbookCount");
  }

  const byId = new Map(scenarios.map((scenario) => [scenario.id, scenario]));
  let screenshotCount = 0;
  for (const expected of manifest.scenarios) {
    const scenario = requireObject(
      byId.get(expected.id),
      `${kind} missing scenario ${expected.id}`,
    );
    assert.equal(
      scenario.batchRole,
      expected.batchRole,
      `${kind}.${expected.id}.batchRole`,
    );
    assert.equal(
      scenario.batchId,
      expected.batchId,
      `${kind}.${expected.id}.batchId`,
    );
    assert.equal(
      scenario.batchCode,
      expected.batchCode,
      `${kind}.${expected.id}.batchCode`,
    );
    if (kind === "admin") {
      assert.deepEqual(
        scenario.metrics,
        expectedClientMetrics(expected),
        `${kind}.${expected.id}.metrics`,
      );
      const screenshots = requireObject(
        scenario.screenshots,
        `${kind}.${expected.id}.screenshots`,
      );
      for (const [name, relative] of Object.entries(screenshots)) {
        assertArtifactFile(
          artifactRoot,
          relative,
          `${kind}.${expected.id}.${name}`,
        );
        screenshotCount += 1;
      }
      assert.ok(
        Object.keys(screenshots).length >= 2,
        `${kind}.${expected.id} screenshot count`,
      );
      assertArtifactFile(
        artifactRoot,
        scenario.workbook?.file,
        `${kind}.${expected.id}.workbook`,
      );
    } else {
      assert.deepEqual(
        scenario.metricCodes,
        metricCodes,
        `${kind}.${expected.id}.metricCodes`,
      );
      assert.deepEqual(
        scenario.metricDisplayValues,
        Object.fromEntries(
          expected.metrics.map((metric) => [metric.code, metric.displayValue]),
        ),
        `${kind}.${expected.id}.metricDisplayValues`,
      );
      assert.deepEqual(
        scenario.metricVisibleValues,
        Object.fromEntries(
          expected.metrics.map((metric) => [
            metric.code,
            metric.status === "AVAILABLE"
              ? metric.displayValue
              : statusDisplayValues.get(metric.status),
          ]),
        ),
        `${kind}.${expected.id}.metricVisibleValues`,
      );
      assert.deepEqual(
        scenario.metricStatuses,
        Object.fromEntries(
          expected.metrics.map((metric) => [metric.code, metric.status]),
        ),
        `${kind}.${expected.id}.metricStatuses`,
      );
      assert.deepEqual(
        scenario.metricMissingCauses,
        Object.fromEntries(
          expected.metrics.map((metric) => [metric.code, metric.missingCauses]),
        ),
        `${kind}.${expected.id}.metricMissingCauses`,
      );
      assert.ok(
        Array.isArray(scenario.screenshotNames),
        `${kind}.${expected.id}.screenshotNames`,
      );
      const minimum = expected.batchRole === "primary" ? 8 : 1;
      assert.ok(
        scenario.screenshotNames.length >= minimum,
        `${kind}.${expected.id} screenshot count`,
      );
      for (const name of scenario.screenshotNames) {
        assert.match(
          name,
          /^[A-Za-z0-9][A-Za-z0-9._-]*$/,
          `${kind}.${expected.id} screenshot name`,
        );
        assertArtifactFile(
          path.join(artifactRoot, "scenarios", expected.id, "android"),
          `${name}.png`,
          `${kind}.${expected.id}.${name}`,
        );
        screenshotCount += 1;
      }
    }
  }

  let security;
  if (kind === "admin") {
    security = requireObject(result.roles, "admin roles");
    assert.equal(
      security.OWNER?.canEditCarcassYield,
      true,
      "admin owner edit action",
    );
    assert.equal(
      security.OWNER?.canReadCarcassYieldHistory,
      true,
      "admin owner history action",
    );
    assert.equal(security.OWNER?.canExport, true, "admin owner export action");
    assert.equal(
      security.READ_ONLY?.canEditCarcassYield,
      false,
      "admin read-only edit action",
    );
    assert.equal(
      security.READ_ONLY?.canReadCarcassYieldHistory,
      false,
      "admin read-only history action",
    );
    assert.equal(
      security.READ_ONLY?.canExport,
      true,
      "admin read-only export action",
    );
    const readOnlyWorkbook = requireObject(
      security.READ_ONLY?.workbook,
      "admin read-only workbook",
    );
    assert.equal(
      readOnlyWorkbook.mediaType,
      xlsxMediaType,
      "admin read-only workbook media type",
    );
    assert.equal(
      readOnlyWorkbook.zipSignature,
      "PK",
      "admin read-only workbook ZIP signature",
    );
    const readOnlyWorkbookFile = assertArtifactFile(
      artifactRoot,
      readOnlyWorkbook.file,
      "admin read-only workbook",
    );
    assert.equal(
      readFileSync(path.resolve(artifactRoot, readOnlyWorkbookFile))
        .subarray(0, 2)
        .toString("ascii"),
      "PK",
      "admin read-only workbook file signature",
    );
    assert.equal(
      security.OUTSIDER?.targetHouseVisible,
      false,
      "admin outsider target house",
    );
    assert.equal(
      security.OUTSIDER?.targetBatchesVisible,
      false,
      "admin outsider target batches",
    );
    for (const role of ["OWNER", "READ_ONLY", "OUTSIDER"]) {
      assertArtifactFile(
        artifactRoot,
        security[role].screenshot,
        `admin.${role}.screenshot`,
      );
      screenshotCount += 1;
    }
  } else {
    security = requireObject(
      result.securityEvidence,
      "android security evidence",
    );
    assert.equal(
      security.owner?.editVisible,
      true,
      "android owner edit action",
    );
    assert.equal(
      security.owner?.historyVisible,
      true,
      "android owner history action",
    );
    assert.equal(
      security.owner?.exportVisible,
      true,
      "android owner export action",
    );
    assert.equal(
      security.readOnly?.editVisible,
      false,
      "android read-only edit action",
    );
    assert.equal(
      security.readOnly?.historyVisible,
      false,
      "android read-only history action",
    );
    assert.equal(
      security.readOnly?.exportVisible,
      true,
      "android read-only export action",
    );
    assert.equal(
      security.readOnly?.xlsxDownloaded,
      true,
      "android read-only workbook download",
    );
    assert.ok(
      Number.isSafeInteger(security.readOnly?.xlsxByteLength) &&
        security.readOnly.xlsxByteLength > 0,
      "android read-only workbook byte length",
    );
    assert.equal(
      security.readOnly?.xlsxZipSignature,
      "PK",
      "android read-only workbook ZIP signature",
    );
    assert.equal(
      security.outsider?.accessDenied,
      true,
      "android outsider denial",
    );
    assert.equal(
      security.outsider?.targetDataVisible,
      false,
      "android outsider target data",
    );
    for (const entry of [security.readOnly, security.outsider]) {
      assertArtifactFile(
        path.join(artifactRoot, "roles", "android"),
        `${entry.screenshotName}.png`,
        "android security screenshot",
      );
    }
  }
  const validation = {
    passed: true,
    kind,
    runId,
    scenarioCount: scenarios.length,
    screenshotCount,
    security,
  };
  if (kind === "admin") {
    validation.scenarioWorkbookCount = result.scenarioWorkbookCount;
    validation.roleWorkbookCount = result.roleWorkbookCount;
    validation.workbookCount = result.workbookCount;
  }
  writeJson(output, validation);
}

function textFiles(root) {
  const files = [];
  const visit = (directory) => {
    for (const name of readdirSync(directory)) {
      const file = path.join(directory, name);
      const stat = statSync(file);
      if (stat.isDirectory()) visit(file);
      else if (
        !/\.(?:png|jpg|jpeg|gif|webp|xlsx|zip)$/i.test(name) &&
        name !== "SHA256SUMS"
      )
        files.push(file);
    }
  };
  visit(root);
  return files;
}

function validateComplexSecretScan(root, output) {
  let supplied = "";
  process.stdin.setEncoding("utf8");
  process.stdin.on("data", (chunk) => {
    supplied += chunk;
  });
  process.stdin.on("end", () => {
    let secrets;
    try {
      secrets = supplied.trim() ? JSON.parse(supplied) : [];
    } catch (error) {
      throw new Error("secret scan stdin is not valid JSON", { cause: error });
    }
    assert.ok(Array.isArray(secrets), "secret scan stdin must be a JSON array");
    const patterns = [
      /Bearer\s+[A-Za-z0-9._~-]+/i,
      /eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/,
      /(?:password|passwd|token|secret)\s*[:=]\s*["']?[^\s,"'}]+/i,
    ];
    const failures = [];
    const files = textFiles(root);
    for (const file of files) {
      const content = readFileSync(file, "utf8");
      if (patterns.some((pattern) => pattern.test(content)))
        failures.push(file);
      if (
        secrets.some(
          (secret) =>
            typeof secret === "string" && secret && content.includes(secret),
        )
      )
        failures.push(file);
    }
    assert.deepEqual(
      [...new Set(failures)],
      [],
      "text artifacts contain secret material",
    );
    writeJson(output, { passed: true, scannedTextFiles: files.length });
  });
}

const [mode, ...args] = process.argv.slice(2);
if (mode === "api" && args.length === 3) {
  validateApi(args[0], Number(args[1]), args[2]);
} else if (mode === "database" && args.length === 3) {
  validateDatabase(args[0], args[1], args[2]);
} else if (mode === "android" && args.length === 5) {
  validateAndroid(args[0], args[1], Number(args[2]), Number(args[3]), args[4]);
} else if (mode === "complex-manifest" && args.length === 4) {
  validateComplexManifest(args[0], args[1], args[2], args[3]);
} else if (mode === "complex-api" && args.length === 5) {
  validateComplexApi(args[0], args[1], Number(args[2]), args[3], args[4]);
} else if (mode === "complex-xlsx" && args.length === 6) {
  validateComplexXlsx(args[0], args[1], args[2], args[3], args[4], args[5]);
} else if (mode === "complex-security" && args.length === 6) {
  validateComplexSecurity(
    args[0],
    args[1],
    args[2],
    Number(args[3]),
    args[4],
    args[5],
  );
} else if (mode === "complex-database" && args.length === 4) {
  validateComplexDatabase(args[0], args[1], args[2], args[3]);
} else if (mode === "complex-admin" && args.length === 5) {
  validateComplexClient("admin", args[0], args[1], args[2], args[3], args[4]);
} else if (mode === "complex-android" && args.length === 5) {
  validateComplexClient("android", args[0], args[1], args[2], args[3], args[4]);
} else if (mode === "complex-secret-scan" && args.length === 2) {
  validateComplexSecretScan(args[0], args[1]);
} else {
  process.stderr.write(
    "Usage: batch-statistics-cross-client-validate.mjs " +
      "api <input> <batch-id> <output> | database <input> <run-id> <output> | " +
      "android <input> <run-id> <house-id> <batch-id> <output> | " +
      "complex-manifest <input> <catalog> <run-id> <output> | " +
      "complex-api <input> <scenario-id> <batch-id> <catalog> <output> | " +
      "complex-xlsx <xlsx> <headers> <scenario-id> <batch-code> <catalog> <output> | " +
      "complex-security <input> <run-id> <scenario-id> <house-id> <artifact-root> <output> | " +
      "complex-database <input> <run-id> <manifest> <output> | " +
      "complex-admin|complex-android <input> <run-id> <manifest> <artifact-root> <output> | " +
      "complex-secret-scan <artifact-root> <output>\n",
  );
  process.exit(64);
}
