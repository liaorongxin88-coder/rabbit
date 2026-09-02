# 操作追踪与事务边界

后端用一套注解基座接管写操作的上下文绑定、幂等记账和事件留痕，对外由
`GET /api/operation-events` 统一读取。本文解释这套机制为什么长成现在这样、
哪几条约束不能碰，以及新增写操作时要遵守什么。

代码入口在 `backend/rabbit-access/src/main/java/com/rabbit/app/tracking/`，
读接口在 `backend/rabbit-production/src/main/java/com/rabbit/app/modules/operation/`。

## 一、为什么需要它

在这套基座之前，追踪能力被割成两套互不连通的机制：

- `audit_logs` 记录谁调了哪个接口，但没有 `batch_id` / `cage_id` / `rabbit_id`，
  也不存请求体。事故复盘还原不出到底写了什么。
- 业务表的 `create_by` / `update_by` 记录谁写了这行，但语义分裂：一部分服务写数字用户 ID，
  另一部分写展示名，跨表归因直接算错。

全仓唯一的追加型事件流是 `repro_events`（V26 引入），设计最好——追加写、唯一键幂等、
JSON 载荷、操作人快照——但只覆盖繁育域。后续工作把它泛化成通用事件流
（V51 增加 `cage_id` 与 `target_type` / `target_id`），并让所有写操作走同一条路径。

目标是一次写操作落地后能回答：谁、在哪个兔舍、对哪个批次的哪只兔、在哪个笼位、
做了什么、什么时候。

## 二、三层结构

| 层 | 组件 | 职责 |
| --- | --- | --- |
| 上下文 | `OperationContext` | ThreadLocal 承载 `houseId / batchId / cageId / rabbitId / userId / operatorName / requestId`，由业务鉴权拦截器播种 |
| 标注 | `@TrackedOperation` | 标注写方法，用 SpEL 取标识，声明操作码、事件类型和是否接管幂等 |
| 落库 | `OperationStampInterceptor` | MyBatis 写拦截器，自动填 `create_by` / `update_by` / `house_id` / `operator_name`，取代散落各服务的 setter |

`@TrackedOperation` 选 SpEL 而不是参数注解，是因为标识在现有方法签名里的位置不统一：
有的是独立入参，有的埋在 DTO 字段，有的要从返回值取。参数注解只覆盖第一种，
改成全覆盖就得动几十个方法签名。表达式解析结果按 `Method` 缓存。

## 三、不能违反的约束

### 切面与事务的相对顺序

这是整套基座最关键的一条。两条规则方向相反：

- **去重状态必须写在事务外。** 业务失败要回滚，但「这个 requestId 试过且失败了」
  必须留下来。写在事务内，回滚会把 `markFailed` 一起抹掉。
- **事件写入必须在事务内。** 写在事务外，业务回滚后事件还在，事件流会声称
  发生过一次实际没落地的操作，比没有事件更糟。

所以是三层夹心而不是一个切面，顺序常量定义在 `OperationTrackingOrder`：

```text
OperationContextAspect  order = 0      ← 事务外：绑上下文、幂等记账
  └── Spring 事务通知     order = 1000   ← 事务边界
        └── OperationEventAspect order = 2000  ← 事务内：事件批量落库
              └── 业务方法
```

事务通知的 order 必须显式设置。Spring 默认把它排在 `Ordered.LOWEST_PRECEDENCE`，
那样没有任何切面能排在它之后，「事务内的切面」根本写不出来。boot 模块用
`@EnableTransactionManagement(order = TRANSACTION)` 把它拉到 1000。这个前提一旦被撤掉，
内层切面会静默跑到事务外，而测试很可能测不出来——`OperationTrackingOrderVerifier`
在启动时核对真实生效的 order，对不上就拒绝启动。

### 一个操作只能有一个幂等归属方

切面的去重键是 `(houseId, userId, operationCode, requestId)`，与服务内部手写的 api 名重合。
如果方法内部已经自己调 `RequestDedupService`，注解上的 `dedup` 就必须保持 `false`，
否则服务会撞上自己刚写的 `PROCESSING`，整个接口退化成 429。

操作码同时是幂等记账的 api 键，必须与历史手写字符串完全一致，否则同一 requestId
的旧记账认不出来。

### 批量端点必须批量插入

单次请求可处理 500 只兔，事件走批量 Sink，不能逐条插。

## 四、Spring AOP 不拦截同类自调用

Spring AOP 走代理。类内部的 `this.foo()` 直接命中目标方法，不经过代理，
所以 `@Transactional`、`@TrackedOperation` 在自调用路径上**静默失效**：
方法照常执行、照常返回，没有异常也没有日志，事件流出现无声空洞。

全仓排查用了两种互相独立的方法，两种都必要。文本扫描解析方法边界与注解，
找到 6 处；ArchUnit 字节码规则遍历 `getMethodCallsFromSelf()`，找到 7 处。
第 7 处是文本扫描漏掉的 `RabbitService.createRabbit` 四参重载调用五参重载——
按名字比对会把同名调用当成递归跳过，字节码比对不会。**这类排查不能只靠 grep。**

