#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { createServer } from "node:net";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";
import {
  COMPLEX_PRIMARY_SCENARIO_IDS,
  COMPLEX_SUPPORT_SCENARIO_ID,
  FIXED_METRIC_CODES,
  validateComplexDefines,
} from "./batch-statistics-browser-real-e2e-contract.mjs";

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const ADMIN_DIR = path.resolve(SCRIPT_DIR, "..");
const DEFAULT_API_HOST = "127.0.0.1";
const DEFAULT_API_PORT = 8080;
const DESKTOP = { width: 1440, height: 900 };
const XLSX_MEDIA_TYPE =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

const EXPECTED_METRICS = [
  ["MATING_DATE", "2024-04-22"],
  ["MATED_DOE_COUNT", "1,230"],
  ["CONCEPTION_RATE", "86.10%"],
  ["DOE_BUCK_RATIO", "20.50:1"],
  ["PREGNANT_DOE_COUNT", "1,059"],
  ["ABORTION_RATE", "1.98%"],
  ["DELIVERED_LITTER_COUNT", "1,004"],
  ["TOTAL_KIT_COUNT", "10,040"],
  ["AVERAGE_KITS_PER_LITTER", "10.00"],
  ["LIVE_KIT_COUNT", "9,870"],
  ["LIVE_BIRTH_RATE", "98.31%"],
  ["KEPT_LITTER_COUNT", "987"],
  ["KEPT_KIT_COUNT", "9,490"],
  ["KEPT_LIVE_RATE", "96.15%"],
  ["AVERAGE_KEPT_PER_LITTER", "9.61"],
  ["WEANED_KIT_COUNT", "8,604"],
  ["AVERAGE_WEANING_WEIGHT", "0.74 kg"],
  ["WEANING_SURVIVAL_RATE", "90.66%"],
  ["SOLD_RABBIT_COUNT", "6,834"],
  ["OUTBOUND_SURVIVAL_RATE", "79.43%"],
  ["SOLD_WEIGHT", "13,095.00 kg"],
  ["AVERAGE_SOLD_WEIGHT", "1.92 kg"],
  ["TOTAL_SALES_AMOUNT", "157,140.00 元"],
  ["SALES_PRICE_PER_KG", "12.00 元/kg"],
  ["SALES_PRICE_PER_RABBIT", "22.99 元/只"],
  ["FULL_FEED_CONVERSION_RATIO", "3.68"],
  ["FATTENING_FEED_CONVERSION_RATIO", "3.84"],
  ["CARCASS_YIELD_RATE", "56.00%"],
];

const EXPECTED_GROUPS = [
  [
    "配种",
    ["MATING_DATE", "MATED_DOE_COUNT", "CONCEPTION_RATE", "DOE_BUCK_RATIO"],
  ],
  ["怀孕", ["PREGNANT_DOE_COUNT", "ABORTION_RATE"]],
  [
    "产崽",
    [
      "DELIVERED_LITTER_COUNT",
      "TOTAL_KIT_COUNT",
      "AVERAGE_KITS_PER_LITTER",
      "LIVE_KIT_COUNT",
      "LIVE_BIRTH_RATE",
    ],
  ],
  [
    "选留",
    [
      "KEPT_LITTER_COUNT",
      "KEPT_KIT_COUNT",
      "KEPT_LIVE_RATE",
      "AVERAGE_KEPT_PER_LITTER",
    ],
  ],
  [
    "断奶",
    ["WEANED_KIT_COUNT", "AVERAGE_WEANING_WEIGHT", "WEANING_SURVIVAL_RATE"],
  ],
  [
    "出栏",
    [
      "SOLD_RABBIT_COUNT",
      "OUTBOUND_SURVIVAL_RATE",
      "SOLD_WEIGHT",
      "AVERAGE_SOLD_WEIGHT",
    ],
  ],
  [
    "销售",
    ["TOTAL_SALES_AMOUNT", "SALES_PRICE_PER_KG", "SALES_PRICE_PER_RABBIT"],
  ],
  [
    "料肉比",
    [
      "FULL_FEED_CONVERSION_RATIO",
      "FATTENING_FEED_CONVERSION_RATIO",
      "CARCASS_YIELD_RATE",
    ],
  ],
];

const EXPECTED_ROWS = [
  ["MATING_DATE"],
  ["MATED_DOE_COUNT", "CONCEPTION_RATE"],
  ["DOE_BUCK_RATIO"],
  ["PREGNANT_DOE_COUNT", "ABORTION_RATE"],
  ["DELIVERED_LITTER_COUNT", "TOTAL_KIT_COUNT"],
  ["AVERAGE_KITS_PER_LITTER"],
  ["LIVE_KIT_COUNT", "LIVE_BIRTH_RATE"],
  ["KEPT_LITTER_COUNT", "KEPT_KIT_COUNT"],
  ["KEPT_LIVE_RATE", "AVERAGE_KEPT_PER_LITTER"],
  ["WEANED_KIT_COUNT"],
  ["AVERAGE_WEANING_WEIGHT", "WEANING_SURVIVAL_RATE"],
  ["SOLD_RABBIT_COUNT", "OUTBOUND_SURVIVAL_RATE"],
  ["SOLD_WEIGHT", "AVERAGE_SOLD_WEIGHT"],
  ["TOTAL_SALES_AMOUNT", "SALES_PRICE_PER_KG", "SALES_PRICE_PER_RABBIT"],
  ["FULL_FEED_CONVERSION_RATIO", "FATTENING_FEED_CONVERSION_RATIO"],
  ["CARCASS_YIELD_RATE"],
];

function environmentValue(...names) {
  for (const name of names) {
    const value = process.env[name]?.trim();
    if (value) return value;
  }
  throw new Error(`Missing required environment value: ${names.join(" or ")}`);
}

function fixtureValues() {
  const file = process.env.RABBIT_E2E_DEFINES_FILE?.trim();
  if (!file) return {};
  let values;
  try {
    values = JSON.parse(readFileSync(file, "utf8"));
  } catch (error) {
    throw new Error(`Cannot read RABBIT_E2E_DEFINES_FILE: ${file}`, {
      cause: error,
    });
  }
  assert.ok(
    values && typeof values === "object" && !Array.isArray(values),
    "RABBIT_E2E_DEFINES_FILE must contain a JSON object",
  );
  return values;
}

function fixtureValue(values, name, ...fallbackNames) {
  const value = values[name];
  if (
    (typeof value === "string" || typeof value === "number") &&
    String(value).trim()
  ) {
    return String(value).trim();
  }
  return environmentValue(name, ...fallbackNames);
}

function positiveInteger(value, name) {
  const parsed = Number(value);
  assert.ok(
    Number.isSafeInteger(parsed) && parsed > 0,
    `${name} must be a positive integer`,
  );
  return parsed;
}

