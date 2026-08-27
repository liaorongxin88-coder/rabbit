# 操作追踪改造 + 飞书需求积压 决策单

> 用法：勾选 `[ ]` 做决策，横线处填写。改完告诉我，我按这份文档执行。
> 核对时间：2026-08-25 · 飞书 Base `FYhLbGvJ7aFAzwszmFmcILYsn5e` / 表 `tbldzpHQ8yqCg6wp` · Base revision 855 · 代码 HEAD `677e533`
>
> **当前基线：本文和 `manual-verification-checklist.md` 为准，飞书暂不回写。** 飞书表仅作为需求来源快照，其状态列已知滞后于代码，不作为判断依据。

---

## 一、一页纸总览

飞书需求表 81 条，其中 39 条已标记完成、42 条未完成。对这 42 条逐条核实源码后：

| 真实状态 | 条数 | 剩余开发量 |
| --- | --- | --- |
| 已完成，只是没改状态 | 17 | 0 |
| 部分完成 | 14 | 25h |
| 确实没做 | 11 | 17h（不含批次统计 14h） |

两项工作的合并估算：

| 项目 | agent 工时 |
| --- | --- |
| 操作追踪改造（注解方案） | 44h |
| 飞书剩余需求 | 28h（含批次统计则 42h） |
| **合并推进（推荐顺序）** | **约 70h** |

70h 约等于连续跑 3 个自然日，或按每天 8 小时算 9 个工作日。不含人工评审排队时间。

---

## 二、需要你决策的三件事

### 决策 1：批次「每母兔每批次唯一周期」条款

飞书 `recvqh3EJXzmO1` 的新定义要求每只母兔在同一批次内只有一个繁育周期。当前代码是相反语义，且有通过中的测试明确断言：`ReproParallelCycleIT.bothParallelCyclesBlockBatchCompletion`（`backend/rabbit-boot/src/test/java/com/rabbit/app/e2e/ReproParallelCycleIT.java:185-201`）保留同一母兔在同一批次内的两个开放周期，对应血配场景（哺乳期同时启动下一轮配种）。提交 `4ef1769` 的说明里写明这条被主动推迟。

- [ ] **A. 维持现状**，同一批次允许并行周期，修改飞书需求描述以匹配代码。成本 0.5h
- [ ] **B. 按新定义改造**，一母兔一批次一周期，多周期则归属多批次。成本 20h，需改状态机成员派生、加迁移、重写该 IT、波及双端批次详情页
- [ ] **C. 暂缓**，本轮不动，留待现场验证后再定

选 B 时补充：血配场景下第二个周期应归属到 ************（新建批次 / 原批次外的独立容器 / 其他：************）

### 决策 2：批次统计数据

飞书 `recvsV7QhkxxvD` 要求约 20 项指标，当前只有 4 项有数据源（产仔、活仔、断奶等）。料肉比缺饲喂消耗采集，出栏均重和成活率缺销售重量采集。仓库根目录 `业务需求-模块级任务拆分.md:74,145,183` 已把该条排在 M6/M9 之后。

- [ ] **A. 推迟**，等饲喂和销售数据采集落地后再做
- [ ] **B. 先做可算的部分**，交付 4 项指标并在界面标注数据缺失。成本 4h
- [ ] **C. 本轮全量做**，同时补齐饲喂消耗和销售重量采集。成本 14h

### 决策 3：17 条已完成需求的飞书状态回写

- [x] **E. 暂不回写**（2026-08-25 已定）。以本地文档为准，待人工核对完 A 组 17 条后再决定是否回写
- [ ] A. 全部回写为「已完成」，并附源码证据到详细说明字段
- [ ] B. 只回写 9 条 P0 BUG，其余 8 条留待人工确认
- [ ] C. 我自己改，不需要代写
- [ ] D. 先出一份回写预览给我看，确认后再执行

---

## 三、17 条待改状态清单

逐条核实过源码，勾掉表示你认可该条改为「已完成」。有异议的留空并在备注写原因。

### P0 BUG（9 条）

