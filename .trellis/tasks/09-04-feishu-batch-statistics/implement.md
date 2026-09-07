# 飞书批次统计实施计划

## 1. 开始条件

- [x] 旧客户端采用兼容过渡，`design.md` 已写明开关、观测、强制升级、关闭条件、用户提示和回退步骤。
- [x] `research/source-gap-analysis.md` 的 O1 已解决；`PREGNANT_DOE_COUNT`、夹具和契约均按确认怀孕母兔去重。
- [x] 用户已审阅并明确批准最新的 `prd.md`、`design.md` 和本计划。
- [x] `task.py start` 已将任务状态从 `planning` 切换为 `in_progress`。
- [x] 实施代理已读取 `implement.jsonl`；检查代理已读取 `check.jsonl`。
- [x] 当前工作树中的既有改动已确认，不覆盖与本任务无关的内容。
- [x] 基线测试通过，或已记录与本任务无关的既有失败。

## 2. 实施顺序

### A. 基线与契约测试

- [x] 运行现有后端 `BatchStatisticsServiceTest` 与 `BatchStatisticsIT`，记录当前四字段响应。
- [x] 运行现有 Admin lint、test、build 和批次统计浏览器脚本。
- [x] 运行现有 Flutter 批次统计领域、Repository 和页面测试。
- [x] 先为固定 28 项目录、排序、状态优先级、舍入和附件验收样例增加失败测试。
- [x] 确认旧四字段兼容断言仍保留，避免后端先发布时破坏旧客户端。

验证门：测试必须先证明当前实现缺少 28 项契约，且附件样例的期望值已经固定。

### B. 追加数据库结构

- [x] 增加 `V55__batch_statistics_write_snapshots.sql` 和 `V56__outbound_draft_batch_allocations.sql`，未修改更早的已应用迁移。
- [x] 为投喂增加批次与阶段分配明细，并建立 `house_id`、复合父记录/批次外键及查询索引。
- [x] 为权威断奶记录增加可空的 `weaning_total_weight_kg`，保留历史空值。
- [x] 为销售订单增加批次重量、单价和金额分配明细。
- [x] 增加 `replacement_batch_allocations`，按转换 `requestId` 保存来源批次、只数和实测总重，不在每只兔的 `replacement_records` 中重复组总重。
- [x] 增加批次出肉率追加版本表及 `request_id` 幂等约束。
- [x] 按仓库惯例同步完整 schema 参考文件，未修改 demo 数据。
- [x] 增加新鲜 schema 迁移集成测试，验证外键、正数范围、兔舍隔离、版本追加和重复请求。

验证门：新结构可以在空库和现有测试库上迁移；历史记录保持可读；没有自动回填或数据丢失。

回滚点：只回滚应用代码，保留追加表和列。不得逆向删除已经写入的快照。

### C. 写入链路与幂等

#### C1. 投喂

- [x] 增加投喂分组预览契约，按兔只、投喂时间、批次成员有效期、配种日、断奶分笼和 `birth_batch_id` 返回候选组。
- [x] 扩展投喂请求 DTO 和领域模型，接收批次阶段分配。
- [x] 保存时重新解析候选组，并在服务事务内校验分组唯一、合法空值组合、每行大于零、合计等于总用量。
- [x] 单一候选且没有未归属对象时自动归属；其他情况缺少明细时返回明确业务错误。
- [x] 保存 `BREEDING`、`FATTENING` 或 `UNASSIGNED`，只接受 kg；未归批次用量不进入统计。
- [x] 分配明细继承投喂操作的稳定 `requestId`；重放不能重复新增父记录或分配行。
- [x] 兼容开关开启时，旧投喂载荷保留原操作并记录 `LEGACY_FEED_ALLOCATION_GAP`；关闭后在事务前拒绝旧载荷。
- [x] 增加服务单元测试和 MySQL 集成覆盖。

#### C2. 断奶

- [x] 新请求在断奶数量大于零时要求正数总重。
- [x] 在同一事务内保存总重快照，并由后端派生兼容平均体重。
- [x] 总重快照继承现有繁殖操作的稳定 `requestId`；相同请求重放不重复更新或新增记录。
- [x] 兼容开关开启时，旧平均体重请求保留原行为并记录 `LEGACY_WEANING_WEIGHT_GAP`；关闭后在事务前拒绝，任何阶段都不从平均值生成总重快照。
- [x] 覆盖零只断奶、正数校验、重复提交和历史空值。

#### C3. 出库与销售

