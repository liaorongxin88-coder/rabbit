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
const SALES_ROW_CODES = [
  "TOTAL_SALES_AMOUNT",
  "SALES_PRICE_PER_KG",
  "SALES_PRICE_PER_RABBIT",
];

const house = {
  id: 7,
  name: "统计验收兔场",
  layoutRows: 1,
  layoutCols: 1,
  layoutLayers: 1,
};
const secondHouse = {
  id: 8,
  name: "切换验收兔场",
  layoutRows: 1,
  layoutCols: 1,
  layoutLayers: 1,
};
const batch = {
  id: 42,
  houseId: 7,
  batchCode: "B-042",
  status: "ACTIVE",
  startDate: "2024-04-22",
  remark: "浏览器验收批次",
};

const metricCatalog = [
  [
    "MATING_DATE",
    "配种日期",
    "MATING",
    "配种",
    "DATE",
    "DATE_RANGE",
    "配种日期按业务自然日去重",
  ],
  [
    "MATED_DOE_COUNT",
    "配种母兔数",
    "MATING",
    "配种",
    "COUNT",
    "INTEGER",
    "已配种周期中的去重母兔数",
  ],
  [
    "CONCEPTION_RATE",
    "受胎率",
    "MATING",
    "配种",
    "PERCENT",
    "PERCENT_2",
    "确认怀孕周期数 / 已配种周期数",
  ],
  [
    "DOE_BUCK_RATIO",
    "配种母兔/公兔比例",
    "MATING",
    "配种",
    "RATIO",
    "RATIO_TO_ONE",
    "去重配种母兔数 / 去重参与配种公兔数",
  ],
  [
    "PREGNANT_DOE_COUNT",
    "怀孕数量",
    "PREGNANCY",
    "怀孕",
    "COUNT",
    "INTEGER",
    "确认怀孕周期中的去重母兔数",
  ],
  [
    "ABORTION_RATE",
    "流产率",
    "PREGNANCY",
    "怀孕",
    "PERCENT",
    "PERCENT_2",
    "已怀孕流产周期数 / 确认怀孕周期数",
  ],
  [
    "DELIVERED_LITTER_COUNT",
    "产崽窝数",
    "BIRTH",
    "产崽",
    "LITTER",
    "INTEGER",
    "批次内产崽窝数",
  ],
  [
    "TOTAL_KIT_COUNT",
    "产崽总数",
    "BIRTH",
    "产崽",
    "COUNT",
    "INTEGER",
    "批次内产崽数之和",
  ],
  [
    "AVERAGE_KITS_PER_LITTER",
    "平均窝产数",
    "BIRTH",
    "产崽",
    "COUNT_PER_LITTER",
    "DECIMAL_2",
    "产崽总数 / 产崽窝数",
  ],
  [
    "LIVE_KIT_COUNT",
    "活崽总数",
    "BIRTH",
    "产崽",
    "COUNT",
    "INTEGER",
    "批次内活崽数之和",
  ],
  [
    "LIVE_BIRTH_RATE",
    "平均活崽率",
    "BIRTH",
    "产崽",
    "PERCENT",
    "PERCENT_2",
    "活崽总数 / 产崽总数",
  ],
  [
    "KEPT_LITTER_COUNT",
    "选留窝数",
    "SELECTION",
    "选留",
    "LITTER",
    "INTEGER",
    "留崽数大于零的窝数",
  ],
  [
    "KEPT_KIT_COUNT",
    "选留总数",
    "SELECTION",
    "选留",
    "COUNT",
    "INTEGER",
    "批次内选留数之和",
  ],
  [
    "KEPT_LIVE_RATE",
    "选留活崽率",
    "SELECTION",
    "选留",
    "PERCENT",
    "PERCENT_2",
    "选留总数 / 活崽总数",
  ],
  [
    "AVERAGE_KEPT_PER_LITTER",
    "窝均选留",
    "SELECTION",
    "选留",
    "COUNT_PER_LITTER",
    "DECIMAL_2",
    "选留总数 / 选留窝数",
  ],
  [
    "WEANED_KIT_COUNT",
    "断奶数量",
    "WEANING",
    "断奶",
    "COUNT",
    "INTEGER",
    "批次内断奶数之和",
  ],
  [
    "AVERAGE_WEANING_WEIGHT",
    "断奶均重",
    "WEANING",
    "断奶",
    "KG_PER_RABBIT",
    "DECIMAL_2",
    "断奶总重快照之和 / 断奶数量",
  ],
  [
    "WEANING_SURVIVAL_RATE",
    "断奶成活率",
    "WEANING",
    "断奶",
    "PERCENT",
    "PERCENT_2",
    "断奶数量 / 选留总数",
  ],
  [
    "SOLD_RABBIT_COUNT",
    "出栏数量",
    "OUTBOUND",
    "出栏",
    "COUNT",
    "INTEGER",
    "批次快照匹配的已销售兔只数",
  ],
  [
    "OUTBOUND_SURVIVAL_RATE",
    "出栏成活率",
    "OUTBOUND",
    "出栏",
    "PERCENT",
    "PERCENT_2",
    "出栏数量 / 断奶数量",
  ],
  [
    "SOLD_WEIGHT",
    "出栏总重",
    "OUTBOUND",
    "出栏",
    "KG",
    "DECIMAL_2",
    "批次销售实际重量之和",
  ],
  [
    "AVERAGE_SOLD_WEIGHT",
    "出栏均重",
    "OUTBOUND",
    "出栏",
    "KG_PER_RABBIT",
    "DECIMAL_2",
    "出栏总重 / 出栏数量",
  ],
  [
    "TOTAL_SALES_AMOUNT",
    "总销售金额",
    "SALES",
    "销售",
    "CNY",
    "DECIMAL_2",
    "批次销售金额快照之和",
  ],
  [
    "SALES_PRICE_PER_KG",
    "销售单价（重量口径）",
    "SALES",
    "销售",
    "CNY_PER_KG",
    "DECIMAL_2",
    "总销售金额 / 出栏总重",
  ],
  [
    "SALES_PRICE_PER_RABBIT",
    "销售单价（只数口径）",
    "SALES",
    "销售",
    "CNY_PER_RABBIT",
    "DECIMAL_2",
    "总销售金额 / 出栏数量",
  ],
  [
    "FULL_FEED_CONVERSION_RATIO",
    "全程料肉比",
    "FEED_CONVERSION",
    "料肉比",
    "RATIO",
    "DECIMAL_2",
    "批次全程饲料量 /（商品兔实际销售重量 + 转后备兔实测总重）",
  ],
  [
    "FATTENING_FEED_CONVERSION_RATIO",
    "育肥期料肉比",
    "FEED_CONVERSION",
    "料肉比",
    "RATIO",
    "DECIMAL_2",
    "批次育肥饲料量 /（商品兔实际销售重量 + 转后备兔实测总重 - 断奶总重）",
  ],
  [
    "CARCASS_YIELD_RATE",
    "出肉率",
    "FEED_CONVERSION",
    "料肉比",
    "PERCENT",
    "PERCENT_2",
    "最新出肉率版本",
  ],
];

