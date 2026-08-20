#!/usr/bin/env node
/**
 * 后台（浏览器）验收：笼内兔只逐只管理、登记离场、换笼位对调/并笼、种母兔录入入轨。
 *
 * 对应飞书 recvrpTL16SBwu、recvqh5TC8wd3y、recvsrEA6TRuK6、recvsrnEJ8bKrk、recvsrpMlvu2SC。
 *
 * 为什么用真浏览器而不是组件测试：这一轮改的全是「点开对话框、选一个笼位、
 * 勾一个确认框、看提示语」这类只在真实 DOM 与真实后端之间才会出错的东西。
 * lint 与 tsc 证明不了「换笼下拉里有没有那个占用的笼位」。
 *
 * 与 app/scripts/android_cage_ops_e2e.sh 同一套口径：
 *   隔离 fixture → 真实后端 → 真实界面操作 → 截图 → 数据库断言（actual=expected）。
 *
 * 用法：
 *   node scripts/admin_cage_ops_browser_e2e.mjs            # 无头
 *   HEADED=1 node scripts/admin_cage_ops_browser_e2e.mjs   # 看着它点
 */

import { spawn, spawnSync } from 'node:child_process'
import { createServer } from 'node:net'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))
const ADMIN_DIR = path.resolve(SCRIPT_DIR, '..')
const REPO_DIR = path.resolve(ADMIN_DIR, '..')
const FIXTURE = path.join(REPO_DIR, 'backend/src/test/resources/fixtures/cage_ops_fixture.sql')

const API_BASE = process.env.RABBIT_API_BASE_URL ?? 'http://127.0.0.1:8080'
const DB_CONTAINER = process.env.RABBIT_DB_CONTAINER ?? 'rabbit_mysql_1'
const DB_NAME = process.env.RABBIT_DB_NAME ?? 'rabbit_app'
const DB_USER = process.env.RABBIT_DB_USER ?? 'root'
const DB_PASSWORD = process.env.RABBIT_DB_PASSWORD ?? 'rabbit_root'
const PASSWORD = '123456'
const HEADED = process.env.HEADED === '1'
const DESKTOP = { width: 1440, height: 900 }
const NARROW = { width: 390, height: 844 }

function fail(message, code = 1) {
  console.error(`\n${message}`)
  process.exit(code)
}

function mysql(sql) {
  const result = spawnSync(
    'docker',
    ['exec', '-e', `MYSQL_PWD=${DB_PASSWORD}`, '-i', DB_CONTAINER,
      'mysql', '--default-character-set=utf8mb4', '-N', '-B', `-u${DB_USER}`, DB_NAME, '-e', sql],
    { encoding: 'utf8' },
  )
  if (result.status !== 0) {
    fail(`mysql failed: ${result.stderr || result.stdout}`, 65)
  }
  return result.stdout.trim()
}

