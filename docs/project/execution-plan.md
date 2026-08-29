# 鸿兔项目 未完成项 · 注释日志系统 · 多 Agent 并行执行计划

> 单一事实来源。核对方式为代码级自查，不依赖飞书状态列，不依赖界面截图。
> 成文基线 `677e533` · 现基线 `4d286f8` · 飞书 Base revision 855
>
> 关联文档：`manual-verification-checklist.md`（人工点检指引）、`tracking-and-backlog-decision.md`（决策留档）

**基线变更说明。** `4d286f8` 交付了批次编号可改与提醒页显示批次编号。查过全部 81 条飞书记录，
「批次编号 / 重命名 / 改名」**零命中** —— 这是需求表**之外**的工作量。推论比这条改动本身重要：
需求表并不完整覆盖实际在做的事，本文的 104h 只涵盖表内 25 项，按这个数承诺会漏掉同类插入项。

复跑第零节的核对命令，11 项未做仍全部零命中，`pendingCompletion` 双端仍 0 处消费，
**25 项未完成清单不变**。

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

### 2.3 两个决策（已定）

| 决策 | 结论 | 增量 |
| --- | --- | --- |
| 决策 1 | **选 B**：按新定义改造批次「每母兔每批次唯一周期」 | +20h |
| 决策 2 | **选 B**：只做当前有数据源的 4 项指标，其余标注缺口 | +4h |

决策 1 的语义歧义已由飞书原文消除，不必再向现场确认：

> 每只母兔在同一 Batch 内只有一个繁育周期，**当母兔同时位于两个繁殖周期时它也同时处于两个批次之中**。

即血配的第二个周期**不是被禁止，而是要落到另一个批次**。风险登记里「血配场景批次归属未确认」
这条因此下调 —— 原估的 +25h 返工风险主要来自语义不明，现在方向是确定的。

仍需实现方自行判断的是：第二个周期的批次归属**自动新建还是要求显式传入**。这是 A2 泳道的
首要设计决策。

改造要推翻的既有断言在 `ReproParallelCycleIT:186-201`，注释写着「两条并行周期都应计入批次的
未结束周期」，提交 `4ef1769` 说明该条被主动推迟。

基准工作量因此由 104h 升至 **128h**。

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
| 已解决 | 全部测试集中在 `rabbit-boot` | 每次验证都是全量构建 | 单测已下沉到各业务模块（platform 95 / access 232 / production 439 / reporting 129 / boot 19，合计 914），`BootTestPlacementTest` 防回流；`*IT.java` 仍在 boot，因为它们需要完整应用上下文 | 内环可用 `mvn -pl <模块> -am test` 只跑改动模块，外环才跑 e2e |

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

> **2026-08-27 补充：重置机制已改，结论不变。** 现在是每个 JVM 建一次 schema，
> 用例之间只 TRUNCATE 非空表（见 `E2eDatabaseReset`），全量从 14 分 48 秒降到 7 分 37 秒。
> 共库依然会互相清数据，所以上面那句「必须分配 `E2E_SCHEMA_SUFFIX`」仍然成立，
> CI 分片也是基于同一理由每片一套库。当前写法见 `docs/project/testing.md`。

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
| 单测按模块归位（已改） | 四个业务模块各自持有其业务范围的单测 | 可以 `mvn -pl <模块> -am test` 只验证改动那一块 |
| E2E 仍在 `rabbit-boot` | 55 个 `*IT.java` 需要完整 Spring 上下文，而启动类在 boot | 模块级拆不开，只能靠 CI 分片并发 |
| Maven 模块链式依赖 | access ← production ← reporting ← boot | 改底层模块触发上层全量重建 |
| e2e 需要真实 MySQL | 无 Testcontainers，三个数据源变量默认指向 localhost:3306 | 已由 `scripts/e2e-local.sh` 解决，支持 `E2E_SCHEMA_SUFFIX` 每 agent 一套库 |
| 独立泳道数量有限 | 后端写路径、兔笼域、Flutter 表层、全新后端功能、跨端功能 | 约 5 条，超过 5 个 agent 收益迅速衰减 |

### 5.2 迁移版本号分配（按合并顺序，非按泳道）

**原先按泳道预分配的表是错的，已推翻。** `application.yml:15-18` 的 Flyway 配置只设了
`baseline-on-migrate`，**没有启用 `out-of-order`**（默认 false）。于是在任何已迁移的库上
（生产库、开发库都已到 V43），先合入 V50 再补 V44 会被 Flyway 直接拒绝。

e2e 不受影响 —— `E2eTestSupport` 跑在全新库上（当时是每个用例 `flyway.clean()` 再
`migrate()`，现在是每个 JVM 一次，都是从空库起步），
**所以这个坑在 e2e 里测不出来，只会在合并到已有库时炸**。

规则改为：**迁移号按预期合并顺序发放，先合并的拿低号。**

| 版本 | 归属 | 内容 | 状态 |
| --- | --- | --- | --- |
| V44 | A2 · 决策 1 选 B | 批次周期归属约束 | 已交付 |
| V45 | A3 · F1 | 疫苗接种表 | 已合入 |
| V46 | A4 · F12 | OTA 版本清单 | 已合入 |
| V47 | — | 未使用，A5 判定可复用现有坐标，无需建表 | 空号可回收 |
| V48 | T2 | `create_by` 清洗 + `operator_name` 快照列 | 未分配 |
| V49 | T3 | 移笼记录表 + `cage_id` 快照回填 | 未分配 |
| V50 | T4 | `repro_events` 扩列 | 未分配 |
| V51 | T5 | `audit_logs` 三列 | 未分配 |
| V52 | F7 | 入栏表单字段（总重量、母亲兔 ID、卖家） | 未分配 |

**一个必须改的做法：关键约束不能只写在文档里。** A2 和 A3 都在最终汇报里报了迁移号冲突，
两次都是误报 —— 它们读的是启动时那版文档，而号段是在它们跑起来之后才重排的。
文档的中途修正传不到已启动的 agent。下一波把迁移号、schema 后缀、设备序列号这类
硬约束**直接写进提示词**，文档只做背景。

波次 1 里只有 V44 和 V45 是确定要建的，V46、V47 是预留号 —— A4 和 A5 自行判断是否真需要迁移，
不需要就不要加，空号对 Flyway 无害。

配套约束：**不得在高号迁移已进入共享库之后再合并低号迁移。** 若某泳道提前完成，
合并前把 `.sql` 文件重编号即可 —— 只要它还没落到任何共享库，改名是安全的。

### 5.3 四 Agent 泳道与排期

关键洞察：**T1 注解基建是后端串行瓶颈，但 Flutter 表层工作完全不依赖它**，所以 T1 那 7.5 小时里另外三个 agent 不必空转。