function metricStatus(code) {
  if (code === "DOE_BUCK_RATIO") return "NOT_APPLICABLE";
  if (code === "SOLD_WEIGHT") return "DATA_MISSING";
  if (code === "CARCASS_YIELD_RATE") return "NOT_RECORDED";
  return "AVAILABLE";
}

function metricFixtureDisplay(code, index, status) {
  if (status !== "AVAILABLE") return null;
  if (code === "MATING_DATE") return "2024-04-22 至 2024-04-23（2 天）";
  if (code === "MATED_DOE_COUNT") return "0";
  return `${index + 1}.00`;
}

function metricFixtureCauses(status) {
  if (status === "DATA_MISSING") {
    return [
      {
        code: "MISSING_BATCH_SALE_ALLOCATION",
        message: "历史批次销售重量缺失",
      },
    ];
  }
  if (status === "NOT_APPLICABLE") {
    return [{ code: "ZERO_DENOMINATOR", message: "分母为零" }];
  }
  if (status === "NOT_RECORDED") {
    return [{ code: "CARCASS_YIELD_NOT_RECORDED", message: "尚未录入出肉率" }];
  }
  return [];
}

function buildMetrics() {
  return metricCatalog.map(
    ([code, name, stage, stageName, unit, format, formula], index) => {
      const status = metricStatus(code);
      const dateValue =
        code === "MATING_DATE"
          ? {
              firstDate: "2024-04-22",
              lastDate: "2024-04-23",
              dateCount: 2,
              dailyCycleCounts: [
                { date: "2024-04-22", cycleCount: 8 },
                { date: "2024-04-23", cycleCount: 4 },
              ],
            }
          : null;
      const displayValue = metricFixtureDisplay(code, index, status);
      return {
        code,
        name,
        stage,
        stageName,
        order: (index + 1) * 10,
        excelColumnName: name,
        valueType: code === "MATING_DATE" ? "DATE_RANGE" : "NUMBER",
        unit,
        format,
        formula,
        status,
        numericValue:
          status === "AVAILABLE" && code !== "MATING_DATE"
            ? format === "PERCENT_2"
              ? 0.5
              : index + 1
            : null,
        displayValue,
        dateValue,
        numerator: code.includes("RATE")
          ? { code: "NUMERATOR", label: "分子", value: 1, unit: "COUNT" }
          : null,
        denominator: code.includes("RATE")
          ? { code: "DENOMINATOR", label: "分母", value: 2, unit: "COUNT" }
          : null,
        components: [],
        missingCauses: metricFixtureCauses(status),
      };
    },
  );
}

