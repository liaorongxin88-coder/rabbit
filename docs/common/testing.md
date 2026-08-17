# 测试和验证

按改动范围选择验证，不需要每次都跑全量检查。

## 后端构建

```bash
cd backend
mvn -DskipTests package
```

涉及业务逻辑、权限、迁移、MyBatis mapper 或 API 契约时，应优先跑后端测试或 E2E。

## 后端 API E2E

准备 MySQL 测试库：

```bash
mysql -uroot -e "create database if not exists rabbit_app_e2e default character set utf8mb4 collate utf8mb4_general_ci;"
mysql -uroot -e "create database if not exists rabbit_app_e2e_migration default character set utf8mb4 collate utf8mb4_general_ci;"
mysql -uroot -e "create database if not exists rabbit_app_e2e_large_loop default character set utf8mb4 collate utf8mb4_general_ci;"
```

> **数据库时区必须是 `Asia/Shanghai`。** 应用通过 `serverTimezone=Asia/Shanghai`
> 写入时间，而部分断言用 SQL 的 `now()` 比较到期日（如
> `next_event_date <= now()`、`work_tasks.due_date`）。若 MySQL 跑在 UTC，两者相差 8
> 小时，会出现「行存在但查不到」的假失败（典型现象：
> `BreedingCycleTerminationIT` 报 `expected: <1> but was: <0>`）。
> `docker-compose.yml` 已设 `TZ: Asia/Shanghai`；自建临时容器时切勿遗漏。

临时容器一键启动（与 CI 等价）：

