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

迁移兼容测试默认使用独立的 `rabbit_app_e2e_migration`，可通过
`E2E_MIGRATION_DATASOURCE_URL` 覆盖。两个测试库都只允许用于本地或 CI 测试。

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

客户端和后端现已使用统一的批量配种契约：`POST /api/batches/{batchId}/mating/bulk`。一次请求最多选择 1000 只处于“待配种”或“哺乳中”的母兔，并统一公兔和配种日期；服务端先锁定 Batch，再按稳定顺序锁定母兔和成员，整批预校验后才写入。请求 ID 与 Batch、母兔集合、公兔和日期的 payload hash 绑定：完全相同的失败草稿安全重试时复用原 ID，修改公兔、日期或母兔集合后必须生成新 ID；不同 payload 复用同一请求 ID 必须返回冲突。

7000 只商品兔最终销售提交由
`backend/src/test/java/com/rabbit/app/e2e/LargeHouseOutboundSubmitScaleIT.java` 覆盖；销售明细按 1000 条分块写入同一事务，避免单次写入触发 `max-affected-rows=2000`。

2026-08-15 已在独立真实 MySQL 数据库完成规模回归：`LargeWholeHouseBatchLifecycleIT` 业务段 71.672 秒，覆盖 1000 母兔、20 公兔、100 空怀、20 失败分娩、100 哺乳期二配、880 窝断奶和 6160 只商品兔出库；`LargeHouseOutboundTaskScaleIT` 覆盖 7000 只/700 笼，业务段 115.722 秒；`LargeHouseOutboundSubmitScaleIT` 覆盖 7000 只最终提交，业务段 112.278 秒，完全相同请求的幂等重试为 36 毫秒。`LargeHouseBatchScaleIT`、`LargeEventReminderScaleIT`、`BulkMatingIT`、`BreedingCycleTerminationIT` 和 `LargeFarmSchemaMigrationIT` 同轮通过。该证据是后端规模证明，不能替代 Flutter 真机 UI。

同轮真实 MySQL 并发回归还验证了 `BatchConcurrentCreateIT`：同一母兔同时创建两个 Batch 时，返回码严格为一个成功、一个 400，且最终只有一条活跃 Batch 关系。实现先锁定按 ID 排序的母兔行，再以 `FOR UPDATE` 当前读检查活跃关联，避免 MySQL `REPEATABLE READ` 旧快照漏看另一个事务刚提交的成员关系；历史 inactive 关系继续保留。

Batch 客户端生命周期脚本为：

```bash
cd app
RABBIT_ANDROID_E2E_DEVICE_ID=<设备序列号> \
RABBIT_ANDROID_E2E_DEVICE_API_URL=http://<真机可达的后端地址>:8080 \
./scripts/android_batch_lifecycle_e2e.sh
```

脚本会先要求 Flyway V24 已成功执行，再注入隔离 fixture。创建、批量催情开始/完成、首次两母兔批量配种、摸胎（含空怀）、备产、分娩、哺乳期重叠批量二配、断奶、商品兔出库和最终母兔淘汰走客户端真实 UI。数据库只用于准备/查询隔离 fixture、登录辅助、最终断言，以及每 250 毫秒把该 fixture 的 `next_event_date` 压缩到可执行日期；这项时间压缩只跳过真实养殖周期中的等待天数，不替代任何生产业务动作。通过标准是本轮 artifact 同时存在 `flutter-drive.log`、18 张 PNG、`database_assertions.txt` 且 `actual=expected`。历史 artifact 只能作为历史证据，不能替代新版本真机运行。

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