function parseUrl(value, name) {
  try {
    return new URL(value);
  } catch (error) {
    throw new Error(`${name} is not a valid URL`, { cause: error });
  }
}

function baseUrl(value, name) {
  const parsed = parseUrl(value, name);
  assert.ok(
    parsed.protocol === "http:" || parsed.protocol === "https:",
    `${name} must use http or https`,
  );
  assert.equal(parsed.username, "", `${name} must not include credentials`);
  assert.equal(parsed.password, "", `${name} must not include credentials`);
  assert.equal(parsed.pathname, "/", `${name} must not include a path`);
  assert.equal(parsed.search, "", `${name} must not include a query`);
  assert.equal(parsed.hash, "", `${name} must not include a fragment`);
  return parsed.toString().replace(/\/$/, "");
}

function artifactDirectory() {
  const configured =
    process.env.RABBIT_ADMIN_E2E_ARTIFACT_DIR?.trim() ||
    process.env.RABBIT_E2E_ARTIFACT_DIR?.trim();
  if (!configured) {
    return path.join(ADMIN_DIR, "build/browser-e2e/batch-statistics-real");
  }
  return path.isAbsolute(configured)
    ? configured
    : path.resolve(ADMIN_DIR, configured);
}

async function freePort() {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const { port } = server.address();
      server.close((error) => (error ? reject(error) : resolve(port)));
    });
  });
}

async function servesThisAdmin(url) {
  try {
    const response = await fetch(url);
    const content = await response.text();
    return response.ok && content.includes("/src/main.tsx");
  } catch {
    return false;
  }
}

async function waitForAdmin(url, server, log) {
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    if (server.exitCode !== null) {
      throw new Error(`Vite exited before becoming available\n${log.join("")}`);
    }
    if (await servesThisAdmin(url)) return;
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  throw new Error(
    `Vite did not become available within 60 seconds\n${log.join("")}`,
  );
}

async function stopDevServer(server) {
  if (server.exitCode !== null || server.signalCode !== null) return;
  const exited = new Promise((resolve) => server.once("exit", resolve));
  server.kill("SIGTERM");
  await Promise.race([
    exited,
    new Promise((resolve) => setTimeout(resolve, 5_000)),
  ]);
  if (server.exitCode === null && server.signalCode === null) {
    server.kill("SIGKILL");
    await exited;
  }
}

async function checkBackend(apiUrl) {
  const response = await fetch(`${apiUrl}/api/auth/captcha`);
  assert.ok(
    response.ok,
    `Backend captcha check failed with HTTP ${response.status}`,
  );
  const payload = await response.json();
  assert.equal(
    payload.code,
    501,
    "The real browser fixture requires image captcha to be disabled",
  );
}

async function assertStatisticsResponse(response, batchId) {
  const payload = await response.json();
  assert.equal(payload.code, 0, "Statistics response business code");
  assert.equal(payload.data?.batchId, batchId, "Statistics response batch ID");
  assert.ok(payload.data?.houseName, "Statistics response house name");
  assert.deepEqual(
    payload.data.metrics.map((metric) => [
      metric.code,
      metric.status,
      metric.displayValue,
    ]),
    EXPECTED_METRICS.map(([code, displayValue]) => [
      code,
      "AVAILABLE",
      displayValue,
    ]),
    "The real API must return exactly 28 AVAILABLE metrics and approved display values",
  );
  return payload.data;
}

async function assertMetricLayout(page) {
  const panel = page.getByTestId("batch-statistics-panel");
  const metricCodes = await panel
    .locator("[data-metric-code]")
    .evaluateAll((elements) =>
      elements.map((element) => element.dataset.metricCode),
    );
  assert.deepEqual(
    metricCodes,
    EXPECTED_METRICS.map(([code]) => code),
    "The page must render exactly the 28 fixed metrics in order",
  );

  for (const [code, expectedValue] of EXPECTED_METRICS) {
    const item = panel.locator(`[data-metric-item="${code}"]`);
    assert.equal(await item.count(), 1, `${code} must render once`);
    assert.equal(
      (
        await item.locator(`[data-metric-code="${code}"]`).textContent()
      )?.trim(),
      expectedValue,
      `${code} display value`,
    );
    assert.equal(
      await item.getByText("数据可用", { exact: true }).count(),
      1,
      `${code} must be AVAILABLE`,
    );
  }

  const groups = panel.locator(":scope > section");
  assert.equal(await groups.count(), EXPECTED_GROUPS.length);
  for (let index = 0; index < EXPECTED_GROUPS.length; index += 1) {
    const [expectedName, expectedCodes] = EXPECTED_GROUPS[index];
    const group = groups.nth(index);
    assert.equal(
      (await group.getByRole("heading", { level: 2 }).textContent())?.trim(),
      expectedName,
      `metric group ${index + 1}`,
    );
    assert.deepEqual(
      await group
        .locator("[data-metric-code]")
        .evaluateAll((elements) =>
          elements.map((element) => element.dataset.metricCode),
        ),
      expectedCodes,
      `${expectedName} metric order`,
    );
  }

  const rows = panel.locator("[data-metric-row]");
  assert.deepEqual(
    await rows.evaluateAll((elements) =>
      elements.map((element) =>
        element.getAttribute("data-metric-row")?.split(" "),
      ),
    ),
    EXPECTED_ROWS,
    "The desktop layout must retain all 16 approved rows",
  );
  await assertSalesRow(rows.nth(13));
}

async function assertSalesRow(row) {
  const expectedCodes = EXPECTED_ROWS[13];
  const layout = await row.evaluate((element) => {
    const items = [...element.querySelectorAll(":scope > [data-metric-item]")];
    return {
      columns: getComputedStyle(element)
        .gridTemplateColumns.split(" ")
        .filter(Boolean).length,
      items: items.map((item) => {
        const rect = item.getBoundingClientRect();
        return {
          code: item.getAttribute("data-metric-item"),
          left: rect.left,
          right: rect.right,
          top: rect.top,
          bottom: rect.bottom,
          overflow:
            item.scrollWidth > item.clientWidth + 1 ||
            item.scrollHeight > item.clientHeight + 1,
        };
      }),
    };
  });
  assert.equal(layout.columns, 3, "Sales row 14 must have three columns");
  assert.deepEqual(
    layout.items.map((item) => item.code),
    expectedCodes,
    "Sales row 14 must contain three independent metrics",
  );
  assert.ok(
    layout.items.every((item) => !item.overflow),
    "Sales metrics must not overflow",
  );
  assert.ok(
    layout.items.every((item) => Math.abs(item.top - layout.items[0].top) <= 1),
    "Sales metrics must share row 14",
  );
  for (let left = 0; left < layout.items.length; left += 1) {
    for (let right = left + 1; right < layout.items.length; right += 1) {
      const a = layout.items[left];
      const b = layout.items[right];
      const overlap =
        a.left < b.right - 1 &&
        a.right > b.left + 1 &&
        a.top < b.bottom - 1 &&
        a.bottom > b.top + 1;
      assert.equal(overlap, false, `${a.code} overlaps ${b.code}`);
    }
  }
}

