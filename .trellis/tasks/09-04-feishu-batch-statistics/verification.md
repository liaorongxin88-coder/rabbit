# 批次统计验证记录

## 本地实现门禁

记录时间：2026-09-06 01:40-02:00 CST；Admin 补充验证：2026-09-06 12:47 CST。

### Backend

- 聚焦服务测试：`BatchStatisticsServiceTest`、`OutboundSubmitServiceTest`、`OutboundSubmitCoordinatorTest`，共 29 项通过。
- 新鲜 MySQL schema `rabbit_app_e2e_bsf_cross3`：`BatchStatisticsIT`、`BatchStatisticsWritePathIT`、`BatchStatisticsLegacyWriteDisabledIT`、`OutboundDraftAllocationIT`、`BatchStatisticsExportIT`，共 25 项通过。
- `mvn --file backend/pom.xml test`：1,094 项单元和架构测试通过，所有模块 Checkstyle 为 0。
- `mvn --file backend/pom.xml -DskipTests package`：通过。
- 当前后端镜像从工作树重新构建，Flyway 在隔离设备库 `rabbit_app_bsf_device_final` 从空库成功迁移到 V56。

新鲜 schema 测试覆盖 28 项统计、旧四字段、历史缺口、V55/V56 复合租户外键、权限、兔舍隔离、兼容开关开启和关闭、请求认领前零写入、出库草稿恢复、冲突、重试、事务回滚及 Excel。日志中的重复销售请求约束异常是测试主动触发的断言，最终构建成功。

两次未计入结果的环境失败已定位：第一次只构建 `rabbit-boot`，读取了本地仓库中的旧 `rabbit-production` 包；第二次连接了未暴露的 `localhost:3306`。最终使用当前 reactor 模块和 `rabbit-e2e` 的 `13307` 端口，在全新 schema 上通过。

### Admin

- `corepack pnpm --dir admin lint`：通过。
- `corepack pnpm --dir admin test`：85/85 通过，包含畸形顶层响应、operand、缺失原因、非法日期、百分比和整数格式，以及 `Content-Disposition` 参数边界与转义回归。
- `corepack pnpm --dir admin build`：242 个模块构建通过。
- `corepack pnpm --dir admin e2e:browser:batch-statistics`：通过，无控制台错误。
- 浏览器产物：`admin/build/browser-e2e/batch-statistics/`，包括桌面、窄屏、200% 字号、无权限、首次失败、重试恢复和刷新失败保留数据。
- Admin 开发服务：`http://127.0.0.1:5174/`。

### Flutter

- 对 52 个改动的 Dart 文件执行 `dart format`。
- `cd app && ./rabbit check`：628 项测试通过，`flutter analyze` 无问题。
- `cd app && ./rabbit apk dev --debug`：通过。
- 最终普通 debug APK：`app/build/app/outputs/flutter-apk/app-dev-debug.apk`。
- APK 大小：119,644,210 字节。
- APK SHA-256：`1ee9170aa376d905cf94a2bda2bd1bcdc477d3b6ef2e5a0e2944c11b7490fa08`。

### 实体 Android 设备

当前工作树在 Android 15 实体设备 `00152155M000372`（A059，1080x2392，420 dpi）完成维护中的出库 E2E。运行 ID 为 `20260906015822165071`，产物位于：

```text
app/build/android-e2e/20260906015822165071/
```

流程覆盖查看权限、三批次称重、统一单价、冻结确认、并发冲突、继续修改和最终出库。七张截图齐全，数据库断言为：

```text
expected=1 2 2 1 1 1 0-or-1 1
actual=1 2 2 1 1 1 0 1
```

第一次当前代码真机尝试因临时 backend 继承了图片验证码开关而停在登录页。重新启动同一工作树 backend 并按 E2E 配置关闭验证码后通过；这不是产品代码修复。

此前批次生命周期脚本只完成登录和创建批次，后端夹具没有生成后续繁殖周期、任务或 `AWAIT_ESTRUS`/`ESTRUS` 状态，因此未到达断奶。旧产物位于 `app/build/android-batch-lifecycle-e2e/20260905234019483093/`，不作为当前 APK 的完整生命周期证据。

## 附件规模共享真实夹具验收

2026-09-06 在同一 V56 夹具 run `059e0fec2e6fe953963b` 上完成 API、Excel、Admin 和 Android 真机验收。产物位于：

```text
artifacts/batch-statistics-cross-client/059e0fec2e6fe953963b/
```

