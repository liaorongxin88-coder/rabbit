package com.rabbit.app.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;

/**
 * 启动时核对切面与事务通知的真实生效顺序，对不上就拒绝启动。
 *
 * <p>为什么需要它：{@link OperationTrackingOrder} 里的三个数字只是常量，
 * 真正决定事务通知位置的是 boot 模块那句
 * {@code @EnableTransactionManagement(order = ...)}。这两处一旦脱钩——
 * 有人删掉配置类、或 Spring Boot 的自动配置抢先生效把 order 恢复成
 * {@code Integer.MAX_VALUE}——内层切面会静默地跑到事务外，事件流开始记录
 * 已经回滚掉的操作。
 *
 * <p>这种失效<b>不会有任何运行期报错</b>，单元测试也测不出来（它们不带事务）。
 * 唯一可靠的防线是启动即校验：把一个隐蔽的语义错误变成一次响亮的启动失败。
 */
@Component
public class OperationTrackingOrderVerifier implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(OperationTrackingOrderVerifier.class);

    private final ObjectProvider<BeanFactoryTransactionAttributeSourceAdvisor> transactionAdvisor;

    public OperationTrackingOrderVerifier(
            ObjectProvider<BeanFactoryTransactionAttributeSourceAdvisor> transactionAdvisor
    ) {
        this.transactionAdvisor = transactionAdvisor;
    }

    @Override
    public void afterSingletonsInstantiated() {
        BeanFactoryTransactionAttributeSourceAdvisor advisor = transactionAdvisor.getIfAvailable();
        if (advisor == null) {
            log.debug("未启用声明式事务，跳过操作追踪切面顺序校验");
            return;
        }
        int actual = advisor.getOrder();
        verify(actual);
        log.info(
                "操作追踪切面顺序校验通过: contextAspect={} < transaction={} < eventAspect={}",
                OperationTrackingOrder.CONTEXT_ASPECT, actual, OperationTrackingOrder.EVENT_ASPECT
        );
    }

    static void verify(int transactionAdvisorOrder) {
        if (OperationTrackingOrder.CONTEXT_ASPECT >= transactionAdvisorOrder) {
            throw new IllegalStateException(
                    "操作追踪外层切面必须排在事务通知之前（去重状态要写在事务外，否则回滚会抹掉 markFailed）："
                            + "contextAspect=" + OperationTrackingOrder.CONTEXT_ASPECT
                            + ", transactionAdvisor=" + transactionAdvisorOrder);
        }
        if (OperationTrackingOrder.EVENT_ASPECT <= transactionAdvisorOrder) {
            throw new IllegalStateException(
                    "操作追踪内层切面必须排在事务通知之后（事件要写在事务内，否则业务回滚后事件仍在）："
                            + "eventAspect=" + OperationTrackingOrder.EVENT_ASPECT
                            + ", transactionAdvisor=" + transactionAdvisorOrder
                            + "。请检查 TransactionAdvisorOrderConfig 是否仍在生效。");
        }
    }
}
