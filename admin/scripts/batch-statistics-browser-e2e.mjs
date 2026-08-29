#!/usr/bin/env node

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { get } from "node:http";
import { createServer } from "node:net";
import { mkdirSync } from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const ADMIN_DIR = path.resolve(SCRIPT_DIR, "..");
const ARTIFACT_DIR = path.join(ADMIN_DIR, "build/browser-e2e/batch-statistics");
const DESKTOP = { width: 1440, height: 900 };
const NARROW = { width: 390, height: 844 };

const house = {
  id: 7,
  name: "统计验收兔场",
  layoutRows: 1,
  layoutCols: 1,
  layoutLayers: 1,
};

const cage = {
  id: 11,
  houseId: 7,
  cageNumber: "1-1-1",
  status: "0",
  rabbitCount: 0,
  isFed: true,
  isEnabled: true,
};

const permission = {
  perms: "view",
  role: "OWNER",
  isAdmin: false,
  permissions: [
    "rabbit:batches:query",
    "rabbit:rabbits:edit",
    "rabbit:cages:edit",
  ],
};

function apiResponse(data) {
  return JSON.stringify({ code: 0, data });
}

function fixtureData(pathname, scenario) {
  if (pathname === "/api/houses") return [house];
  if (pathname === "/api/houses/permission") return permission;
  if (pathname === "/api/cages") return [cage];
  if (pathname === "/api/rabbits") return [];
  if (pathname === "/api/repro/stage-actions") return [];
  if (pathname === "/api/repro/entry-points") return [];
  if (pathname === "/api/batches") {
    return scenario === "empty"
      ? []
      : [{ id: 42, houseId: 7, batchCode: "B-042", status: "ACTIVE" }];
  }
  if (pathname === "/api/batches/42/weaning-records") return [];
  return [];
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

function serverIsReady(port) {
  return new Promise((resolve) => {
    const request = get(
      { hostname: "127.0.0.1", port, path: "/" },
      (response) => {
        response.resume();
        resolve(response.statusCode === 200);
      },
    );
    request.setTimeout(1_000, () => request.destroy());
    request.on("error", () => resolve(false));
  });
}

async function waitForServer(port, server) {
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    if (server.exitCode !== null) {
      throw new Error("Vite dev server exited before it became available");
    }
    if (await serverIsReady(port)) return;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("Vite dev server did not become available within 30 seconds");
}

async function main() {
  mkdirSync(ARTIFACT_DIR, { recursive: true });
  const port = await freePort();
  const baseUrl = `http://127.0.0.1:${port}`;
  const pnpmEntry = process.env.npm_execpath;
  const command = pnpmEntry ? process.execPath : "pnpm";
  const args = [
    ...(pnpmEntry ? [pnpmEntry] : []),
    "exec",
    "vite",
    "--host",
    "127.0.0.1",
    "--port",
    String(port),
    "--strictPort",
  ];
  const server = spawn(command, args, {
    cwd: ADMIN_DIR,
    stdio: "ignore",
  });
  let browser;

  try {
    await waitForServer(port, server);
    browser = await chromium.launch({ channel: "chrome", headless: true });
    const context = await browser.newContext({
      viewport: DESKTOP,
      locale: "zh-CN",
    });
    const page = await context.newPage();
    const consoleErrors = [];
    let scenario = "ready";
    let expectStatisticsNetworkFailure = false;
    let batchListRequests = 0;
    let statisticsRequests = 0;

    page.on("console", (message) => {
      const isExpectedNetworkFailure =
        expectStatisticsNetworkFailure &&
        message.text().includes("net::ERR_FAILED");
      if (message.type() === "error" && !isExpectedNetworkFailure) {
        consoleErrors.push(message.text());
      }
    });
    page.on("pageerror", (error) => consoleErrors.push(String(error)));
    await page.addInitScript(() => {
      window.localStorage.setItem(
        "rabbit_workspace_session_v2",
        JSON.stringify({
          token: "browser-fixture-token",
          userId: 71,
          userName: "验收人员",
          phoneBound: true,
          maskedPhone: "138****0000",
          hasPassword: true,
        }),
      );
    });
    await page.route(
      (url) => {
        const requestUrl = new URL(url);
        return (
          requestUrl.origin === baseUrl &&
          requestUrl.pathname.startsWith("/api/")
        );
      },
      async (route) => {
        const pathname = new URL(route.request().url()).pathname;
        if (pathname === "/api/batches/42/statistics") {
          statisticsRequests += 1;
          if (scenario === "network-failure") {
            await route.abort("failed");
            return;
          }
          await route.fulfill({
            contentType: "application/json",
            body: apiResponse({
              totalLitters: 12,
              totalKits: 96,
              totalLiveKits: 90,
              totalWeaned: 78,
            }),
          });
          return;
        }

        if (pathname === "/api/batches") batchListRequests += 1;
        await route.fulfill({
          contentType: "application/json",
          body: apiResponse(fixtureData(pathname, scenario)),
        });
      },
    );

    await page.goto(`${baseUrl}/workspace/livestock`);
    try {
      await page.getByTestId("batch-statistics").waitFor({ timeout: 10_000 });
    } catch (error) {
      const pageText = (await page.locator("body").textContent()) ?? "";
      const pageHtml = (await page.locator("body").innerHTML()).slice(0, 2_000);
      throw new Error(
        `batch statistics did not render at ${page.url()}:\n${pageText}\n${pageHtml}\n${consoleErrors.join("\n")}`,
        { cause: error },
      );
    }
    await assertMetric(page, "产崽窝数", "12");
    await assertMetric(page, "产崽总数", "96");
    await assertMetric(page, "活崽总数", "90");
    await assertMetric(page, "断奶数量", "78");
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "desktop.png"),
      fullPage: true,
    });

    await page.setViewportSize(NARROW);
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "narrow.png"),
      fullPage: true,
    });
    const documentWidth = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    assert.ok(
      documentWidth <= NARROW.width,
      "narrow viewport has horizontal overflow",
    );

    scenario = "empty";
    await page.reload();
    await page
      .getByText("当前没有进行中的批次。建立批次后会显示统计。")
      .waitFor();
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "empty.png"),
      fullPage: true,
    });

    scenario = "network-failure";
    expectStatisticsNetworkFailure = true;
    await page.reload();
    await page
      .getByRole("alert")
      .getByText("批次统计读取失败，请重试。")
      .waitFor();
    await expectEnabled(page.getByRole("button", { name: "录入兔只" }));
    const batchListBeforeRetry = batchListRequests;
    const statisticsBeforeRetry = statisticsRequests;
    scenario = "ready";
    await page.getByRole("button", { name: "重试" }).click();
    await page.getByTestId("batch-statistics").waitFor();
    await assertMetric(page, "产崽窝数", "12");
    assert.equal(batchListRequests, batchListBeforeRetry);
    assert.ok(statisticsRequests > statisticsBeforeRetry);
    expectStatisticsNetworkFailure = false;
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "network-failure.png"),
      fullPage: true,
    });

    assert.deepEqual(
      consoleErrors,
      [],
      `browser console errors:\n${consoleErrors.join("\n")}`,
    );
  } finally {
    await browser?.close();
    server.kill("SIGTERM");
  }
}

async function assertMetric(page, label, value) {
  const metric = page.getByTestId("batch-statistics").getByText(label);
  await metric.waitFor();
  const container = metric.locator("..");
  assert.equal(await container.locator("dd").textContent(), value);
}

async function expectEnabled(locator) {
  assert.equal(await locator.isEnabled(), true);
}

main().catch((error) => {
  process.stderr.write(`${error}\n`);
  process.exitCode = 1;
});