### 已判定无害的自调用

下表逐项对应 `FarmingModuleArchitectureTest.ALLOWED_TRANSACTIONAL_SELF_CALLS`，
两边必须保持一致。

| 类 | 调用方 | 被调方 | 调用方是否事务 |
| --- | --- | --- | --- |
| `SettingService` | `updateUserSetting` | `getOrCreateUserSetting` | 是 |
| `SettingService` | `getEffectiveSetting` | `getOrCreateUserSetting` | 是 |
| `SettingService` | `updateHouseSetting` | `getOrCreateUserSetting` | 是 |
| `SettingService` | `getOrCreateHouseSetting`（private） | `getOrCreateUserSetting` | 否 |
| `PhoneAuthService` | `loginOrRegister` | `authenticate` | 是 |
| `RabbitService` | `createRabbit`（4 参） | `createRabbit`（5 参） | 是 |
| `RequestDedupService` | `begin`（4 参） | `begin`（5 参） | 是（修复后） |

判定依据只有一条：被调方的传播行为是默认的 `REQUIRED`。调用方已在事务里，
被调方即使走代理也只是加入同一个事务，绕过代理不改变语义；
`getOrCreateHouseSetting` 是 private，只被三个 `@Transactional` 公开方法调用，进入时事务已经开着。

**这些全都建立在「被调方是 `REQUIRED`」这个前提上。** 哪天有人把
`getOrCreateUserSetting` 改成 `REQUIRES_NEW`、或给它加上 `@TrackedOperation`，
绕过代理就从无害变成静默失效，而这个变化不会有任何运行期信号。

`RequestDedupService.begin` 是排查当时唯一需要动手的一处：四参重载原本没有注解却调用五参重载，
给五参加上 `@Transactional` 后，四参这条入口上的事务根本不会开。两个重载都加上注解后，
自调用仍在，但已变成上述无害形态，因此留在允许清单里。
这一处也说明排查为什么必须先于加注解做——注解正是加在有自调用的类上。

### 落到构建里

两条 ArchUnit 规则写在
`backend/rabbit-boot/src/test/java/com/rabbit/app/architecture/FarmingModuleArchitectureTest.java`：

- `trackedOperationsAreNeverInvokedFromTheSameClass`——**零容忍**。
  `@TrackedOperation` 的自调用没有「无害」的情形：上下文不绑、幂等不记账、事件不落库。
  要在类内复用被追踪的逻辑，把逻辑抽成不带注解的私有方法，让两个入口各自被代理。
- `noNewTransactionalSelfInvocationsAppear`——**允许清单制**。
  上表写进 `ALLOWED_TRANSACTIONAL_SELF_CALLS`，新增的一律构建失败。
  要新增，得先在本文写明为什么无害。

选允许清单而不是零容忍，是因为把这几处无害调用改成注入自身代理或抽私有方法，
改动面比它防住的风险还大。但「不改」和「不知道」是两回事，清单让前者可审计。

## 五、读接口

`GET /api/operation-events` 是整个兔场写操作的统一查询入口。

| 项 | 约定 |
| --- | --- |
| 权限 | `RABBIT_AUDIT_LIST`。这是能翻遍整个兔场的审计面，VIEWER 不该有；客户端据此隐藏入口，而不是让人点出 403 |
| 租户 | 必须带 `X-House-Id`，服务端再校验一次兔场权限 |
| 筛选 | `targetType`、`targetId`、`operationCode`、`cageId`、`batchId`、`occurredFrom`、`occurredTo` |
| 分页 | keyset 游标。`cursor` 是 `(occurredAt 毫秒, id)` 的 base64 编码，客户端不得解析或自行拼装 |
| 响应 | `items` + `nextCursor` + `hasMore`，**不返回 total**——追加流为每次翻页做全表 count 正是 keyset 要避开的成本 |
| 错误 | 游标解码失败一律 400。伪造或过期的游标是客户端错误，不该变成 500 |

单条留痕不含 `payload` 与 `requestId`：前者是操作差异的内部结构，后者是幂等键，
外泄都会让客户端依赖服务端内部约定。`operatorName` 是写入当时的展示名快照，不 join `sys_user`——
事故复盘要的是「当时是谁」，join 出来的是「现在叫什么」。

## 六、验证

```bash
mvn --file backend/pom.xml test -Dtest=FarmingModuleArchitectureTest
mvn --file backend/pom.xml test -Dtest=TrackedOperationPlacementTest
mvn --file backend/pom.xml -Pe2e verify
```

E2E 覆盖在 `rabbit-boot` 的 `OperationEventReadIT`、`OperationEventCoverageIT`
和 `OperationTrackingBoundaryIT`：租户与权限隔离、游标稳定性、目标筛选、
事务提交后事件可见且回滚不留事件、同请求重放不产生重复事件。