| 时段 | Agent 1（追踪主线） | Agent 2（兔笼与后端） | Agent 3（全新功能） | Agent 4（跨端与表单） |
| --- | --- | --- | --- | --- |
| 0 到 8h | T0 修 e2e 0.3h → **T1 注解基建 7.5h** | F4 四个 NFC 触点 4h → F8 按钮文字化 3h | F6 投喂录入页 + NFC 6h → F12 OTA 前端 2h | F7 表单 Flutter 部分 4h → F5 母亲兔 ID 前端 2h → F12 OTA 收尾 2h |
| 8 到 20h | T2 `create_by` 统一 6h → T5 `audit_logs` 2h | **T3 兔笼维度 9h** | **F1 疫苗全栈 10h** | F9 批量范围入笼 8h → F10 断奶待分配提示 3h |
| 20 到 32h | **T4 事件流泛化 9h** → T6 统一读接口 4.5h | F2 异常记录 5h → F11 `pendingCompletion` UI 3h → F3 图片验证码 4h | F7 表单后端 4h → F5 母亲兔 ID 后端 2h → 支援 T4 注解铺开 | 跨端联调、回归 |
| 32 到 40h | 全员收敛：集成、跨端联调、e2e 全绿、文档回填 | | | |

同步点两个：**t=8h**（T1 产出后，所有后端写路径工作才能开始）和 **t=32h**（进入收敛）。

### 5.4 工时随 agent 数的变化（已修正）

**原先「串行只有 T0 + T1 = 7.8h，占比 7.5%」的说法是错的。** 实测：

```bash
# T2 要删 setter 的服务类 24 个，T4 要加注解的服务类 37 个，两者重叠 19 个
comm -12 <(grep -rln 'setCreateBy\|setUpdateBy' --include=*.java backend --exclude-dir=target | grep -i service | grep -v /src/test/ | sort -u) \
         <(grep -rln '@Transactional'          --include=*.java backend --exclude-dir=target | grep -i service | grep -v /src/test/ | sort -u) | wc -l
```

重叠的 19 个类包括 `BatchService`、`RabbitService`、`FeedService`、`OutboundSubmitService` 等。
T2 和 T4 因此**不能分给两个 agent**，否则在同一批文件上撞车，只能同泳道串行。

于是追踪链的真实长度是：

```text
T1 7.5h → T2 6h → T4 9h → T6 4.5h = 27h
```

这 27 小时无论投多少 agent 都压不下去。串行占比是 27/128 ≈ **21%**，不是 7.5%。

墙钟下界取两者较大值：链长 27h，或总量 128h 除以 agent 数。

| Agent 数 | 理论下界 | 含约 20% 集成开销 | 说明 |
| --- | --- | --- | --- |
| 1 | 128h | 128h | 纯串行 |
| 2 | 64h | ~77h | |
| 3 | 43h | ~52h | |
| **4** | **32h** | **~40h** | 泳道铺满，同步点少 |
| **5** | **27h** | **~33h** | **触底**，链长成为唯一瓶颈 |
| 6 | 27h | ~33h | 无增益 |
| 8 | 27h | ~33h | 无增益，合并开销反而上升 |

**5 个 agent 触底。** `128/5 = 25.6h` 已低于 27h 链长，再加人只是让空闲 agent 等追踪链跑完。

决策 1 选 B 那 20h 虽然也是单块不可分，但短于 27h，**藏在追踪链的影子里**，不是绑定约束。

区间给法：**40 到 47 小时**。下界来自上表，上界来自更悲观的集成开销口径。
真实值取决于 T1 有没有返工 —— 若切面顺序推翻重做，链长直接变 34.5h，所有下界跟着抬。

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

### 5.6 波次 1 泳道（进行中）

三条泳道文件不相交，各自在独立 git worktree 里工作，完成后提交到各自分支。

| Agent | 泳道 | 范围 | 迁移 | e2e schema | 模型 |
| --- | --- | --- | --- | --- | --- |
| A1 | 注解基建 T1 | `rabbit-access`、`rabbit-platform`、MyBatis 插件、Weight + Treatment 双域试点 | 无 | `_a1` | 默认 |
| A2 | 批次唯一周期（决策 1 = B） | `modules/batch`、`modules/repro`、重写 `ReproParallelCycleIT` | V44 | `_a2` | 默认 |
| A3 | 疫苗接种 F1 | 全新模块 + app + admin | V45 | `_a3` | 默认 |
| A4 | OTA 升级 F12 | 版本清单接口 + app 升级流程 | V46 预留 | `_a4` | `gpt-5.6-terra` xhigh |
| A5 | 批量范围入笼 F9 | 笼位范围选择 + 批量放兔 | V47 预留 | `_a5` | `gpt-5.6-terra` xhigh |

A1 到 A3 在换模型要求提出前已跑了十四分钟（A2 已 52 次工具调用），中途重启会白扭这些进度，
所以保留原模型跑完，**从 A4 起新开的 agent 一律用 `gpt-5.6-terra` + xhigh**。

三个故意的范围切割：

- **A2 的前端推到波次 2**。否则它会和 App 表层泳道（F8 图标按钮、F11a `pendingCompletion` UI）
  抢 `app/lib/src/ui/batches/screens/detail.dart` —— F8 要改的 6 个行号就在这个文件里。
- **A1 只做基建加双域试点，不铺开到 45 个写方法**。先拿两个域验证切面与事务顺序真的成立，
  再谈铺开。这是风险登记里那条高风险的直接缓解。
- **A3 选疫苗而不是别的新功能**，因为它全仓零命中，碰撞面天然为零。

### 5.7 App 验证设备分配

原先记的「只有一台真机，带 App 交互的泳道必须串行验证」**已不成立**。拉起两个模拟机后
共三台目标，一人一台，互斥锁已取消。

| 目标 | 设备 | 系统 | 分配给 |
| --- | --- | --- | --- |
| `00152155M000372` | A059 真机（Nothing） | Android 15 | A4 · OTA |
| `emulator-5554` | AVD Pixel_10_Pro | Android 17 | A3 · 疫苗 |
| `emulator-5556` | AVD Medium_Phone | Android 17 | A5 · 批量入笼 |

多设备连接下所有 `adb` 命令必须带 `-s <serial>`，不带会直接报错。

**真机给 A4 而不是别人，是因为 OTA 的核心风险在安装环节。** `REQUEST_INSTALL_PACKAGES` 权限、
未知来源安装的系统弹窗、下载完成后拉起安装器的行为，在 Android 17 模拟机和 Android 15 真机上
表现不一致，模拟机上跑通不代表现场能用。

模拟机反过来有个真机没有的优势：可以改分辨率和字号压测布局。项目既有的无障碍基线是
360x800 真实 200% 字号，已写进多个 widget 测试；范围选择这类控件在这个条件下不能溢出或不可点。

```bash
adb -s emulator-5556 shell wm size 360x800
adb -s emulator-5556 shell settings put system font_scale 2.0
# 验证完还原
adb -s emulator-5556 shell wm size reset
adb -s emulator-5556 shell settings put system font_scale 1.0
```