| 勾选 | 单号 | 需求 | 源码证据 | 备注 |
| --- | --- | --- | --- | --- |
| [ ] | `recvsUyEOkaS5f` | 商品兔成熟时首页没有提醒 | `CommodityDailyCareReminderService.java:34-71`；测试 `CommodityDailyCareReminderIT` | |
| [ ] | `recvsUOP4ogw58` | 后备兔转为种兔时没有提醒 | `RabbitService.java:253-276`；测试 `ReplacementPromotionIT` | |
| [ ] | `recvsUPFjP1Jhb` | 流产后该兔在批次中活动没结束 | `TransitionTable.java:140-148`（T8）；测试 `app/test/domain/reproduction/task_test.dart:59-77` | |
| [ ] | `recvsURZXJ6XAN` | 空怀后该兔在批次中活动没结束 | `TransitionTable.java:98-102`（T4b） | |
| [ ] | `recvsUSy6FpP9n` | 分笼后该兔在批次中活动没结束 | `TransitionTable.java:135-139`（T7） | |
| [ ] | `recvsV2P18D4Vd` | 母兔繁育周期的时间没有同步 | `ReproStateMachineService.java:600-603`；测试 `ReproStateMachineIT:706-724`，断言语「阶段时间必须同步为操作时间」 | 旧验收报告标「证据不完整」，那是只看了模拟器截图，后端有直接断言 |
| [ ] | `recvt6Qkxa4nHE` | 断奶操作不创建仔兔对象 | `KitPlacementService.java:71-99` 只写 WeaningRecord；测试 `ReproWeaningPlacementIT` | |
| [ ] | `recvt6Re55lzbJ` | 中途加入的母兔没有活跃标签 | `ReproStateMachineService.java:384-424`；测试 `ReproLifecycleIT` | |
| [ ] | `recvt6ZYHN0yki` | 只针对商品兔有可售卖提醒 | `CommodityDailyCareReminderService.java:39` 限定 `type='2'`；测试 `V39SaleReadyTaskCleanupIT` | |

### 业务与软件需求（8 条）

| 勾选 | 单号 | 需求 | 源码证据 | 备注 |
| --- | --- | --- | --- | --- |
| [ ] | `recvqgPNkRN69z` | NFC 功能实现（底座） | `NfcController.java:44-96`；`V1__init.sql:60-82`；`app/pubspec.yaml:43` nfc_manager 3.5.1；`app.dart:63-118` 全局碰一碰；`move.dart:182-246` 已跑通端到端 | 底座完成，6 个业务触点单列在第五节 |
| [ ] | `recvrqyf6o1BsN` | 批量出售的用户自定义范围 | `outbound/screens/flow.dart:413,417`（按排/整舍）；`home/screens/overview.dart:106,332,958` 出售提醒 | |
| [ ] | `recvszXohbbWYC` | 笼位地图（排→层→位） | `app/lib/src/ui/cages/widgets/map.dart`；`admin/src/components/cage-map.tsx` + `admin/src/lib/cage-map.ts` | 功能已交付。需求文里 3 个判断题待现场定，见第六节 |
| [ ] | `recvsUTRis0IpF` | 分笼操作的实现 | `TransitionTable.java:132-137`（T7）；`BatchWeaningSeparationService.java:75-152` | |
| [ ] | `recvt6UeydlX3S` | 入笼操作 | `KitPlacementService.java:131-222` `insertKitsAndHydrateIds` | |
| [ ] | `recvt7fkY08CyR` | 后备兔转种兔操作 | `RabbitService.java:876-957`；测试 `ReplacementPromotionIT.java:186` | 就地改兔只行，非「离场再新建 ID」，净效果相同 |
| [ ] | `recvt7gqTqKreu` | 商品兔转后备兔的留种操作 | `RabbitService.java:679-856`；`RabbitController.java:119`；测试 `RabbitServiceTest.java:27` | 同上 |
| [ ] | `recvt82rWBOCHx` | 记录流产操作需要新增上传图片功能 | `V35__business_images_and_repro_form_contracts.sql`；`ReproStateMachineService.java:1074` 强制校验；测试 `ReproRequiredFieldsIT`、`abortion_test.dart` | |

---

## 四、状态标反了的一条