- 新鲜隔离 schema 上的 `BatchStatisticsIT` 7 项和 `BatchStatisticsExportIT` 3 项全部通过。API 恰好返回 28 项，全部为 `AVAILABLE`，顺序、原始值和展示值与 `research/acceptance-fixture.md` 第 3 节一致；同一响应生成的两个 Excel 页签逐项一致。
- 根级运行器重新构建当前后端，临时关闭验证码并加入单一 Admin Origin。真实登录、`Authorization`、`X-House-Id`、MIME、ASCII/UTF-8 文件名和 OOXML ZIP 校验通过。
- Admin 使用真实业务登录和兔舍选择进入目标批次，28 项展示值、八组布局、Excel 下载、控制台和请求错误检查通过；桌面截图为 `admin/desktop-detail.png`，尺寸 1440x2899。
- Android 15 实体设备 `00152155M000372` 使用同一账号、兔舍和批次完成 28 项展示值、八组、导出入口、出肉率表单及版本历史检查；11 张截图均为 1080x2392，Flutter 报告 2 个步骤全部通过。
- 数据库断言确认 1,230 个周期、1,059 个怀孕周期、60 只公兔、21 个流产周期、1,004 窝、8,604 只断奶、6,834 只销售、13,095 kg 销售重量、157,140 元销售额、600 只转后备、52,120 kg 饲料和 56% 出肉率。
- `manifest.json` 中 API、XLSX、Admin、Android、数据库、夹具清理、后端恢复和设备恢复全部为 `true`。清理后本次用户、兔舍、批次和提醒偏好残留均为 0；验证码恢复为业务码 0，CORS 与绑定恢复原值，Vite 无残留监听，临时凭据文件为 0。
- `SHA256SUMS` 覆盖 35 个最终产物并校验通过。21 个文本产物未发现明文密码、Bearer token、JWT 或 secret 赋值；12 张 PNG 哈希均不同。Android 结果保留 28 项展示值和 11 个截图名称，不重复嵌入 PNG 字节。
- 既有回归保持通过：Admin lint、85 项测试、生产构建和模拟批次统计浏览器脚本；Flutter 分析及统计、出肉率、架构相关 62 项测试；后端上述 10 项真实 MySQL 集成测试。

实施期间的失败路径发现并修复了 Excel 时间戳正则、随机 Vite Origin 的 CORS、默认兔舍竞态、运行时 `reminder_preferences` 清理、Vite 子进程回收和 Android 无界 `pumpAndSettle`。每个有效失败 run 都核验了 fixture 清理和后端恢复；工具强制终止绕过 trap 的两次 run 已手动清理并恢复，不作为通过证据。

## 复杂场景跨端矩阵验收

2026-09-07 完成复杂矩阵 run `932481d900bb90978433`。产物位于：

```text
artifacts/batch-statistics-cross-client/932481d900bb90978433/
```

- 同一 V56 fixture 创建一个目标兔舍、一个隔离兔舍、五个主批次、一个混批辅助批次，以及 OWNER、VIEWER 和无关兔舍三类账号。独立 JSON 目录固定六个场景各 28 项 code、顺序、原始值、`displayValue`、状态和有序缺失原因。
- 六个场景的真实 API 与直接 XLSX 校验全部通过。`mixed-data-quality` 同时覆盖 18 项 `AVAILABLE`、1 项 `NOT_APPLICABLE`、1 项 `NOT_RECORDED` 和 8 项 `DATA_MISSING`；两端另保存实际渲染的 `visibleValue`，没有用零替代缺失值。
- `time-and-cycle-boundaries` 证明饲料窗口包含结束日 `2024-04-20 23:59:59`，排除次日 `2024-04-21 00:00:00`。生产 SQL 的四个饲料谓词均使用 `DATE_ADD(DATE(end_date), INTERVAL 1 DAY)`，API、XLSX、Admin 和 Android 显示一致。
- 混批订单在主批次 0.500 kg、辅助批次 0.500 kg、未归批次 0.501 kg 间守恒；18.03 元分别分配为 6.00、6.01 和 6.02 元，确定性的首组吸收 -0.01 元尾差。
- 安全与重试场景写入 58% 出肉率，同 `requestId` 同载荷重放只保留一个版本；同 `requestId` 改为 59% 返回冲突且不增加版本。数据库最终只有 1 个版本和 1 条 dedup 记录。
- VIEWER 在后端、Admin 和 Android 均可查询统计并导出真实 OOXML；Admin 与 Android 下载分别校验 `PK` 签名。该角色不能编辑出肉率或读取完整历史。无关兔舍账号无法看到目标兔舍和批次数据。
- Admin 在一个 Vite/Chrome 会话中遍历六个批次，逐项验证 28 项、八组、状态、原因、`X-House-Id`、控制台和水平溢出；生成六份场景工作簿、一份 VIEWER 工作簿及 15 张截图。
- Android 15 实体设备 `00152155M000372` 在一次安装中完成六批次遍历和三角色切换，1 分 45 秒内通过。结果包含六组各 28 项 `metricDisplayValues`、`metricVisibleValues`、状态和原因，以及 46 个截图名称；其中 40 张为五个主场景的八组证据，另有辅助批次、缺失原因、OWNER 操作/历史、VIEWER 和拒绝访问证据。
- Android Driver 写盘后，运行器校验 46 个嵌入截图名称与清单一致，再删除结果 JSON 中的 PNG 数组；最终 `android_e2e_result.json` 为 44,091 字节且不含截图字节。
- 产物共 193 个文件。六份直接场景工作簿、一份后端 VIEWER 权限工作簿和七份 Admin 工作簿分别计数；规范位置有 61 张 PNG，61 个 SHA-256 均不同，尺寸为 Admin 1440 宽和 Android 1080x2392。视觉检查未发现空白、裁切、文字重叠或横向溢出。
- `SHA256SUMS` 的 192 项全部复核通过。secret scan 扫描 71 个文本文件，未发现明文密码、Bearer token、JWT 或 secret 赋值。
- cleanup 后 `remaining_users`、`remaining_houses`、`remaining_batches`、`remaining_dedup` 和 `remaining_events` 均为 0。验证码恢复为业务码 0，后端绑定和 CORS 恢复，测试包、Vite 监听和 mode-0600 临时 defines 均无残留。