const dashboardSummary = {
  selectedHouseId: 7,
  houseCount: 1,
  year: 2026,
  totalRabbits: 0,
  seedRabbits: 0,
  maleRabbits: 0,
  femaleRabbits: 0,
  bredRabbits: 0,
  readyForBreeding: 0,
  litters: 0,
  nursingKits: 0,
  commodityRabbits: 0,
  replacementRabbits: 0,
  liveRate: 0,
  monthlyBirths: Array(12).fill(0),
  monthlyWeaned: Array(12).fill(0),
};

const unknownMetric = {
  ...buildMetrics()[1],
  code: "SERVER_ADDED_METRIC",
  name: "服务端新增指标",
  order: 290,
  excelColumnName: "服务端新增指标",
  displayValue: "29.00",
  numericValue: 29,
};

const statistics = {
  schemaVersion: 1,
  batchId: 42,
  houseName: house.name,
  batchCode: batch.batchCode,
  calculatedAt: "2026-09-04T03:20:00Z",
  totalLitters: 12,
  totalKits: 96,
  totalLiveKits: 90,
  totalWeaned: 78,
  metrics: [...buildMetrics(), unknownMetric],
};

const fullPermission = {
  perms: "edit",
  role: "OWNER",
  isAdmin: false,
  permissions: [
    "rabbit:batches:query",
    "rabbit:batches:edit",
    "rabbit:audit:list",
    "rabbit:reports:export",
  ],
};
const readOnlyPermission = {
  perms: "view",
  role: "VIEWER",
  isAdmin: false,
  permissions: ["rabbit:batches:query"],
};

function apiResponse(data) {
  return JSON.stringify({ code: 0, message: "ok", data });
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
    if (server.exitCode !== null)
      throw new Error("Vite exited before becoming available");
    if (await serverIsReady(port)) return;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error("Vite did not become available within 30 seconds");
}

