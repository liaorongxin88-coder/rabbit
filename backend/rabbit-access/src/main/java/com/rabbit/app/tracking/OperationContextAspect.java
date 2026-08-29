package com.rabbit.app.tracking;

import com.rabbit.app.modules.dedup.service.RequestDedupService;
import java.lang.reflect.Method;
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
 * 外层切面：绑定操作上下文并接管幂等记账。<b>整个方法体都在业务事务之外。</b>
 *
 * <p>顺序见 {@link OperationTrackingOrder}。之所以必须在事务外：
 * markFailed 写的是「这个 requestId 试过并且失败了」，业务回滚时它必须活下来。
 * 和业务同事务，回滚会把它一起抹掉，重试时看不到失败痕迹，去重记账形同虚设。
 *
 * <p>幂等状态机完整落在这一层，业务方法不再自己调
 * shouldSkipAsDone / markProcessing / markDone / markFailed。顺序不能颠倒：
 * 必须先判「是否已完成」再 markProcessing，否则自己刚写下的 PROCESSING
 * 会把回放语义打坏。判定结果放进 {@link OperationContext#isDedupReplay()}，
 * 业务方法读一个布尔值就够了。
 *
 * <p><b>嵌套调用的已知取舍</b>：一个被追踪的方法调另一个被追踪的方法时
 * （跨 bean，同类自调用已被 ArchUnit 规则禁止），内层求出的 batchId/cageId/rabbitId
 * 会留在上下文里，不回滚给外层。这是刻意的：标识只会变得更具体，
 * 而恢复快照要为一个当前并不存在的场景付出每次调用都拷贝一遍上下文的代价。
 * T4 铺开到 45 个写方法、真的出现嵌套时再按需收紧。
 */
@Aspect
@Component
@Order(OperationTrackingOrder.CONTEXT_ASPECT)
public class OperationContextAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationContextAspect.class);

    private final TrackedOperationRegistry registry;
    private final OperatorNameResolver operatorNameResolver;
    private final RequestDedupService requestDedupService;

    public OperationContextAspect(
            TrackedOperationRegistry registry,
            OperatorNameResolver operatorNameResolver,
            RequestDedupService requestDedupService
    ) {
        this.registry = registry;
        this.operatorNameResolver = operatorNameResolver;
        this.requestDedupService = requestDedupService;
    }

    @Around("@annotation(com.rabbit.app.tracking.TrackedOperation)")
    public Object bindContext(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = targetMethod(joinPoint);
        TrackedOperationDescriptor descriptor = registry.describe(method);
        if (descriptor == null) {
            return joinPoint.proceed();
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 说明切面顺序被破坏，或调用方自己开了事务。此时 markFailed 会被回滚，
            // 幂等记账退回到改造前的缺陷状态。不抛异常打断生产写入，但必须留声。
            log.warn(
                    "TrackedOperation {} 在已有事务内进入外层切面，去重状态将随业务回滚一起丢失",
                    descriptor.getCode()
            );
        }

        OperationContext existing = OperationContext.current();
        boolean owned = existing == null;
        OperationContext context = owned ? OperationContext.bind() : existing;
        String previousCode = context.getOperationCode();
        boolean previousReplay = context.isDedupReplay();

        StandardEvaluationContext spel = registry.evaluationContext(method, joinPoint.getTarget(), joinPoint.getArgs());
        applyIdentifiers(context, descriptor, spel);
        String operationCode = descriptor.code(spel);
        context.setOperationCode(operationCode);

        boolean dedupActive = descriptor.isDedup() && hasText(context.getRequestId());
        if (dedupActive) {
            context.setDedupReplay(requestDedupService.shouldSkipAsDone(
                    context.getHouseId(), context.getUserId(), operationCode, context.getRequestId()));
            if (!context.isDedupReplay()) {
                requestDedupService.markProcessing(
                        context.getHouseId(), context.getUserId(), operationCode, context.getRequestId());
            }
        } else {
            context.setDedupReplay(false);
        }

        boolean replay = context.isDedupReplay();
        try {
            Object result = joinPoint.proceed();
            if (dedupActive && !replay) {
                // 此刻业务事务已经提交（事务通知是内层）。markDone 必须在提交之后，
                // 否则「已完成」会先于数据落地，重试时回放出一条并不存在的记录。
                requestDedupService.markDone(
                        context.getHouseId(), context.getUserId(), operationCode, context.getRequestId());
            }
            return result;
        } catch (RuntimeException e) {
            if (dedupActive && !replay) {
                requestDedupService.markFailed(
                        context.getHouseId(), context.getUserId(), operationCode,
                        context.getRequestId(), e.getMessage());
            }
            throw e;
        } finally {
            if (owned) {
                OperationContext.clear();
            } else {
                context.setOperationCode(previousCode);
                context.setDedupReplay(previousReplay);
            }
        }
    }

    private void applyIdentifiers(
            OperationContext context,
            TrackedOperationDescriptor descriptor,
            StandardEvaluationContext spel
    ) {
        Long houseId = descriptor.houseId(spel);
        if (houseId != null) {
            context.setHouseId(houseId);
        }
        Long userId = descriptor.userId(spel);
        if (userId != null) {
            context.setUserId(userId);
        }
        Long batchId = descriptor.batchId(spel);
        if (batchId != null) {
            context.setBatchId(batchId);
        }
        Long cageId = descriptor.cageId(spel);
        if (cageId != null) {
            context.setCageId(cageId);
        }
        Long rabbitId = descriptor.rabbitId(spel);
        if (rabbitId != null) {
            context.setRabbitId(rabbitId);
        }
        String requestId = descriptor.requestId(spel);
        if (hasText(requestId)) {
            context.setRequestId(requestId);
        }
        if (context.getOperatorName() == null && context.getUserId() != null) {
            context.setOperatorName(operatorNameResolver.resolve(context.getUserId()));
        }
    }

    /**
     * 取<b>目标类</b>上的方法而不是接口方法：注解写在实现类上，
     * 从 {@code MethodSignature} 直接拿到的可能是接口声明，注解会丢。
     */
    private Method targetMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object target = joinPoint.getTarget();
        if (target == null) {
            return signature.getMethod();
        }
        return AopUtils.getMostSpecificMethod(signature.getMethod(), target.getClass());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
