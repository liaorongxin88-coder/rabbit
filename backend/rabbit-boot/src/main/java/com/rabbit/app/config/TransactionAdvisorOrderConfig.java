package com.rabbit.app.config;

import com.rabbit.app.tracking.OperationTrackingOrder;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 把 Spring 事务通知的 order 从默认的 {@code Integer.MAX_VALUE} 拉到一个
 * 有限值，好让操作追踪的内层切面有位置排在它<b>之后</b>。
 *
 * <p>没有这个配置，「事务内的切面」在 Spring 里写不出来：
 * {@code @EnableTransactionManagement} 默认 order 是
 * {@code Ordered.LOWEST_PRECEDENCE}，任何切面都只能排在它前面（事务外）。
 * 事件写入必须在事务内，否则业务回滚后事件仍留在流里。
 *
 * <p><b>{@code proxyTargetClass = true} 不能省。</b>Spring Boot 的
 * {@code TransactionAutoConfiguration} 在检测到已有
 * {@code AbstractTransactionManagementConfiguration} 时会整体退让，
 * 它默认开启的 CGLIB 代理也随之退让。本仓的 service 大多不实现接口，
 * 退回 JDK 动态代理会让它们根本无法被代理，事务静默消失。
 *
 * <p>三个 order 的关系与理由见 {@link OperationTrackingOrder}；
 * 运行期由 {@code OperationTrackingOrderVerifier} 在启动时核对，
 * 这个配置若被移除，应用会拒绝启动而不是悄悄降级。
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(proxyTargetClass = true, order = OperationTrackingOrder.TRANSACTION)
public class TransactionAdvisorOrderConfig {
}