async function assertNoOverflow(page) {
  const overflow = await page.evaluate(() => ({
    document:
      document.documentElement.scrollWidth -
      document.documentElement.clientWidth,
    metricItems: [...document.querySelectorAll("[data-metric-item]")]
      .filter(
        (element) =>
          element.scrollWidth > element.clientWidth + 1 ||
          element.scrollHeight > element.clientHeight + 1,
      )
      .map((element) => element.getAttribute("data-metric-item")),
  }));
  assert.ok(
    overflow.document <= 1,
    `Page has ${overflow.document}px horizontal overflow`,
  );
  assert.deepEqual(overflow.metricItems, [], "Metric items must not overflow");
}

function assertProxiedRequest(request, adminUrl, houseId) {
  const url = parseUrl(request.url(), "Request URL");
  assert.equal(
    url.origin,
    parseUrl(adminUrl, "Admin URL").origin,
    `${url.pathname} must use the Vite /api proxy`,
  );
  assert.equal(request.headers()["x-house-id"], String(houseId));
  assert.match(request.headers().authorization ?? "", /^Bearer .+/);
}

const STATUS_LABELS = {
  AVAILABLE: "数据可用",
  NOT_APPLICABLE: "暂无可计算数据",
  NOT_RECORDED: "未录入",
  DATA_MISSING: "历史数据缺失",
};

function fixtureSuite(fixture) {
  const value =
    fixture.RABBIT_E2E_SUITE ?? process.env.RABBIT_E2E_SUITE ?? "baseline";
  assert.ok(
    typeof value === "string" || typeof value === "number",
    "RABBIT_E2E_SUITE must be a scalar value",
  );
  const suite = String(value).trim();
  assert.ok(
    suite === "baseline" || suite === "complex",
    "RABBIT_E2E_SUITE must be baseline or complex",
  );
  return suite;
}

function assertComplexDefinesMode() {
  const file = process.env.RABBIT_E2E_DEFINES_FILE?.trim();
  assert.ok(file, "Complex mode requires RABBIT_E2E_DEFINES_FILE");
  assert.equal(
    statSync(file).mode & 0o077,
    0,
    "Complex RABBIT_E2E_DEFINES_FILE must have mode 0600",
  );
}

function expectedUiValue(metric) {
  return metric.status === "AVAILABLE"
    ? metric.displayValue
    : STATUS_LABELS[metric.status];
}

function writeJson(file, value) {
  writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
}

function artifactPath(artifactDir, file) {
  return path.relative(artifactDir, file).split(path.sep).join("/");
}

async function assertComplexStatisticsResponse(response, scenario) {
  const payload = await response.json();
  assert.equal(payload.code, 0, `${scenario.id} statistics business code`);
  assert.equal(
    payload.data?.batchId,
    scenario.batchId,
    `${scenario.id} statistics batch ID`,
  );
  assert.equal(
    payload.data?.batchCode,
    scenario.batchCode,
    `${scenario.id} statistics batch code`,
  );
  assert.ok(
    Array.isArray(payload.data?.metrics),
    `${scenario.id} statistics metrics must be an array`,
  );
  assert.deepEqual(
    payload.data.metrics.map((metric) => ({
      code: metric.code,
      status: metric.status,
      displayValue: metric.displayValue,
      missingCauses: metric.missingCauses?.map((cause) => ({
        code: cause.code,
        message: cause.message,
      })),
    })),
    scenario.metrics,
    `${scenario.id} API metrics must match the frozen complex fixture`,
  );
  return payload.data;
}

async function assertComplexMetricLayout(page, scenario) {
  const panel = page.getByTestId("batch-statistics-panel");
  const metricCodes = await panel
    .locator("[data-metric-code]")
    .evaluateAll((elements) =>
      elements.map((element) => element.dataset.metricCode),
    );
  assert.deepEqual(
    metricCodes,
    FIXED_METRIC_CODES,
    `${scenario.id} must render the 28 fixed metrics in order`,
  );

  const metricEvidence = [];
  for (const expected of scenario.metrics) {
    const item = panel.locator(`[data-metric-item="${expected.code}"]`);
    assert.equal(
      await item.count(),
      1,
      `${scenario.id} ${expected.code} count`,
    );
    const displayValue = (
      await item.locator(`[data-metric-code="${expected.code}"]`).textContent()
    )?.trim();
    assert.equal(
      displayValue,
      expectedUiValue(expected),
      `${scenario.id} ${expected.code} UI value`,
    );
    const statusLabel = STATUS_LABELS[expected.status];
    // Unavailable metrics repeat the state in the badge and primary value.
    const expectedStatusTextCount = expected.status === "AVAILABLE" ? 1 : 2;
    assert.equal(
      await item.getByText(statusLabel, { exact: true }).count(),
      expectedStatusTextCount,
      `${scenario.id} ${expected.code} status`,
    );
    const missingCauses = await item
      .locator("details li")
      .evaluateAll((elements) =>
        elements
          .map((element) => element.textContent?.trim() ?? "")
          .filter((text) => /（[^（）]+）$/.test(text)),
      );
    const expectedCauses = expected.missingCauses.map(
      (cause) => `${cause.message}（${cause.code}）`,
    );
    assert.deepEqual(
      missingCauses,
      expectedCauses,
      `${scenario.id} ${expected.code} ordered missing causes`,
    );
    metricEvidence.push({
      code: expected.code,
      displayValue: expected.displayValue,
      visibleValue: displayValue,
      status: expected.status,
      missingCauses: expected.missingCauses,
    });
  }

  const groups = panel.locator(":scope > section");
  assert.equal(
    await groups.count(),
    EXPECTED_GROUPS.length,
    `${scenario.id} metric group count`,
  );
  const groupEvidence = [];
  for (let index = 0; index < EXPECTED_GROUPS.length; index += 1) {
    const [expectedName, expectedCodes] = EXPECTED_GROUPS[index];
    const group = groups.nth(index);
    const name = (
      await group.getByRole("heading", { level: 2 }).textContent()
    )?.trim();
    const codes = await group
      .locator("[data-metric-code]")
      .evaluateAll((elements) =>
        elements.map((element) => element.dataset.metricCode),
      );
    assert.equal(name, expectedName, `${scenario.id} group ${index + 1}`);
    assert.deepEqual(
      codes,
      expectedCodes,
      `${scenario.id} ${expectedName} order`,
    );
    groupEvidence.push({ name, codes });
  }

  assert.deepEqual(
    await panel
      .locator("[data-metric-row]")
      .evaluateAll((elements) =>
        elements.map((element) =>
          element.getAttribute("data-metric-row")?.split(" "),
        ),
      ),
    EXPECTED_ROWS,
    `${scenario.id} must retain the 16 approved rows`,
  );
  await assertSalesRow(panel.locator("[data-metric-row]").nth(13));
  return { metrics: metricEvidence, groups: groupEvidence };
}

