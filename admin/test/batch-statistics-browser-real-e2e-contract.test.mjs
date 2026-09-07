import assert from "node:assert/strict";
import test from "node:test";
import {
  COMPLEX_SCENARIO_IDS,
  FIXED_METRIC_CODES,
  validateComplexDefines,
} from "../scripts/batch-statistics-browser-real-e2e-contract.mjs";

function availableMetrics() {
  return FIXED_METRIC_CODES.map((code) => ({
    code,
    status: "AVAILABLE",
    displayValue: code === "MATING_DATE" ? "2024-04-01" : "1",
    missingCauses: [],
  }));
}

function validDefines() {
  const scenarios = COMPLEX_SCENARIO_IDS.map((id, index) => ({
    id,
    batchRole: id === "mixed-batch-rounding-support" ? "support" : "primary",
    batchId: 100 + index,
    batchCode: `BSX-${id}-${index}`,
    metrics: availableMetrics(),
  }));
  const users = [
    {
      role: "OWNER",
      userName: "complex-owner",
      password: "owner-fixture-password",
      houseId: 41,
      houseName: "Complex target house",
    },
    {
      role: "READ_ONLY",
      userName: "complex-reader",
      password: "reader-fixture-password",
      houseId: 41,
      houseName: "Complex target house",
    },
    {
      role: "OUTSIDER",
      userName: "complex-outsider",
      password: "outsider-fixture-password",
      houseId: 42,
      houseName: "Complex isolation house",
    },
  ];
  return {
    RABBIT_BUILD_ENV: "dev",
    RABBIT_E2E_SUITE: "complex",
    RABBIT_E2E_HOUSE_ID: 41,
    RABBIT_E2E_HOUSE_NAME: "Complex target house",
    RABBIT_E2E_SCENARIOS_JSON: JSON.stringify(scenarios),
    RABBIT_E2E_USERS_JSON: JSON.stringify(users),
  };
}

function mutateScenarios(defines, change) {
  const scenarios = JSON.parse(defines.RABBIT_E2E_SCENARIOS_JSON);
  change(scenarios);
  defines.RABBIT_E2E_SCENARIOS_JSON = JSON.stringify(scenarios);
}

test("validates the scalar complex defines contract", () => {
  const config = validateComplexDefines(validDefines());

  assert.equal(config.suite, "complex");
  assert.equal(config.houseId, 41);
  assert.equal(config.scenarios.length, 6);
  assert.deepEqual(
    config.scenarios.map((scenario) => scenario.id),
    [...COMPLEX_SCENARIO_IDS],
  );
  assert.deepEqual(Object.keys(config.users), [
    "OWNER",
    "READ_ONLY",
    "OUTSIDER",
  ]);
  assert.equal(config.users.OWNER.userName, "complex-owner");
  assert.equal(config.users.OWNER.password, "owner-fixture-password");
});

test("rejects the legacy username alias in the complex users contract", () => {
  const defines = validDefines();
  const users = JSON.parse(defines.RABBIT_E2E_USERS_JSON);
  users[0].username = users[0].userName;
  delete users[0].userName;
  defines.RABBIT_E2E_USERS_JSON = JSON.stringify(users);

  assert.throws(
    () => validateComplexDefines(defines),
    /RABBIT_E2E_USERS_JSON\[0\]\.username must not be present; use userName/,
  );
});

test("rejects nested top-level values instead of accepting a non-scalar file", () => {
  const defines = validDefines();
  defines.RABBIT_E2E_SCENARIOS_JSON = [];

  assert.throws(
    () => validateComplexDefines(defines),
    /complex defines\.RABBIT_E2E_SCENARIOS_JSON must be a scalar JSON value/,
  );
});

test("reports malformed scenario JSON at the defines key", () => {
  const defines = validDefines();
  defines.RABBIT_E2E_SCENARIOS_JSON = "[{";

  assert.throws(
    () => validateComplexDefines(defines),
    /RABBIT_E2E_SCENARIOS_JSON must contain valid JSON/,
  );
});

test("rejects reordered fixed metrics with the exact failing path", () => {
  const defines = validDefines();
  mutateScenarios(defines, (scenarios) => {
    [scenarios[0].metrics[0], scenarios[0].metrics[1]] = [
      scenarios[0].metrics[1],
      scenarios[0].metrics[0],
    ];
  });

  assert.throws(
    () => validateComplexDefines(defines),
    /RABBIT_E2E_SCENARIOS_JSON\[0\]\.metrics\[0\]\.code must be MATING_DATE/,
  );
});

test("requires null display values and causes for unavailable metrics", () => {
  const defines = validDefines();
  mutateScenarios(defines, (scenarios) => {
    scenarios[1].metrics[3] = {
      code: "DOE_BUCK_RATIO",
      status: "NOT_APPLICABLE",
      displayValue: "暂无可计算数据",
      missingCauses: [],
    };
  });

  assert.throws(
    () => validateComplexDefines(defines),
    /metrics\[3\]\.displayValue must be null when status is NOT_APPLICABLE/,
  );
});

test("requires one named rounding support batch", () => {
  const defines = validDefines();
  mutateScenarios(defines, (scenarios) => {
    scenarios.at(-1).id = "rounding-helper";
  });

  assert.throws(
    () => validateComplexDefines(defines),
    /\.id must be one of .*mixed-batch-rounding-support/,
  );
});

test("requires owner and read-only users in the target house and outsider elsewhere", () => {
  const defines = validDefines();
  const users = JSON.parse(defines.RABBIT_E2E_USERS_JSON);
  users[2].houseId = 41;
  defines.RABBIT_E2E_USERS_JSON = JSON.stringify(users);

  assert.throws(
    () => validateComplexDefines(defines),
    /houseId must differ from the target house for OUTSIDER/,
  );
});