| 单号 | 需求 | 飞书状态 | 实际 | 证据 |
| --- | --- | --- | --- | --- |
| `recvrqlA3OU53F` | APP 实现 OTA 升级功能 | 验收中 | ~~未实现~~ **已实现** | 原证据：`app/pubspec.yaml` 无升级依赖、`ota\|upgrade\|checkUpdate` 零命中。**现已由 A4 交付** |

> **2026-08-26 已解决**：波次 1 的 A4 泳道完成 OTA，飞书状态不再是错的。
> 交付内容：`V46__app_ota_release_catalog.sql`、`GET /api/app-updates/check`（匿名）、
> `POST /api/admin/app-updates`、app 侧升级流程与设置页入口。
> 两个勾选项作废：无需改回「方案设计中」，也无需再排 8h。
>
> **但方向仍成立**：飞书状态两个方向都不可信，不能因为这一条对上了就放弃核对。

---

## 五、剩余工作排期表

勾选表示纳入本轮。工时为 agent 墙钟估算，已按 3 到 4 路并发折算。

### 部分完成，补齐边角

| 纳入 | 单号 | 剩余工作 | 面 | 工时 |
| --- | --- | --- | --- | --- |
| [x] | `recvqh6N0wWVjR` | ~~批量范围入笼：排 2-9 × 列 1-4 × 层 2-3 的范围选择。三端零命中~~ **A5 已交付并验收通过**：跳过不可用笼位并报告原因，每笼固定数 | 后端+App+Admin | ~~8h~~ 0h |
| [ ] | `recvsV3h8S6Ovc` `recvsV3pCDzZej` `recvsV3qcy068u` `recvsV3w0NLret` `recvt72IkTmhpT` `recvt72IJvo7DL` | 表单补齐：批量新增数量 + 总重量、创建时母亲兔 ID、卖家字段。六条共用一套实现 | 后端+App+Admin | 8h |
| [ ] | `recvt6SWUAVejo` | 断奶待分配数量提示挪到笼位管理页 | App+Admin | 3h |
| [ ] | `recvrqsCS3Six3` | 残留图标按钮文字化。`cages/widgets/management.dart:231,372,623,645,720,807`、`batches/screens/detail.dart:1235,1342,1483,1490,1497,1506` 等约 12 处 | App | 3h |
| [ ] | `recvqh3EJXzmO1` | 批次 `pendingCompletion` 提醒 UI。后端已有派生字段并有 `ReproLifecycleIT:502-553` 覆盖，双端无消费 | App+Admin | 3h |
| [ ] | `recvsUUz1W4eG3` `recvsUUAXydUwg` `recvsUUMyAT4Sd` | 伞形条目，随上面各条自动收敛 | 汇总 | 0h |

### 确实没做

| 纳入 | 单号 | 工作 | 面 | 工时 |
| --- | --- | --- | --- | --- |
| [ ] | `recvsUVpLZ09rx` `recvt6zZEIjiK8` `recvt855ZE8KgN` `recvt8aQ6LIyxs` | 4 个 NFC 触点：配种选公兔、批次追踪标签、批量出售、留崽来源母兔。底座和范式现成，重复劳动 | App | 4h |
| [ ] | `recvt6DIqoMP8b` `recvt7ipLUiZzv` | 后备兔和商品兔的母亲兔 ID NFC。需先补创建时的母亲兔 ID 字段（当前 `entry.dart` 创建路径无此输入） | 后端+App | 4h |
| [x] | `recvt7fpa64K76` | ~~接种疫苗。全新，全仓 `疫苗\|vaccin` 零命中~~ **A3 已交付并验收通过**：V45 + 批量接种 + 幂等 | 迁移+后端+双端 | ~~10h~~ 0h |
| [ ] | `recvtchyDpV4b7` | 投喂 App 端录入 + NFC。后端 `modules/feed` 已有，Flutter 无录入页 | App | 6h |
| [ ] | `recvt7VzBVO50v` | 异常记录按钮。表 `rabbit_abnormal_conditions` 和图片服务都在，缺 create 端点和双端入口 | 后端+双端 | 5h |
| [ ] | `recvqgIXayF1W8` | 账号密码登录图片验证码 | 后端+双端 | 4h |
| [x] | `recvrqlA3OU53F` | ~~APP OTA 升级~~ **A4 已交付**，待下一环节真机验收 | 后端+App | ~~8h~~ 0h |
| [ ] | `recvsV7QhkxxvD` | 批次统计数据 | 见决策 2 | 4h / 14h |

