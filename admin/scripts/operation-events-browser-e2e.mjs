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
const ARTIFACT_DIR = path.join(ADMIN_DIR, "build/browser-e2e/operation-events");
const DESKTOP = { width: 1440, height: 900 };
const NARROW = { width: 390, height: 844 };
const RABBIT_ID = 31;

const house = {
  id: 7,
  name: "事件流验收兔场",
  layoutRows: 1,
  layoutCols: 1,
  layoutLayers: 1,
};

const cage = {
  id: 11,
  houseId: 7,
  cageNumber: "1-1-1",
  status: "0",
  rabbitCount: 1,
  isFed: true,
  isEnabled: true,
};

const rabbit = {
  id: RABBIT_ID,
  houseId: 7,
  cageId: 11,
  earTag: "E-031",
  type: "0",
  gender: "1",
  status: "0",
  weight: 2.4,
  isActive: true,
};

function permission(canReadAudit) {
  return {
    perms: "control",
    role: "OWNER",
    isAdmin: false,
    permissions: [
      "rabbit:batches:query",
      "rabbit:rabbits:edit",
      "rabbit:rabbits:control",
      ...(canReadAudit ? ["rabbit:audit:list"] : []),
    ],
  };
}

function apiResponse(data) {
  return JSON.stringify({ code: 0, data });
}

function eventRow(id, overrides = {}) {
  return {
    id,
    occurredAt: "2026-08-29T10:00:00.000Z",
    operationCode: "feed:add",
    eventType: "FEED_RECORDED",
    eventLabel: "投喂",
    targetType: "RABBIT",
    targetId: RABBIT_ID,
    cageId: 11,
    batchId: null,
    rabbitId: RABBIT_ID,
    cycleId: null,
    litterId: null,
    fromStage: null,
    toStage: null,
    operatorId: 71,
    operatorName: "验收人员",
    ...overrides,
  };
}

const FIRST_PAGE = {
  items: [
    eventRow(9, {
      operationCode: "repro:state-machine",
      eventType: "ESTRUS_DONE",
      fromStage: "待催情",
      toStage: "待配种",
    }),
    eventRow(8),
  ],
  nextCursor: "cursor-page-2",
  hasMore: true,
};

// 第二页故意重复带回 id=8，验证边界行不会重复渲染。
const SECOND_PAGE = {
  items: [eventRow(8), eventRow(7, { operationCode: "weight:create" })],
  nextCursor: null,
  hasMore: false,
};

function eventPage(scenario, cursor) {
  if (scenario === "empty") {
    return { items: [], nextCursor: null, hasMore: false };
  }
  return cursor ? SECOND_PAGE : FIRST_PAGE;
}

function fixtureData(pathname, scenario) {
  if (pathname === "/api/houses") return [house];
  if (pathname === "/api/houses/permission") {
    return permission(scenario !== "no-permission");
  }
  if (pathname === "/api/cages") return [cage];
  if (pathname === `/api/rabbits/${RABBIT_ID}`) return rabbit;
  if (pathname === "/api/vaccinations") return [];
  if (pathname === "/api/repro/stage-actions") return [];
  if (pathname === "/api/batches") return [];
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
  const server = spawn(command, args, { cwd: ADMIN_DIR, stdio: "ignore" });
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
    let expectEventsNetworkFailure = false;
    let rabbitRequests = 0;
    let eventRequests = 0;

    page.on("console", (message) => {
      const isExpectedNetworkFailure =
        expectEventsNetworkFailure && message.text().includes("net::ERR_FAILED");
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
        const requestUrl = new URL(route.request().url());
        const pathname = requestUrl.pathname;

        if (pathname === "/api/operation-events") {
          eventRequests += 1;
          if (scenario === "network-failure") {
            await route.abort("failed");
            return;
          }
          // 只给 targetId 不给 targetType 会被后端拒绝，这里断言前端不会那样发。
          assert.ok(
            requestUrl.searchParams.get("targetType"),
            "operation events request must carry targetType",
          );
          await route.fulfill({
            contentType: "application/json",
            body: apiResponse(
              eventPage(scenario, requestUrl.searchParams.get("cursor")),
            ),
          });
          return;
        }

        if (pathname === `/api/rabbits/${RABBIT_ID}`) rabbitRequests += 1;
        await route.fulfill({
          contentType: "application/json",
          body: apiResponse(fixtureData(pathname, scenario)),
        });
      },
    );

    const detailUrl = `${baseUrl}/workspace/livestock/rabbits/${RABBIT_ID}`;
    await page.goto(detailUrl);
    const stream = page.getByTestId("operation-event-stream");
    try {
      await stream.waitFor({ timeout: 10_000 });
    } catch (error) {
      const pageText = (await page.locator("body").textContent()) ?? "";
      throw new Error(
        `operation event stream did not render at ${page.url()}:\n${pageText}\n${consoleErrors.join("\n")}`,
        { cause: error },
      );
    }
    await stream.getByText("繁育流程").waitFor();
    await stream.getByText("待催情 → 待配种").waitFor();
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "desktop.png"),
      fullPage: true,
    });

    // 加载更多：第二页重复带回的 id=8 不能变成两行。
    await stream.getByRole("button", { name: "加载更多" }).click();
    await stream.getByText("称重").waitFor();
    assert.equal(
      await stream.getByRole("row").count(),
      4,
      "表头 1 行 + 去重后 3 条事件",
    );
    await assert.rejects(
      stream.getByRole("button", { name: "加载更多" }).waitFor({
        timeout: 1_000,
      }),
      "最后一页不该还留着加载更多",
    );
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "load-more.png"),
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
    // 视口必须切回去，否则后面的截图都会是窄屏画面。
    await page.setViewportSize(DESKTOP);

    scenario = "empty";
    await page.reload();
    await page.getByTestId("operation-event-stream-empty").waitFor();
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "empty.png"),
      fullPage: true,
    });

    scenario = "network-failure";
    expectEventsNetworkFailure = true;
    await page.reload();
    await page.getByTestId("operation-event-stream-error").waitFor();
    // 事件流挂掉时，兔只本身的信息和操作入口必须照常可用。
    await page.getByText(`兔 #${RABBIT_ID}`).first().waitFor();
    await expectEnabled(page.getByRole("button", { name: "编辑" }));
    // 先存失败态本身，否则截图只能证明重试后的成功页面。
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "network-failure.png"),
      fullPage: true,
    });

    const rabbitBeforeRetry = rabbitRequests;
    const eventsBeforeRetry = eventRequests;
    scenario = "ready";
    await page
      .getByTestId("operation-event-stream-error")
      .getByRole("button", { name: "重试" })
      .click();
    await stream.getByText("繁育流程").waitFor();
    assert.equal(
      rabbitRequests,
      rabbitBeforeRetry,
      "重试只该重拉事件流，不该重拉兔只详情",
    );
    assert.ok(eventRequests > eventsBeforeRetry);
    expectEventsNetworkFailure = false;
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "retry-recovered.png"),
      fullPage: true,
    });

    scenario = "no-permission";
    await page.reload();
    await page.getByText(`兔 #${RABBIT_ID}`).first().waitFor();
    assert.equal(
      await page.getByTestId("operation-event-stream").count(),
      0,
      "没有 rabbit:audit:list 时不应出现操作记录区块",
    );
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "no-permission.png"),
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

async function expectEnabled(locator) {
  assert.equal(await locator.isEnabled(), true);
}

main().catch((error) => {
  process.stderr.write(`${error}\n`);
  process.exitCode = 1;
});
