# 批次统计验证记录

## 本地实现门禁

记录时间：2026-09-06 01:40-02:00 CST。

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
- `corepack pnpm --dir admin test`：83/83 通过。
- `corepack pnpm --dir admin build`：242 个模块构建通过。
- `node admin/scripts/batch-statistics-browser-e2e.mjs`：通过，无控制台错误。
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

## 契约与诊断

- 最终跨端审查已核对数据库、API、Admin、Flutter 和 Excel 的固定 28 项元数据、值、状态与顺序。
- 主 LSP：8 个关键后端 Java 文件为 0 诊断；8 个关键 Admin 文件为 0 诊断。
- Dart LSP 对 7 个关键文件在 60 秒预算内超时，不能记为 LSP 通过；同一工作树的完整 `flutter analyze` 已通过。
- `lens_diagnostics mode=all` 的 14 个阻断项均位于本任务未修改的既有 Java、JSONC 和 Docker 文件；已由前序审查确认是规则误报、合法 JSONC 注释或任务外 Docker root 用户问题。本任务文件只有既有重复代码类警告，没有新增阻断诊断。

## 尚未完成的生产发布门禁

以下项目需要真实发布环境和时间窗口，当前任务保持 `in_progress`：

- 创建并发布 `force_update = true` 的 Flutter 版本。
- 在生产设备完成投喂、断奶、转后备、出肉率和 Excel 分享冒烟；当前只完成出库真机流程。
- 从全部写入真机验证通过后的下一自然日开始，执行 `design.md` 中的固定 SQL，取得全部兔舍连续 7 个完整自然日零 `LEGACY_*_GAP` 事件证据。
- 根据上述证据关闭 `app.batch-statistics.legacy-write-enabled`。
- 演练重新开启兼容、下架问题版本且保留追加结构的回退步骤。

生产发布记录必须补充观察窗口、执行人、SQL 结果、兼容开关变更和回退结果；这些项目不能用本地自动化或旧截图代替。