- [x] 扩展出库请求、服务端草稿、V56 分配快照和 `OutboundSubmitService`，接收批次及未归批次组的实际重量。
- [x] `SaleService` 在兔只退出批次前冻结归属：单一批次自动分配订单总重量，全部未归批次写空批次分配，多批次要求分组重量。
- [x] 服务端根据选中兔只和批次快照派生组与只数，不信任客户端计数。
- [x] 新销售要求统一正数重量单价，并校验分组重量、金额与订单总值一致。
- [x] 兼容开关开启时，多批次无分配或缺单价的旧销售继续原操作，并分别记录 `LEGACY_SALE_ALLOCATION_GAP`、`LEGACY_SALE_PRICE_GAP`；关闭后在事务前拒绝且不产生部分写入。
- [x] 保持原 `requestId` 对同一逻辑草稿稳定，使用 `RequestDedupService.begin(..., payloadHash)` 或 `OutboundSubmitCoordinator` 校验载荷；未知结果重试前查询请求状态。
- [x] 旧销售记录不补算，相关统计通过 `DATA_MISSING` 暴露。

#### C4. 转后备

- [x] 扩展转换请求，按来源批次接收实测总重。
- [x] 服务端校验兔只分组完整、总重大于零，并保存操作时批次快照。
- [x] 快照继承转换操作的稳定 `requestId`；相同请求重放不重复转换或累计重量。
- [x] 兼容开关开启时旧转换请求保留原操作并记录 `LEGACY_REPLACEMENT_WEIGHT_GAP`，关闭后在事务前拒绝。
- [x] 转换后刷新受影响批次统计，不能根据转换后的当前类别反推。

#### C5. 出肉率

- [x] 增加追加版本写接口，要求 `rabbit:batches:edit`。
- [x] 校验 0 至 1 范围、来源单位、检测日期和修改说明。
- [x] 用 `requestId` 防止重复版本，记录操作人和时间。
- [x] 增加仅限 `rabbit:audit:list` 的分页历史接口。
- [x] 添加权限、兔舍隔离、首次录入、修改、重复提交和历史读取测试。

#### C6. 兼容过渡

- [x] 增加 `app.batch-statistics.legacy-write-enabled` 配置，并集中判断投喂、断奶、批量出库、`SaleService` 和转后备旧载荷，避免各服务产生不同过渡行为。
- [x] 在 `repro_events` 持久化五类 `LEGACY_*_GAP` 操作事件，目标为受影响批次；顶层记录 `houseId`、`requestId`、时间，payload 只记录客户端 build，未提供时为 `UNKNOWN`。
- [x] 缺口事件与原业务处于同一事务，父操作失败时不得留下事件；同一请求重放不得重复计数。
- [x] 在 `design.md` 固化按数据库业务本地自然日统计全部兔舍缺口事件的 SQL，沿用操作事件的持久化保留。
- [ ] 生产发布检查记录需附上固定 SQL 的窗口、执行人和查询结果。
- [x] 新 Flutter 请求统一发送 `X-App-Build`，版本读取失败时发送 `UNKNOWN`；后端只用于兼容观测，不把该头作为权限或可信业务依据。
- [x] 覆盖开关开启、关闭、父子记录原子性、缺口事件和明确升级提示测试。
- [ ] 发布新版 Flutter 时设置 `force_update = true`；完成全部写入和 Excel 分享真机冒烟后，从下一自然日开始观察，全部兔舍连续 7 个完整自然日零旧载荷后关闭开关。
- [ ] 演练回退：重新开启开关、下架问题版本，确认旧客户端恢复操作且新表列不回滚。

验证门：每条新写入都在后端完成权限、兔舍、金额或重量合计和幂等校验；旧载荷行为受同一开关控制并可观测，客户端校验不能成为唯一防线。

### D. 28 项统计后端

