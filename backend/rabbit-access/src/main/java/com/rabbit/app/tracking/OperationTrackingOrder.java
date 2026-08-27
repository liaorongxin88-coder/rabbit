package com.rabbit.app.tracking;

/**
 * 切面与事务通知的相对顺序。<b>整套基座最关键的一条约束。</b>
 *
 * <p>两条不能违反的规则，方向相反：
 *
 * <ul>
 *   <li><b>去重状态必须写在事务外。</b>业务失败要回滚，但「这个 requestId 试过并且
 *       失败了」这件事必须留下来。写在事务内，回滚会把 markFailed 一起抹掉，
 *       去重记账失效——这正是 {@code RequestDedupService} 现在的缺陷。</li>
 *   <li><b>事件写入必须在事务内。</b>写在事务外，业务回滚后事件还在，
 *       事件流会声称发生过一次实际没落地的操作，比没有事件更糟。</li>
 * </ul>
 *
 * <p>因此必须是三层夹心，而不是一个切面：
 *
 * <pre>
 *   {@link OperationContextAspect}   order = 0      ← 事务外：绑上下文、幂等记账
 *     └── Spring 事务通知             order = 1000   ← 事务边界
 *           └── {@link OperationEventAspect} order = 2000 ← 事务内：事件批量落库
 *                 └── 业务方法
 * </pre>
 *
 * <p><b>为什么要显式设置事务通知的 order。</b>Spring 的
 * {@code @EnableTransactionManagement} 默认 order 是 {@code Ordered.LOWEST_PRECEDENCE}
 * （{@code Integer.MAX_VALUE}），没有任何切面能排在它<i>之后</i>——也就是说
 * 默认配置下「事务内的切面」根本写不出来。所以 boot 模块用
 * {@code @EnableTransactionManagement(order = TRANSACTION)} 把它拉到 1000，
 * 内层切面才有位置可站。
 *
 * <p>这个前提一旦被人不小心撤掉，内层切面会静默地跑到事务外，事件流开始
 * 记录回滚掉的操作，而测试很可能测不出来。所以有
 * {@link OperationTrackingOrderVerifier} 在启动时核对真实生效的 order，
 * 对不上就拒绝启动。
 */
public final class OperationTrackingOrder {

    /** 外层：绑上下文 + 幂等记账，事务外。 */
    public static final int CONTEXT_ASPECT = 0;

    /** Spring 事务通知。由 boot 模块的 {@code TransactionAdvisorOrderConfig} 生效。 */
    public static final int TRANSACTION = 1000;

    /** 内层：事件批量落库，事务内。 */
    public static final int EVENT_ASPECT = 2000;

    private OperationTrackingOrder() {
    }
}
