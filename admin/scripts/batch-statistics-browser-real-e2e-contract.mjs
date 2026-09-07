const PRIMARY_SCENARIO_IDS = [
  "complex-available",
  "mixed-data-quality",
  "mixed-batch-rounding",
  "time-and-cycle-boundaries",
  "security-and-retry",
];

const SUPPORT_SCENARIO_ID = "mixed-batch-rounding-support";

const METRIC_CODES = [
  "MATING_DATE",
  "MATED_DOE_COUNT",
  "CONCEPTION_RATE",
  "DOE_BUCK_RATIO",
  "PREGNANT_DOE_COUNT",
  "ABORTION_RATE",
  "DELIVERED_LITTER_COUNT",
  "TOTAL_KIT_COUNT",
  "AVERAGE_KITS_PER_LITTER",
  "LIVE_KIT_COUNT",
  "LIVE_BIRTH_RATE",
  "KEPT_LITTER_COUNT",
  "KEPT_KIT_COUNT",
  "KEPT_LIVE_RATE",
  "AVERAGE_KEPT_PER_LITTER",
  "WEANED_KIT_COUNT",
  "AVERAGE_WEANING_WEIGHT",
  "WEANING_SURVIVAL_RATE",
  "SOLD_RABBIT_COUNT",
  "OUTBOUND_SURVIVAL_RATE",
  "SOLD_WEIGHT",
  "AVERAGE_SOLD_WEIGHT",
  "TOTAL_SALES_AMOUNT",
  "SALES_PRICE_PER_KG",
  "SALES_PRICE_PER_RABBIT",
  "FULL_FEED_CONVERSION_RATIO",
  "FATTENING_FEED_CONVERSION_RATIO",
  "CARCASS_YIELD_RATE",
];

const STATUSES = new Set([
  "AVAILABLE",
  "NOT_APPLICABLE",
  "NOT_RECORDED",
  "DATA_MISSING",
]);
const USER_ROLES = ["OWNER", "READ_ONLY", "OUTSIDER"];

export const COMPLEX_SCENARIO_IDS = Object.freeze([
  ...PRIMARY_SCENARIO_IDS,
  SUPPORT_SCENARIO_ID,
]);
export const COMPLEX_PRIMARY_SCENARIO_IDS = Object.freeze([
  ...PRIMARY_SCENARIO_IDS,
]);
export const COMPLEX_SUPPORT_SCENARIO_ID = SUPPORT_SCENARIO_ID;
export const FIXED_METRIC_CODES = Object.freeze([...METRIC_CODES]);

function fail(path, message) {
  throw new Error(`${path} ${message}`);
}

function objectAt(value, path) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    fail(path, "must be a JSON object");
  }
  return value;
}

function arrayAt(value, path) {
  if (!Array.isArray(value)) fail(path, "must be a JSON array");
  return value;
}

function stringAt(value, path) {
  if (typeof value !== "string" || !value.trim()) {
    fail(path, "must be a non-empty string");
  }
  return value.trim();
}

function positiveIntegerAt(value, path) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    fail(path, "must be a positive integer");
  }
  return parsed;
}

function parseJsonString(value, path) {
  if (typeof value !== "string") fail(path, "must be a JSON string scalar");
  try {
    return JSON.parse(value);
  } catch (error) {
    fail(path, `must contain valid JSON (${error.message})`);
  }
}

function validateMissingCauses(value, path) {
  const causes = arrayAt(value, path);
  return causes.map((rawCause, index) => {
    const causePath = `${path}[${index}]`;
    const cause = objectAt(rawCause, causePath);
    return {
      code: stringAt(cause.code, `${causePath}.code`),
      message: stringAt(cause.message, `${causePath}.message`),
    };
  });
}

