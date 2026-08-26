# 鸿兔项目 未完成项 · 注释日志系统 · 多 Agent 并行执行计划

> 单一事实来源。核对方式为代码级自查，不依赖飞书状态列，不依赖界面截图。
> 代码 HEAD `677e533` · 飞书 Base revision 855 · 成文 2026-08-25
>
> 关联文档：`manual-verification-checklist.md`（人工点检指引）、`tracking-and-backlog-decision.md`（决策留档）

---

## 零、核对方式与可复现性

本文所有判定由下列命令在当前工作树上直接产出，任何人可复跑。

```bash
# 判定「未做」：确认零命中
grep -rniE '疫苗|vaccin' --include=*.java --include=*.dart --include=*.tsx backend app/lib admin/src | grep -v node_modules
grep -rniE '\bota\b|checkUpdate|forceUpdate|downloadUrl' app/lib
grep -rniE 'captcha|kaptcha' --include=*.java backend | grep -v /target/
grep -ci nfc app/lib/src/ui/reproduction/sheets/event.dart

# 判定「部分完成」：确认缺口落在哪个入口
grep -ciE 'totalWeight|总重量|新增数量|卖家' app/lib/src/ui/rabbits/sheets/entry.dart
grep -rn 'pendingCompletion' app/lib admin/src
grep -nE '@(Get|Post|Put|Delete)Mapping' $(find backend -name AbnormalController.java ! -path '*/target/*')
```

### 三处需要澄清的命中

初次粗查有三处像是反例，逐一查证后原判定成立：

| 现象 | 查证结果 |
| --- | --- |
| `totalWeight/卖家` 全仓 104 处命中 | 全部落在**出库与销售**上下文（`OutboundSubmitService`、`SaleOrder`、`CreateSaleOrderRequest`）。新增兔子表单 `entry.dart` 命中 **0**，缺口成立 |
| 异常记录有 1 个 `@PostMapping` | 是 `POST /abnormal/{id}/deal`（处理已有记录）。该 Controller 只有 list 和 deal 两个端点，**无 create**，缺口成立 |
| 批次统计指标有 3 处命中 | 一处是测试断言文案 `"流产结果要可统计，否则算不出流产率"`，两处是 dashboard 的**兔舍级**「仔兔成活率」。**批次级**指标确实不存在，缺口成立 |

---

## 一、核对结论总览

飞书需求表 81 条，42 条未标记完成。逐条核对源码后：

| 真实状态 | 条数 | 含义 |
| --- | --- | --- |
| 已完成，仅状态未更新 | 17 | 无开发量，待人工点检后改状态 |
| 部分完成 | 14 | 主体已交付，缺明确的边角 |
| 确实没做 | 11 | 需要排期 |

外加 1 条状态标反：`recvrqlA3OU53F` APP OTA 升级，飞书标「验收中」，代码里零命中。

17 条已完成项的逐条源码证据见 `manual-verification-checklist.md` A 组，本文不再重复。

---

## 二、未完成项清单（25 项）

### 2.1 部分完成，需补齐（14 项）

| 编号 | 飞书单号 | 已有 | 缺口 | 涉及面 |
| --- | --- | --- | --- | --- |
| F7 | `recvsV3h8S6Ovc` `recvsV3pCDzZej` `recvsV3qcy068u` `recvsV3w0NLret` `recvt72IkTmhpT` `recvt72IJvo7DL` | 性别、成长阶段、品种、来源、体重；种公兔可配/休整；种母兔六阶段入轨字典 `GET /api/repro/entry-points` | 批量新增数量 + 总重量；创建时母亲兔 ID；卖家字段。六条共用一套实现 | 后端 + App + Admin |
| F9 | `recvqh6N0wWVjR` | 按兔种分别填阶段和天数，三端齐全 | 「排 2-9 × 列 1-4 × 层 2-3」范围批量放兔，三端零命中 | 后端 + App + Admin |
| F10 | `recvt6SWUAVejo` | 断奶端到端可用，`BatchWeaningSeparationService:75-152` | 「待分配入笼商品兔数量」未出现在笼位管理页 | App + Admin |
| F11 | `recvqh3EJXzmO1` | 空批次可建、后续可加兔、手动点完成 | `pendingCompletion` 后端有派生字段并有 IT 覆盖，双端 **0 处消费**；「每母兔每批次唯一周期」未做，见决策 1 | App + Admin |
| F8 | `recvrqsCS3Six3` | 主要操作已是文字按钮 | Flutter 列表行内约 12 处纯图标按钮：`cages/widgets/management.dart:231,372,623,645,720,807`、`batches/screens/detail.dart:1235,1342,1483,1490,1497,1506` | App |
| F2 | `recvt7VzBVO50v` | `rabbit_abnormal_conditions` 表和图片服务齐备，分娩失败自动记一条 | 无手动新增接口，兔只详情页无入口 | 后端 + 双端 |
| — | `recvsUUz1W4eG3` `recvsUUAXydUwg` `recvsUUMyAT4Sd` | 伞形条目 | 随上述各条自动收敛，无独立工作量 | — |