```bash
docker run -d --name rabbit-e2e \
  -e MYSQL_ROOT_PASSWORD=rabbit_root -e TZ=Asia/Shanghai \
  -p 13307:3306 mysql:8.0 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

运行：

```bash
cd backend
mvn -Pe2e verify
```

可覆盖测试库连接：

```bash
E2E_DATASOURCE_URL='jdbc:mysql://localhost:3306/rabbit_app_e2e?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true' \
E2E_DATASOURCE_USERNAME=root \
E2E_DATASOURCE_PASSWORD=rabbit_root \
mvn -Pe2e verify
```

**一共三个独立测试库，各自有环境变量，漏配任一个都会让对应用例以
`Failed to load ApplicationContext` 失败，而不是提示连不上库：**

| 环境变量 | 默认库 | 使用方 |
| --- | --- | --- |
| `E2E_DATASOURCE_URL` | `rabbit_app_e2e` | 绝大多数 IT |
| `E2E_MIGRATION_DATASOURCE_URL` | `rabbit_app_e2e_migration` | `BreedingCycleMigrationIT`、`LargeFarmSchemaMigrationIT` |
| `E2E_LARGE_LOOP_DATASOURCE_URL` | `rabbit_app_e2e_large_loop` | `LargeWholeHouseBatchLifecycleIT` |

三个默认值都指向 `localhost:3306`；改端口时三个要一起改。三个测试库都
只允许用于本地或 CI 测试。

注意：E2E 会清空 `rabbit_app_e2e`，不要指向开发库或生产库。

## 接口演示回归

后端启动后：

```powershell
.\tools\demo_flow.ps1 -BaseUrl "http://localhost:8080"
.\tools\demo_flow_full.ps1 -BaseUrl "http://localhost:8080"
```

这些脚本适合验证核心业务链路：注册/登录、兔场、笼位、兔只、批次、事件提醒和繁殖性能。

## Flutter 验证

```bash
cd app
./rabbit check
./rabbit apk dev --debug
```

默认规则：

- Flutter UI 或状态改动至少跑 `./rabbit analyze`。
- model、repository、provider 或业务逻辑改动跑 `./rabbit test`。
- Android 构建配置、依赖或 manifest 改动跑 `./rabbit apk dev --debug`。

运营商一键登录的本地测试只证明 Flutter/Kotlin 通道、后端供应商契约、防重放、账号归一和
短信回退。正式验收还必须使用控制台已登记包名与签名的 Android 真机、有效 SIM 和蜂窝网络，
至少覆盖移动、联通、电信；模拟器或测试 Provider 不能替代该证据。

如果 Flutter SDK cache 权限导致命令失败，先修复本机 Flutter SDK/cache 权限，再判断项目代码是否有问题。当前客户端主题允许最高 200% 有效字号；验收应以 360x800、393x852 和 412x915 的真实渲染为准，旧的 150% 口径只作为历史兼容信息，不作为当前通过条件。软键盘高度不得写死：表单和底部操作栏必须读取实时 `MediaQuery.viewInsets.bottom`，至少覆盖 180、300、420 逻辑像素键盘及横屏短视口。

### App 全操作流程矩阵

完整验收不是只跑 Batch 页面。应使用同一个隔离测试账号和兔舍，按下表顺序执行；失败后保留当前页面、请求 ID 和 artifact，不要直接重建数据掩盖问题。

| 流程 | 人工操作重点 | 自动化与证据边界 |
| --- | --- | --- |
| 认证与会话 | 法律条款、账号/短信/一键登录切换、注册、会话恢复、401 后回到登录、密码与手机号设置 | repository/controller/widget 用例已覆盖主要分支；运营商一键登录仍必须用有效 SIM 真机验收 |
| 兔舍与成员 | 创建/选择兔舍、空态和错误重试、手机号邀请、只读/edit/control 权限 | 已有列表分页、邀请和权限会话用例；邀请真实送达需后端与短信服务 |
| 笼位与 NFC | 笼位分页、批量建笼、详情、兔只录入、NFC 写入/离线队列/冲突重试 | 已有权限、分页、NFC 编解码和错误恢复用例；真实标签读写必须在 Android 真机完成 |
| 兔只档案 | 千级兔只分页、搜索/筛选、编辑、隔离/恢复、离场 | repository 和大列表用例覆盖分页；当前母兔淘汰/死亡 UI 位于 Batch 详情，兔只总表尚无独立批量离场入口 |
| Batch 与繁殖周期 | 创建 Batch、搜索/全选、批量催情、最多 1000 只母兔批量配种、摸胎、备产、分娩、断奶、哺乳期二配、结束 Batch | repository/widget 与后端单元/IT 已覆盖契约和状态规则；1000 母兔真实 MySQL 闭环通过；当前构建 A059 两母兔双周期 UI 闭环 18/18 通过 |
| 商品兔出库 | 按笼/按排/整舍选择、提前出售确认、阻断项、冲突恢复、幂等提交 | 选择和确认页按可视区域惰性构建；7000 只真实 MySQL 任务冻结和提交通过；A059 已完成 7 只商品兔真实 UI 出库 |
| 数据面板与设置 | 全兔舍/单兔舍切换、月份/年份、生产设置继承和保存、App 设置 | 有汇总模型、筛选和 200% 布局用例；报表数值必须与大型闭环数据库断言一起核对 |
| 权限、断网与人体工学 | 只读深链、加载/空/错误态、失败后重试、360x800、393x852、412x915、200% 字号、动态键盘 | Flutter 178/178 runtime 与 A059 基线通过；真实 NFC、TalkBack、横屏全流程和运营商登录仍需专项人工验收 |

## Flutter Android 设备 E2E

### Batch 生命周期（真实 UI + 后端）

千母兔闭环的后端规模用例是
`backend/src/test/java/com/rabbit/app/e2e/LargeWholeHouseBatchLifecycleIT.java`，覆盖单兔舍单 Batch 的 1000 只母兔、20 只公兔、空怀/失败分娩、100 个重叠二配周期、880 窝断奶、6160 只生产来源商品兔整舍出库和 Batch 结束。该用例通过直接 API 驱动业务并用 SQL 对账，属于后端规模证明，不等同于 1000 只在 Flutter 画面上逐项操作。

批量配种契约已随 doe-breeding-v2 改为待办驱动：`POST /api/repro/tasks/bulk-actions`（旧的
`POST /api/batches/{batchId}/mating/bulk` 已删除）。客户端先用 `GET /api/tasks` 把选中母兔
解析成待配种任务 id，再按 id 批量推进；单次上限 500 项。哺乳中的母兔没有配种待办（哺乳
周期不占流水线），对她配种即血配，由客户端先开一条新的待配种周期再配种，于是她同时
持有哺乳周期与新怀孕周期。

批量结果是**逐项**的：部分成功是常态，整体仍返回 HTTP 200，失败项在 `items` 里带原因。
一百头里有一头被别人先推进了，不应该让另外九十九头白做。幂等按标准的 requestId 语义：
相同 requestId 重试返回首次结果并标记 `replayed`。

7000 只商品兔最终销售提交由
`backend/src/test/java/com/rabbit/app/e2e/LargeHouseOutboundSubmitScaleIT.java` 覆盖；销售明细按 1000 条分块写入同一事务，避免单次写入触发 `max-affected-rows=2000`。

2026-08-15 已在独立真实 MySQL 数据库完成规模回归：`LargeWholeHouseBatchLifecycleIT` 业务段 71.672 秒，覆盖 1000 母兔、20 公兔、100 空怀、20 失败分娩、100 哺乳期二配、880 窝断奶和 6160 只商品兔出库；`LargeHouseOutboundTaskScaleIT` 覆盖 7000 只/700 笼，业务段 115.722 秒；`LargeHouseOutboundSubmitScaleIT` 覆盖 7000 只最终提交，业务段 112.278 秒，完全相同请求的幂等重试为 36 毫秒。`LargeHouseBatchScaleIT`、`LargeEventReminderScaleIT`、`BulkMatingIT`、`BreedingCycleTerminationIT` 和 `LargeFarmSchemaMigrationIT` 同轮通过。该证据是后端规模证明，不能替代 Flutter 真机 UI。

同轮真实 MySQL 并发回归还验证了 `BatchConcurrentCreateIT`：同一母兔同时创建两个 Batch 时，返回码严格为一个成功、一个 400，且最终只有一条活跃 Batch 关系。实现先锁定按 ID 排序的母兔行，再以 `FOR UPDATE` 当前读检查活跃关联，避免 MySQL `REPEATABLE READ` 旧快照漏看另一个事务刚提交的成员关系；历史 inactive 关系继续保留。

Batch 客户端生命周期脚本为：

```bash
# 先起完整 compose 集群（mysql + valkey + backend）
docker compose --profile valkey up -d --build