验证要求不变：不是跑通 widget 测试就算完，而是要确认入口位置符合现有操作习惯、提交后反馈明确、
异常路径（网络失败、重复提交、拒绝权限）不会让人卡住或误以为成功。

### 5.8 波次 1 结果

五条泳道全部交付。每次合并前的 e2e 都是在**前一次合并结果上**跑的集成测试，不是各自分支单测。

| 泳道 | 交付 | 合并时 e2e | 人工验收 |
| --- | --- | --- | --- |
| A4 | OTA 升级，V46 | 212 | **通过**（模拟器） |
| A1 | 注解基建 T1，无迁移 | 219 | 无可见交互，由边界 IT 覆盖 |
| A3 | 疫苗接种，V45 | 223 | **通过** |
| A2 | 批次唯一周期，V44 | 224 | **通过** |
| A5 | 批量范围入笼，无迁移 | 226 | **通过** |

### 人工验收结论（2026-08-26）

跨端验收在带存量数据的 `rabbit_app_acceptance` 库上跑通，详细记录为一次性产物，
按《交互验收规范》第四节不入仓库。结论如下。

**迁移验证**：V44/V45/V46 在非空库（91 只兔、14 个批次、17 条未结束周期）一次应用成功，
0.143s。迁移前 V44 违规行为 0，恢复批次逻辑未触发。但该库是 e2e 夹具残留（同构
`H-CAGEOPS-*` 兔场），**不是生产级样本，生产库存量情况仍未知**。

**V44** 四步全中：同批次血配被 409 拒绝并给出改选指引、改批次后成功、
两条 OPEN 周期分属两批、`batch_rabbits` 显示母兔同属两批。前置确认 `pipeline_guard=NULL`，
命中的确实是新增的 `assertBatchCycleFree`。

**F9** 故意把停用笼与占满笼放进范围：4 个笼位请求、成功 2 笼 4 只、
跳过项带笼号与原因（「商品兔笼已满」「笼位已停用」），非整批失败，数量为每笼固定值。

**F1** 三次同 `requestId` 提交只落 4 行，`created` 从 4 变 0 但仍返回既有记录；
501 只超限被拒；跨兔场兔只报「兔子不存在」。

**F12** 发布 buildNumber 9999 后，旧版 4005/4006 查到 `updateAvailable: true`，
同版本查到 `false`。界面上点「检查更新」弹出「发现新版本 9.9.9」带发布说明，
两个按钮「暂不更新 / 立即更新」——非强制版本可跳过。下载失败时弹出内显示
「无法连接后端服务，请确认地址和网络」，并给出「暂时继续使用 / 重新下载」，
错误就在用户注视的弹层内且不把人卡死。真机安装环节（未知来源授权）仍待下一环节补验。

#### 验收期间发现并修复的缺陷

`gender: "FEMALE"` 返回 500 并泄漏 jar 路径、`BOOT-INF` 结构、mapper XML 位置和完整
`INSERT` 语句。根因是 `rabbits.type`/`rabbits.gender` 为 `varchar(1)` 编码，而两个 DTO 只有
`@NotBlank`。非 A5 引入，是既有缺陷被新端点继承。已补 `@Pattern`（`84d5036`）。

#### 两条开放项

**登录页协议提示被键盘遮挡**：不勾协议点登录，提示以屏幕底部浮层弹出，
而键盘占据下半屏，提示被挤在键盘上沿并盖住协议勾选框本身。命中交互规范第 1 条，
与 A3 在疫苗表单遇到的是同一类问题。属既有缺陷，不在 A1~A5 范围。

**`APP_*` 环境变量污染单测**：`CacheConfigurationTest` 与 `ApplicationSecretValidatorTest`
断言的是「没有配置时的默认行为」，为启动服务而 `export` 的密钥会泄漏进测试 JVM，
使这两条必然失败。清除后 164 全绿。**A4 此前报的「2 个单测失败」由此得到解释**，
先前归因于 `~/.m2` 污染是错的。

### 决策 1 的最终实现

第二个周期的批次归属：**要求调用方显式传入，服务端不自动建批**。

理由：需求把批次定义成用户创建、用户点击结束的容器，自动造的批次会带着用户没取过的编号出现在列表里，
还得用户亲手去结束，与「批次的结束由人员主动点击」直接冲突；且自动建批只覆盖「新建一个」，
剥夺了「放进现有批次」这个更常见的选择。代价是血配时多一次选批次交互，而那正是需要用户决策的地方。

注意区分：迁移里的「恢复批次」`V44-PARALLEL-H{house}-R{n}` 只用于**存量数据收敛**，
不是运行时行为。

### 波次 2 必须做的前端契约变更

`POST /api/repro/cycles` 新增 409：「该母兔在本批次已有进行中的生产周期（阶段：X），并行的下一轮请改选其他批次」。

三项跟进：血配入口先让用户选或新建批次再提交（不能沿用当前周期的 `batchId`）；
批次详情按批次算未结束周期数（一兔一批一条）；兔只详情的批次归属改为可多值。

### 两条工程教训

**并发在验证阶段是负收益。** 四条泳道同时跑 e2e 时单次从 15 分钟涨到 30 分钟，
只剩一个验证进程时立刻回到 15:58。并发的收益在写代码阶段，验证阶段反而互相拖慢 ——
下一波排期要把验证窗口错开，而不是让所有泳道在终点同时撞上去。

**worktree 机制会把收尾时的未提交内容打包成 `pi-agent: <任务名>` 提交。** 五条泳道中两条
中招：A4 的 `MainActivity.kt` 59 行、A5 的 687 行 admin 代码都落进了这种通用消息的提交，
其中 A5 那一个还夾带了一处范围外的 Dart 空检查模式回归。下一波在提示词里要求：
结束前自查 `git status`，任何剩余改动自己用表意的消息提交。

---

### 5.9 波次 2 泳道（进行中）

开波基线 `ed98914`。波次 1 五条泳道已全部并入 `main`。

**迁移号重新核对。** V44 到 V47 **全部已占用** —— V47 被 `decouple_doe_recovery_from_batches` 用掉，
不是 A5 用的。波次 2 从 **V48** 起。这次号段直接写进每个 agent 的提示词，不再靠文档传递，
因为波次 1 有两条泳道读的是启动时那版文档，中途重排传不到它们。