完整通过前保留了以下失败路径证据，并逐次确认 cleanup 和恢复：

- `957756ee7f2fd953bd1a`：Admin 历史 dialog 有两个 accessible name 为“关闭”的按钮；定位器改为 dialog 内精确匹配后的首个正文按钮。
- `0bf7c249b6fcff9a2b8d`：validator 混淆 API 的可空 `displayValue` 与 UI 的状态文案；两端证据改为同时保留 `displayValue` 和 `visibleValue`。
- `73430b0de161f9f1059c`：Android 缺失原因断言使用页面级文本查找；改为限定当前指标详情容器。
- `d015cc79b2e4ca40f038`：第二角色登录时“账号”同时匹配模式和字段标签；改为限定 `login-mode-selector`。
- `a2c7da3685ccb99d9b25`：widget test 在 Driver 消费前删除截图报告，导致没有 PNG；改为写盘后由根运行器瘦身。
- `ffdc6bd2013e0f8e6431`：46 张 PNG 已写盘，但 complex 后处理漏调已有 sanitizer；补充名称核验和 JSON 瘦身后，真实产物离线 validator 通过。

复杂矩阵通过后，又执行默认 baseline run `f7acf4d6d35d9c6e88ba`。附件规模 API、XLSX、Admin、Android 和数据库验证全部通过；37 个产物包含 12 张不同截图和 2 份工作簿，36 项 checksum 通过，22 个文本文件无敏感信息。该产物的 `validations` 只保留五个共同阶段，没有把 complex 专用的 `security` 和 `secretScan` 写成误导性的 `false`；最终审查另发现顶层仍有空的 `scenarioValidations`，运行器已改为在后续 baseline manifest 中省略该字段。此形状修复只做有界静态与契约验证，未重跑真机流程。

最终审查还补全了复杂 XLSX 第一页对 `AVAILABLE` 日期、日期范围和数值单元格，以及明细页 raw 日期与数值单元格的精确校验。run `932481d900bb90978433` 保留的六份直接场景工作簿全部通过增强后的离线 validator；将 `MATED_DOE_COUNT` 的冻结数值由 6 改为 7 会在汇总值断言失败，仅修改 `MATING_DATE.firstDate` 且保持展示值不变则会在明细 raw 日期断言失败。运行器真实 `write_manifest` 函数的合成检查确认 baseline 省略 `scenarioValidations`、`security` 和 `secretScan`，complex 保留三者。

最终回归包括：新鲜 schema 的复杂矩阵 3 项集成测试；后端 1,094 项测试、Checkstyle 和 package；Admin lint、93 项测试、242 模块 build、8 项复杂定义契约和 mocked 浏览器；Flutter analyzer 与 628 项测试；shell、三个 Node 入口及四个嵌入 Node heredoc 语法检查。全部通过。

## 契约与诊断

- 最终跨端审查已核对数据库、API、Admin、Flutter 和 Excel 的固定 28 项元数据、值、状态与顺序。
- 4 个 Admin/Node 文件和 4 个 Java 文件的主 LSP 均为 0。Dart LSP 在 120 秒预算内仍超时，不能记为 LSP 通过；同一文件的针对性 `flutter analyze` 和工作树完整 `./rabbit check` 均已通过。Mapper XML 没有配置 LSP，由新鲜 schema 集成测试、后端完整测试、Checkstyle 和 package 覆盖。
- `lens_diagnostics mode=all` 覆盖本次 23 个文件且没有阻断错误。11 个统计 Mapper 和 5 个独立 fixture/cleanup jscpd 重复警告已逐项审查并在本会话 defer；提取共享片段会扩大本轮边界修复范围。此前 Flutter package 解析和本地 NFC 示例密钥告警已确认是工具缓存或固定本地测试值，不需要改动业务代码。

## 尚未完成的生产发布门禁

以下项目需要真实发布环境和时间窗口，当前任务保持 `in_progress`：

- 创建并发布 `force_update = true` 的 Flutter 版本。
- 在生产设备完成投喂、断奶、转后备、出肉率和 Excel 分享冒烟；当前只完成出库真机流程。
- 从全部写入真机验证通过后的下一自然日开始，执行 `design.md` 中的固定 SQL，取得全部兔舍连续 7 个完整自然日零 `LEGACY_*_GAP` 事件证据。
- 根据上述证据关闭 `app.batch-statistics.legacy-write-enabled`。
- 演练重新开启兼容、下架问题版本且保留追加结构的回退步骤。

生产发布记录必须补充观察窗口、执行人、SQL 结果、兼容开关变更和回退结果；这些项目不能用本地自动化或旧截图代替。
