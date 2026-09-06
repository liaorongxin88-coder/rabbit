#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { createServer } from "node:net";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

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

async function main() {
  const fixture = fixtureValues();
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

main().catch((error) => {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
});