| 泳道 | 范围 | 迁移 | e2e schema | 设备 |
| --- | --- | --- | --- | --- |
| B1 | 追踪链后端：T2 `create_by` 统一 → T5 `audit_logs` 扩列 → T3 兔笼维度 | V48 V49 V50 | `_b1` | 无 |
| B2 | 决策 1 前端契约 + F11 `pendingCompletion` + **F14 休养期改手动** + F8 批次侧 6 处 + F4 三个触点 | 无 | `_b2` | `emulator-5554` |
| B3 | F7 表单补齐 + F5 母亲兔 ID NFC | V51 预留 | `_b3` | `emulator-5556` |
| B4 | F2 异常记录手动新增 + F3 图片验证码 | V52 预留 | `_b4` | 真机 `00152155M000372` |
| B5 | F6 投喂录入页 + F10 断奶待分配 + F8 兔笼侧 6 处 + F4 批量出售触点 | 无 | `_b5` | 待分配 |

模型统一 `gpt-5.6-terra` + xhigh。

#### B1 为什么吞下三段

T2 要改的 24 个 service 类和 T4 要加注解的 37 个类重叠 19 个，T3 的 cage service 又和 T2 的
setter 清理落在同一批文件上。拆给两个 agent 必然撞车，只能同泳道串行。代价是 B1 约 17h，
成为本波墙钟瓶颈。实测 `setCreateBy/setUpdateBy` 是 **217 处 / 63 个文件**，比原估的 135 处高出六成。

T4 事件流泛化和 T6 统一读接口因此推到波次 3 —— 它们要动的正是 T2 刚清理完的那批文件。

#### F14 休养期改手动（本波新增）

需求：休养期和其他状态一样，由用户手动切换（休养 → 待催情）。

地基是现成的，不需要新建状态机概念。`TransitionTable` 的 T1 已定义
`READY --START_CYCLE--> AWAIT_ESTRUS`（事件 `RECOVERY_DONE`），`TaskType.RECOVERY` 已映射到
`START_CYCLE`，App 的 `task.dart:10` 已有 `startCycle('START_CYCLE', '入轨')` 枚举。

**唯一让它变成自动的是 `ReproRecoveryAdvanceJob`** —— 每 15 分钟扫一遍到期的 RECOVERY 待办，
拿 `operatorName="system"` 替用户点了这一下。用户从来看不到这一步，App 侧也就从来没做过消费界面。
所以这条的工作量主要在 App，后端只是删掉这个 job 和它的测试。

两个留给实现方判断的点：`START_CYCLE` 现在 `postponable=false`，而 T9 POSTPONE 只对 `isAwaiting()`
成立，`READY` 不是 `AWAIT_*` 开头，所以休养到期目前不能推迟，而其他待办都能 —— 要不要跟上；
以及 job 一删，历史积压的 RECOVERY 待办会一次性冒出来，列表会不会爆量。

#### 文件归属（防撞车的实际依据）

三个热点文件决定了泳道的切法：

- `app/lib/src/ui/batches/screens/detail.dart` 上同时压着 F8 的 6 处图标按钮、F11 的
  `pendingCompletion` UI、决策 1 的未结束周期数、F4 的批次追踪标签触点 —— 四件事必须同一泳道，归 B2。
- `app/lib/src/ui/reproduction/**` 上压着 F14、决策 1 的血配选批次、F4 的配种选公兔和留崽来源母兔
  两个触点 —— 同样归 B2。这是 B2 变成第二长泳道的原因。
- `app/lib/src/ui/rabbits/` 按文件切开：`sheets/entry.dart` 归 B3（F7 + F5），
  `screens/detail.dart` 归 B4（F2 入口）。同目录不同文件，冲突面可控。

决策 1 的第三项「兔只详情批次归属改为可多值」因此推到波次 3 —— 它落在 B4 的文件上，
硬塞进去会让决策 1 横跨两条泳道。

#### 设备与验证窗口

波次 1 的教训是并发在验证阶段是负收益：四条泳道同时跑 e2e 时单次从 15 分钟涨到 30 分钟，
只剩一个验证进程时立刻回到 15:58。所以本波三台目标只分给 B2、B3、B4，**B5 先做到代码就绪
加 widget 测试通过，设备验证窗口排在最后**，由人工分配。

真机给 B4，因为登录链路和相机权限在真机上的表现和模拟器不一致，异常记录要传图片、
验证码要走真实网络。顺带补上波次 1 欠的一次确认：F12 OTA 只在模拟器上验过，
真机未知来源安装授权还没走过。

#### 顺带修掉的既有缺陷

登录页协议提示被键盘遮挡这条开放项归 B4 —— 它正好要动那个页面加验证码。
波次 1 判定它是既有缺陷、不在 A1~A5 范围，现在有了归属。

### 5.10 波次 2 合并记录

| 泳道 | 交付 | 合并提交 | 合并时单测 | 合并时 e2e |
| --- | --- | --- | --- | --- |
| B1 | 追踪链 T2 + T5 + T3，V48/V49/V50 | `78490ba` | 914 全绿 | 228 通过 / 0 失败 / 3 跳过 |
| B2 | 决策 1 契约 + F11 + F14 + F8 批次侧 + 3 个 NFC 触点 | `198f053` | 914 全绿 | 见开放项 |
| B3 | F7 表单补齐 + 批量入栏 + F5 母兔 NFC | `eaead00` | 914 全绿 | 230 通过 / 0 失败 / 3 跳过 |
| B5 | F6 投喂页 + F10 待分配 + F8 笼位侧 + F4 出售 NFC | `94fc00c` | —（纯前端） | Flutter 403 全通过 |
| B4 | F2 异常手动录入 + F3 图片验证码 | `798e9a1` | 923 全绿 | 231 通过 / 0 失败 / 3 跳过 |

波次 2 收官状态（`86a987c`）：后端单测 923、e2e 231、Flutter 403、admin lint 与 build 均通过。
**V51 和 V52 两个预留迁移号都没用上，波次 3 从 V51 开始。**

**单测基数订正：914，不是 1071。** 合并 B1 时报的 1071 是错的 —— 当时没有 `clean`，
`target/surefire-reports/` 里混着上一轮的陈旧 XML，包括已被删除的测试类的报告。
`mvn clean test` 之后的真实分布是 platform 95 / access 232 / production 438 /
reporting 130 / boot 19。以后统计测试数必须先 `clean`，否则删掉的测试还会被计进去。

B1 的实测数字：`.setCreateBy(` 与 `.setUpdateBy(` 调用点从 **141 降到 14**，其中 2 处是
`OperationStampInterceptor` 自己的回填，6 处是有意留下的例外（`AppUpdateService` 2 处、
`AdminFarmService` 4 处）—— 平台兔场管理和 OTA 发布走的是平台管理员身份，没有
`OperationContext`，必须写入 `platform` 操作人。剩下 6 处在测试里。

e2e 日志里能看到切面顺序约束在运行时被验了：
`contextAspect=0 < transaction=1000 < eventAspect=2000`。这正是设计期定死的三条约束之一。

开放项：存量清洗未在非空的生产级库上验证过。这和波次 1 的 V44/V45/V46 是同一个缺口 ——
验收库 `rabbit_app_acceptance` 是 e2e 夹具残留，同构兔场，不代表生产库存量情况。