function responseMatches(response, method, pathname) {
  return (
    response.request().method() === method &&
    parseUrl(response.url(), "Response URL").pathname === pathname
  );
}

async function assertCaptchaFallback(response, adminUrl) {
  assert.equal(response.ok(), true, "Captcha request must succeed");
  assert.equal(
    parseUrl(response.url(), "Captcha response URL").origin,
    parseUrl(adminUrl, "Admin URL").origin,
    "Captcha request must use the Vite /api proxy",
  );
  const payload = await response.json();
  assert.equal(
    payload.code,
    501,
    "The proxied captcha request must report that captcha is disabled",
  );
}

async function loginComplexUser(page, adminUrl, user, openLoginPage) {
  const captchaResponsePromise = page.waitForResponse(
    (response) => responseMatches(response, "GET", "/api/auth/captcha"),
    { timeout: 30_000 },
  );
  await openLoginPage();
  await page.waitForURL((url) => url.pathname === "/workspace/login", {
    timeout: 30_000,
  });
  await assertCaptchaFallback(await captchaResponsePromise, adminUrl);
  await page
    .locator("#workspace-captcha-code")
    .waitFor({ state: "detached", timeout: 30_000 });
  await page.locator("#workspace-user-name").fill(user.userName);
  await page.locator("#workspace-password").fill(user.password);
  const loginResponsePromise = page.waitForResponse(
    (response) => responseMatches(response, "POST", "/api/auth/login"),
    { timeout: 30_000 },
  );
  await page.getByRole("button", { name: "登录", exact: true }).click();
  const loginResponse = await loginResponsePromise;
  const loginBody = await loginResponse.json();
  assert.equal(loginResponse.status(), 200, `${user.role} login HTTP status`);
  assert.equal(
    parseUrl(loginResponse.url(), "Login response URL").origin,
    parseUrl(adminUrl, "Admin URL").origin,
    `${user.role} login must use the Vite /api proxy`,
  );
  assert.equal(loginBody.code, 0, `${user.role} login business code`);
  await page.waitForURL((url) => url.pathname === "/workspace/dashboard", {
    timeout: 30_000,
  });
  const session = await page.evaluate(() => {
    const raw = localStorage.getItem("rabbit_workspace_session_v2");
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  });
  assert.ok(session?.token, `${user.role} login did not establish a session`);
  assert.ok(
    Number.isSafeInteger(session.userId),
    `${user.role} session user ID`,
  );
  assert.equal(
    session.userName,
    user.userName,
    `${user.role} session username`,
  );
}

async function loginInitialComplexUser(page, adminUrl, user) {
  await loginComplexUser(page, adminUrl, user, () =>
    page.goto(`${adminUrl}/workspace/login`, { waitUntil: "domcontentloaded" }),
  );
}

async function switchComplexUser(page, adminUrl, user) {
  await loginComplexUser(page, adminUrl, user, () =>
    page.getByRole("button", { name: "退出兔场工作台", exact: true }).click(),
  );
}

async function selectVisibleHouse(page, user) {
  const selector = page.locator('[aria-label="选择兔场"]:visible');
  await selector.waitFor({ state: "visible", timeout: 30_000 });
  await selector.click();
  await page.getByRole("option", { name: user.houseName, exact: true }).click();
  await page.waitForFunction(
    (expectedName) =>
      [...document.querySelectorAll('[aria-label="选择兔场"]')].some(
        (element) =>
          element instanceof HTMLElement &&
          element.offsetParent !== null &&
          element.textContent?.trim() === expectedName,
      ),
    user.houseName,
  );
}

async function gotoProduction(page, adminUrl, houseId) {
  const responsePromise = page.waitForResponse(
    (response) => responseMatches(response, "GET", "/api/batches"),
    { timeout: 30_000 },
  );
  if (
    parseUrl(page.url(), "Current page URL").pathname ===
    "/workspace/production"
  ) {
    await page.reload({ waitUntil: "domcontentloaded" });
  } else {
    await page
      .locator("aside")
      .getByRole("link", { name: "生产批次", exact: true })
      .click();
  }
  await page.waitForURL((url) => url.pathname === "/workspace/production", {
    timeout: 30_000,
  });
  const response = await responsePromise;
  assert.equal(response.ok(), true, "Production batch list must load");
  assertProxiedRequest(response.request(), adminUrl, houseId);
}

function scenarioRow(page, scenario) {
  return page
    .getByRole("row")
    .filter({ hasText: scenario.batchCode })
    .filter({ hasText: `ID ${scenario.batchId}` });
}

async function openScenarioDetail(page, adminUrl, houseId, scenario) {
  const row = scenarioRow(page, scenario);
  await row.waitFor({ state: "visible", timeout: 30_000 });
  const statisticsResponsePromise = page.waitForResponse(
    (response) =>
      responseMatches(
        response,
        "GET",
        `/api/batches/${scenario.batchId}/statistics`,
      ),
    { timeout: 30_000 },
  );
  await row.getByRole("link", { name: "详情", exact: true }).click();
  await page.waitForURL(
    (url) =>
      url.pathname === `/workspace/production/batches/${scenario.batchId}`,
    { timeout: 30_000 },
  );
  const statisticsResponse = await statisticsResponsePromise;
  assert.equal(
    statisticsResponse.ok(),
    true,
    `${scenario.id} statistics request must succeed`,
  );
  assertProxiedRequest(statisticsResponse.request(), adminUrl, houseId);
  const statistics = await assertComplexStatisticsResponse(
    statisticsResponse,
    scenario,
  );
  await page
    .getByTestId("batch-statistics-panel")
    .waitFor({ state: "visible", timeout: 30_000 });
  assert.equal(
    (
      await page.locator('[aria-label="选择兔场"]:visible').textContent()
    )?.trim(),
    statistics.houseName,
    `${scenario.id} workspace house selection`,
  );
  return assertComplexMetricLayout(page, scenario);
}