cd app
RABBIT_ANDROID_E2E_DEVICE_ID=<设备序列号> \
./scripts/android_batch_lifecycle_e2e.sh
```

**运行形态（故意如此）：**

- **完整 compose 集群**。脚本会确认 `rabbit_mysql_1`、`rabbit_valkey_1`、`rabbit_backend_1`
  三个容器均存在，否则直接退出。本地 `mvn spring-boot:run` 也能让用例通过，但那就
  测不到容器网络、镜像构建与缓存接线这三件只在真实部署里才会出错的事。
- **缓存走 valkey**。脚本校验后端容器的 `APP_CACHE_PROVIDER=valkey` 且 `valkey-cli ping`
  响应。缓存的真实业务消费方是短信验证码存储（`LettuceSmsVerificationStore`），
  发一次验证码即可在 valkey 里看到 `rabbit:cache:v1:sms:*` 键。
- **真机走局域网直连**，不用 `adb reverse`。脚本自动取主机 `en0/en1` 的局域网 IP
  并从设备侧实探一次（期望 401/200）；失败则提前报错。`adb reverse` 会把流量隧道回
  USB，掩盖掉真实网络下的延迟与断连行为。前提是 `.env` 里 `BACKEND_BIND_ADDRESS=0.0.0.0`。

脚本要求 Flyway **V27** 已成功执行（doe-breeding-v2 回填与 `uk_bc_pipeline`），再注入隔离 fixture。
建批、批量催情、两母兔批量配种、摸胎（含空怀）、备产、接产、哺乳期血配、分笼、
商品兔出库和两头母兔离场全部走客户端真实 UI。数据库只用于准备/查询隔离 fixture、
登录辅助、最终断言，以及每 250 毫秒把该 fixture 的 `work_tasks.due_date` 压缩到可执行日期；
这项时间压缩只跳过真实养殖周期中的等待天数，不替代任何生产业务动作。

**每步操作后的全员状态校验。** 每一个生产动作之后，用例都会拉取批次下**所有**母兔的
`(current_stage, 未完成待办类型)` 并逐头比对期望（`_assertBatchState`）。关键在于“所有”：
新模型里一个动作会连带写周期、待办与母兔投影三处，“误伤旁人”是真实发生过的故障
（给 A 分笼把 B 的阶段覆盖掉），只断言当事母兔永远发现不了。未被列入期望的母兔
同样会失败，防止新增成员后校验静默变窄。

通过标准是本轮 artifact 同时存在 `flutter-drive.log`、19 张 PNG、`database_assertions.txt`
且 `actual=expected`。历史 artifact 只能作为历史证据，不能替代新版本真机运行。

### 笼内兔只操作真机脚本（死亡记录 / 换笼位 / 录入入轨）

```bash
cd app
RABBIT_ANDROID_E2E_DEVICE_ID=<设备序列号> \
./scripts/android_cage_ops_e2e.sh
```

跡象写到 `app/build/android-e2e/cage-ops-<run_id>/`，通过标准是 14 张 PNG 齐全且
`database_assertions.txt` 里 `actual=expected`。这个脚本与 Batch 生命周期脚本同样
走局域网直连、不用 `adb reverse`，并额外做一件事：跑之前先探一下
`/api/repro/entry-points` 与 `/api/rabbits/{id}/cage-transfer` 是否真的在路由上。
因为 `docker compose up -d --build backend` 在 podman 下只重建镜像、**不重建容器**，
旧容器会静静少接口，用例就退化成「界面看着没坏」——那不是验收。
遇到该报错时跑 `docker compose up -d --build --force-recreate backend`。

笼位区默认是**分层地图**（排 → 层 → 位），格子上不写笼位编号，所以用例按
`cage-map-cell-<cageId>` 点格子进笼位详情，不再认文字；换笼也在弹窗内的地图上选。
分层地图每一排都是一个横向滚动区，`_scrollUntilPresent` 必须显式传入竖向滚动容器
（`house-cage-list-scroll` / `rabbit-move-cage-scroll`），否则 `Scrollable.last` 会抓到横向那个，
竖着拖永远不会动。

`cage_ops_fixture.sql` 故意把五种关注度摆全：C1/C2 已投喂（已满）、C3/C4 未投喂（待投喂）、
C5/C6 空笼（有空位）、C7 停用、C8 标为空闲却写着在栏 2 只（异常）。
否则所有有兔的笼都是未投喂，整张地图一片琥色，截图就证明不了颜色真的在分状态。

2026-08-17 首轮通过，run ID `20260817233240414116`，13/13 截图与 14 项数据库断言一致。
覆盖：两只商品兔同笼时挑一只登记死亡（不需批次）、种母兔与后备兔对调笼位、
商品兔并入未满的商品兔笼、以及录入种母兔时从【待摸胎】入轨并补录配种日期。
数据库断言不只看提示语：对调后两只兔的 `cage_id` 互换、两笼用途与计数互换、
两只都仍在栏（证明 SWAP 的 `is_active` 寄存已恢复），新母兔带着 `current_stage`
与一条开放周期、一条待办。

2026-08-18 分层地图上线后重跑通过，run ID `20260818013425677899`，14/14 截图。
新增 `01b-cage-map` 专门留地图本身的证据，并断言图例里五种关注度全部出现。
这轮真机验收抳出三个单测看不见的问题：带「对调」标注的格子把 56px 方格撑出 2px；
数量 chip 与展开的筛选把地图整个推到首屏之外（“更直观”反而要先滚一屏）；
以及商品兔笼被错贴了「对调」标注（它没有对调路径，旧列表模式因为不显示而没暴露）。

NFC 碰标签选目标笼不在本脚本内（需人拿着实体标签贴上去），仍留人工验收。

本轮靠真机抳出一个单测看不见的缺陷：录入流程的类型页 pop 后用 post-frame 回调
另开表单且不 await，于是调用方的列表刷新在兔子创建之前就跑完了——录入完看不到
新兔，得退出重进页面。已改成两步都在 `showRabbitEntryTypeSheet` 里 await，
并补上回归用例 `entry flow future completes only after the form closes`。

2026-08-17 流产（非计划事件）已纳入 Batch 生命周期用例，run ID `20260817181356093836`，
19/19 截图与 19 项数据库断言全部一致。流产不对应任何待办，所以走母兔行上的独立
入口，而不是今日清单；用例选在母兔 A 处于待催情、母兔 B 处于待摸胎的时点提交，
同一屏上同时断言「B 有入口」与「A 没有入口」——只验证能点会放过「到处都能点」，
而后者才是真实风险。断言列新增 `aborted_cycles`：单看周期总数变化不能证明流产真的
落库。母兔 B 的周期链因此变为 `#1 EMPTY → #2 ABORTED → #3 自动接续后 REMOVED`。