### 2.2 确实没做（11 项）

| 编号 | 飞书单号 | 需求 | 关键事实 | 涉及面 |
| --- | --- | --- | --- | --- |
| F1 | `recvt7fkY08CyR` 之外的 `recvt7fpa64K76` | 接种疫苗 | 全仓 `疫苗\|vaccin` **零命中**，无表无接口无界面 | 迁移 + 后端 + 双端 |
| F6 | `recvtchyDpV4b7` | 投喂完成 NFC | 后端 `modules/feed` 已有，**App 无投喂录入页** | App |
| F3 | `recvqgIXayF1W8` | 密码登录图片验证码 | 后端 `captcha` 零命中，`AuthController:66-68` 只收账号密码 | 后端 + 双端 |
| F12 | `recvrqlA3OU53F` | APP OTA 升级 | `app/lib` 零命中，`pubspec.yaml` 无升级依赖，后端无版本清单接口。CI 已产出签名 APK | 后端 + App |
| F4 | `recvsUVpLZ09rx` `recvt6zZEIjiK8` `recvt855ZE8KgN` `recvt8aQ6LIyxs` | 4 个 NFC 触点 | 各自宿主文件 `nfc` 命中均为 0。底座已跑通（`move.dart` 换笼流程） | App |
| F5 | `recvt6DIqoMP8b` `recvt7ipLUiZzv` | 母亲兔 ID NFC | 更底层的问题：`entry.dart` 创建路径**连母亲兔 ID 输入框都没有**，NFC 无处可挂 | 后端 + App |
| F13 | `recvsV7QhkxxvD` | 批次统计数据 | 约 20 项指标中仅 4 项有数据源。料肉比缺饲喂消耗，出栏均重和成活率缺销售重量 | 见决策 2 |

### 2.3 两个待决策项

| 决策 | 内容 | 选项与成本 |
| --- | --- | --- |
| 决策 1 | 批次「每母兔每批次唯一周期」 | A 维持现状改需求文 0.5h ｜ B 按新定义改造 20h ｜ C 暂缓 0h。**当前代码是相反语义**，`ReproParallelCycleIT:185-201` 明确断言同批次内保留两个开放周期（血配场景），提交 `4ef1769` 写明该条被主动推迟 |
| 决策 2 | 批次统计数据 | A 推迟 0h ｜ B 只做可算的 4 项 4h ｜ C 全量含采集补齐 14h |

---

## 三、注释日志系统（操作追踪）开发需求与计划

### 3.1 现状问题

追踪能力被割裂成两套互不连通的机制：

- **API 层** `audit_logs`：知道谁调了哪个接口，但没有 `batch_id` / `cage_id` / `rabbit_id`，也不存请求体。事故复盘时还原不出写了什么
- **业务表** `create_by` / `update_by`：知道谁写了这行，但语义分裂。部分服务存数字用户 ID（`WeightService:59`、`SaleService:73`、`BatchService:237`、`CageAdminService:44`），另一部分存展示名（`ReproStateMachineService:682`、`TreatmentService:71`、`KitPlacementService:89`），**跨表归因直接错**

