package com.rabbit.app.modules.repro.service;

import org.springframework.stereotype.Component;

/**
 * @deprecated 已迁移到 {@link com.rabbit.app.tracking.OperatorNameResolver}。
 *
 * <p>实现搬去了 access：它依赖的 {@code SysUserMapper} 就在那边，而原先的位置
 * 让 batch、rabbit、repro 三个模块都要反向 import 一个繁育包里的类，模块方向是反的。
 * 新实现还加了请求级缓存——批量端点单次 500 只兔，逐只解析就是 500 次主键查询。
 *
 * <p>这里保留一层转发而不是直接删类，是为了不动 {@code BatchService}、
 * {@code BatchWeaningSeparationService}、{@code ReproCycleController}、
 * {@code WorkTaskController} 这四个当前正被其他改造占用的文件。
 * 用<b>组合而非继承</b>：若继承新类，容器里会同时存在两个可赋值给
 * {@code com.rabbit.app.tracking.OperatorNameResolver} 的 bean，按类型注入直接歧义。
 *
 * <p>bean 名必须显式指定：两个类的简名相同，默认命名策略会把两者都叫成
 * {@code operatorNameResolver}，导致容器启动时抛
 * {@code ConflictingBeanDefinitionException}。
 *
 * <p>T2/T4 铺开注解时把四处 import 改掉，然后删除本类。
 */
@Deprecated(since = "T1")
@Component("reproOperatorNameResolver")
public class OperatorNameResolver {

    private final com.rabbit.app.tracking.OperatorNameResolver delegate;

    public OperatorNameResolver(com.rabbit.app.tracking.OperatorNameResolver delegate) {
        this.delegate = delegate;
    }

    public String resolve(Long userId) {
        return delegate.resolve(userId);
    }
}