- [x] 将固定目录实现为批次域中的单一所有者，包含 code、名称、阶段、顺序、Excel 列名、单位、格式、公式和缺失原因顺序。
- [x] 扩展原始聚合 Mapper，分别读取繁殖、产崽、断奶、销售、饲料、转后备和出肉率，避免一条多表巨大 SQL。
- [x] 所有 Mapper 方法显式接收 `houseId`，SQL 的读、连接和子查询保留兔舍条件。
- [x] 在服务层按 `research/metric-catalog.md` 计算 28 项；受胎率和流产率按周期，`PREGNANT_DOE_COUNT` 按确认怀孕母兔去重，并处理配种日期明细和状态传播。
- [x] 使用原始精度计算，统一生成 `displayValue`，展示舍入采用 `HALF_UP`。
- [x] 响应返回 `schemaVersion`、`batchId`、`houseName`、`batchCode`、`calculatedAt`、28 项 `metrics` 及旧四字段兼容投影；`houseName` 与 `batchCode` 由同一次兔舍范围内的批次查询提供，并固定 `dateValue`、操作数、组成项和 `missingCauses` 子结构。
- [x] 缺失原因使用稳定代码并返回全部原因；指标来源不完整时禁止返回部分合计。
- [x] 扩展 `BatchStatisticsServiceTest` 与 `BatchStatisticsIT`，覆盖附件样例、人工授精空公兔、自然配种缺公兔、金额尾差、育肥增重为零或负数和全部边界场景。
- [x] 在 `rabbit-reporting` 增加 OOXML 依赖、批次统计 Excel 写出器和 `GET /api/reports/batches/{batchId}/statistics.xlsx`。
- [x] 导出器只消费一次 `BatchStatisticsService` 结果，使用快照中的 `houseName` 和 `batchCode`，固定内容类型、UTF-8 文件名、ASCII 回退文件名、`Content-Disposition` 和 `rabbit:reports:export` 权限；文件名清理批次编号中的路径、控制和平台保留字符。
- [x] 生成 `批次统计` 页，使用快照中的兔舍名称和批次编号，后接统计时间和 28 项横向指标；可用数值使用数值单元格和 Excel 格式，非可用状态使用确认的中文状态文本。
- [x] 生成 `口径与状态` 页，按指标逐行写入名称、code、阶段、单位、原始值、展示值、状态、公式、分子、分母、组成项和全部缺失原因。
- [x] 增加导出单元测试与 MySQL 集成测试，重新打开 `.xlsx` 并断言两个页签、28 项表头与明细行、单元格类型、附件样例值、状态、兔舍隔离和权限。

验证门：附件样例 28 项按 `research/acceptance-fixture.md` 通过；新建批次、历史缺字段、散养、混批和同兔多周期都有明确状态，导出的工作簿可由 OOXML 解析器重新打开。

### E. Admin

- [x] 在 `src/types/` 增加结构化统计、日期明细、操作数、状态和出肉率版本类型。
- [x] 扩展 `src/api/workspace.ts`，保持业务客户端和 `X-House-Id`；原始 Blob 请求只封装在共享请求层。
- [x] 在 Admin 请求层增加认证 Blob 下载和服务端文件名解析；只对 `rabbit:reports:export` 显示批次详情下载操作，并覆盖无权限入口和直接请求。
- [x] 注册 `/workspace/production/batches/:batchId`，新增独立批次详情页。
- [x] 在生产批次列表的桌面和移动布局增加详情入口，已结束批次仍可进入。
- [x] 保留现有概览，在具体批次旁增加“查看完整批次统计”入口。
- [x] 按八组渲染 28 项；宽屏保留飞书原文的固定 16 行关系，包括第 14 行三个独立销售指标，窄屏和 200% 字号按顺序逐项降为单列；支持口径、分子、分母、组成项、状态和缺失原因展开。
- [x] 统计首次加载使用骨架；局部失败可重试；刷新失败保留上次成功数据和取数时间。
- [x] 增加出肉率录入与历史界面，并按 `rabbit:batches:edit`、`rabbit:audit:list` 分别控制。
- [x] 更新断奶、混批出库和转后备表单，保持保存中防重复提交和稳定 `requestId`。
- [x] 为格式化、状态、合计校验增加 Node 测试，扩展批次统计 Playwright 脚本。

验证门：`1440x900` 与 `390x844` 都能完整查看和操作，无水平溢出、文字遮挡、控制台错误或权限泄漏。

### F. Flutter

- [x] 扩展 `domain/batches/statistics.dart`，严格解析固定 28 项元数据、四种状态、日期明细、操作数和缺失原因顺序。
- [x] 扩展 Batch Repository 的统计读取、出肉率写入与历史接口。
- [x] 保持 `houseId + batchId` provider family、请求取消和独立刷新；刷新失败时保留旧数据。
- [x] 在现有批次详情替换四项统计区，按八组展示全部指标；宽屏保留飞书原文的固定 16 行关系，包括第 14 行三个独立销售指标，窄屏和 200% 字号按顺序逐项降为单列，不阻断成员操作。
- [x] 在数据看板选中具体批次时增加详情入口。
- [x] 更新投喂表单，按批次和阶段录入分配并校验合计。
- [x] 更新断奶表单，录入必填总重并显示后端派生均重。
- [x] 更新出库控制器、确认页、本地草稿和服务端 V56 草稿，保存批次重量分配与统一单价，并在最终提交前强制刷新服务端草稿。
- [x] 更新转后备表单，按来源批次录入实测总重。
- [x] 增加出肉率录入与历史界面，并按权限隐藏操作。
- [x] 在 Flutter 增加带认证头的受保护文件下载、临时目录和系统分享或保存能力，不复用 OTA 公共下载路径；只对 `rabbit:reports:export` 显示入口并覆盖无权限场景。
- [x] 增加领域、Repository、provider、Widget、草稿恢复和获准导出入口测试。