#### 一条新的工程教训：batch 改写会溢出 worktree

合并 B1 时发现主工作树里躺着 **24 个后端 service 的未提交改动、133 行 `;` 空语句垃圾**：

```java
-            order.setCreateBy(String.valueOf(userId));
-            order.setUpdateBy(String.valueOf(userId));
+            ;
+            ;
```

B1 把批量替换跑到了主 checkout（`/Users/texas/Workspace/rabbit`）而不是自己的 worktree，
之后在 worktree 里重做了一遍正确的，**垃圾留在主树没清**。分支本身是干净的，
所以从交付物看不出问题，只有 `git merge` 拒绝合并时才暴露。

worktree 隔离的是 **git 状态**，不是 **文件系统路径**。agent 只要写出绝对路径或在命令里
`cd` 到仓库根，就能直接改到主树上。下一波要在提示词里写死：
**所有路径必须相对于当前工作目录，绝不得出现 `/Users/texas/Workspace/rabbit` 这个字面路径**，
尤其是 `sed -i` / `find -exec` / `xargs` 这类批量改写。合并前也要先看主树的 `git status`。

#### B2 的两个自主判断

F14 留了两个我没有替它拿主意的问题，B2 的结论和依据：

**休养待办支持延期。** `ReproAction.START_CYCLE` 的 `postponable` 从 `false` 翻成 `true`，
`TransitionTable` 的 T9 POSTPONE 规则从 `from.isAwaiting()` 扩到
`from == ReproStage.READY || from.isAwaiting()`。理由是休养和其他待办一样可能暂不适合执行，
延期只改提醒日期、不推进状态，语义上站得住。

**不清理历史 RECOVERY 待办。** 删掉自动推进的 job 之后，存量待办会一次性浮出来。
B2 没有单方面写数据清洗迁移，而是确认列表按到期时间和 ID 稳定排序、单页上限 200 条，
把分批处理的策略留给业务决定。这个判断是对的 —— 存量口径不该由 agent 定。

顺带把 `LegacyEventType` 里 `case RECOVERY, ESTRUS -> "催情"` 拆开了：休养变成用户手动动作
之后，它不该再和催情共用一个词，`RECOVERY` 现在显示「结束休养」。

B2 与已合并的 B1 **零冲突面**，19 个文件全部落在 batches / reproduction / home /
admin 生产页，没有越界。

开放项：B2 报告首轮 e2e 的商品兔日常照护用例偶发失败、复跑通过。这是一个 flaky 信号，
合并后的 e2e 门禁要盯这个用例是否重现。另外它的设备验证用的是临时账号，
没有真实生产数据和 NFC 实体标签，网络失败 / 重复提交 / 权限拒绝 / NFC 实扫仍需在验收兔舍补测。

#### 关于 B4 的 Redis 默认值：警报比最初判断的小

最初担心 B4 把 `APP_CACHE_PROVIDER` 从 `none` 翻成 `redis` 会让登录硬依赖 Redis。
实际查下来有两个缓解事实：

1. `ImageCaptchaStoreConfiguration` 复用了既有的 `CacheConfiguration.CacheEnabledCondition`，
   即 `"redis".equals(provider) || "valkey".equals(provider)`，**没有硬编码 redis**。
   本机环境实际跑的是 `APP_CACHE_PROVIDER=valkey`，验证码存储照常工作。
2. `ApplicationSecretValidator.requireSmsCache` 早就规定 `APP_SMS_ENABLED=true` 时
   provider 必须是 redis 或 valkey。生产配置本来就开着短信，**缓存本来就是硬依赖**。

真正受影响的只有「关短信 + cache=none」的环境，它们需要补 cache 或设
`APP_CAPTCHA_ENABLED=false`。这仍然需要人确认，但不是原先以为的破坏性变更。

#### B3：最重的冲突面自动合干净了

B3 是唯一与已合并的 B1 有重叠文件的泳道（`RabbitService.java`）。实际 `git merge`
**无冲突自动合并**，并且合并后两边的成果共存：该文件里 `.setCreateBy(` / `.setUpdateBy(`
调用点为 0（B1 的成果保住），`validateMotherReference` 完整存在（B3 的成果保住）。

这归功于 B3 把主体逻辑放进独立的 `BatchRabbitEntryService`，只在 `RabbitService` 里留了 13 行。
**结论：预判到冲突时，让后入场的泳道新建服务类、在热点文件里只留调用点，比事后解冲突便宜得多。**

V51 未使用：B3 判断批次总重量按数量均摊到现有 `rabbits.weight` 即可，不需新表。
对应的，**波次 3 可以从 V51 开始分配**。

校验按要求补齐了：`@Pattern(regexp = "[012]")` 类型、`@Pattern(regexp = "[01]")` 性别与来源、
数量 `@Min(1)/@Max(10)`、总重 `@DecimalMin("0.01")/@DecimalMax("100.00")`、卖家 `@Size(max=120)`。
这正是防 `84d5036` 那个 FEMALE 传入 varchar(1) 列导致 500 泄露 jar 路径的回归。

我让 B3 查的存量风险有了答案：它把母兔校验从「是母兔」收紧到「是本兔舍在栏种母兔」，
**生产库里指向非种母兔的 `motherId` 存量为 0**，收紧不会让存量编辑开始报 400。

开放项：常驻 8080 后端未部署新端点，设备端走不通成功 / 部分成功 / 实体 NFC 标签三条路径，
这三条目前只有 e2e 覆盖。

#### 又一条教训：agent 卡死时，交付物仍然可以救

B5 在 245 次工具调用、785.0k token 处**停死超过 65 分钟**，四次状态检查数字纹丝不动。
发出的 steering 也没被处理 —— steering 要等当前工具执行结束才生效，卡在工具里就永远等不到。

**关键事实：agent 卡死 ≠ 工作丢失。** 它的 5 个提交都已落盘、工作树干净。但 harness 是
**在 agent 正常收尾时才创建分支的**，worktree 平时处于 detached HEAD，所以
`git merge pi-agent-<id>` 会报 `not something we can merge`。

救回方式：`git worktree list` 拿到该 worktree 的 HEAD SHA，**直接按 SHA 合并**（对象库是共享的）。
因为没有 agent 的自述报告，验证必须自己补：本例跑了 `./rabbit check`（403 全通过）
与 admin lint/build（均通过）才合入。

开放项：B5 从未分配到设备，**投喂录入、笼位文字操作、出售 NFC 的设备端人工验证全部欠缺**，
只有组件测试兑现了 360x800 / 200% 基线。这是本波最大的验证空白。

#### 验证码：一个 agent 发现不了的跨层缺陷

B4 的验证码后端、app 端、admin 端分开看都是对的，合起来却是坏的。
`.env.example` 把 `APP_CAPTCHA_ENABLED` 写成「紧急维护窗口之外保持开启」，暗示关掉就能绕过，
实际链路是：