async function main() {
  // ---------------------------------------------------------------- fixture
  const fixtureSql = readFileSync(FIXTURE, 'utf8')
  const seeded = spawnSync(
    'docker',
    ['exec', '-e', `MYSQL_PWD=${DB_PASSWORD}`, '-i', DB_CONTAINER,
      'mysql', '--default-character-set=utf8mb4', `-u${DB_USER}`, DB_NAME],
    { encoding: 'utf8', input: fixtureSql },
  )
  if (seeded.status !== 0) {
    fail(`fixture seeding failed: ${seeded.stderr || seeded.stdout}`, 65)
  }
  const fixtureOutput = seeded.stdout
  const lines = fixtureOutput.split('\n').map((line) => line.split('\t'))
  const header = lines[1] ?? []
  const runId = header[0]
  const houseId = Number(header[1])
  const controlUser = header[3]
  const cageRows = lines.filter((cols) => cols[0] === 'CAGE')
  const rabbitRows = lines.filter((cols) => cols[0] === 'RABBIT')
  const cageId = (number) => Number(cageRows.find((cols) => cols[1] === number)?.[2])
  const rabbitId = (breed) => Number(rabbitRows.find((cols) => cols[1] === breed)?.[2])

  const c1 = cageId('1-1-1')
  const c2 = cageId('1-2-1')
  const c3 = cageId('1-3-1')
  const c4 = cageId('1-4-1')
  const c6 = cageId('1-6-1')
  // 停用空笼：用来验证地图没有把停用笼位默默丢掉。
  const c7 = cageId('1-7-1')
  // 末位：验双面笼架的折行（它应该落在第一位的正下方）。
  const c8 = cageId('1-8-1')
  const doeId = rabbitId('CAGEOPS-DOE')
  const reserveId = rabbitId('CAGEOPS-RESERVE')
  const commAId = rabbitId('CAGEOPS-COMM-A')
  const commBId = rabbitId('CAGEOPS-COMM-B')
  const commCId = rabbitId('CAGEOPS-COMM-C')

  if (!runId || !houseId || !controlUser || !c1 || !c7 || !doeId || !commAId || !commCId) {
    fail(`unable to parse fixture output:\n${fixtureOutput}`, 65)
  }

  const artifactDir = path.join(ADMIN_DIR, 'build/browser-e2e', `cage-ops-${runId}`)
  mkdirSync(artifactDir, { recursive: true })
  writeFileSync(path.join(artifactDir, 'fixture.txt'), fixtureOutput)
  console.log(`fixture run ${runId} house ${houseId} user ${controlUser}`)
  console.log(`artifacts ${artifactDir}`)

  // ------------------------------------------------------- backend staleness
  const login = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userName: controlUser, password: PASSWORD }),
  }).then((res) => res.json())
  if (login.code !== 0) {
    fail(`fixture login failed: ${JSON.stringify(login)}`, 69)
  }
  const token = login.data?.token ?? login.data?.accessToken
  const entryPoints = await fetch(`${API_BASE}/api/repro/entry-points`, {
    headers: { Authorization: `Bearer ${token}`, 'X-House-Id': String(houseId) },
  }).then((res) => res.json())
  if (entryPoints.code !== 0 || !JSON.stringify(entryPoints.data).includes('AWAIT_PALPATION')) {
    fail(
      'Backend image is stale: GET /api/repro/entry-points is not served.\n' +
      'Run: docker compose up -d --build --force-recreate backend',
      78,
    )
  }

  // ------------------------------------------------------------- dev server
  let devServerAlreadyRunning = false

  /** 这个口有没有人在听（不管是谁）。 */
  const portInUse = (port) =>
    new Promise((resolve) => {
      const probe = createServer()
      probe.once('error', () => resolve(true))
      probe.once('listening', () => probe.close(() => resolve(false)))
      probe.listen(port, '127.0.0.1')
    })

  const freePort = () =>
    new Promise((resolve, reject) => {
      const probe = createServer()
      probe.once('error', reject)
      probe.listen(0, '127.0.0.1', () => {
        const { port } = probe.address()
        probe.close(() => resolve(port))
      })
    })

  /**
   * 后端认不认这个来源。用预检请求问一声，把“登录后神秘不跳转”
   * 提前成一句说得清楚的报错。
   */
  const backendAllowsOrigin = async (origin) => {
    try {
      const res = await fetch(`${API_BASE}/api/auth/login`, {
        method: 'OPTIONS',
        headers: {
          Origin: origin,
          'Access-Control-Request-Method': 'POST',
          'Access-Control-Request-Headers': 'content-type',
        },
      })
      return res.headers.get('access-control-allow-origin') === origin
    } catch {
      return false
    }
  }

  /** 这个口上应门的是不是本项目的 dev server。 */
  const servesThisAdmin = async (url) => {
    try {
      const res = await fetch(url, { method: 'GET' })
      return res.ok && (await res.text()).includes('/src/main.tsx')
    } catch {
      return false
    }
  }

  let devServer = null
  let baseUrl = process.env.ADMIN_BASE_URL
  if (!baseUrl) {
    // 首选 5173：后端 CORS 默认白名单写的就是它。被占了就换一个空口，
    // 但换口之后必须先确认后端认这个来源，否则页面能打开、登录被 403 挡下，
    // 现象只是“登录后不跳转”，很难想到是跨域。
    const preferred = Number(process.env.ADMIN_DEV_PORT ?? 5173)
    let port = preferred

    // 占口的服务同样会对 GET / 回 200，不验内容的话，探活会以为“起来了”，
    // 然后在别人的页面上找登录框。
    if (await servesThisAdmin(`http://127.0.0.1:${preferred}`)) {
      console.log(`ℹ 复用已在 127.0.0.1:${preferred} 运行的 admin dev server`)
      devServerAlreadyRunning = true
    } else if (await portInUse(preferred)) {
      port = await freePort()
      console.log(`ℹ 端口 ${preferred} 被其它服务占着，改用 ${port}`)
    }
    baseUrl = `http://127.0.0.1:${port}`

    if (!(await backendAllowsOrigin(baseUrl))) {
      fail(
        `后端不接受来源 ${baseUrl}，登录会被 CORS 挡成 403。\n` +
          `要么腾出端口 ${preferred}（lsof -ti tcp:${preferred}），要么把这个来源声明给后端：\n` +
          `  APP_CORS_ALLOWED_ORIGINS="http://localhost:5173,http://127.0.0.1:5173,${baseUrl}" \\\n` +
          '    docker compose up -d --force-recreate backend',
        72,
      )
    }
    // 必须显式 --host 127.0.0.1：vite 默认只听 localhost，而本机 localhost 先解到 ::1，
    // 探活 127.0.0.1 会一直连不上，看起来像“dev server 没起来”。
    if (!devServerAlreadyRunning) {
    devServer = spawn('pnpm', ['exec', 'vite', '--host', '127.0.0.1', '--port', String(port), '--strictPort'], {
      cwd: ADMIN_DIR,
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    process.on('exit', () => devServer?.kill('SIGTERM'))
    const devLog = []
    devServer.stdout.on('data', (chunk) => devLog.push(chunk.toString()))
    devServer.stderr.on('data', (chunk) => devLog.push(chunk.toString()))
    const deadline = Date.now() + 60_000
    for (;;) {
      if (Date.now() > deadline) {
        writeFileSync(path.join(artifactDir, 'vite.log'), devLog.join(''))
        fail('vite dev server did not become reachable within 60s', 70)
      }
      if (await servesThisAdmin(baseUrl)) break
      await new Promise((resolve) => setTimeout(resolve, 500))
    }
    }
  }
  console.log(`admin at ${baseUrl}`)

  // ---------------------------------------------------------------- browser
  const browser = await chromium.launch({ channel: 'chrome', headless: !HEADED })
  const context = await browser.newContext({ viewport: DESKTOP, locale: 'zh-CN' })
  const page = await context.newPage()
  const consoleErrors = []
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  page.on('pageerror', (error) => consoleErrors.push(String(error)))

  const shots = []
  const shot = async (name) => {
    // 对话框有开场动画，不等它停下来拍出来的是半透明叠影，当不了验收证据。
    await page.waitForTimeout(300)
    await page.screenshot({ path: path.join(artifactDir, `${name}.png`), fullPage: false })
    shots.push(name)
  }

  // sonner 的提示语会自己消失，所以每一步都要在它还在时断言。
  const expectToast = async (text) => {
    await page.getByText(text, { exact: false }).first().waitFor({ timeout: 15_000 })
  }

  // 笼内卡片没有稳定选择器。只用 hasText 会同时命中卡片本体和里面那个只有编号的
  // 表头 div（.last() 拿到的就是它，上面没有按钮），所以再叠一个“必须包含该按钮”。
  const rabbitCard = (dialog, id, buttonName) =>
    dialog
      .locator('div')
      .filter({ hasText: new RegExp(`^兔 #${id}`) })
      .filter({ has: page.getByRole('link', { name: buttonName, exact: true }) })
      .last()

  const assertNoOverflow = async (label) => {
    const overflow = await page.evaluate(() =>
      document.documentElement.scrollWidth - document.documentElement.clientWidth)
    if (overflow > 1) {
      fail(`${label}: horizontal overflow of ${overflow}px at ${NARROW.width}px wide`, 71)
    }
  }

  // “页面不溢出”还不够：表格把列挤成一列一个字也不溢出，但行身份已经读不出来。
  // DESIGN.md 的口径是表格可以横向滚动，所以窄屏下要真的能滚。
  const assertTableScrolls = async (label) => {
    const slack = await page.evaluate(() => {
      const table = document.querySelector('table')
      const box = table?.parentElement
      if (!box) return null
      return box.scrollWidth - box.clientWidth
    })
    if (slack === null) {
      fail(`${label}: no table found to measure`, 71)
    }
    if (slack < 8) {
      fail(`${label}: table collapsed instead of scrolling (slack ${slack}px)`, 71)
    }
  }

  try {
    // 登录兔场工作台
    await page.goto(`${baseUrl}/workspace/login`, { waitUntil: 'domcontentloaded' })
    await page.fill('#workspace-user-name', controlUser)
    await page.fill('#workspace-password', PASSWORD)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL(/\/workspace\/dashboard/, { timeout: 30_000 })

    // 选中 fixture 兔场，再进兔群管理
    await page.getByRole('combobox', { name: '选择兔场' }).click()
    await page.getByRole('option', { name: new RegExp(`H-CAGEOPS-${runId}`) }).click()
    await page.getByRole('link', { name: '兔群管理' }).first().click()
    await page.waitForURL(/\/workspace\/livestock/, { timeout: 30_000 })
    await page.getByRole('tab', { name: /笼位/ }).click()

    // 笼位区默认是分层地图（排 → 层 → 位），格子上不写编号，所以认 testid 不认文字。
    await page.locator('[data-testid="cage-map"]').waitFor({ timeout: 30_000 })
    await page.locator('[data-testid="cage-map-legend"]').waitFor()
    // fixture 故意把五种关注度都摆出来；少一种就说明颜色没真的在分状态。
    for (const state of ['异常', '停用', '待投喂', '已满', '有空位']) {
      const legendEntry = page.locator('[data-testid="cage-map-legend"]').getByText(state, { exact: false })
      if (await legendEntry.count() === 0) {
        fail(`地图图例缺少关注度「${state}」`, 73)
      }
    }
    // 停用笼位必须出现在图上：它在货架上是真存在的，丢掉就凭空少一个位置。
    await page.locator(`[data-testid="cage-map-cell-${c7}"]`).waitFor()

    // 一排就是一条线：末位要在首位右边、同一行上。
    // 只断言“格子都在”的话，排成两行或者反序也能蒙混过去。
    const firstBox = await page.locator(`[data-testid="cage-map-cell-${c1}"]`).boundingBox()
    const lastBox = await page.locator(`[data-testid="cage-map-cell-${c8}"]`).boundingBox()
    if (!firstBox || !lastBox || lastBox.x <= firstBox.x || Math.abs(lastBox.y - firstBox.y) > 4) {
      console.error(
        `✖ 一排没有从左往右排开：首位 ${JSON.stringify(firstBox)}，末位 ${JSON.stringify(lastBox)}`,
      )
      process.exit(75)
    }

    await shot('01-cage-map')

    // ---------------------------------------------------------- 场景一：死亡
    // 两只商品兔同笼，从「笼内兔只」里挑一只登记死亡。
    // 地图上直接点格子就能开笼内兔只，这是地图视图的主路径。
    await page.locator(`[data-testid="cage-map-cell-${c3}"]`).click()
    const cageDialog = page.getByRole('dialog')
    await cageDialog.getByText('1-3-1 笼内兔只').waitFor()
    await cageDialog.getByText('在栏 2 只', { exact: false }).waitFor()
    await shot('02-cage-rabbits-two')

    await rabbitCard(cageDialog, commAId, '查看详情')
      .getByRole('link', { name: '查看详情', exact: true })
      .click()
    await page.waitForURL((url) => url.pathname === `/workspace/livestock/rabbits/${commAId}`)
    await page.getByRole('heading', { name: `兔 #${commAId}` }).waitFor()
    await page.getByText('当前状态', { exact: true }).waitFor()
    await shot('02a-rabbit-detail')
    await page.getByRole('button', { name: '登记离场', exact: true }).click()
    const departureDialog = page.getByRole('dialog').filter({ hasText: '登记离场' })
    await departureDialog.getByText('登记离场').first().waitFor()
    await page.locator('#livestock-departure-type').click()
    await page.getByRole('option', { name: '死亡' }).click()
    await page.fill('#livestock-departure-reason', '浏览器验收：夜间发现死亡')
    await page.fill('#livestock-departure-remark', `run ${runId}`)
    await shot('03-departure-dialog')
    await page.locator('#livestock-departure-confirm').check()
    await departureDialog.getByRole('button', { name: '确认离场' }).click()
    await expectToast(`兔 #${commAId} 已登记死亡`)
    await shot('04-departure-done')

    // 离场后同笼只剩一只，且是另外那只。
    await page.getByRole('link', { name: '返回兔群' }).click()
    await page.getByRole('tab', { name: /笼位/ }).click()
    await page.locator(`[data-testid="cage-map-cell-${c3}"]`).click()
    const afterDialog = page.getByRole('dialog')
    await afterDialog.getByText('在栏 1 只', { exact: false }).waitFor({ timeout: 15_000 })
    await afterDialog.getByText(`兔 #${commBId}`).waitFor()
    await shot('05-cage-rabbits-one')
    // 对话框自带一个右上角 X（无障碍名也叫“关闭”），所以取页脚那一个。
    await afterDialog.getByRole('button', { name: '关闭', exact: true }).first().click()

    // ---------------------------------------------------------- 场景二：对调
    await page.getByRole('tab', { name: /兔只/ }).click()
    const doeRow = page.getByRole('row', { name: new RegExp(`兔 #${doeId}\\b`) })
    // fixture 把这只母兔停在 AWAIT_MATING；列表要显示服务端字典给的中文名，
    // 而不是枚举值，也不是旧的 reproductive_stage。
    await doeRow.getByText('待配种', { exact: false }).waitFor({ timeout: 15_000 })
    await shot('06-rabbit-list-with-stage')
    await doeRow.getByRole('link', { name: '查看详情' }).click()
    await page.waitForURL((url) => url.pathname === `/workspace/livestock/rabbits/${doeId}`)
    await page.getByRole('button', { name: '换笼', exact: true }).click()
    const transferDialog = page.getByRole('dialog').filter({ hasText: '换笼位' })
    // 商品兔笼没有对调路径，不能出现在种母兔的候选里（否则选完才吃 400）。
    if (await transferDialog.locator(`[data-testid="cage-map-cell-${c3}"][disabled]`).count() === 0) {
      fail('换笼地图把商品兔笼也做成了可选目标', 74)
    }
    await page.locator('#transfer-target-cage').click()
    // 被后备兔占用的 1-2-1 必须出现在候选里，否则对调根本无从发起；
    // 并且要当场标出「对调」，不能让用户提交后才发现自己动了两只兔。
    await page.getByRole('option', { name: /1-2-1.*对调/ }).click()
    await shot('07-transfer-dialog-swap')
    await transferDialog.getByRole('button', { name: '确认换笼' }).click()
    await expectToast(`已与兔 #${reserveId} 对调笼位`)
    await shot('08-transfer-swap-done')

    // ---------------------------------------------------------- 场景三：并笼
    await page.getByRole('link', { name: '返回兔群' }).click()
    const commCRow = page.getByRole('row', { name: new RegExp(`兔 #${commCId}\\b`) })
    await commCRow.getByRole('link', { name: '查看详情' }).click()
    await page.waitForURL((url) => url.pathname === `/workspace/livestock/rabbits/${commCId}`)
    await page.getByRole('button', { name: '换笼', exact: true }).click()
    const appendDialog = page.getByRole('dialog').filter({ hasText: '换笼位' })
    // 这一次走“输入笼位编号”那条路：完整对上就直接选中。
    await appendDialog.locator('#transfer-cage-number').fill('1-3-1')
    await appendDialog.locator('[data-testid="transfer-number-hint"]').getByText('已选中 1-3-1').waitFor()
    await shot('09-transfer-dialog-append')
    await appendDialog.getByRole('button', { name: '确认换笼' }).click()
    await expectToast('已并入目标商品兔笼')
    await shot('10-transfer-append-done')

    // ------------------------------------------------------ 场景四：母兔入轨
    await page.getByRole('link', { name: '返回兔群' }).click()
    await page.getByRole('button', { name: '录入兔只' }).click()
    const entryDialog = page.getByRole('dialog').filter({ hasText: '录入兔只' })
    await page.locator('#rabbit-cage').click()
    await page.getByRole('option', { name: /1-6-1/ }).click()
    await page.locator('#rabbit-type').click()
    await page.getByRole('option', { name: '种兔' }).click()
    await page.locator('#rabbit-gender').click()
    await page.getByRole('option', { name: '母', exact: true }).click()
    // 种母兔不能再出现旧的繁殖阶段下拉（飞书 recvsrpMlvu2SC）。
    if (await page.locator('#rabbit-reproductive-stage').count() > 0) {
      fail('种母兔仍然渲染了繁殖阶段下拉，recvsrpMlvu2SC 未收口', 72)
    }
    await entryDialog.getByText('种母兔阶段由生产流程维护').waitFor()
    await shot('11-doe-entry-form')

    await page.locator('#rabbit-repro-stage').click()
    await page.getByRole('option', { name: '待摸胎' }).click()
    // 待摸胎入轨要配种日期，这个字段是服务端字典驱动出来的。
    await page.locator('#rabbit-mating-date').waitFor({ timeout: 10_000 })
    const today = new Date()
    const iso = (date) => date.toISOString().slice(0, 10)
    await page.fill('#rabbit-stage-entered-at', iso(today))
    await page.fill('#rabbit-mating-date', iso(new Date(today.getTime() - 5 * 86_400_000)))
    await page.fill('#rabbit-breed', 'BROWSER-NEWDOE')
    await shot('12-doe-entry-stage-picked')
    await entryDialog.getByRole('button', { name: '保存' }).click()
    // 提示语要说清楚入的是哪个阶段，不能只说“已录入”。
    await expectToast('兔只已录入，并从【待摸胎】入轨')
    await page.getByText('BROWSER-NEWDOE').first().waitFor({ timeout: 20_000 })
    await shot('13-doe-entry-done')

    // ------------------------------------------------- 窄屏（DESIGN.md 要求）
    await page.setViewportSize(NARROW)
    await page.getByRole('tab', { name: /笼位/ }).click()
    // 窄屏先看地图：页面本身不得横向溢出，每排自己横向滚。
    await page.locator('[data-testid="cage-map"]').waitFor()
    await assertNoOverflow('cage map')
    await shot('14a-narrow-cage-map')

    // 再切回列表，保证两个视图在窄屏下都可用。
    await page.getByRole('button', { name: '列表', exact: true }).click()
    await page.getByText('1-3-1', { exact: false }).first().waitFor()
    await assertNoOverflow('cage tab')
    await assertTableScrolls('cage tab')
    await shot('14-narrow-cage-tab')
    await page.getByRole('row', { name: /1-3-1/ }).getByRole('button', { name: '笼内兔只' }).click()
    await page.getByRole('dialog').getByText('笼内兔只', { exact: false }).first().waitFor()
    await assertNoOverflow('cage rabbits dialog')
    await shot('15-narrow-cage-rabbits')
    await page.getByRole('dialog').getByRole('button', { name: '关闭', exact: true }).first().click()

    await page.getByRole('tab', { name: /兔只/ }).click()
    const narrowRow = page.getByRole('row', { name: new RegExp(`兔 #${commBId}\\b`) })
    await narrowRow.getByRole('link', { name: '查看详情' }).click()
    await page.waitForURL((url) => url.pathname === `/workspace/livestock/rabbits/${commBId}`)
    await page.getByRole('button', { name: '换笼', exact: true }).click()
    await page.getByRole('dialog').getByText('换笼位').first().waitFor()
    await assertNoOverflow('transfer dialog')
    // 窄屏下主操作必须还点得到，不能被挤出可视区。
    const confirm = page.getByRole('dialog').getByRole('button', { name: '确认换笼' })
    const box = await confirm.boundingBox()
    if (!box || box.y + box.height > NARROW.height + 1) {
      fail(`确认换笼 button is out of the ${NARROW.width}x${NARROW.height} viewport`, 71)
    }
    await shot('16-narrow-transfer-dialog')
  } finally {
    await context.close()
    await browser.close()
    devServer?.kill('SIGTERM')
  }

  writeFileSync(path.join(artifactDir, 'screenshots.txt'), `${shots.join('\n')}\n`)
  if (consoleErrors.length > 0) {
    writeFileSync(path.join(artifactDir, 'console-errors.txt'), `${consoleErrors.join('\n')}\n`)
    fail(`browser console reported ${consoleErrors.length} error(s); see console-errors.txt`, 73)
  }

  const required = [
    '01-cage-map', '02-cage-rabbits-two', '02a-rabbit-detail', '03-departure-dialog', '04-departure-done',
    '05-cage-rabbits-one', '06-rabbit-list-with-stage', '07-transfer-dialog-swap',
    '08-transfer-swap-done', '09-transfer-dialog-append', '10-transfer-append-done',
    '11-doe-entry-form', '12-doe-entry-stage-picked', '13-doe-entry-done',
    '14a-narrow-cage-map', '14-narrow-cage-tab', '15-narrow-cage-rabbits', '16-narrow-transfer-dialog',
  ]
  const missing = required.filter((name) => !shots.includes(name))
  if (missing.length > 0) {
    fail(`missing screenshots: ${missing.join(', ')}`, 74)
  }

  // -------------------------------------------------------- 数据库断言
  // 提示语只证明界面说了什么，这里证明库里真的变了。
  const actual = mysql(`
    SELECT CONCAT_WS(' ',
      (SELECT is_active FROM rabbits WHERE id = ${commAId}),
      (SELECT COUNT(*) FROM rabbit_departure_records
         WHERE house_id = ${houseId} AND rabbit_id = ${commAId} AND departure_type = 'death'),
      (SELECT cage_id FROM rabbits WHERE id = ${doeId}),
      (SELECT cage_id FROM rabbits WHERE id = ${reserveId}),
      (SELECT CONCAT(status, ':', rabbit_count) FROM cages WHERE id = ${c1}),
      (SELECT CONCAT(status, ':', rabbit_count) FROM cages WHERE id = ${c2}),
      (SELECT cage_id FROM rabbits WHERE id = ${commCId}),
      (SELECT CONCAT(status, ':', rabbit_count) FROM cages WHERE id = ${c3}),
      (SELECT CONCAT(status, ':', rabbit_count) FROM cages WHERE id = ${c4}),
      (SELECT cage_id FROM rabbits
         WHERE house_id = ${houseId} AND breed = 'BROWSER-NEWDOE' AND is_active = TRUE),
      (SELECT CONCAT(status, ':', rabbit_count) FROM cages WHERE id = ${c6}),
      (SELECT current_stage FROM rabbits
         WHERE house_id = ${houseId} AND breed = 'BROWSER-NEWDOE' AND is_active = TRUE),
      (SELECT COUNT(*) FROM breeding_cycles bc
         INNER JOIN rabbits r ON r.id = bc.mother_rabbit_id
         WHERE r.house_id = ${houseId} AND r.breed = 'BROWSER-NEWDOE' AND bc.closed_at IS NULL),
      (SELECT COUNT(*) FROM work_tasks wt
         INNER JOIN rabbits r ON r.id = wt.rabbit_id
         WHERE r.house_id = ${houseId} AND r.breed = 'BROWSER-NEWDOE' AND wt.status = 'PENDING')
    ) AS actual;`)

  // COMM-A 已离场并落一条死亡记录；母兔与后备兔互换到 c2/c1 且两笼用途互换；
  // COMM-C 并入 c3（离场后剩 1 只，并入后 2 只），c4 归零回空笼；
  // 新母兔停在 AWAIT_PALPATION，且有一条开放周期与一条待办。
  const expected = [
    '0', '1', String(c2), String(c1), '2:1', '1:1',
    String(c3), '3:2', '0:0', String(c6), '1:1', 'AWAIT_PALPATION', '1', '1',
  ].join(' ')

  writeFileSync(
    path.join(artifactDir, 'database_assertions.txt'),
    `expected=${expected}\nactual=${actual}\n`,
  )
  console.log(`expected=${expected}`)
  console.log(`actual=${actual}`)
  if (actual !== expected) {
    fail('Admin browser E2E database assertions failed', 75)
  }
  console.log('\nAdmin cage-ops browser E2E passed')
  console.log(`Artifacts: ${artifactDir}`)
}

main().catch((error) => {
  fail(`unexpected failure: ${error?.stack ?? error}`, 1)
})