验证门：`360x800` 与 `412x915` 下通过，200% 字号时指标降为单列，说明和错误文案可换行，表单动作始终可达。

### G. 长期文档与端到端验收

- [x] 更新 `docs/features/batch-statistics/README.md`、`design.md` 和 `interaction.md`，移除已解决的待确认项和旧 Admin 落点。
- [x] 自动化覆盖附件样例、混批、散养、同兔多周期、负育肥增重、历史缺字段、未录入出肉率和权限场景；新鲜 schema E2E 另验证关系与事务边界。
- [ ] 生产发布说明需写入缺口事件查询 SQL、7 天观察起止日、执行人、计数结果、关闭开关和回退步骤。
- [x] 对照数据库快照、API 原始值、Admin 展示值、Flutter 展示值和 Excel 单元格，确认五层一致。
- [x] `verification.md` 已记录本地 MySQL、浏览器、APK 和实体设备证据，以及未完成的完整批次生命周期和发布门禁。

### H. 共享真实验收数据与跨端验证

- [x] 新增 V56 兼容、按 `run_id` 隔离的 `batch_statistics_acceptance_fixture.sql` 和定向清理脚本；使用附件规模数据使 28 项全部为 `AVAILABLE`，并输出运行器所需 ID 与账号。
- [x] 增加可复用的 Java 夹具加载辅助代码，扩展 MySQL 统计与导出集成测试；先断言 28 项 API 精确值，再逐项核对同一统计快照生成的 `.xlsx`。
- [x] 新增 Admin 实际后端浏览器脚本和 package 命令，验证业务登录、兔舍上下文、28 项展示值、八组布局、真实 Excel 下载、控制台错误和桌面截图；保留现有模拟脚本不变。
- [x] 新增 Flutter 批次统计 Android 集成测试，按稳定 key 遍历八组和 28 项，验证精确展示值、出肉率操作和导出入口，并在实体设备生成分组截图。
- [x] 新增根级跨端运行器：检查 V56、后端和设备连通性、验证码状态与浏览器；加载一次夹具，执行 API 前置断言、Admin、Android、数据库断言和文件哈希。
- [x] 所有成功、失败和中断路径都恢复验证码配置并默认清理夹具；调试保留必须显式开启，产物不得记录 token 或密码。
- [x] 重新运行现有后端、Admin 模拟浏览器和 Flutter 检查，确认新增真实流程没有替代边界、权限和响应式覆盖；把结果与截图路径写入 `verification.md`。

### I. 复杂场景全跨端矩阵

独立复杂夹具已经稳定复现生产饲料查询的结束上界缺陷。用户选择将修复纳入本轮，范围确认门禁已经解除。冻结的 `time-and-cycle-boundaries` 期望保持不变；VIEWER 继续使用现有查询和导出权限。本节新增实现与验证项在对应命令实际通过前保持未完成。