```text
APP_CAPTCHA_ENABLED=false
  → issue() 的 requireEnabled() 抛 503
  → 前端拿不到图，captcha 保持 null
  → login.dart:575 与 workspace-login-page.tsx:65 在 null 时 return
  → 两端彻底登不进去
```

讽刺的是后端本来就放行的 —— `verifyAndConsume` 开头就是 `if (!enabled) return;`。
**是前端的空值拦截把路堵死的。** 同理，Redis/Valkey 宕机时 `UnavailableImageCaptchaStore`
三个方法全是 `throw`，类名里的「Unavailable」让人以为是优雅降级，实际是全员锁死，
而唯一的逃生开关本身也是坏的。

修法（`86a987c`）：`BizException` 只带 `code` + `message`，而两种情况同为 503，
所以把「未启用」改成 **501**（仓库未占用，语义是本服务未启用该功能），
「服务不可用」保持 503。两端据此分支：501 放行提交并隐藏输入框，503 保持拦住。
admin 的 `request.ts` 原本抛 `new Error(message)` 丢掉了业务码，新增 `ApiError` 承载。

运维路径现在通了：Redis 挂掉 → 设 `APP_CAPTCHA_ENABLED=false` 重启 → 能登录救火。

`.env.example` 的默认值翻转（`APP_CACHE_PROVIDER` none→redis 等）只影响新部署，
存量 `.env` 不会被覆盖；真正卡升级的是 `docker-compose.yml` 里
`APP_CAPTCHA_CODE_SECRET` 的 `:?` 硬失败，但这和既有的 `APP_PHONE_HASH_SECRET:?` 是同一套约定。

出图样式同时重写：原先旋转支点步长 25 与绘制步长 27 不一致（字符越靠右偏移越大）、
排版不看真实字宽（M 与 2 的间隙差 6 倍）、未水平居中、干扰线是 5 条等长直线、
噪点灰度 130~229 在白底上不可见。现改为 3 倍分辨率、按真实字宽排版居中、
绕字形自身中心旋转、贝塞尔曲线干扰；base64从约 5 KB 升到 16 KB，登录页只加载一次。

#### 已确认的 flaky 用例（需跟进）

`CommodityDailyCareReminderIT.schedulesStageSpecificDailyCareWithContentAndClearsItAtMaturity`
**已被两次独立观测到偶发失败**（B2 一次，合并 B4 后一次），同一份代码复跑即通过。

失败点在 line 83：`assertEquals(1, commodityGrowthService.advanceHouse(houseId, new Date()))`
报 `expected: <1> but was: <0>`，即「入栏 2 天后应推进阶段」没推进。

可疑点：造数用 MySQL `date_sub(now(), interval ? day)`（带时间分量），判定用 JVM `new Date()`
再按养殖场时区取「今天」。两边时区与截断方式不一致时，天数差会在 1 和 2 之间抑扬。
历史上已有 `eaa6528 fix(repro): stabilize daily care integration` 尝试稳定过它，说明不是新问题。
**建议波次 3 把它当作一个独立缺陷修，而不是继续依赖复跑。**

### 5.11 迁移在非空真实库上的验证（开放项已关闭）

B1 合并时留的开放项是「存量清洗未在非空的生产级库上验证过」，波次 1 的 V44/V45/V46 也同样。
现已补验：把开发库 `rabbit_app`（**208 个兔舍、1149 只兔、1761 个笼位、47 张表**，停在 V42）
克隆为 `rabbit_app_wave2`，在副本上用主线镜像跑完整迁移。**原库未动。**

结果：**V43 到 V50 共 8 个迁移全部成功，耗时 3.142 秒**，服务正常启动，
兔舍/兔只/笼位行数迁移前后一致，验证码端点可用。

**V50 的回填在真实数据上是有效的**（这是之前无法确认的部分）：

| 表 | 行数 | `cage_id` 回填 |
| --- | --- | --- |
| `rabbit_status_history` | 1039 | 1039 |
| `rabbit_departure_records` | 312 | 312 |
| `treatment_records` | 25 | 25 |
| `rabbit_abnormal_conditions` | 25 | 25 |
| `weight_logs` / `vaccination_records` | 0 | —（本就无数据） |

共 1401 行历史记录拿到了笼位快照，覆盖率 100%。迁移注释里诚实标了精度局限：
*“Historical rows did not retain a cage. This is the best available snapshot for existing data”* ——
历史行用的是兔只**当前**笼位作近似，不是当时笼位。

新建的 `rabbit_cage_transfer_records` 台账迁移后为 0 行，**这是设计如此**：
它从新的移笼操作开始累积，V50 里没有也不应该有 `insert`。

**V48 归一化的残留值经核查无害**：`batches` 有 32/295、`breeding_cycles` 有 11/243 行的
`create_by` 仍为非数字，但实际值是 `v42`×31、`v44`×1、`v27`×10、`migration`×1 ——
**都是早期迁移自己写入的标记，不是未转换的人名**。V48 按 `sys_user` 匹配，匹配不上就不动，行为正确。
代价是这 43 行的 `operator_name` 为空，展示时无操作人，对迁移合成行而言可接受。

#### 顺带发现：开发库的 V42 checksum 不匹配

`rabbit_app` 里已应用的 V42 checksum 为 `1564751218`，与仓库里
`V42__enforce_open_cycle_batch_membership.sql` 当前内容不一致，导致直接启动会被
`FlywayValidateException` 拦住。但该文件在 git 里只有 `f2b22de` 一次提交、从未被修改，
指向历史被 squash 重写（仓库里确实有 `pre-squash/wave1-20260827` 分支）。

本次验证用 `SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false` 绕过，**但这个不一致会卡住任何
从 V42 升级的环境**。部署前需要对存量库跑一次 `flyway repair`，否则服务启动不来。
这一条建议写进发布步骤。

---

### 5.12 缺陷修复轮（C1–C5，2026-08-28）

起因是 B5 设备复验挖到的一个 500：投喂页连点两次提交，第二次把 MyBatis 原文和
`jar:nested:/app/app.jar/!BOOT-INF/lib/rabbit-platform-0.0.1-SN` 直接返给了客户端。
查下去发现不是投喂的问题，是平台级的。按用户「群攻」的要求拆成并发泳道。

#### 泳道与落地

| 泳道 | 范围 | 结果 |
| --- | --- | --- |
| C1 | `RequestDedupService.markProcessing` 改原子写入 | 合并 `370bff4` |
| C2 | `GlobalExceptionHandler` 兜底不再回显原始异常 | 合并 `503d8d7` |
| C3 | 权限拒绝 + NFC 不可用设备验证 | 只读，无代码改动 |
| C4 | `CommodityDailyCareReminderIT` flaky 根治 | 合并 `3470a92` |
| C5 | NFC 注入验证（C3 的补课） | 捡到文案缺陷，修复 `e01f969` |

#### 两个根因

