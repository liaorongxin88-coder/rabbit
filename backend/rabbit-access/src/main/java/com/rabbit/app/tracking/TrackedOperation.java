package com.rabbit.app.tracking;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个写操作，让基座接管上下文绑定、幂等记账与事件留痕。
 *
 * <pre>
 * &#64;TrackedOperation(
 *         code = "weight:create",
 *         eventType = "WEIGHT_RECORDED",
 *         rabbitId = "#entity.rabbitId",
 *         dedup = true)
 * &#64;Transactional
 * public WeightLog create(Long userId, Long houseId, WeightLog entity, String requestId) { ... }
 * </pre>
 *
 * <p><b>为什么用 SpEL 而不是参数注解。</b>现有方法签名里，标识要么是独立入参
 * （{@code houseId}、{@code userId}），要么埋在 DTO/实体字段里
 * （{@code entity.rabbitId}），还有的要从返回值里取。参数注解只能覆盖第一种，
 * 覆盖第二种就得改所有方法签名——那是 45 个写方法的返工。表达式一把抓，
 * 且解析结果按 {@code Method} 缓存，热路径上只有一次 map 查找。
 *
 * <p><b>Spring AOP 不拦截同类自调用。</b>本注解走的是代理，类内部
 * {@code this.foo()} 直接命中目标方法，注解静默失效、事件流出现无声空洞。
 * {@code FarmingModuleArchitectureTest} 里有一条 ArchUnit 规则专门禁止对
 * 本注解的自调用，把这个失效模式变成编译期之外的构建期失败。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TrackedOperation {

    /**
     * 操作码，同时作为幂等记账的 api 键。必须与既有 {@code RequestDedupService}
     * 调用里手写的 api 字符串完全一致（如 {@code "weight:create"}），
     * 否则同一 requestId 的历史记账会认不出来。
     */
    String code();

    /**
     * 事件类型。留空表示只绑上下文与幂等、不产生事件（例如纯改名类写操作）。
     */
    String eventType() default "";

    /**
     * 兔舍。默认取名为 houseId 的入参——现有写方法几乎都有这个参数。
     */
    String houseId() default "#houseId";

    /**
     * 操作人。默认取名为 userId 的入参。
     */
    String userId() default "#userId";

    /**
     * 幂等键。默认取名为 requestId 的入参。
     */
    String requestId() default "#requestId";

    String batchId() default "";

    String cageId() default "";

    String rabbitId() default "";

    /** 目标资源类型，如 {@code RABBIT}、{@code BATCH}、{@code INVENTORY_ITEM}。 */
    String targetType() default "";

    /** 目标资源 ID，允许引用 {@code #result.id}。 */
    String targetId() default "";

    /**
     * 是否接管幂等记账。开启后由外层切面在<b>事务外</b>执行
     * markProcessing / markDone / markFailed，业务方法只需读
     * {@link OperationContext#isDedupReplay()} 决定要不要回放旧结果。
     */
    boolean dedup() default false;
}