- [x] 新增 `research/complex-fixture-matrix.md`，冻结五个场景的输入、五个主批次与一个混批辅助批次关系、三类账号、28 项期望、状态和有序缺失原因；期望不得由被测生产代码生成。
- [x] 修正饲料聚合的完成批次上界：把 `end_date` 归一为 `DATE` 后再加 1 天，以该下一自然日零点作为排他上界；不改 28 项公式、V55/V56 或其他迁移。
- [x] 新增 `batch_statistics_complex_matrix_fixture.sql`、对应 cleanup 和机器可读 JSON 目录；按一个 `run_id` 创建目标/隔离兔舍，满足 V56 约束并输出场景 batch ID 数组和无密码账号角色清单。
- [x] 新增 Java 矩阵加载器、`BatchStatisticsComplexMatrixIT` 和 `BatchStatisticsComplexMatrixExportIT`；在新鲜 schema 上逐场景核对 API、两个 XLSX 页签、日期/周期粒度、混批守恒、金额尾差、时间边界、状态传播、权限和幂等零部分写入。
- [x] 扩展 Node 校验器和根运行器支持 `RABBIT_BATCH_STATISTICS_SUITE=baseline|complex`，默认继续运行 baseline；complex 模式一次启动后端并在客户端前验证所有场景 API、XLSX、数据库和安全/重试前置条件。
- [x] 扩展 Admin 实际后端脚本兼容场景数组；在一次 Chrome/Vite 会话中通过真实兔舍和批次导航遍历五个主批次与混批辅助批次，核对 28 项、八组、状态原因、六份下载和三类账号权限。VIEWER 必须能查询并导出 XLSX，但不能编辑出肉率或读取完整历史。保存逐场景完整页面截图。
- [x] 扩展 Flutter Android 集成测试兼容场景数组；一次安装中遍历五个主批次和混批辅助批次，为每个主场景保存八组截图并保留辅助批次销售组证据，再切换 VIEWER 和无关账号。VIEWER 必须保留查询和导出入口，同时隐藏出肉率录入和完整历史；无关账号继续验证拒绝路径。
- [x] 运行器逐场景保存 API、XLSX、数据库断言、Admin/Android 截图与 metric/status/cause 证据；删除 Android 内嵌 PNG 数组，生成总 manifest 和 `SHA256SUMS`，扫描文本产物中的密码、token 和 secret。
- [x] 验证复杂矩阵成功、失败和中断路径均清理静态及运行时副作用，并恢复验证码、CORS、绑定、Vite 和设备状态；显式 KEEP_FIXTURE 调试仍提供无密码清理命令。
- [x] 复杂矩阵通过后重跑当前附件 baseline 跨端套件、Admin 模拟浏览器、Flutter 聚焦测试和后端完整门禁，把每个场景的 run ID、截图数量、精确结果和恢复证据写入 `verification.md`。
- [x] 使用冻结的 `time-and-cycle-boundaries` 期望验证结束日饲料被计入、结束次日饲料被排除，并确认 API、XLSX、Admin 和 Android 一致；不得通过放宽期望、删除场景或改写口径使测试转绿。
- [x] 使用现有 VIEWER 角色验证 API、Admin 和 Android 的查询与 XLSX 导出成功，出肉率编辑和完整历史被拒绝；OWNER 与无关兔舍账号预期保持不变，不修改 `PermissionCode` 或生产角色等级。

## 3. 主要文件与风险点

| 区域 | 主要触点 | 风险 |
| --- | --- | --- |
| Backend 批次统计 | `BatchController`、`BatchStatisticsService`、`BatchStatisticsMapper` 及 XML、统计 DTO | 多表连接造成重复求和；状态规则散落 |
| Backend Excel | `rabbit-reporting` 导出服务、Controller、OOXML 依赖 | 文件值偏离 API；响应流中断；依赖漏洞 |
| Backend 写入 | feed、repro/weaning、outbound/sale、rabbit replacement 服务和 Mapper | 事务中订单与分配不一致；旧客户端兼容 |
| Flyway | `rabbit-boot/src/main/resources/db/migration/`、schema 参考 | 修改已应用迁移；历史空值被错误回填 |
| Admin | 批次详情、统计组件、API/types、出库和转换对话框 | 请求竞态；窄屏操作不可达；权限只在前端判断 |
| Flutter | 批次详情、Riverpod、feed/weaning/outbound/replacement、出库草稿 | 草稿丢失新增字段；刷新清空旧数据；大字号溢出 |
| 文档 | `docs/features/batch-statistics/` | 长期文档继续保留早期冲突口径 |

## 4. 验证命令

先运行聚焦检查，再运行完整检查。命令以仓库根目录为起点。

### Backend

```bash
mvn --file backend/pom.xml -pl rabbit-production -am \
  -Dtest=BatchStatisticsServiceTest,FeedServiceTest,SaleServiceTest,OutboundSubmitServiceTest,RabbitServiceTest test

mvn --file backend/pom.xml -pl rabbit-reporting -am \
  -Dtest=BatchStatisticsWorkbookWriterTest test

E2E_SCHEMA_SUFFIX=_batchstats \
  bash scripts/e2e-local.sh \
  -Dit.test=BatchStatisticsIT,BatchStatisticsWritePathIT,BatchStatisticsLegacyWriteDisabledIT,OutboundDraftAllocationIT,BatchStatisticsExportIT

E2E_SCHEMA_SUFFIX=_batchstats_complex \
  bash scripts/e2e-local.sh \
  -Dit.test=BatchStatisticsComplexMatrixIT,BatchStatisticsComplexMatrixExportIT

mvn --file backend/pom.xml test
mvn --file backend/pom.xml checkstyle:check
mvn --file backend/pom.xml -DskipTests package
```

