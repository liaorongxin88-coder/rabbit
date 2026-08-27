package com.rabbit.app.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

/**
 * 钉死切面与事务通知的相对顺序。
 *
 * <p>这些断言看着像在测常量，实际测的是一条<b>没有运行期报错的语义</b>：
 * 顺序搞反不会抛异常、不会有日志，只会让 markFailed 随回滚消失、
 * 让事件记录下没发生的操作。除了在这里把它写死，没有别的地方能拦住它。
 */
class OperationTrackingOrderTest {

    @Test
    void contextAspectRunsOutsideTheTransactionAndEventAspectInside() {
        assertTrue(
                OperationTrackingOrder.CONTEXT_ASPECT < OperationTrackingOrder.TRANSACTION,
                "外层切面必须排在事务通知之前：去重状态要写在事务外"
        );
        assertTrue(
                OperationTrackingOrder.EVENT_ASPECT > OperationTrackingOrder.TRANSACTION,
                "内层切面必须排在事务通知之后：事件要写在事务内"
        );
    }

    @Test
    void transactionOrderMustStayBelowLowestPrecedence() {
        // Spring 默认的事务 order 是 LOWEST_PRECEDENCE，那种情况下没有任何切面
        // 能排在事务之后——「事务内的切面」根本写不出来。
        assertTrue(
                OperationTrackingOrder.TRANSACTION < Ordered.LOWEST_PRECEDENCE,
                "事务通知的 order 必须被显式下调，否则内层切面无处可站"
        );
    }

    @Test
    void aspectBeansCarryTheDeclaredOrder() {
        assertEquals(
                OperationTrackingOrder.CONTEXT_ASPECT,
                AnnotationUtils.findAnnotation(OperationContextAspect.class, Order.class).value()
        );
        assertEquals(
                OperationTrackingOrder.EVENT_ASPECT,
                AnnotationUtils.findAnnotation(OperationEventAspect.class, Order.class).value()
        );
    }

    @Test
    void verifierAcceptsTheConfiguredTransactionOrder() {
        OperationTrackingOrderVerifier.verify(OperationTrackingOrder.TRANSACTION);
    }

    @Test
    void verifierRejectsSpringDefaultTransactionOrder() {
        // 这正是「有人删掉 TransactionAdvisorOrderConfig」之后的样子。
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> OperationTrackingOrderVerifier.verify(Ordered.LOWEST_PRECEDENCE)
        );
        assertTrue(error.getMessage().contains("事件要写在事务内"));
    }

    @Test
    void verifierRejectsATransactionOrderThatWouldSwallowTheOuterAspect() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> OperationTrackingOrderVerifier.verify(OperationTrackingOrder.CONTEXT_ASPECT - 1)
        );
        assertTrue(error.getMessage().contains("去重状态要写在事务外"));
    }
}