五个必需标识的覆盖打分：兔舍 9/10、兔只 7/10、操作人 5/10、批次 5/10、兔笼 3/10、统一可查询性 2/10。

全仓唯一真正的追加型事件流是 `repro_events`（`V26__doe_breeding_v2_additive.sql:14-35`），设计最好：追加写、`uk_re_request` 幂等、JSON 载荷、操作人快照。但只覆盖繁育域。

### 3.2 目标

一次写操作落地后，能回答：**谁、在哪个兔舍、对哪个批次的哪只兔、在哪个笼位、做了什么、改前改后是什么、什么时候。** 且有统一查询入口。

### 3.3 三层设计

#### 第一层：`OperationContext`（承载上下文）

放在 `rabbit-access`，紧邻已有的 `HouseContext`。后者已经是携带 `userId/houseId/perms/role` 的 ThreadLocal，比只有 `userId` 的 `AuthContext` 更适合做基座。

字段：`houseId, batchId, cageId, rabbitId, userId, operatorName, requestId, traceId`。由 `BusinessAuthenticationInterceptor.preHandle` 播种。

`OperatorNameResolver` 从 `rabbit-production` 移到 `rabbit-access`（依赖 `SysUserMapper`，模块方向正确），并加请求级缓存 —— 现在每请求解析一次，而批量端点可处理 500 只兔。

#### 第二层：`@TrackedOperation`（标注写操作）

```java
@TrackedOperation(
    code = "weight:create",
    eventType = "WEIGHT_RECORDED",
    rabbitId = "#entity.rabbitId",
    batchId  = "#entity.batchId",
    dedup = true
)
```

选 SpEL 而非参数注解，因为现有方法签名里标识要么是入参要么是 DTO 字段，表达式能直接取；解析结果按 `Method` 缓存。

#### 第三层：MyBatis 自动填充插件（消灭样板）

仿照已有的 `HouseSqlGuardInterceptor`（`@Intercepts` on `Executor.update`，由 `app.mybatis.write-guard` 配置），拦截写入，对实现 `Stamped` 接口的实体自动填 `create_by / update_by / house_id / operator_name`。

这一层删掉 **65 处 `setCreateBy` 和 70 处 `setUpdateBy`**，分布在 23 个 service 类。

### 3.4 分阶段任务

| 编号 | 阶段 | 内容 | 迁移 | 原始工时 |
| --- | --- | --- | --- | --- |
| T0 | 前置 | 恢复 e2e 闸门（已完成，见 五.0） | — | 已交付 |
| T1 | 注解基建 | AOP 起步依赖、`OperationContext`、`OperatorNameResolver` 迁移、MyBatis 自动填充、两个切面及顺序、85 处 `@Transactional` 自调用排查 | — | 7.5h |
| T2 | `create_by` 统一 | 清洗迁移 + 删 135 处 setter 调用 + 加 `operator_name` 快照列 | V44 | 6h |
| T3 | 兔笼维度 | 新建移笼记录表，`cage_id` 从反查改写时快照，历史回填 | V45 | 9h |
| T4 | 事件流泛化 | `repro_events` 加 `cage_id` 和 `target_type/target_id`，约 45 个写方法加注解 | V46 | 9h |
| T5 | `audit_logs` 扩列 | 加 `batch_id/cage_id/rabbit_id`，由请求属性回填 | V47 | 2h |
| T6 | 统一读接口 | `GET /api/operation-events` + admin 页面 + app 消费 | — | 4.5h |

小计 **38.3h**。

### 3.5 三个必须在设计期定死的技术约束

**切面与事务的相对顺序。** 去重状态必须写在事务**外**，否则回滚会把 `markFailed` 一起回滚；事件写入必须在事务**内**，否则业务失败了事件还在。这要求拆成两个切面：外层 `@Order(0)` 绑上下文并做去重，内层排在事务通知之后做事件持久化。**这一条设计错了，后面三十多小时的工作全部建在错误地基上。**

**Spring AOP 不拦截同类自调用。** 85 处 `@Transactional` 必须逐一排查内部调用，否则注解静默失效。

**批量端点。** 单次可处理 500 只兔，事件必须批量插入，不能逐条。