### Admin

```bash
corepack enable
corepack prepare pnpm@11.22.0 --activate
pnpm --dir admin install --frozen-lockfile
pnpm --dir admin lint
pnpm --dir admin test
pnpm --dir admin build
pnpm --dir admin e2e:browser:batch-statistics
pnpm --dir admin e2e:browser:batch-statistics:real
```

可见改动还要在真实浏览器检查 `1440x900` 和 `390x844`，记录控制台错误、水平溢出、文字重叠、焦点和对话框操作可达性。

### Flutter

```bash
cd app
./rabbit test test/domain/batches/statistics_test.dart \
  test/data/repositories/batches/statistics_test.dart \
  test/ui/batches/screens/statistics_test.dart
./rabbit check
```

需要真实设备时运行共享实际后端验收：

```bash
RABBIT_ANDROID_E2E_DEVICE_ID=<device-id> \
  bash scripts/batch-statistics-cross-client-e2e.sh

RABBIT_ANDROID_E2E_DEVICE_ID=<device-id> \
RABBIT_BATCH_STATISTICS_SUITE=complex \
  bash scripts/batch-statistics-cross-client-e2e.sh
```

运行器负责记录设备、后端、MySQL、验证码和产物前置条件，并在退出时恢复环境。

## 5. 最终检查

- [x] `task.py validate` 通过，两个 manifest 各 43 项，无缺失或重复路径；每项均有文件理由，实际变更代码由代理按变更清单完整读取。`design.md` 超过单文件注入上限会触发警告，检查代理按任务工件加载流程另行读取全文。
- [x] `lens_diagnostics mode=all` 覆盖本次 23 个文件且无阻断错误；16 个 jscpd 重复警告已逐项审查并 defer，均为并行统计 CTE 或独立 fixture/cleanup 的有意重复。4 个 Admin/Node 和 4 个 Java 文件主 LSP 为 0；Dart LSP 超时由针对性与完整 `flutter analyze` 补充，Mapper XML 无 LSP 则由 Maven 门禁覆盖。
- [x] 后端 1,094 项完整单元和架构测试、Checkstyle 与 package 通过。
- [x] Admin lint、93 项测试、242 模块 build、复杂定义契约 8 项和批次统计浏览器脚本通过；补充畸形 schema-v1 响应及下载文件名参数回归。
- [x] Flutter `./rabbit check` 通过 628 项测试和分析，debug APK 构建成功。
- [x] 数据库迁移、权限、兔舍隔离、幂等和历史兼容有 25 项新鲜 schema 自动化证据。
- [x] 修复 main CI run `34116740251` 暴露的测试契约问题：附件 fixture 的非终止比率使用 DECIMAL128 完整精度，三个重放测试复用相同时间或完整请求体；隔离 schema 下相关 34 项 E2E、Checkstyle、精确值复算和 Java LSP 通过，等待推送后的远端 CI 复核。
- [ ] 固定 SQL 尚需证明生产全部兔舍在 7 个完整自然日内没有 `LEGACY_*_GAP`，发布检查记录需包含窗口、执行人和结果。
- [x] 28 项 code、顺序、原始值、展示值和四种状态在后端、Admin、Flutter 和 Excel 一致。
- [x] Excel 可正常打开，文件名和响应头正确，未授权或跨兔舍请求被拒绝。
- [x] 没有自动回填历史数据，没有修改任务范围外的业务。
- [x] 附件规模 MySQL 夹具在 V56 新鲜 schema 上生成 28 项全 `AVAILABLE` 的精确 API 结果。
- [x] 实际 `.xlsx`、Admin 页面和 Android 真机与同一夹具的 API 值一致，八组截图和运行清单齐全。
- [x] 跨端运行结束后业务 fixture 已清理、验证码配置已恢复、Git 工作树不含生成产物。
- [x] 已完成生产饲料结束上界修复，并由冻结的 `time-and-cycle-boundaries` 期望证明完成批次 `end_date` 整日包含、下一日零点排除。
- [x] 复杂 VIEWER 账号已在真实 API、XLSX、Admin 和 Android 中证明可查询和导出，但不能编辑出肉率或读取完整历史；OWNER 和无关兔舍账号回归通过。
