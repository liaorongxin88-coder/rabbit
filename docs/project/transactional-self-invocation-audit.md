# `@Transactional` 同类自调用排查清单（T1）

Spring AOP 走代理。类内部的 `this.foo()` 直接命中目标方法，不经过代理，
所以 `@Transactional`、`@TrackedOperation` 这类注解在自调用路径上**静默失效**：
方法照常执行、照常返回，没有异常也没有日志。事件流会因此出现无声空洞，
而测试很可能测不出来。

本清单是注解基建（T1）开工前对全仓的一次逐一排查，供 T2 到 T6 铺开注解时对照。

## 一、总量

| 指标 | 数字 |
| --- | --- |
| 带 `@Transactional` 的生产类 | 38 |
| `@Transactional` 注解总数 | 86（计划文档记的 85，实际数为 86） |
| 真正存在同类自调用的调用点 | **7** |
| 需要改动的 | **1** |

排查用了两种互相独立的方法，两种都必要：

1. **文本扫描**：解析每个类的方法边界与注解，在方法体里找对同类 `@Transactional`
   方法的无限定调用。找到 6 处。
2. **ArchUnit 字节码规则**（`FarmingModuleArchitectureTest.noNewTransactionalSelfInvocationsAppear`）：
   遍历 `getMethodCallsFromSelf()`，筛出 origin 与 target 同属一个类、且 target 带注解的调用。
   找到 **7** 处。

第 7 处是文本扫描漏掉的：`RabbitService.createRabbit` 的四参重载调用五参重载。
按名字比对的扫描会把「同名调用」当成递归跳过，字节码比对不会。
**结论：这类排查不能只靠 grep。**

## 二、七处自调用逐条判定

| 文件 | 调用方 | 被调方 | 行 | 调用方是否事务 | 判定 |
| --- | --- | --- | --- | --- | --- |
| `setting/service/SettingService.java` | `updateUserSetting` | `getOrCreateUserSetting` | 51 | 是 | 无害 |
| `setting/service/SettingService.java` | `getEffectiveSetting` | `getOrCreateUserSetting` | 65 | 是 | 无害 |
| `setting/service/SettingService.java` | `updateHouseSetting` | `getOrCreateUserSetting` | 78 | 是 | 无害 |
| `setting/service/SettingService.java` | `getOrCreateHouseSetting`（private） | `getOrCreateUserSetting` | 113 | 否 | 无害 |
| `auth/service/PhoneAuthService.java` | `loginOrRegister` | `authenticate` | 22 | 是 | 无害 |
| `rabbit/service/RabbitService.java` | `createRabbit`(4 参) | `createRabbit`(5 参) | 127 | 是 | 无害 |
| `dedup/service/RequestDedupService.java` | `begin`(4 参) | `begin`(5 参) | — | — | **已修** |

判定依据只有一条：被调方的传播行为是默认的 `REQUIRED`。

- 调用方本身已在事务里 → 被调方即使走代理也只是加入同一个事务，绕过代理不改变任何语义。
- 调用方不在事务里（`getOrCreateHouseSetting` 是 private，只被三个 `@Transactional`
  公开方法调用）→ 进入时事务已经开着，同上。

所以前六处不需要改。**但它们全都建立在「被调方是 `REQUIRED`」这个前提上**：
哪天有人把 `getOrCreateUserSetting` 改成 `REQUIRES_NEW`、或给它加上
`@TrackedOperation`，绕过代理就从无害变成静默失效，而这个变化不会有任何运行期信号。

### 唯一需要改的一处

`RequestDedupService.begin(4 参)` 原本没有注解，它调用五参重载。给五参重载加上
`@Transactional` 之后，四参这条入口上的注解是失效的——调用方从代理进来时，
代理看到的是没有注解的四参方法，事务根本不会开。已给四参重载同样加上注解。

这一处也说明了排查为什么必须先于加注解做：注解是加在有自调用的类上的，
不先摸清自调用分布，新加的注解会有一半落在代理够不着的地方。

## 三、落到构建里

两条 ArchUnit 规则写在
`backend/rabbit-boot/src/test/java/com/rabbit/app/architecture/FarmingModuleArchitectureTest.java`：

- `trackedOperationsAreNeverInvokedFromTheSameClass` —— **零容忍**。
  `@TrackedOperation` 的自调用没有「无害」的情形：上下文不绑、幂等不记账、事件不落库。
  要在类内复用被追踪的逻辑，把逻辑抽成不带注解的私有方法，让两个入口各自被代理。
- `noNewTransactionalSelfInvocationsAppear` —— **允许清单制**。
  上表六处已判定无害的写进 `ALLOWED_TRANSACTIONAL_SELF_CALLS`，新增的一律构建失败。
  要新增，得先在这份文档里写明为什么无害。

选允许清单而不是零容忍，是因为把六处无害调用改成注入自身代理或抽私有方法，
改动面比它防住的风险还大；但「不改」和「不知道」是两回事，清单让前者可审计。

## 四、复现方式

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn -o --file backend/pom.xml test -Dtest=FarmingModuleArchitectureTest
```

统计注解总数：

```bash
grep -rn "@Transactional" backend --include=*.java \
  | grep -v '/target/' | grep -v '/src/test/' | wc -l
```