2026-08-17 doe-breeding-v2 完整闭环已在 A059（Android 15，1080x2392，density 420）通过，
run ID `20260817160028468330`，耗时 1 分 32 秒。运行形态为**完整 compose 集群 + valkey 缓存 +
真机局域网直连**（`http://192.168.31.169:8080`，无 adb reverse），数据库由已有 V24 存量数据
真实升级至 V28。结果为 18/18 截图、Flutter `All tests passed`、18 项数据库断言 expected/actual
完全一致，含每步操作后的批次全员状态校验。该校验做过反向验证：故意写错一个期望后，用例在
对应步骤失败并打印 `期望=/实际=` 差异，确认它不是恒真断言。

2026-08-15 当前构建已在 A059（Android 15，1080x2392，density 420）完成上述闭环，run ID 为 `20260815123218794644`，耗时 1 分 37 秒。结果为 18/18 截图、Flutter `All tests passed`，数据库 expected/actual 完全一致：Batch 165、9 个成员全部退出、3 个繁殖周期、2 次断奶、1 个空怀周期、1 个重叠周期、7 只出生并全部出售，销售单 `SO-25`，Batch 自动完成。证据位于 `app/build/android-batch-lifecycle-e2e/20260815123218794644/`。脚本在运行前保存设备常亮设置、唤醒并保持屏幕可见，退出时恢复原设置，避免长流程因息屏被误判为业务中断。

