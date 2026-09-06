# 可执行批次统计验收夹具

## 当前缺口

28 项附件满值目前只存在于 `BatchStatisticsServiceTest.completeFixture()` 的服务层原始聚合对象。`BatchStatisticsIT` 使用多组小型内联 SQL 验证查询粒度和边界，`BatchStatisticsExportIT` 导出空批次；Admin 浏览器脚本拦截 API 并返回模拟 JSON，Flutter 没有批次统计真机用例。因此，现有证据不能证明真实 MySQL 数据经 Mapper、服务、Excel 和两个客户端后仍保持一致。

## 采用方案

新增附件规模的运行隔离 SQL fixture，并把 `research/acceptance-fixture.md` 第 3 节作为唯一人工期望。夹具生成 1,230 只母兔和 60 只公兔、1,230 个已关闭配种周期、1,059 个确认怀孕周期、21 个流产周期、1,004 窝、6,834 个销售兔明细，以及饲料、销售、转后备和出肉率快照。这样可以保留已经批准的数量、金额和比例，不需要为跨端测试维护第二套数字。

夹具放在 `backend/src/test/resources/fixtures/`，不进入 Flyway 或 demo 数据。Java 集成测试在各自重置后的数据库中重载同一 SQL；跨端运行器只加载一次，Admin 和 Android 消费同一组运行 ID、兔舍、批次和账号。

## Schema 约束

- 要求 Flyway V56 或更高版本成功执行。
- `sys_user.user_name` 和 `user_code` 唯一；用户名、用户编码和所有请求 ID 从 `run_id` 派生，并控制在列长以内。
- 测试账号使用启用的 OWNER 兔舍成员关系。隔离验证使用同一账号可访问的第二兔舍，使错误落在批次归属校验而不是登录前权限校验。
- 每只活跃种兔或后备兔使用独立笼位。V53 规定非商品兔的 `growth_stage` 和 `growth_stage_entered_at` 必须为 `NULL`。
- 历史 `breeding_cycles` 使用 `CLOSED`，必须填写 `stage` 和 `stage_entered_at`，避免 OPEN 周期唯一性约束。
- `litters` 按 `(house_id, cycle_id)` 唯一。只有 `weaned_count > 0` 的窝可以写正数 `weaning_total_weight_kg`，零断奶窝保持 `NULL`。
- 饲料分配只使用有批次的 `BREEDING` 和 `FATTENING`，金额大于零且合计等于父投喂记录。
- 销售分配的只数和重量必须为正；单价和金额同时有值或同时为空。销售明细引用真实兔只，并保存 `batch_id_snapshot`。
- 转后备数量和重量必须为正。出肉率范围为 `(0, 1]`，`payload_hash` 固定为 64 位。
- V55/V56 子表的父记录、批次和 `house_id` 必须满足复合外键，生成列 `batch_scope_id` 不出现在插入列中。

## 精确数据

目标批次固定从 `2024-04-22` 开始并在 `2024-08-01` 结束，避免当前日期改变投喂窗口。主要数据为：

- 配种周期 1,230，确认怀孕 1,059，去重公兔 60，流产周期 21。
- 产仔窝 1,004，总产仔 10,040，活仔 9,870，选留窝 987，选留仔 9,490。
- 前 956 窝各断奶 9 只、总重 6.615 kg，合计 8,604 只、6,323.940 kg。
- 销售 6,834 只、13,095.000 kg，单价 12.00 元/kg，金额 157,140.00 元。
- 转后备 600 只、1,050.000 kg。
- 配种和育肥饲料分别为 22,050.00 kg、30,070.00 kg。
- 出肉率为 0.560000，来源单位为“测试屠宰场”。

完整 28 项原始值、分子、分母和展示值仍以 `research/acceptance-fixture.md` 第 3 节为准。

## 测试接入

后端增加可复用的夹具加载辅助代码。统计 API 测试断言 28 项 code、顺序、元数据、状态和精确值；导出测试先取得同一统计响应，再逐项核对两个可见页签、数值类型、格式、文件名、权限和兔舍隔离。

Admin 新增独立的实际后端浏览器脚本，保留现有模拟脚本不变。实际脚本通过业务登录设置工作区会话和兔舍选择，打开 `/workspace/production/batches/{batchId}`，断言 28 个指标全部“数据可用”、关键展示值和真实 Excel 下载，不对 `/api` 使用 Playwright 路由拦截。

Flutter 新增 `integration_test/batches/statistics_android_test.dart`，复用 `android_e2e_driver.dart`。测试使用账号登录并进入批次详情，按 `batch-statistics-group-*` 和 `batch-statistic-*` 稳定 key 遍历八组，断言展示值、出肉率操作和导出入口，并生成分组截图。

根级跨端脚本负责 V56、服务、Chrome、设备、屏幕方向和验证码检查，加载一次夹具，执行 API 前置断言、Admin、Android、数据库断言、哈希和清理。自动登录期间临时关闭验证码；退出 trap 必须恢复 Compose 原配置。默认删除 fixture，显式调试开关才允许保留。

## 预计文件

- `backend/src/test/resources/fixtures/batch_statistics_acceptance_fixture.sql`
- `backend/src/test/resources/fixtures/batch_statistics_acceptance_fixture_cleanup.sql`
- `backend/rabbit-boot/src/test/java/com/rabbit/app/e2e/BatchStatisticsAcceptanceFixture.java`
- `backend/rabbit-boot/src/test/java/com/rabbit/app/e2e/BatchStatisticsIT.java`
- `backend/rabbit-boot/src/test/java/com/rabbit/app/e2e/BatchStatisticsExportIT.java`
- `admin/scripts/batch-statistics-browser-real-e2e.mjs`
- `admin/package.json`
- `app/integration_test/batches/statistics_android_test.dart`
- `scripts/batch-statistics-cross-client-e2e.sh`
- `.trellis/tasks/09-04-feishu-batch-statistics/verification.md`