### 3.6 顺带修掉的既有缺陷

`RequestDedupService` 全类**无 `@Transactional`**，会加入调用方事务。`WeightService` 等在 `@Transactional` 方法内部调用 `markFailed`，异常重抛导致回滚时，失败标记一并回滚，去重记账失效。注解基建重排顺序时一并修复。

---

## 四、风险登记

| 级别 | 风险 | 影响 | 触发条件 | 缓解 |
| --- | --- | --- | --- | --- |
| 高 | 切面与 `@Transactional` 顺序设计错误 | T1 之后所有工作返工，非局部 | 去重与事件写入的事务边界搞反 | T1 完成即用 `WeightService` 和 `TreatmentService` 双域试点验证，再铺开 |
| 高 | 决策 1 选 B 后中途推翻 | +25h | 血配场景批次归属语义未与现场确认 | 决策前先与现场确认血配场景第二个周期归属 |
| 中 | 存量 `create_by` 出现预期外脏值 | T2 +4h | 34 张表混存数字 ID 与展示名，约 60% 已是数字 ID | 迁移前先对生产库快照跑一遍分布统计 |
| 中 | Spring AOP 自调用导致注解静默失效 | 事件流出现无声空洞，测试可能测不出来 | 85 处 `@Transactional` 中存在内部调用 | T1 内产出自调用清单，纳入 ArchUnit 规则 |
| 中 | 多 agent 并发改同一模块产生合并冲突 | 整体 +15% | 泳道划分不当，两个 agent 同时改 `rabbit-production` 同一包 | 见第五节泳道与迁移号预分配 |
| 低 | e2e 库隔离 | 并发宽度降到 1 到 2 | 已解决：`E2E_SCHEMA_SUFFIX=_a1 bash scripts/e2e-local.sh` | 每 agent 一套独立 schema |
| 低 | 全部测试集中在 `rabbit-boot` | 每次验证都是全量构建 | 32 个 `*Test.java` 和 50 个 `*IT.java` 全在 boot 模块 | 内环用 `mvn -o test`（9.4 秒 142 例），外环才跑 e2e |

---

## 五、多 Agent 并行执行计划

### 5.0 T0 e2e 闸门（已完成）

原先记录的根因「`docker-compose.yml` 把 mysql 的 `ports` 注释掉了」**是错的**。真实情况：

- `docker-compose.yml` 不暴露 3306 是**有意的安全设置**，其他服务都用 `127.0.0.1` 绑定，不应该改
- CI 本来就是好的，`_quality-gates.yml` 起 mysql service 映射 `3306:3306` 并显式设三个变量
- 真正的缺陷是文档自相矛盾：`testing.md` 先叫你用 `-p 13307:3306` 建容器，紧接着的
  复制粘贴示例却写 `localhost:3306`，且只列了三个变量中的一个

交付物：

| 改动 | 内容 |
| --- | --- |
| 新增 `scripts/e2e-local.sh` | 自动定位 JDK 21、拉起或复用 `rabbit-e2e` 容器、建三个库、导出三个变量、跑 `mvn -Pe2e verify` |
| 修 `docs/project/testing.md` | 消除 3306 与 13307 的矛盾，补齐三个变量的完整示例，说明 CI 与本地互不影响 |
| 未动 | `docker-compose.yml`、CI 配置、Java 里的默认值全部保持原样 |

验证结果（`bash scripts/e2e-local.sh`）：

| 轮次 | 条件 | 结果 |
| --- | --- | --- |
| 修复前 | 直接 `mvn -Pe2e verify` | 208 例中 **205 errors**，全挂在 Flyway 建连 |
| 1 | 脚本，未 clean | 208 跑完，1 failure（`FieldInventoryNfcExportIT` 报 `NoClassDefFoundError`） |
| 2 | 脚本 + `E2E_CLEAN=1` | 209 跑完，1 error（`LargeHouseOutboundTaskScaleIT`），耗时 15:41 |
| 3 | 脚本 | **209 跑完，0 failures，0 errors，3 skipped，耗时 14:48，BUILD SUCCESS** |

两个干扰项已定性，均与本次修复无关：