批量出库 Android 测试使用真实 Flutter Dev APK、Android 模拟器、`rabbit_app` 后端和每轮隔离 fixture。runner 会在没有设备时启动第一个可用 AVD，注入测试数据，执行只读权限、人体工学、提前出售、并发冲突恢复和成功提交，并对销售单、兔只状态和请求状态做数据库断言：

前置条件是 `http://127.0.0.1:8080` 后端和 Compose MySQL 已运行且指向本地开发库 `rabbit_app`。runner 会自动探测当前的 `rabbit_mysql_1` 或历史 `rabbit-mysql-1` 容器名，也可用 `RABBIT_ANDROID_E2E_DB_CONTAINER` 显式指定；预检不通过时 runner 会在注入 fixture 和启动模拟器之前退出。

```bash
cd app
./scripts/android_e2e.sh
```

runner 固定要求 JDK 21。macOS/Homebrew 默认使用 `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`；其他环境通过 `RABBIT_ANDROID_E2E_JAVA_HOME` 指定，避免 Android Studio JBR 升级后破坏 Gradle 8.4 构建。

常用矩阵参数：

```bash
RABBIT_ANDROID_E2E_AVD=Medium_Phone \
RABBIT_ANDROID_E2E_TEXT_SCALE=1.0 \
RABBIT_ANDROID_E2E_PROFILE=visual-baseline \
./scripts/android_e2e.sh

RABBIT_ANDROID_E2E_AVD=Medium_Phone \
RABBIT_ANDROID_E2E_TEXT_SCALE=2.0 \
RABBIT_ANDROID_E2E_PROFILE=accessibility-stress \
./scripts/android_e2e.sh
```

`visual-baseline` 使用 100% 系统字体，作为设计还原、日常回归和对外交付截图；runner 会拒绝用其他字号伪装成该档位，并在每张截图前断言系统字号与 App 有效字号仍和测试配置一致。`accessibility-stress` 使用 200% 系统字体，并验证 App 保留 200% 有效字号，同时检查换行和溢出边界。压力档只验证可达性、换行和溢出边界，不能作为视觉还原或交付截图基准。