**幂等竞态。** `markProcessing` 先 `selectByKey` 再裸 `insert`，重放或并发必然撞
`request_dedup.uk_request_dedup` 抛 `DuplicateKeyException`。同文件里的 `begin()`
早就用了 `insertIgnore`，两者写法不一致。全仓 31 处 `markProcessing` 调用点走的都是
有问题的那条，只有 5 处波次 1/2 的新代码用了安全的 `begin()`。C1 只改方法本身，
31 个调用点一个没动。

**兜底泄漏。** `handleOther` 是 `return ApiResponse.error(500, e.getMessage())`，
而且被 `GlobalExceptionHandlerTest` 用「钉住现状」的注释固定住了。注释里写了
「对前端不友好」，但没人动。这次连同两个测试一起改掉，理由写进测试注释：
泄漏是真实发生过的，不是假想风险。

#### flaky 用例：假设被推翻

原假设是 MySQL 与 JVM 时区不一致。C4 实测两边都是 `+08`，**假设不成立**。
真因是夹具用 MySQL `date_sub(now(), interval ? day)` 写阶段进入时间、推进逻辑
却用 JVM `Date` 判定，两个时钟各走各的，锚点又正好压在 `DATETIME` 阈值上，
日界处必然抖。改为每阶段捕获一个 `Date` 共用，锚点落在阈值之外。

此前 `eaa6528` 那次稳定化只换了开头一个锚点，后三次 `updateGrowthEntry` 仍走
MySQL `now()`，没覆盖真正的失败点。

生产侧不受影响：`growth_stage_entered_at` 一律由 JVM 传参写入，该列用 MySQL
`now()` 的写入点为 0，`advanceHouse` 也是外部传时间。纯测试改动。

#### 真库回放

用 `rabbit-c-verify`（18082，接克隆库 `rabbit_app_wave2`）复现原始场景：
同一 `requestId` 并发打两次 `POST /api/feed-logs`，得到一个 `code=0`、
一个 `code=409`「请求幂等状态异常，请稍后重试」，`feed_logs` 恰好 1 行，
jar 路径泄漏消失。

并发落 409 而不是 429，是因为 REPEATABLE READ 下失败方看不见未提交的行——
这是 `begin()` 本来就有的语义，不是新缺陷。

#### NFC 注入：方案被证伪

模拟器没有 NFC 栈（特性、系统服务、`adb emu` 命令三处皆无）。本轮尝试用
`am start` 投递 `NDEF_DISCOVERED` intent 绕过，**这条路走不通**：
`MainActivity.kt:76` 读的是 `getParcelableArrayExtra(EXTRA_NDEF_MESSAGES)`，
而 `am start -d <uri>` 只设 data URI，不生成 NDEF Parcelable。

主会话验证时只确认了 intent **送达**就下了「可用」的结论，没往下确认 app 能否
**解析**。C5 按此配方打了 6 发空包弹，三项 NFC 结论因此作废——是没测到，不是缺陷。

**NFC 读取路径在模拟器上无法验证**，需要真机加实体标签，或在 debug 构建里加
合成 `NdefMessage` 的钩子。真机 `00152155M000372` 有完整 NFC 栈（含 HCE）。

#### 确立的结论

- 权限拒绝通过：只读账号入口隐藏且有中文提示，服务端 `api_code=403`，审计表 5 条佐证
- 无硬件不提示坐实：`flow.dart` 捕获初始化异常、`move.dart` 不捕获，**两者都不查
  `NfcHardwareService.isAvailable()`**，而写标签路径（`hardware.dart:32`）查了
- C1/C2 的修复在设备上确认：投喂双击只发一条请求、`api_code=0`、无堆栈
- 新缺陷已修：`entry.dart:244` 的 `'$rabbitIds.length'` 少了花括号，用户看到
  `已录入 [970].length 只兔的投喂记录`；全仓同类写法仅此一处

#### 门禁

| 项 | 结果 |
| --- | --- |
| 后端单元测试 | 927 全绿（platform 98 / access 239 / production 441 / reporting 130 / boot 19） |
| 全量 e2e | 231 用例、0 失败、3 跳过 |
| `CommodityDailyCareReminderIT` | 完整套件内 3 用例全绿 |
| Flutter | 分析器干净、403 测试通过 |

#### 教训

**验证要验到端点，不能停在中间信号。** intent「送达」和 app「解析成功」是两件事，
把前者当后者，等于给下游发了个半成品配方，白烧一个 agent 近一小时。

**比较分支要用真实 merge-base。** C1 从 C2 合并前的提交切出，`main..branch`
会假报它改了 C2 的文件。用 `git merge-base` 重算才看到真实的 2 个文件。

**别拿单点采样当趋势。** 中途判断 C5「没产出截图」，实际是 glob 只匹配了顶层、
漏了 `app/build/verification/` 子目录；判断它「一直没开始注入」，实际它在校正消息
送达前一分钟就已自己开始。两次都是测量方法的问题，不是 agent 的问题。

**冲突预测准了不等于要串行。** C1/C2 按文件切分，零重叠、零冲突、可以早合。

### 5.13 v1.0.10 后的下一阶段（2026-08-29）

#### 当前基线

- 线上已到 v1.0.10（Android build 4013），后端、Admin 和 OTA 均可用；Flyway 到 V50，下一迁移号是 V51。
- 飞书 81 条记录现为：已完成 45、验收中 35、方案设计中 1。唯一未进入验收的是 F13 批次统计。
- v1.0.8 到 v1.0.10 已补齐 NFC 无硬件提示、APK 安装、安装页重入、更新说明乱码和验证码刷新按钮，这些不再排入下一阶段。
- 兔只多批次归属已经存在于后端 Mapper、Admin 类型和 App `batch_membership.dart`，下一阶段只补回归，不重复改造。
- 仍需闭环的是 T4 事件流泛化、T6 统一读接口、F13 四项统计、非法 JSON 的 400 契约，以及手工发布流程的脚本化。
- `schema.sql` 尚未同步 V48 到 V50，只能作参考；V51 的验收以 Flyway 从 V50 升级的结果为准。

#### 初始五条泳道

| 泳道 | 范围 | 迁移 / E2E | 设备 | 合并约束 |
| --- | --- | --- | --- | --- |
| D1 | T4：`repro_events` 增 `cage_id/target_type/target_id`，实现批量 Sink，铺设约 45 个 `@TrackedOperation` | V51 / `_d1_t4` | 无 | 宽改后端写服务和事件 Mapper，优先合并 |
| D2 | F13 后端：聚合产崽窝数、产崽总数、活崽总数、断奶数量 | 原则上无迁移 / `_d2_f13` | 无 | 新建统计服务，数据查询集中在 Batch Mapper |
| D3 | F13 Admin：展示四项统计，回归当前/历史多批次关系 | 无迁移 / 不跑 E2E | Chromium | 独占 `workspace.ts`、共享类型和兔只详情页 |
| D4 | F13 App：展示四项统计，回归多批次关系 | 无迁移 / 不跑 E2E | `emulator-5554` | 独占批次 repository 与 `batches/screens/detail.dart` |
| D5 | 平台收口：非法 JSON 返回 400；Admin/OTA 人工发布流程脚本化 | 无迁移 / `_d5_contract` | 无 | 分两个提交；只碰 `rabbit-platform` 与 `deploy/`，不碰业务服务 |