async function downloadScenarioWorkbook(
  page,
  adminUrl,
  houseId,
  scenario,
  targetPath,
) {
  const responsePromise = page.waitForResponse(
    (response) =>
      responseMatches(
        response,
        "GET",
        `/api/reports/batches/${scenario.batchId}/statistics.xlsx`,
      ),
    { timeout: 30_000 },
  );
  const downloadPromise = page.waitForEvent("download", { timeout: 30_000 });
  await page.getByRole("button", { name: "导出 Excel" }).click();
  const [response, download] = await Promise.all([
    responsePromise,
    downloadPromise,
  ]);
  assert.equal(
    response.ok(),
    true,
    `${scenario.id} Excel request must succeed`,
  );
  assertProxiedRequest(response.request(), adminUrl, houseId);
  assert.equal(
    response.headers()["content-type"]?.split(";", 1)[0],
    XLSX_MEDIA_TYPE,
    `${scenario.id} Excel response Content-Type`,
  );
  assert.match(download.suggestedFilename(), /\.xlsx$/i);
  assert.equal(
    await download.failure(),
    null,
    `${scenario.id} download failure`,
  );
  await download.saveAs(targetPath);
  assert.ok(
    statSync(targetPath).size > 0,
    `${scenario.id} XLSX must not be empty`,
  );
  assert.equal(
    readFileSync(targetPath).subarray(0, 2).toString("ascii"),
    "PK",
    `${scenario.id} workbook must be an OOXML ZIP file`,
  );
  return download.suggestedFilename();
}

async function assertOwnerSecurity(
  page,
  adminUrl,
  houseId,
  scenario,
  rolesDir,
) {
  await page
    .getByRole("button", { name: "修正出肉率", exact: true })
    .waitFor({ state: "visible", timeout: 30_000 });
  await page
    .getByRole("button", { name: "出肉率历史", exact: true })
    .waitFor({ state: "visible", timeout: 30_000 });
  await page
    .getByRole("button", { name: "导出 Excel", exact: true })
    .waitFor({ state: "visible", timeout: 30_000 });
  const historyResponsePromise = page.waitForResponse(
    (response) =>
      responseMatches(
        response,
        "GET",
        `/api/batches/${scenario.batchId}/carcass-yields`,
      ),
    { timeout: 30_000 },
  );
  await page.getByRole("button", { name: "出肉率历史", exact: true }).click();
  const historyResponse = await historyResponsePromise;
  assert.equal(
    historyResponse.ok(),
    true,
    "OWNER history request must succeed",
  );
  assertProxiedRequest(historyResponse.request(), adminUrl, houseId);
  const historyPayload = await historyResponse.json();
  assert.equal(historyPayload.code, 0, "OWNER history business code");
  assert.equal(historyPayload.data?.total, 1, "OWNER history version count");
  assert.equal(
    historyPayload.data?.items?.length,
    1,
    "OWNER history item count",
  );
  assert.equal(
    historyPayload.data.items[0]?.yieldRate,
    0.58,
    "OWNER history yield",
  );
  const dialog = page.getByRole("dialog", { name: "出肉率版本历史" });
  await dialog.waitFor({ state: "visible", timeout: 30_000 });
  assert.equal(await dialog.getByText("58.00%", { exact: true }).count(), 1);
  const screenshot = path.join(rolesDir, "owner-history.png");
  await page.screenshot({ path: screenshot, fullPage: true });
  await dialog
    .getByRole("button", { name: "关闭", exact: true })
    .first()
    .click();
  await dialog.waitFor({ state: "hidden", timeout: 30_000 });
  return { historyVersionCount: 1, historyYieldRate: 0.58, screenshot };
}

async function startComplexAdmin(apiUrl, artifactDir) {
  let adminUrl = process.env.ADMIN_BASE_URL?.trim();
  const viteLog = [];
  if (adminUrl) {
    adminUrl = baseUrl(adminUrl, "ADMIN_BASE_URL");
    assert.equal(
      await servesThisAdmin(adminUrl),
      true,
      `ADMIN_BASE_URL does not serve this Admin app: ${adminUrl}`,
    );
    return { adminUrl, devServer: null, viteLog };
  }
  const configuredPort = process.env.ADMIN_DEV_PORT?.trim();
  const port = configuredPort
    ? positiveInteger(configuredPort, "ADMIN_DEV_PORT")
    : await freePort();
  adminUrl = `http://127.0.0.1:${port}`;
  const viteEntry = path.join(
    ADMIN_DIR,
    "node_modules",
    "vite",
    "bin",
    "vite.js",
  );
  const devServer = spawn(
    process.execPath,
    [viteEntry, "--host", "127.0.0.1", "--port", String(port), "--strictPort"],
    {
      cwd: ADMIN_DIR,
      env: {
        ...process.env,
        RABBIT_API_BASE_URL: apiUrl,
        VITE_API_BASE_URL: "",
      },
      stdio: ["ignore", "pipe", "pipe"],
    },
  );
  devServer.stdout.on("data", (chunk) => viteLog.push(chunk.toString()));
  devServer.stderr.on("data", (chunk) => viteLog.push(chunk.toString()));
  try {
    await waitForAdmin(adminUrl, devServer, viteLog);
  } catch (error) {
    await stopDevServer(devServer);
    writeFileSync(path.join(artifactDir, "vite.log"), viteLog.join(""));
    throw error;
  }
  return { adminUrl, devServer, viteLog };
}