function validateMetric(rawMetric, path, expectedCode) {
  const metric = objectAt(rawMetric, path);
  const code = stringAt(metric.code, `${path}.code`);
  if (code !== expectedCode) {
    fail(`${path}.code`, `must be ${expectedCode}, received ${code}`);
  }
  const status = stringAt(metric.status, `${path}.status`);
  if (!STATUSES.has(status)) {
    fail(`${path}.status`, `must be one of ${[...STATUSES].join(", ")}`);
  }
  if (status === "AVAILABLE") {
    stringAt(metric.displayValue, `${path}.displayValue`);
  } else if (metric.displayValue !== null) {
    fail(`${path}.displayValue`, `must be null when status is ${status}`);
  }
  const missingCauses = validateMissingCauses(
    metric.missingCauses,
    `${path}.missingCauses`,
  );
  if (status === "AVAILABLE" && missingCauses.length !== 0) {
    fail(`${path}.missingCauses`, "must be empty when status is AVAILABLE");
  }
  if (status !== "AVAILABLE" && missingCauses.length === 0) {
    fail(`${path}.missingCauses`, `must not be empty when status is ${status}`);
  }
  return {
    code,
    status,
    displayValue: metric.displayValue,
    missingCauses,
  };
}

function validateScenario(rawScenario, index) {
  const path = `RABBIT_E2E_SCENARIOS_JSON[${index}]`;
  const scenario = objectAt(rawScenario, path);
  const id = stringAt(scenario.id, `${path}.id`);
  if (!COMPLEX_SCENARIO_IDS.includes(id)) {
    fail(`${path}.id`, `must be one of ${COMPLEX_SCENARIO_IDS.join(", ")}`);
  }
  const batchRole = stringAt(scenario.batchRole, `${path}.batchRole`);
  const expectedRole = id === SUPPORT_SCENARIO_ID ? "support" : "primary";
  if (batchRole !== expectedRole) {
    fail(`${path}.batchRole`, `must be ${expectedRole} for ${id}`);
  }
  const metrics = arrayAt(scenario.metrics, `${path}.metrics`);
  if (metrics.length !== METRIC_CODES.length) {
    fail(
      `${path}.metrics`,
      `must contain exactly ${METRIC_CODES.length} metrics`,
    );
  }
  return {
    id,
    batchRole,
    batchId: positiveIntegerAt(scenario.batchId, `${path}.batchId`),
    batchCode: stringAt(scenario.batchCode, `${path}.batchCode`),
    metrics: metrics.map((metric, metricIndex) =>
      validateMetric(
        metric,
        `${path}.metrics[${metricIndex}]`,
        METRIC_CODES[metricIndex],
      ),
    ),
  };
}

function validateScenarios(rawScenarios) {
  const scenarios = arrayAt(rawScenarios, "RABBIT_E2E_SCENARIOS_JSON");
  if (scenarios.length !== COMPLEX_SCENARIO_IDS.length) {
    fail(
      "RABBIT_E2E_SCENARIOS_JSON",
      `must contain exactly ${COMPLEX_SCENARIO_IDS.length} scenarios`,
    );
  }
  const validated = scenarios.map(validateScenario);
  const ids = validated.map((scenario) => scenario.id);
  if (new Set(ids).size !== ids.length) {
    fail("RABBIT_E2E_SCENARIOS_JSON", "contains duplicate scenario IDs");
  }
  for (const id of COMPLEX_SCENARIO_IDS) {
    if (!ids.includes(id)) {
      fail("RABBIT_E2E_SCENARIOS_JSON", `is missing scenario ${id}`);
    }
  }
  const batchIds = validated.map((scenario) => scenario.batchId);
  if (new Set(batchIds).size !== batchIds.length) {
    fail("RABBIT_E2E_SCENARIOS_JSON", "contains duplicate batch IDs");
  }
  const batchCodes = validated.map((scenario) => scenario.batchCode);
  if (new Set(batchCodes).size !== batchCodes.length) {
    fail("RABBIT_E2E_SCENARIOS_JSON", "contains duplicate batch codes");
  }
  return COMPLEX_SCENARIO_IDS.map((id) =>
    validated.find((scenario) => scenario.id === id),
  );
}