async function main() {
  mkdirSync(ARTIFACT_DIR, { recursive: true });
  const port = await freePort();
  const baseUrl = `http://127.0.0.1:${port}`;
  const pnpmEntry = process.env.npm_execpath;
  const command = pnpmEntry ? process.execPath : "corepack";
  const args = pnpmEntry
    ? [
        pnpmEntry,
        "exec",
        "vite",
        "--host",
        "127.0.0.1",
        "--port",
        String(port),
        "--strictPort",
      ]
    : [
        "pnpm",
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
      acceptDownloads: true,
    });
    const page = await context.newPage();
    const consoleErrors = [];
    let permission = fullPermission;
    let statisticsMode = "ready";
    let expectNetworkFailure = false;
    let statisticsRequests = 0;
    let batchListRequests = 0;
    let exportRequests = 0;
    let exportMode = "ready";
    let evidenceUploadRequests = 0;
    let carcassFailuresRemaining = 1;
    const carcassRequestIds = [];
    let carcassPayload = null;

    page.on("console", (message) => {
      const expected =
        expectNetworkFailure && message.text().includes("net::ERR_FAILED");
      if (message.type() === "error" && !expected)
        consoleErrors.push(message.text());
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
      window.__batchStatisticsDownloadUrls = { created: 0, revoked: 0 };
      const createObjectURL = URL.createObjectURL.bind(URL);
      const revokeObjectURL = URL.revokeObjectURL.bind(URL);
      URL.createObjectURL = (blob) => {
        window.__batchStatisticsDownloadUrls.created += 1;
        return createObjectURL(blob);
      };
      URL.revokeObjectURL = (url) => {
        window.__batchStatisticsDownloadUrls.revoked += 1;
        return revokeObjectURL(url);
      };
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
        const request = route.request();
        const pathname = new URL(request.url()).pathname;
        if (pathname === "/api/houses")
          return route.fulfill({
            contentType: "application/json",
            body: apiResponse([house, secondHouse]),
          });
        if (pathname === "/api/houses/permission")
          return route.fulfill({
            contentType: "application/json",
            body: apiResponse(permission),
          });
        if (pathname === "/api/batches") {
          batchListRequests += 1;
          return route.fulfill({
            contentType: "application/json",
            body: apiResponse([batch]),
          });
        }
        if (pathname === "/api/batches/42") {
          if (request.headers()["x-house-id"] === "8") {
            return route.fulfill({
              contentType: "application/json",
              body: JSON.stringify({ code: 404, message: "批次不存在" }),
            });
          }
          return route.fulfill({
            contentType: "application/json",
            body: apiResponse(batch),
          });
        }
        if (pathname === "/api/rabbits" || pathname === "/api/cages") {
          return route.fulfill({
            contentType: "application/json",
            body: apiResponse([]),
          });
        }
        if (
          pathname === "/api/repro/stage-actions" ||
          pathname === "/api/repro/entry-points" ||
          pathname === "/api/batches/42/batch-rabbits" ||
          pathname === "/api/batches/42/breeding-cycles" ||
          pathname === "/api/batches/42/weaning-records"
        ) {
          return route.fulfill({
            contentType: "application/json",
            body: apiResponse([]),
          });
        }
        if (pathname === "/api/reports/dashboard") {
          return route.fulfill({
            contentType: "application/json",
            body: apiResponse(dashboardSummary),
          });
        }
        if (pathname === "/api/tasks") {
          return route.fulfill({
            contentType: "application/json",
            body: apiResponse({ items: [], total: 0, page: 1, size: 1 }),
          });
        }
        if (pathname === "/api/batches/42/statistics") {
          statisticsRequests += 1;
          assert.equal(
            request.headers().authorization,
            "Bearer browser-fixture-token",
          );
          if (request.headers()["x-house-id"] === "8") {
            return route.fulfill({
              contentType: "application/json",
              body: JSON.stringify({ code: 404, message: "批次不存在" }),
            });
          }
          assert.equal(request.headers()["x-house-id"], "7");
          if (statisticsMode === "failure") return route.abort("failed");
          if (statisticsMode === "slow")
            await new Promise((resolve) => setTimeout(resolve, 1_000));
          return route.fulfill({
            contentType: "application/json",
            body: apiResponse(statistics),
          });
        }
        if (pathname === "/api/reports/batches/42/statistics.xlsx") {
          exportRequests += 1;
          assert.equal(request.headers()["x-house-id"], "7");
          assert.equal(
            request.headers().authorization,
            "Bearer browser-fixture-token",
          );
          await new Promise((resolve) => setTimeout(resolve, 250));
          if (exportMode === "json-error") {
            return route.fulfill({
              contentType: "application/json",
              body: JSON.stringify({
                code: 503,
                message: "Excel 服务暂不可用",
              }),
            });
          }
          return route.fulfill({
            contentType:
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            headers: {
              "Content-Disposition":
                "attachment; filename=batch-42.xlsx; filename*=UTF-8''%E6%89%B9%E6%AC%A1-B-042-%E7%BB%9F%E8%AE%A1.xlsx",
            },
            body: Buffer.from("mock xlsx"),
          });
        }
        if (pathname === "/api/business-files/images") {
          evidenceUploadRequests += 1;
          return route.fulfill({
            contentType: "application/json",
            body: apiResponse({ fileId: "yield-evidence-file" }),
          });
        }
        if (
          pathname === "/api/batches/42/carcass-yields" &&
          request.method() === "POST"
        ) {
          carcassPayload = request.postDataJSON();
          carcassRequestIds.push(carcassPayload.requestId);
          if (carcassFailuresRemaining > 0) {
            carcassFailuresRemaining -= 1;
            return route.abort("failed");
          }
          return route.fulfill({
            contentType: "application/json",
            body: apiResponse({
              id: 1,
              houseId: 7,
              batchId: 42,
              ...carcassPayload,
              createdBy: 71,
              createdByName: "验收人员",
              createdAt: "2026-09-04T03:30:00Z",
            }),
          });
        }
        if (pathname === "/api/batches/42/carcass-yields") {
          return route.fulfill({
            contentType: "application/json",
            body: apiResponse({
              items: [
                {
                  id: 1,
                  houseId: 7,
                  batchId: 42,
                  yieldRate: 0.56,
                  sourceUnit: "验收屠宰场",
                  measuredDate: "2024-08-01",
                  reportNumber: "REPORT-42",
                  evidenceFileId: "yield-evidence-file",
                  remark: "抽检样本",
                  changeReason: "首次录入",
                  requestId: "yield-1",
                  createdBy: 71,
                  createdByName: "验收人员",
                  createdAt: "2026-09-04T03:30:00Z",
                },
              ],
              total: 1,
              page: 1,
              pageSize: 20,
            }),
          });
        }
        return route.fulfill({
          contentType: "application/json",
          body: apiResponse([]),
        });
      },
    );

    statisticsMode = "slow";
    await page.goto(`${baseUrl}/workspace/production/batches/42`);
    await page.locator('[aria-label="正在读取完整批次统计"]').waitFor();
    await page.getByTestId("batch-statistics-panel").waitFor();
    assert.equal(
      batchListRequests,
      0,
      "direct detail route must not list batches",
    );
    assert.equal(await page.locator("[data-metric-code]").count(), 28);
    assert.equal(
      await page.locator('[data-metric-code="SERVER_ADDED_METRIC"]').count(),
      0,
    );
    assert.equal(
      await page.getByRole("heading", { name: "其他指标" }).count(),
      0,
    );
    await assertMetric(page, "MATED_DOE_COUNT", "0");
    await assertMetric(page, "DOE_BUCK_RATIO", "暂无可计算数据");
    await assertMetric(page, "SOLD_WEIGHT", "历史数据缺失");
    await assertMetric(page, "CARCASS_YIELD_RATE", "未录入");
    await page
      .locator('[data-metric-code="MATING_DATE"]')
      .locator("xpath=../../..")
      .getByText("查看口径")
      .click();
    await page.getByText("2024-04-22：8 个周期").waitFor();
    assert.equal(await metricRowColumnCount(page, "MATED_DOE_COUNT"), 2);
    await assertSalesMetricPeerLayout(page, 3);
    await assertNoOverflow(page, DESKTOP.width);
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "desktop-detail.png"),
      fullPage: true,
    });

    await page.setViewportSize(NARROW);
    assert.equal(await metricRowColumnCount(page, "MATED_DOE_COUNT"), 1);
    await assertSalesMetricPeerLayout(page, 1);
    await assertNoOverflow(page, NARROW.width);
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "narrow-detail.png"),
      fullPage: true,
    });

    await page.setViewportSize(DESKTOP);
    await page.evaluate(() => {
      document.documentElement.style.fontSize = "200%";
    });
    assert.equal(await metricRowColumnCount(page, "MATED_DOE_COUNT"), 1);
    await assertSalesMetricPeerLayout(page, 1);
    await assertNoOverflow(page, DESKTOP.width);
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "text-scale-200.png"),
      fullPage: true,
    });
    await page.evaluate(() => {
      document.documentElement.style.fontSize = "";
    });
    await page.setViewportSize(DESKTOP);

    statisticsMode = "slow";
    await page.getByRole("button", { name: "刷新统计" }).click();
    await page.locator('[aria-label="选择兔场"]:visible').click();
    await page.getByRole("option", { name: secondHouse.name }).click();
    await page.getByRole("heading", { name: "批次不存在" }).waitFor();
    await page.waitForTimeout(1_100);
    assert.equal(await page.getByTestId("batch-statistics-panel").count(), 0);
    assert.equal(
      await page.getByRole("heading", { name: "批次 B-042" }).count(),
      0,
    );
    statisticsMode = "ready";
    await page.locator('[aria-label="选择兔场"]:visible').click();
    await page.getByRole("option", { name: house.name }).click();
    await page.getByTestId("batch-statistics-panel").waitFor();

    statisticsMode = "failure";
    expectNetworkFailure = true;
    const beforeRefresh = statisticsRequests;
    await page.getByRole("button", { name: "刷新统计" }).click();
    await page.getByText("更新失败，当前保留上次成功统计。").waitFor();
    assert.ok(statisticsRequests > beforeRefresh);
    await assertMetric(page, "MATED_DOE_COUNT", "0");
    await page.getByText(/取数时间：/).waitFor();
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "refresh-failure-retained.png"),
      fullPage: true,
    });
    expectNetworkFailure = false;
    statisticsMode = "ready";

    await page.getByRole("button", { name: "录入出肉率" }).click();
    await page.getByLabel("出肉率（%）").fill("56");
    await page.getByLabel("来源单位").fill("验收屠宰场");
    await page.getByLabel("检测或屠宰日期").fill("2024-08-01");
    await page.getByLabel("凭证图片").setInputFiles({
      name: "yield.png",
      mimeType: "image/png",
      buffer: Buffer.from("image"),
    });
    const saveCarcassButton = page.getByRole("button", { name: "保存版本" });
    expectNetworkFailure = true;
    await saveCarcassButton.click();
    await page.getByText("无法连接 API 服务，请检查网络或跨域配置").waitFor();
    await page.waitForFunction(() => {
      const button = [...document.querySelectorAll("button")].find((item) =>
        item.textContent?.includes("保存版本"),
      );
      return button instanceof HTMLButtonElement && !button.disabled;
    });
    expectNetworkFailure = false;
    await saveCarcassButton.click();
    await page
      .getByRole("heading", { name: "录入出肉率", exact: true })
      .waitFor({ state: "hidden" });
    assert.equal(carcassPayload.yieldRate, 0.56);
    assert.equal(carcassPayload.changeReason, "首次录入");
    assert.equal(carcassPayload.evidenceFileId, "yield-evidence-file");
    assert.equal(evidenceUploadRequests, 1);
    assert.equal(carcassRequestIds.length, 2);
    assert.equal(carcassRequestIds[0], carcassRequestIds[1]);
    assert.ok(carcassPayload.requestId);

    await page.getByRole("button", { name: "出肉率历史" }).click();
    await page.getByText("验收屠宰场").waitFor();
    await page.getByText("报告 REPORT-42 · 已保存凭证").waitFor();
    await page.getByText("抽检样本").waitFor();
    await page.getByRole("button", { name: "关闭" }).first().click();

    const exportButton = page.getByRole("button", { name: "导出 Excel" });
    exportMode = "json-error";
    await exportButton.click();
    await page.getByText("Excel 服务暂不可用").waitFor();
    await assertMetric(page, "MATED_DOE_COUNT", "0");
    await page.waitForFunction(() => {
      const button = [...document.querySelectorAll("button")].find((item) =>
        item.textContent?.includes("导出 Excel"),
      );
      return button instanceof HTMLButtonElement && !button.disabled;
    });

    exportMode = "ready";
    const downloadPromise = page.waitForEvent("download");
    await exportButton.evaluate((button) => {
      if (button instanceof HTMLElement) button.click();
    });
    await page.waitForFunction(() => {
      const button = [...document.querySelectorAll("button")].find((item) =>
        item.textContent?.includes("导出 Excel"),
      );
      return button instanceof HTMLButtonElement && button.disabled;
    });
    await exportButton.evaluate((button) => {
      if (button instanceof HTMLElement) button.click();
    });
    const download = await downloadPromise;
    assert.equal(download.suggestedFilename(), "批次-B-042-统计.xlsx");
    await page.waitForFunction(
      () =>
        window.__batchStatisticsDownloadUrls.created === 1 &&
        window.__batchStatisticsDownloadUrls.revoked === 1,
    );
    assert.equal(exportRequests, 2);

    permission = readOnlyPermission;
    await page.reload();
    await page.getByTestId("batch-statistics-panel").waitFor();
    assert.equal(
      await page.getByRole("button", { name: "录入出肉率" }).count(),
      0,
    );
    assert.equal(
      await page.getByRole("button", { name: "出肉率历史" }).count(),
      0,
    );
    assert.equal(
      await page.getByRole("button", { name: "导出 Excel" }).count(),
      0,
    );
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "read-only-permissions.png"),
      fullPage: true,
    });

    await page.goto(`${baseUrl}/workspace/production`);
    await page.getByRole("link", { name: "详情" }).waitFor();
    assert.equal(
      await page.getByRole("link", { name: "详情" }).getAttribute("href"),
      "/workspace/production/batches/42",
    );
    await page.setViewportSize(NARROW);
    await page.getByRole("link", { name: "查看批次详情" }).waitFor();
    await assertNoOverflow(page, NARROW.width);
    await page.setViewportSize(DESKTOP);

    await page.goto(`${baseUrl}/workspace/dashboard`);
    await page.getByRole("link", { name: "查看完整统计" }).waitFor();
    assert.equal(
      await page
        .getByRole("link", { name: "查看完整统计" })
        .getAttribute("href"),
      "/workspace/production/batches/42",
    );

    statisticsMode = "failure";
    expectNetworkFailure = true;
    await page.goto(`${baseUrl}/workspace/production/batches/42`);
    await page.getByText("批次统计读取失败，请重试。").waitFor();
    await page.getByRole("heading", { name: "批次 B-042" }).waitFor();
    await page.screenshot({
      path: path.join(ARTIFACT_DIR, "initial-statistics-error.png"),
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

async function assertMetric(page, code, expected) {
  assert.equal(
    await page.locator(`[data-metric-code="${code}"]`).textContent(),
    expected,
  );
}

async function metricRowColumnCount(page, code) {
  return page.locator(`[data-metric-code="${code}"]`).evaluate((metric) => {
    const row = metric.closest("[data-metric-row]");
    if (!row)
      throw new Error(`metric row missing for ${metric.dataset.metricCode}`);
    return getComputedStyle(row).gridTemplateColumns.split(" ").filter(Boolean)
      .length;
  });
}

async function assertSalesMetricPeerLayout(page, expectedColumns) {
  const row = page.locator(`[data-metric-row="${SALES_ROW_CODES.join(" ")}"]`);
  await row.waitFor();

  const layout = await row.evaluate((element) => {
    const items = [...element.querySelectorAll(":scope > [data-metric-item]")];
    const boxes = items.map((item) => {
      const rect = item.getBoundingClientRect();
      return {
        code: item.getAttribute("data-metric-item"),
        left: rect.left,
        right: rect.right,
        top: rect.top,
        bottom: rect.bottom,
        clippedHorizontally: item.scrollWidth > item.clientWidth + 1,
        clippedVertically: item.scrollHeight > item.clientHeight + 1,
        metricCount: item.querySelectorAll("[data-metric-code]").length,
        nestedItemCount: item.querySelectorAll("[data-metric-item]").length,
      };
    });
    return {
      boxes,
      columns: getComputedStyle(element)
        .gridTemplateColumns.split(" ")
        .filter(Boolean).length,
    };
  });

  assert.deepEqual(
    layout.boxes.map((box) => box.code),
    SALES_ROW_CODES,
    "sales metrics must remain peer items in metric order",
  );
  assert.equal(layout.columns, expectedColumns);
  assert.ok(
    layout.boxes.every(
      (box) =>
        box.metricCount === 1 &&
        box.nestedItemCount === 0 &&
        !box.clippedHorizontally &&
        !box.clippedVertically,
    ),
    "sales metric items must be independent and unclipped",
  );

  for (let leftIndex = 0; leftIndex < layout.boxes.length; leftIndex += 1) {
    const left = layout.boxes[leftIndex];
    for (
      let rightIndex = leftIndex + 1;
      rightIndex < layout.boxes.length;
      rightIndex += 1
    ) {
      const right = layout.boxes[rightIndex];
      const overlaps =
        left.left < right.right - 1 &&
        left.right > right.left + 1 &&
        left.top < right.bottom - 1 &&
        left.bottom > right.top + 1;
      assert.equal(overlaps, false, `${left.code} overlaps ${right.code}`);
    }
  }

  if (expectedColumns === 3) {
    assert.ok(
      layout.boxes.every((box) => Math.abs(box.top - layout.boxes[0].top) <= 1),
      "wide sales metrics must share one row",
    );
  } else {
    for (let index = 1; index < layout.boxes.length; index += 1) {
      assert.ok(
        layout.boxes[index].top > layout.boxes[index - 1].bottom,
        "single-column sales metrics must be vertically ordered",
      );
    }
  }

  for (const code of SALES_ROW_CODES) {
    const item = row.locator(`[data-metric-item="${code}"]`);
    const details = item.locator("details");
    const summary = details.locator("summary");
    await summary.scrollIntoViewIfNeeded();
    assert.equal(await summary.isVisible(), true);
    await summary.focus();
    assert.equal(
      await summary.evaluate((element) => document.activeElement === element),
      true,
      `${code} detail control must accept focus`,
    );
    await summary.click();
    assert.equal(await details.evaluate((element) => element.open), true);
    await item.getByText(/^公式：/).waitFor();
    const clipped = await item.evaluate(
      (element) =>
        element.scrollWidth > element.clientWidth + 1 ||
        element.scrollHeight > element.clientHeight + 1,
    );
    assert.equal(clipped, false, `${code} expanded detail must not be clipped`);
    await summary.click();
  }
}

async function assertNoOverflow(page, viewportWidth) {
  const documentWidth = await page.evaluate(
    () => document.documentElement.scrollWidth,
  );
  assert.ok(
    documentWidth <= viewportWidth,
    `viewport has horizontal overflow: ${documentWidth} > ${viewportWidth}`,
  );
}

main().catch((error) => {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
});
