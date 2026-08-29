package com.rabbit.app.tracking;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 内层切面：把事件写进库。<b>整个方法体都在业务事务之内。</b>
 *
 * <p>顺序见 {@link OperationTrackingOrder}。之所以必须在事务内：事件声称
 * 「这件事发生了」，业务回滚时它必须一起消失。写在事务外，一次失败的写入
 * 会在事件流里留下一条查无对证的记录，比没有事件更糟——复盘时你会照着它
 * 去找一条不存在的业务行。
 *
 * <p><b>批量而非逐条。</b>业务方法在循环里调
 * {@link OperationContext#recordEvent} 只是往上下文里塞对象，真正落库在这里
 * 一次性交给 {@link OperationEventSink#append(List)}。单次 500 只兔的批量端点
 * 因此只有一次插入往返，而不是 500 次。{@link OperationEventSink} 也刻意
 * 不提供单条入口，堵死写成循环插入的可能。
 *
 * <p>只在业务方法<b>正常返回</b>后落库。抛异常时事务要回滚，事件跟着作废，
 * 没有必要写；失败痕迹由外层切面的 markFailed 在事务外记录。
 */
@Aspect
@Component
@Order(OperationTrackingOrder.EVENT_ASPECT)
public class OperationEventAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationEventAspect.class);

    private final TrackedOperationRegistry registry;
    private final List<OperationEventSink> sinks;

    /**
     * 注入的是 sink 列表而不是单个 sink：本阶段没有落库实现（事件表扩列属 T4），
     * 空列表让基座可以先跑起来并被测试钉死；T4 接入真实 sink 时只是往列表里
     * 多一个 bean，不改这里一行。
     */
    public OperationEventAspect(TrackedOperationRegistry registry, List<OperationEventSink> sinks) {
        this.registry = registry;
        this.sinks = sinks == null ? List.of() : List.copyOf(sinks);
    }

    @Around("@annotation(com.rabbit.app.tracking.TrackedOperation)")
    public Object persistEvents(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = targetMethod(joinPoint);
        TrackedOperationDescriptor descriptor = registry.describe(method);
        if (descriptor == null) {
            return joinPoint.proceed();
        }

        Object result = joinPoint.proceed();
        StandardEvaluationContext spel = registry.evaluationContext(
                method, joinPoint.getTarget(), joinPoint.getArgs());
        registry.bindResult(spel, result);

        OperationContext context = OperationContext.current();
        if (context == null) {
            return result;
        }
        if (context.isDedupReplay()) {
            // 回放不是一次新的操作，不该再产生一条事件。
            context.drainPendingEvents();
            return result;
        }

        List<OperationEvent> events = new ArrayList<>(context.drainPendingEvents());
        if (descriptor.hasEventType() && events.isEmpty()) {
            // 业务方法没有自己登记事件（非批量场景），由切面按上下文补一条。
            events.add(defaultEvent(context, descriptor, spel));
        }
        if (events.isEmpty()) {
            return result;
        }

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // 到这里还没有事务，说明业务方法没加 @Transactional，或切面顺序被破坏。
            // 事件将无法与业务写入同生共死；不打断请求，但必须留声。
            log.warn(
                    "TrackedOperation {} 在无事务状态下写入 {} 条事件，事件与业务写入不再原子",
                    descriptor.getCode(), events.size()
            );
        }
        for (OperationEventSink sink : sinks) {
            sink.append(events);
        }
        return result;
    }

    private OperationEvent defaultEvent(
            OperationContext context,
            TrackedOperationDescriptor descriptor,
            StandardEvaluationContext spel
    ) {
        Long targetId = descriptor.targetId(spel);
        if (targetId == null) {
            targetId = context.getRabbitId();
        }
        String targetType = descriptor.getTargetType();
        if (targetType == null || targetType.isBlank()) {
            targetType = targetId == null ? "OPERATION" : "RABBIT";
        }
        return OperationEvent.from(context)
                .operationCode(descriptor.code(spel))
                .eventType(descriptor.getEventType())
                .targetType(targetType)
                .targetId(targetId)
                .build();
    }

    private Method targetMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object target = joinPoint.getTarget();
        if (target == null) {
            return signature.getMethod();
        }
        return AopUtils.getMostSpecificMethod(signature.getMethod(), target.getClass());
    }
}