function validateUser(rawUser, index, targetHouseId) {
  const path = `RABBIT_E2E_USERS_JSON[${index}]`;
  const user = objectAt(rawUser, path);
  if (Object.hasOwn(user, "username")) {
    fail(`${path}.username`, "must not be present; use userName");
  }
  const role = stringAt(user.role, `${path}.role`);
  if (!USER_ROLES.includes(role)) {
    fail(`${path}.role`, `must be one of ${USER_ROLES.join(", ")}`);
  }
  const houseId = positiveIntegerAt(user.houseId, `${path}.houseId`);
  if (role === "OUTSIDER" && houseId === targetHouseId) {
    fail(`${path}.houseId`, "must differ from the target house for OUTSIDER");
  }
  if (role !== "OUTSIDER" && houseId !== targetHouseId) {
    fail(
      `${path}.houseId`,
      `must equal target house ${targetHouseId} for ${role}`,
    );
  }
  return {
    role,
    userName: stringAt(user.userName, `${path}.userName`),
    password: stringAt(user.password, `${path}.password`),
    houseId,
    houseName: stringAt(user.houseName, `${path}.houseName`),
  };
}

function validateUsers(rawUsers, targetHouseId, targetHouseName) {
  const users = arrayAt(rawUsers, "RABBIT_E2E_USERS_JSON");
  if (users.length !== USER_ROLES.length) {
    fail(
      "RABBIT_E2E_USERS_JSON",
      `must contain exactly ${USER_ROLES.length} users`,
    );
  }
  const validated = users.map((user, index) =>
    validateUser(user, index, targetHouseId),
  );
  const roles = validated.map((user) => user.role);
  if (new Set(roles).size !== roles.length) {
    fail("RABBIT_E2E_USERS_JSON", "contains duplicate roles");
  }
  for (const role of USER_ROLES) {
    if (!roles.includes(role)) {
      fail("RABBIT_E2E_USERS_JSON", `is missing role ${role}`);
    }
  }
  for (const user of validated) {
    if (user.role !== "OUTSIDER" && user.houseName !== targetHouseName) {
      fail(
        `RABBIT_E2E_USERS_JSON.${user.role}.houseName`,
        `must equal target house name ${targetHouseName}`,
      );
    }
  }
  const userNames = validated.map((user) => user.userName);
  if (new Set(userNames).size !== userNames.length) {
    fail("RABBIT_E2E_USERS_JSON", "contains duplicate userName values");
  }
  return Object.fromEntries(validated.map((user) => [user.role, user]));
}

export function validateComplexDefines(input) {
  const values = objectAt(input, "complex defines");
  for (const [key, value] of Object.entries(values)) {
    if (
      value !== null &&
      !["string", "number", "boolean"].includes(typeof value)
    ) {
      fail(`complex defines.${key}`, "must be a scalar JSON value");
    }
  }
  const suite = stringAt(values.RABBIT_E2E_SUITE, "RABBIT_E2E_SUITE");
  if (suite !== "complex") fail("RABBIT_E2E_SUITE", "must be complex");
  const houseId = positiveIntegerAt(
    values.RABBIT_E2E_HOUSE_ID,
    "RABBIT_E2E_HOUSE_ID",
  );
  const houseName = stringAt(
    values.RABBIT_E2E_HOUSE_NAME,
    "RABBIT_E2E_HOUSE_NAME",
  );
  const scenarios = validateScenarios(
    parseJsonString(
      values.RABBIT_E2E_SCENARIOS_JSON,
      "RABBIT_E2E_SCENARIOS_JSON",
    ),
  );
  const users = validateUsers(
    parseJsonString(values.RABBIT_E2E_USERS_JSON, "RABBIT_E2E_USERS_JSON"),
    houseId,
    houseName,
  );
  return { suite, houseId, houseName, scenarios, users };
}