async function runComplex(config) {
  const apiUrl = baseUrl(
    process.env.RABBIT_API_BASE_URL?.trim() ||
      `http://${DEFAULT_API_HOST}:${DEFAULT_API_PORT}`,
    "RABBIT_API_BASE_URL",
  );
  const artifactDir = artifactDirectory();
  const scenariosDir = path.join(artifactDir, "scenarios");
  const rolesDir = path.join(artifactDir, "roles");
  const headed = process.env.HEADED === "1";
  mkdirSync(scenariosDir, { recursive: true });
  mkdirSync(rolesDir, { recursive: true });
  await checkBackend(apiUrl);
  const { adminUrl, devServer, viteLog } = await startComplexAdmin(
    apiUrl,
    artifactDir,
  );

  let browser;
  try {
    const chromeExecutable = process.env.RABBIT_CHROME_BIN?.trim();
    browser = await chromium.launch({
      ...(chromeExecutable
        ? { executablePath: chromeExecutable }
        : { channel: "chrome" }),
      headless: !headed,
    });
    const context = await browser.newContext({
      viewport: DESKTOP,
      locale: "zh-CN",
      acceptDownloads: true,
    });
    const page = await context.newPage();
    const consoleErrors = [];
    const pageErrors = [];
    const failedApiRequests = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(String(error)));
    page.on("requestfailed", (request) => {
      if (
        parseUrl(request.url(), "Failed request URL").pathname.startsWith(
          "/api/",
        )
      ) {
        failedApiRequests.push(
          `${request.method()} ${request.url()}: ${request.failure()?.errorText ?? "failed"}`,
        );
      }
    });

    const owner = config.users.OWNER;
    await loginInitialComplexUser(page, adminUrl, owner);
    await selectVisibleHouse(page, owner);
    await gotoProduction(page, adminUrl, config.houseId);

    const scenarioResults = [];
    let ownerSecurity;
    for (const scenario of config.scenarios) {
      const scenarioDir = path.join(scenariosDir, scenario.id);
      mkdirSync(scenarioDir, { recursive: true });
      const detailEvidence = await openScenarioDetail(
        page,
        adminUrl,
        config.houseId,
        scenario,
      );
      if (scenario.id === "security-and-retry") {
        ownerSecurity = await assertOwnerSecurity(
          page,
          adminUrl,
          config.houseId,
          scenario,
          rolesDir,
        );
      }
      await assertNoOverflow(page);
      const fullPageScreenshot = path.join(scenarioDir, "full-page.png");
      await page.screenshot({ path: fullPageScreenshot, fullPage: true });
      await page
        .getByTestId("batch-statistics-panel")
        .locator("details")
        .evaluateAll((elements) => {
          for (const element of elements) element.open = true;
        });
      await assertNoOverflow(page);
      const detailsScreenshot = path.join(scenarioDir, "details.png");
      await page.screenshot({ path: detailsScreenshot, fullPage: true });
      await page
        .getByTestId("batch-statistics-panel")
        .locator("details")
        .evaluateAll((elements) => {
          for (const element of elements) element.open = false;
        });
      const workbook = path.join(scenarioDir, "batch-statistics.xlsx");
      const suggestedFilename = await downloadScenarioWorkbook(
        page,
        adminUrl,
        config.houseId,
        scenario,
        workbook,
      );
      const result = {
        id: scenario.id,
        batchRole: scenario.batchRole,
        batchId: scenario.batchId,
        batchCode: scenario.batchCode,
        metrics: detailEvidence.metrics,
        groups: detailEvidence.groups,
        screenshots: {
          fullPage: artifactPath(artifactDir, fullPageScreenshot),
          details: artifactPath(artifactDir, detailsScreenshot),
        },
        workbook: {
          file: artifactPath(artifactDir, workbook),
          suggestedFilename,
          mediaType: XLSX_MEDIA_TYPE,
          zipSignature: "PK",
        },
      };
      writeJson(path.join(scenarioDir, "detail-evidence.json"), result);
      scenarioResults.push(result);
      await page.getByRole("link", { name: "返回列表", exact: true }).click();
      await page.waitForURL((url) => url.pathname === "/workspace/production", {
        timeout: 30_000,
      });
      if (scenario !== config.scenarios.at(-1)) {
        await scenarioRow(
          page,
          config.scenarios[config.scenarios.indexOf(scenario) + 1],
        ).waitFor({ state: "visible", timeout: 30_000 });
      }
    }

    const securityScenario = config.scenarios.find(
      (scenario) => scenario.id === "security-and-retry",
    );
    assert.ok(securityScenario, "security-and-retry scenario is required");
    const readOnly = config.users.READ_ONLY;
    await switchComplexUser(page, adminUrl, readOnly);
    await selectVisibleHouse(page, readOnly);
    await gotoProduction(page, adminUrl, config.houseId);
    const readOnlyEvidence = await openScenarioDetail(
      page,
      adminUrl,
      config.houseId,
      securityScenario,
    );
    await page
      .getByText("权限加载中", { exact: true })
      .waitFor({ state: "hidden", timeout: 30_000 });
    assert.equal(
      await page.getByRole("button", { name: /录入出肉率|修正出肉率/ }).count(),
      0,
      "READ_ONLY must not see carcass yield editing",
    );
    assert.equal(
      await page.getByRole("button", { name: "出肉率历史" }).count(),
      0,
      "READ_ONLY must not see carcass yield history",
    );
    assert.equal(
      await page.getByRole("button", { name: "导出 Excel" }).count(),
      1,
      "READ_ONLY must see workbook export",
    );
    const readOnlyWorkbook = path.join(
      rolesDir,
      "read-only-batch-statistics.xlsx",
    );
    const readOnlySuggestedFilename = await downloadScenarioWorkbook(
      page,
      adminUrl,
      config.houseId,
      securityScenario,
      readOnlyWorkbook,
    );
    await assertNoOverflow(page);
    const readOnlyScreenshot = path.join(rolesDir, "read-only.png");
    await page.screenshot({ path: readOnlyScreenshot, fullPage: true });

    const outsider = config.users.OUTSIDER;
    await switchComplexUser(page, adminUrl, outsider);
    await selectVisibleHouse(page, outsider);
    const houseSelector = page.locator('[aria-label="选择兔场"]:visible');
    await houseSelector.click();
    assert.equal(
      await page
        .getByRole("option", { name: config.houseName, exact: true })
        .count(),
      0,
      "OUTSIDER must not see the target house in the visible selector",
    );
    await page.keyboard.press("Escape");
    await gotoProduction(page, adminUrl, outsider.houseId);
    for (const scenario of config.scenarios) {
      assert.equal(
        await scenarioRow(page, scenario).count(),
        0,
        `OUTSIDER must not see ${scenario.id}`,
      );
    }
    assert.equal(
      await page.getByTestId("batch-statistics-panel").count(),
      0,
      "OUTSIDER must remain in the authorized batch list",
    );
    await assertNoOverflow(page);
    const outsiderScreenshot = path.join(rolesDir, "outsider.png");
    await page.screenshot({ path: outsiderScreenshot, fullPage: true });

    assert.equal(
      scenarioResults.length,
      COMPLEX_PRIMARY_SCENARIO_IDS.length + 1,
      "Complex scenario evidence count",
    );
    assert.equal(
      scenarioResults.filter((scenario) => scenario.batchRole === "primary")
        .length,
      COMPLEX_PRIMARY_SCENARIO_IDS.length,
      "Complex primary scenario evidence count",
    );
    assert.equal(
      scenarioResults.filter(
        (scenario) => scenario.id === COMPLEX_SUPPORT_SCENARIO_ID,
      ).length,
      1,
      "Complex rounding support evidence count",
    );
    assert.deepEqual(
      consoleErrors,
      [],
      `Browser console errors:\n${consoleErrors.join("\n")}`,
    );
    assert.deepEqual(pageErrors, [], `Page errors:\n${pageErrors.join("\n")}`);
    assert.deepEqual(
      failedApiRequests,
      [],
      `Failed API requests:\n${failedApiRequests.join("\n")}`,
    );

    assert.ok(ownerSecurity, "OWNER security evidence is required");
    const result = {
      schemaVersion: 1,
      suite: "complex",
      passed: true,
      targetHouse: { id: config.houseId, name: config.houseName },
      primaryScenarioIds: [...COMPLEX_PRIMARY_SCENARIO_IDS],
      supportScenarioId: COMPLEX_SUPPORT_SCENARIO_ID,
      scenarioCount: scenarioResults.length,
      scenarioWorkbookCount: scenarioResults.length,
      roleWorkbookCount: 1,
      workbookCount: scenarioResults.length + 1,
      scenarios: scenarioResults,
      roles: {
        OWNER: {
          targetHouseSelected: true,
          canEditCarcassYield: true,
          canReadCarcassYieldHistory: true,
          canExport: true,
          historyVersionCount: ownerSecurity.historyVersionCount,
          historyYieldRate: ownerSecurity.historyYieldRate,
          screenshot: artifactPath(artifactDir, ownerSecurity.screenshot),
        },
        READ_ONLY: {
          targetHouseSelected: true,
          canViewStatistics: readOnlyEvidence.metrics.length === 28,
          canEditCarcassYield: false,
          canReadCarcassYieldHistory: false,
          canExport: true,
          workbook: {
            file: artifactPath(artifactDir, readOnlyWorkbook),
            suggestedFilename: readOnlySuggestedFilename,
            mediaType: XLSX_MEDIA_TYPE,
            zipSignature: "PK",
          },
          screenshot: artifactPath(artifactDir, readOnlyScreenshot),
        },
        OUTSIDER: {
          selectedHouseId: outsider.houseId,
          targetHouseVisible: false,
          targetBatchesVisible: false,
          screenshot: artifactPath(artifactDir, outsiderScreenshot),
        },
      },
      diagnostics: {
        consoleErrors,
        pageErrors,
        failedApiRequests,
        horizontalOverflow: false,
      },
    };
    writeJson(path.join(artifactDir, "result.json"), result);
    process.stdout.write(
      `Admin batch statistics complex E2E passed. Artifacts: ${artifactDir}\n`,
    );
  } finally {
    await browser?.close();
    if (devServer) {
      await stopDevServer(devServer);
      writeFileSync(path.join(artifactDir, "vite.log"), viteLog.join(""));
    }
  }
}