截图、截图清单、fixture 标识、设备物理尺寸、测试档位和数据库断言保存在 `app/build/android-e2e/<run_id>/`。脚本会验证 7 张业务流程截图完整存在，可修改模拟器字体比例并在结束时恢复；实体机默认不修改系统设置，只有显式设置 `RABBIT_ANDROID_E2E_ALLOW_DEVICE_SETTINGS=1` 才执行字体矩阵。真机 NFC、TalkBack、左右手持机误触和疲劳仍需人工验收。

## Admin 验证

```bash
pnpm --dir admin lint
pnpm --dir admin build
```

涉及登录、请求层或路由守卫时，建议启动本地 dev server 并验证平台登录：

```bash
pnpm --dir admin dev --host 127.0.0.1
curl -s -H 'Content-Type: application/json' \
  -d '{"userName":"admin","password":"admin123456"}' \
  http://127.0.0.1:5173/api/admin/auth/login
```

涉及布局、表格、弹窗和响应式时，还需要浏览器检查桌面和窄屏宽度，确认没有控制台错误、横向溢出、文本重叠或按钮不可达。

### 笼内兔只操作浏览器验收脚本

```bash
pnpm --dir admin e2e:browser       # 无头
HEADED=1 pnpm --dir admin e2e:browser  # 看着它点
```

脚本（`admin/scripts/admin_cage_ops_browser_e2e.mjs`）自己注入隔离 fixture
（与真机脚本共用 `cage_ops_fixture.sql`）、起 vite、用 Playwright 驱本机 Chrome
（`channel: 'chrome'`，不下载 Chromium）跑完四个场景，再回头查库。
一定要给 vite 加 `--host 127.0.0.1`：默认只听 localhost，而本机 localhost 先解到 ::1，
探活 127.0.0.1 会一直连不上、看起来像 dev server 没起来。
通过标准：16 张截图齐全、console/page error 为 0、`database_assertions.txt` 里 `actual=expected`，
跡象在 `admin/build/browser-e2e/cage-ops-<run_id>/`。

2026-08-18 首轮通过，run `20260818001511706795`，14 项数据库断言一致：
两只商品兔同笼时挑一只登记死亡（提示“兔 #N 已登记死亡”，同笼另一只不受影响）、
种母兔与后备兔对调（提示“已与兔 #N 对调笼位”，两笼用途与计数互换）、
商品兔并入未满商品兔笼、录入种母兔从【待摸胎】入轨（服务端字典自动要求配种日期，
并断言旧的繁殻阶段下拉已不存在）。

本轮浏览器验收抳出一个真缺陷：390px 下两张表格会把列挤成一列一个字
（“R1-C3-L1”折三行、“商品兔”竖着排）。它不触发“横向溢出”，所以只看溢出的检查放得过去。
修法是给表格加最小宽度，让已有的 `overflow-auto` 真的横向滚起来（DESIGN.md 允许表格横滚，
但要求行身份可读）；脚本同时添了 `assertTableScrolls`，以后挤回去会直接失败。

2026-08-15 已从 `/workspace/login` 使用真实本地账号和真实后端完成 Admin Batch 闭环：两母兔首次批量配种、空怀、哺乳期二配、两轮分娩/断奶、7 只商品兔出库、母兔离场和 Batch 自动完成；24 张截图、HTTP 4xx/5xx 为 0、console/page error 为 0。另在 390x844 下分别以 OWNER 和 VIEWER 登录复测完成 Batch 条目，页面无横向溢出，只读账号无可执行写入口。测试账号矩阵保存在 `/private/tmp/rabbit-test-account-matrix-20260815112735492410.txt`，仅适用于当前本机 `rabbit_app`，不得复制到生产或提交密码到仓库。

## 文档改动

纯文档改动不要求跑应用构建。建议检查：

```bash
rg -n "file:///|d:/rabbit|TODO|TBD" README.md E2E_TESTING.md CONTRIBUTING.md backend/README.md app/README.md admin/README.md docs
```

如果文档涉及命令或路径，优先用当前仓库实际文件验证路径仍存在。

## 历史 Android Instrumentation

历史原生 Android 冒烟测试曾使用：

```bash
cd android
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.baseUrl=http://10.0.2.2:8080
```

当前仓库维护重心已转向 `app/`。除非恢复原生 Android 目录，否则该命令只作为历史记录保留。