D1 合并后再启动 D6：T6 `GET /api/operation-events` 使用游标分页，支持权限、目标类型和目标 ID 筛选，并由 Admin/App 消费，E2E 后缀 `_d6_t6`。它依赖 T4 的持久化契约，不能与 D1 并发定义。关键链是 **D1 9h → D6 4.5h**，其余泳道可并行，预计 14 到 18 小时进入整体验收。

热点归属提前固定：D1 独占 `ReproEventMapper.xml` 和追踪契约；D2 独占 `BatchRabbitMapper.xml` 与新统计服务；D3 独占 Admin 的 API/类型文件；D4 独占 App 批次 repository/detail。D6 在 D1 合并前不得编辑事件 Mapper。

#### 验收与设备窗口

- D1：V50 到 V51 可升级；非繁育目标能落 `target_*`；事务提交后事件可见，回滚不留事件；批量同请求不撞唯一键，重复请求不产生重复事件。
- D2：四个原始计数与固定 SQL 夹具逐项对账；不顺手增加比率，也不把缺少饲料、销售重量或屠宰参数的指标伪造为 0。
- D3/D4：四项名称、单位、空态和失败态一致；现有多批次关系覆盖当前批次与历史批次，不退化为单值。
- D5：畸形 JSON 固定返回 400 和稳定中文文案，客户端看不到 Jackson 类名、字段路径或堆栈；发布脚本先验哈希和备份再切换，失败可回到上一镜像/静态目录，并生成部署证据。保持「CI 只出制品，人工触发线上部署」。
- D6：租户与权限隔离、游标稳定性、目标筛选和跨端分页一致；一条业务操作不会在流水页重复出现。
- 真机 `00152155M000372` 单独排 NFC 闭环窗口：先写一张实体标签，再读回 F4/F5/F6 入口，同时测错签名、跨兔舍、NFC 关闭和手动兜底。模拟器不能替代这项验收。

#### 统一门禁

- 各泳道只跑归属模块单测；后端 unit test 放在所属模块，不能放进 `rabbit-boot`。只有迁移和跨模块 IT 留在 `rabbit-boot`。
- 后端泳道使用独立 `E2E_SCHEMA_SUFFIX`；完整 e2e 只在 main 合并后以 `_main51` 跑一次。
- Flutter 变更跑 `./rabbit check`，Admin 变更跑 lint/build 和 Chromium 验证；设备验证错峰，不与全量 e2e 并发。
- 批量改写只能使用 agent 自己的工作目录相对路径，禁止写死主树绝对路径；提交前检查 `git status` 和真实 merge-base。
- V52 不预分配。只有实现证明需要 schema 变化时才占用，避免再次留下空号。

---

## 六、执行检查点

开工前：

- [x] T0 完成，`bash scripts/e2e-local.sh` 能跑绿
- [x] 迁移版本号按合并顺序重新分配（见 5.2，原按泳道预分配的方案已推翻）
- [x] 每个 agent 分配独立 e2e schema（`_a1` 到 `_a3`）
- [x] 决策 1 选 B、决策 2 选 B

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

波次 2 开工前（已就绪）：

- [x] 迁移号从 V48 起重新分配，V44~V47 确认已全部占用
- [x] 硬约束（迁移号、schema 后缀、设备序列号、文件归属）直接写进提示词，不靠文档传递
- [x] 每个 agent 分配独立 e2e schema（`_b1` 到 `_b5`）
- [x] 验证窗口错峰：B5 不分配设备，排到最后
- [x] 提示词里要求结束前自查 `git status`，不得留 `pi-agent:` 通用提交

缺陷修复轮（C1–C5）结清：

- [x] 幂等竞态、兜底泄漏、flaky 用例均已合并并过门禁
- [x] 修复在真实克隆库上回放确认（一个 `code=0` 一个 `code=409`，1 行数据）
- [x] 权限拒绝设备验证完成，审计表有佐证
- [ ] **NFC 读取路径仍未验证**：注入方案已证伪，需真机
  `00152155M000372` 加实体标签，或 debug 构建加合成 `NdefMessage` 的钩子；
  可先用系统自带的写标签功能写一张再读回，形成闭环
- [x] `flow.dart` 与 `move.dart` 已补上 `NfcHardwareService.isAvailable()` 检查，
  由 `bd077d3` 随 v1.0.8 上线；无 NFC 设备会提示改用手动选笼、地图或列表
- [ ] 非法 JSON 返回 500 而非 400，旧问题，本轮未动

下一阶段 D1–D6 开工前：

- [x] 飞书已交付记录全部进入验收，F13 是唯一仍在方案设计中的需求
- [x] 迁移从 V51 开始；V52 不预分配
- [x] 五条初始泳道和 D1 → D6 串行链已写入 5.13
- [ ] 评审 T4 的 `repro_events` 扩列、批量 Sink 和唯一键方案
- [x] F13 API 已冻结为 `GET /api/batches/{batchId}/statistics`，字段为
  `totalLitters/totalKits/totalLiveKits/totalWeaned`，无记录返回 0
- [ ] 准备真机 `00152155M000372` 和至少一张可反复写入的实体 NFC 标签
- [x] D5 只提供人工部署脚本，不启用 CI 自动部署（用户已明确当前 CI 不负责直接部署）

下一阶段启动记录（2026-08-29）：

| 泳道 | Agent ID | 模型 / 思考 | 基线 | 状态 |
| --- | --- | --- | --- | --- |
| D1 T4 | `9647e5c1-357c-42f` | `gpt-5.6-terra` / `xhigh` | `f68d728` | 进行中 |
| D2 F13 后端 | `4cef86b5-0a2b-413` | `gpt-5.6-terra` / `xhigh` | `f68d728` | 进行中 |
| D3 F13 Admin | `c0734ded-d47a-493` | `gpt-5.6-terra` / `xhigh` | `f68d728` | 进行中 |
| D4 F13 App | `00926434-57ca-40a` | `gpt-5.6-terra` / `xhigh` | `f68d728` | 进行中 |
| D5 契约与发布 | `5a10c895-33cf-44d` | `gpt-5.6-terra` / `xhigh` | `f68d728` | 进行中 |

五条泳道都使用独立 worktree。当前 `adb devices` 无在线设备；D3 用浏览器，D4 先跑 widget 测试，NFC 真机窗口待设备上线后安排。D6 在 D1 合并并冻结事件读模型前不启动。