### 操作追踪改造

| 纳入 | 阶段 | 工作 | 工时 |
| --- | --- | --- | --- |
| [ ] | 前置 | 修 e2e 环境。`docker-compose.yml` 里 mysql 的 `ports` 被注释，宿主机连不上 3306，208 个 IT 全挂在 Flyway 建连 | 0.3h |
| [ ] | Phase 0 | 注解基建：aop 依赖、`OperationContext`、MyBatis 自动填充、两个切面及顺序、85 个 `@Transactional` 自调用排查 | 7.5h |
| [ ] | Phase 1 | `create_by` 口径统一：V44 清洗迁移 + 删 65 处 `setCreateBy` 和 99 处 `setUpdateBy` | 4.5h |
| [ ] | Phase 2 | 兔笼维度：移笼记录表 + 8 张表 `cage_id` 从反查改快照 + 回填 | 9h |
| [ ] | Phase 3 | 事件流泛化：`repro_events` 扩列 + 约 45 个写方法加注解 | 9h |
| [ ] | Phase 4 | `audit_logs` 加 batch/cage/rabbit 三列 | 2h |
| [ ] | Phase 5 | 统一操作流水读接口 + admin 页面 + app 消费 | 4.5h |

---

## 六、待现场确认的判断题

笼位地图功能已交付，但需求描述里这三条是政策而非工程，需要现场人员定：

| 勾选确认 | 问题 | 你的选择 |
| --- | --- | --- |
| [ ] | 第 1 层的方向（自下而上还是自上而下） | ____________ |
| [ ] | 折返排的渲染是否需要可配置 | ____________ |
| [ ] | 双套笼位编号方案如何取舍 | ____________ |

---

## 七、推进顺序（三选一）

| 勾选 | 方案 | 总时长 | 代价 |
| --- | --- | --- | --- |
| [ ] | 先做完飞书需求，再做追踪改造 | 78 到 88h | 疫苗、投喂、异常这些新写入点建好后要回头补注解，Phase 3 待标注方法从 45 个涨到约 52 个 |
| [ ] | 先做完追踪改造，再做飞书需求 | 72h | 不返工，但 P0 需求等 44h |
| [ ] | **注解基建先落地，然后两轨并行**（推荐） | **约 70h** | 先串行做完 Phase 0 + Phase 1（12h），之后飞书功能与追踪 Phase 2-5 并行。新写的疫苗、投喂、异常从第一行就带 `@TrackedOperation`，不返工 |

三档预期：顺利 56h，现实 70h，若决策 1 选 B 且中途推翻重做则 95h。

---

## 八、风险登记

| 风险 | 影响 | 触发条件 |
| --- | --- | --- |
| 切面与 `@Transactional` 相对顺序设计错误 | Phase 0 之后的 36h 工作建在错误地基上，返工非局部 | 去重状态需在事务外写，事件写入需在事务内，两者必须拆成两个切面 |
| 决策 1 选 B 后中途推翻 | +25h | 血配场景的批次归属语义未与现场确认 |
| 存量 `create_by` 出现预期外脏值形态 | Phase 1 +4h | 34 张表、混存数字 ID 与展示名，清洗迁移需对真实库验证 |
| e2e 并发库隔离 | 并发宽度降到 1 到 2，总时长 +50% | MySQL 里已有 `rabbit_app_e2e_codex_concurrent` 等库，说明隔离做法已在用，风险较低 |

顺带修掉的既有缺陷：`RequestDedupService` 全类无 `@Transactional`，加入调用方事务，导致 `markFailed` 在回滚时一起回滚，去重失败标记写不进去。注解基建会把这个顺序问题一并解决。

---

## 九、我的执行入口

改完这份文档后，勾选下面一项：

- [ ] 按文档执行，从推进顺序里勾选的方案开始
- [ ] 先只做飞书状态回写（已按决策 3 暂停）
- [ ] 先只修 e2e 环境 + Phase 0 注解基建
- [ ] 我还有问题，见下方

补充说明：

```text
（在这里写任何补充或修改意见）
```