async function runBaseline(fixture) {
  const apiUrl = baseUrl(
    process.env.RABBIT_API_BASE_URL?.trim() ||
      `http://${DEFAULT_API_HOST}:${DEFAULT_API_PORT}`,
    "RABBIT_API_BASE_URL",
  );
  const houseId = positiveInteger(
    fixtureValue(
      fixture,
      "RABBIT_E2E_HOUSE_ID",
      "RABBIT_BATCH_STATISTICS_HOUSE_ID",
    ),
    "RABBIT_E2E_HOUSE_ID",
  );
  const houseName = fixtureValue(
    fixture,
    "RABBIT_E2E_HOUSE_NAME",
    "RABBIT_FIXTURE_HOUSE_NAME",
  );
  const batchId = positiveInteger(
    fixtureValue(
      fixture,
      "RABBIT_E2E_BATCH_ID",
      "RABBIT_BATCH_STATISTICS_BATCH_ID",
    ),
    "RABBIT_E2E_BATCH_ID",
  );
  const username = fixtureValue(
    fixture,
    "RABBIT_E2E_USERNAME",
    "RABBIT_FIXTURE_USERNAME",
  );
  const password = fixtureValue(
    fixture,
    "RABBIT_E2E_PASSWORD",
    "RABBIT_FIXTURE_PASSWORD",
  );
  const artifactDir = artifactDirectory();
  const headed = process.env.HEADED === "1";
  mkdirSync(artifactDir, { recursive: true });

  await checkBackend(apiUrl);

  let adminUrl = process.env.ADMIN_BASE_URL?.trim();
  let devServer;
  const viteLog = [];
  if (adminUrl) {
    adminUrl = baseUrl(adminUrl, "ADMIN_BASE_URL");
    assert.equal(
      await servesThisAdmin(adminUrl),
      true,
      `ADMIN_BASE_URL does not serve this Admin app: ${adminUrl}`,
    );
  } else {
    const configuredPort = process.env.ADMIN_DEV_PORT?.trim();
    const port = configuredPort
      ? positiveInteger(configuredPort, "ADMIN_DEV_PORT")
      : await freePort();
    adminUrl = `http://127.0.0.1:${port}`;
    const viteEntry = path.join(
      ADMIN_DIR,
      "node_modules",
      "vite",
      "bin",
      "vite.js",
    );
    devServer = spawn(
      process.execPath,
      [
        viteEntry,
        "--host",
        "127.0.0.1",
        "--port",
        String(port),
        "--strictPort",
      ],
      {
        cwd: ADMIN_DIR,
        env: {
          ...process.env,
          RABBIT_API_BASE_URL: apiUrl,
          VITE_API_BASE_URL: "",
        },
        stdio: ["ignore", "pipe", "pipe"],
      },
    );
    devServer.stdout.on("data", (chunk) => viteLog.push(chunk.toString()));
    devServer.stderr.on("data", (chunk) => viteLog.push(chunk.toString()));
    try {
      await waitForAdmin(adminUrl, devServer, viteLog);
    } catch (error) {
      await stopDevServer(devServer);
      writeFileSync(path.join(artifactDir, "vite.log"), viteLog.join(""));
      throw error;
    }
  }

  let browser;
  try {
    const chromeExecutable = process.env.RABBIT_CHROME_BIN?.trim();
    browser = await chromium.launch({
      ...(chromeExecutable
        ? { executablePath: chromeExecutable }
        : { channel: "chrome" }),
      headless: !headed,
    });
    const context = await browser.newContext({
      viewport: DESKTOP,
      locale: "zh-CN",
      acceptDownloads: true,
    });
    const page = await context.newPage();
    const consoleErrors = [];
    const pageErrors = [];
    const failedApiRequests = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("pageerror", (error) => pageErrors.push(String(error)));
    page.on("requestfailed", (request) => {
      if (
        parseUrl(request.url(), "Failed request URL").pathname.startsWith(
          "/api/",
        )
      ) {
        failedApiRequests.push(
          `${request.method()} ${request.url()}: ${request.failure()?.errorText ?? "failed"}`,
        );
      }
    });

    const captchaResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        parseUrl(response.url(), "Captcha response URL").pathname ===
          "/api/auth/captcha",
      { timeout: 30_000 },
    );
    await page.goto(`${adminUrl}/workspace/login`, {
      waitUntil: "domcontentloaded",
    });
    const captchaResponse = await captchaResponsePromise;
    assert.equal(captchaResponse.ok(), true, "Captcha request must succeed");
    assert.equal(
      parseUrl(captchaResponse.url(), "Captcha response URL").origin,
      parseUrl(adminUrl, "Admin URL").origin,
      "Captcha request must use the Vite /api proxy",
    );
    assert.equal(
      (await captchaResponse.json()).code,
      501,
      "The proxied captcha request must report that captcha is disabled",
    );
    await page
      .locator("#workspace-captcha-code")
      .waitFor({ state: "detached" });

    await page.locator("#workspace-user-name").fill(username);
    await page.locator("#workspace-password").fill(password);
    const loginResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        parseUrl(response.url(), "Login response URL").pathname ===
          "/api/auth/login",
      { timeout: 30_000 },
    );
    await page.getByRole("button", { name: "登录", exact: true }).click();
    const loginResponse = await loginResponsePromise;
    const loginResponseText = await loginResponse.text();
    let loginResponseBody;
    try {
      loginResponseBody = JSON.parse(loginResponseText);
    } catch {
      loginResponseBody = { code: "non-json" };
    }
    assert.equal(
      loginResponse.status(),
      200,
      `Business login HTTP status (business code ${String(loginResponseBody.code ?? "missing")})`,
    );
    assert.equal(
      parseUrl(loginResponse.url(), "Login response URL").origin,
      parseUrl(adminUrl, "Admin URL").origin,
      "Business login must use the Vite /api proxy",
    );
    assert.equal(loginResponseBody.code, 0, "Business login response code");
    await page.waitForFunction(() => {
      const raw = localStorage.getItem("rabbit_workspace_session_v2");
      if (!raw) return false;
      try {
        return Boolean(JSON.parse(raw)?.token);
      } catch {
        return false;
      }
    });
    await page.waitForFunction(
      () => window.location.pathname === "/workspace/dashboard",
    );

    const session = await page.evaluate(() => {
      const raw = localStorage.getItem("rabbit_workspace_session_v2");
      return raw ? JSON.parse(raw) : null;
    });
    assert.ok(
      session?.token,
      "Business login did not establish the workspace session",
    );
    assert.ok(
      Number.isSafeInteger(session.userId),
      "Workspace session has no user ID",
    );
    assert.equal(session.userName, username, "Workspace session username");
    const houseSelector = page
      .locator("aside")
      .locator('[aria-label="选择兔场"]');
    await houseSelector.waitFor({ state: "visible", timeout: 30_000 });
    if ((await houseSelector.textContent())?.trim() !== houseName) {
      await houseSelector.click();
      await page.getByRole("option", { name: houseName, exact: true }).click();
      await page.waitForFunction(
        (expectedHouseName) =>
          [...document.querySelectorAll('aside [aria-label="选择兔场"]')].some(
            (element) => element.textContent?.trim() === expectedHouseName,
          ),
        houseName,
      );
    }

    await page
      .locator("aside")
      .getByRole("link", { name: "生产批次", exact: true })
      .click();
    await page.waitForFunction(
      () => window.location.pathname === "/workspace/production",
    );
    const batchRow = page.getByRole("row").filter({ hasText: `ID ${batchId}` });
    await batchRow.waitFor({ state: "visible", timeout: 30_000 });
    const statisticsResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        parseUrl(response.url(), "Statistics response URL").pathname ===
          `/api/batches/${batchId}/statistics`,
      { timeout: 30_000 },
    );
    await batchRow.getByRole("link", { name: "详情", exact: true }).click();
    const statisticsResponse = await statisticsResponsePromise;
    assert.equal(
      statisticsResponse.ok(),
      true,
      "Statistics request must succeed",
    );
    assertProxiedRequest(statisticsResponse.request(), adminUrl, houseId);
    const statistics = await assertStatisticsResponse(
      statisticsResponse,
      batchId,
    );
    await page
      .getByTestId("batch-statistics-panel")
      .waitFor({ timeout: 30_000 });
    assert.equal(
      (
        await page.locator('[aria-label="选择兔场"]:visible').textContent()
      )?.trim(),
      statistics.houseName,
      "Workspace house selection",
    );

    await assertMetricLayout(page);
    await assertNoOverflow(page);
    await page.screenshot({
      path: path.join(artifactDir, "desktop-detail.png"),
      fullPage: true,
    });

    const exportResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        parseUrl(response.url(), "Excel response URL").pathname ===
          `/api/reports/batches/${batchId}/statistics.xlsx`,
      { timeout: 30_000 },
    );
    const downloadPromise = page.waitForEvent("download", { timeout: 30_000 });
    await page.getByRole("button", { name: "导出 Excel" }).click();
    const [exportResponse, download] = await Promise.all([
      exportResponsePromise,
      downloadPromise,
    ]);
    assert.equal(exportResponse.ok(), true, "Excel request must succeed");
    assertProxiedRequest(exportResponse.request(), adminUrl, houseId);
    assert.equal(
      exportResponse.headers()["content-type"]?.split(";", 1)[0],
      XLSX_MEDIA_TYPE,
      "Excel response Content-Type",
    );
    assert.match(download.suggestedFilename(), /\.xlsx$/i);
    assert.equal(
      await download.failure(),
      null,
      "Browser download must complete",
    );
    const xlsxPath = path.join(artifactDir, "batch-statistics.xlsx");
    await download.saveAs(xlsxPath);
    assert.ok(statSync(xlsxPath).size > 0, "Downloaded XLSX must not be empty");
    assert.equal(
      readFileSync(xlsxPath).subarray(0, 2).toString("ascii"),
      "PK",
      "Downloaded report must be an OOXML ZIP file",
    );

    await page.waitForTimeout(1_100);
    await assertNoOverflow(page);
    assert.deepEqual(
      consoleErrors,
      [],
      `Browser console errors:\n${consoleErrors.join("\n")}`,
    );
    assert.deepEqual(pageErrors, [], `Page errors:\n${pageErrors.join("\n")}`);
    assert.deepEqual(
      failedApiRequests,
      [],
      `Failed API requests:\n${failedApiRequests.join("\n")}`,
    );
    process.stdout.write(
      `Admin batch statistics real E2E passed. Artifacts: ${artifactDir}\n`,
    );
  } finally {
    await browser?.close();
    if (devServer) {
      await stopDevServer(devServer);
      writeFileSync(path.join(artifactDir, "vite.log"), viteLog.join(""));
    }
  }
}

async function main() {
  const fixture = fixtureValues();
  const suite = fixtureSuite(fixture);
  if (suite === "baseline") {
    await runBaseline(fixture);
    return;
  }

  assertComplexDefinesMode();
  const config = validateComplexDefines(fixture);
  if (process.argv.includes("--validate-defines")) {
    process.stdout.write(
      `Complex Admin defines are valid: ${config.scenarios.length} scenarios, 3 roles.\n`,
    );
    return;
  }
  await runComplex(config);
}

main().catch((error) => {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
});