- `FieldInventoryNfcExportIT` 的 `NoClassDefFoundError` 是 `target/` 陈旧产物。类在源码、
  `target/classes` 和 jar 里都在，`mvn clean` 后直接通过。同一现象伴随
  "The forked VM terminated without properly saying goodbye"。
- `LargeHouseOutboundTaskScaleIT` 报 `Table 'rabbit_app_e2e.rabbits' doesn't exist`，
  **偶发**：隔离跑通过（126.8 秒），第 3 轮全量也通过。

### 一个结构性隐患，直接影响多 agent 方案

`E2eTestSupport`（`rabbit-boot/src/test/java/com/rabbit/app/e2e/E2eTestSupport.java:39-42`）的
`@BeforeEach` 对**主库**执行 `flyway.clean()` 加 `flyway.migrate()`。所有继承它的 E2E 类
共用同一个 `rabbit_app_e2e`，每个用例前都把库清空重建。套件内部是串行的（无任何
`parallel` 配置、无 `junit-platform.properties`、无 `@Execution`），所以单进程没问题。

但这意味着：**两个 agent 同时跑 e2e 而不隔离 schema，会互相清库，产生大量难以
归因的假失败。** 并行时必须给每个 agent 分配 `E2E_SCHEMA_SUFFIX`。

脚本里修掉的两个坑，值得写下来：

1. **JDK 检测不能只判空。** 开发机 `JAVA_HOME` 默认指向 JDK 26，只判
   `-z "$JAVA_HOME"` 会直接放行，然后撞上 enforcer 失败，而报错和数据库无关，极易误判。
   改成实际读取主版本号再决定是否切换。
2. **`set -o pipefail` 遇上 `grep -q` 是竞态。** `docker ps | grep -qx name` 在 grep 命中后
   立即退出并关闭管道，docker 收到 SIGPIPE 返回 141，pipefail 把整条管道判为失败，
   于是「容器存在」被误判成「不存在」，接着 `docker run` 因重名报 125。看运气复现，
   改用字符串匹配避开。

### 5.1 硬约束

| 约束 | 事实 | 对并行的影响 |
| --- | --- | --- |
| Flyway 版本号全局递增 | 当前 42 个迁移，下一个 V44 | 多 agent 同时加迁移会撞号，**必须预分配** |
| 测试全在 `rabbit-boot` | 其余四个模块 0 测试文件 | 无模块级测试隔离，每次验证都是全量构建 |
| Maven 模块链式依赖 | access ← production ← reporting ← boot | 改底层模块触发上层全量重建 |
| e2e 需要真实 MySQL | 无 Testcontainers，三个数据源变量默认指向 localhost:3306 | 已由 `scripts/e2e-local.sh` 解决，支持 `E2E_SCHEMA_SUFFIX` 每 agent 一套库 |
| 独立泳道数量有限 | 后端写路径、兔笼域、Flutter 表层、全新后端功能、跨端功能 | 约 5 条，超过 5 个 agent 收益迅速衰减 |

### 5.2 迁移版本号预分配

开工前一次性钉死，避免撞号：

| 版本 | 归属 | 内容 |
| --- | --- | --- |
| V44 | T2 | `create_by` 清洗 + `operator_name` 快照列 |
| V45 | T3 | 移笼记录表 + `cage_id` 快照回填 |
| V46 | T4 | `repro_events` 扩列 |
| V47 | T5 | `audit_logs` 三列 |
| V48 | F1 | 疫苗接种表 |
| V49 | F7 | 入栏表单字段（总重量、母亲兔 ID、卖家） |
| V50 | 预留 | 决策 1 选 B 时的批次周期归属改造 |

### 5.3 四 Agent 泳道与排期

关键洞察：**T1 注解基建是后端串行瓶颈，但 Flutter 表层工作完全不依赖它**，所以 T1 那 7.5 小时里另外三个 agent 不必空转。

| 时段 | Agent 1（追踪主线） | Agent 2（兔笼与后端） | Agent 3（全新功能） | Agent 4（跨端与表单） |
| --- | --- | --- | --- | --- |
| 0 到 8h | T0 修 e2e 0.3h → **T1 注解基建 7.5h** | F4 四个 NFC 触点 4h → F8 按钮文字化 3h | F6 投喂录入页 + NFC 6h → F12 OTA 前端 2h | F7 表单 Flutter 部分 4h → F5 母亲兔 ID 前端 2h → F12 OTA 收尾 2h |
| 8 到 20h | T2 `create_by` 统一 6h → T5 `audit_logs` 2h | **T3 兔笼维度 9h** | **F1 疫苗全栈 10h** | F9 批量范围入笼 8h → F10 断奶待分配提示 3h |
| 20 到 32h | **T4 事件流泛化 9h** → T6 统一读接口 4.5h | F2 异常记录 5h → F11 `pendingCompletion` UI 3h → F3 图片验证码 4h | F7 表单后端 4h → F5 母亲兔 ID 后端 2h → 支援 T4 注解铺开 | 跨端联调、回归 |
| 32 到 40h | 全员收敛：集成、跨端联调、e2e 全绿、文档回填 | | | |

同步点两个：**t=8h**（T1 产出后，所有后端写路径工作才能开始）和 **t=32h**（进入收敛）。

### 5.4 工时随 agent 数的变化

基准原始工作量 **104h**（追踪 38.3h + 飞书功能 66h，不含两个待决策项）。其中真正无法并行的只有 T0 + T1 = **7.8h**，串行占比 7.5%。

| Agent 数 | 墙钟工时 | 相对单 agent 加速 | 说明 |
| --- | --- | --- | --- |
| 1 | 104h | 1.0x | 纯串行 |
| 2 | 59h | 1.8x | |
| 3 | 47h | 2.2x | |
| **4** | **40h** | **2.6x** | **推荐**，泳道刚好铺满，同步点少 |
| 5 | 36h | 2.9x | 边际收益仍为正 |
| 6 | 34h | 3.1x | 开始出现泳道抢占 |
| 8 | 32h | 3.3x | 收益基本停止，合并冲突吃掉增量 |

加速比明显低于 agent 数，三个原因：串行的 T1、全部测试集中在 `rabbit-boot` 导致验证不能并行分摊、以及跨 agent 合并与联调开销随并发数上升。

**推荐 4 个 agent，约 40 小时墙钟**，等于连续跑约 1.7 个自然日，或按每天 8 小时算 5 个工作日。

### 5.5 决策对工时的影响

| 决策组合 | 增量 | 4-agent 墙钟 |
| --- | --- | --- |
| 决策 1 选 A/C + 决策 2 选 A | +0 到 0.5h | **40h** |
| 决策 1 选 A/C + 决策 2 选 B | +4h | 42h |
| 决策 1 选 B + 决策 2 选 A | +20h | 47h |
| 决策 1 选 B + 决策 2 选 C | +34h | 53h |

三档预期（按推荐组合）：顺利 **32h**，现实 **40h**，若 T1 切面顺序推翻重做则 **58h**。

以上均不含人工评审排队时间。按五个阶段闸门算，评审等待通常超过 40 小时的开发本身。

---

## 六、执行检查点

开工前：

- [x] T0 完成，`bash scripts/e2e-local.sh` 能跑绿
- [ ] 迁移版本号 V44 到 V50 已按 5.2 分配并周知
- [ ] 每个 agent 分配独立 e2e schema（`E2E_SCHEMA_SUFFIX=_a1` 到 `_a4`）
- [ ] 决策 1 和决策 2 已定

T1 交付时必须同时给出：

- [ ] 切面与事务边界的时序图或测试，证明 `markFailed` 在回滚后仍存活
- [ ] 85 处 `@Transactional` 的自调用排查清单
- [ ] `WeightService` 和 `TreatmentService` 双域试点跑通
- [ ] `mvn -o test` 142 例保持全绿

每个泳道合并前：

- [ ] `mvn -o --file backend/pom.xml test` 全绿
- [ ] `pnpm --dir admin lint && pnpm --dir admin build` 通过
- [ ] `cd app && ./rabbit check` 通过
- [ ] 涉及迁移的，在独立 schema 上验证过 Flyway 顺序
