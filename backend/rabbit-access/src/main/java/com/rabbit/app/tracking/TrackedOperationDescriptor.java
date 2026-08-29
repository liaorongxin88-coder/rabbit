package com.rabbit.app.tracking;

import java.lang.reflect.Method;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * 一个 {@link TrackedOperation} 的解析结果：注解常量 + 已编译的 SpEL 表达式。
 *
 * <p>解析发生一次，之后按 {@link Method} 缓存。写路径上不再有注解反射、
 * 也不再有表达式文本解析——批量端点 500 只兔走同一个 Method，
 * 表达式对象是同一批。
 */
public final class TrackedOperationDescriptor {

    private final String code;
    private final Expression codeExpression;
    private final String eventType;
    private final boolean dedup;
    private final Expression houseId;
    private final Expression userId;
    private final Expression requestId;
    private final Expression batchId;
    private final Expression cageId;
    private final Expression rabbitId;
    private final String targetType;
    private final Expression targetId;

    TrackedOperationDescriptor(
            String code,
            Expression codeExpression,
            String eventType,
            boolean dedup,
            Expression houseId,
            Expression userId,
            Expression requestId,
            Expression batchId,
            Expression cageId,
            Expression rabbitId,
            String targetType,
            Expression targetId
    ) {
        this.code = code;
        this.codeExpression = codeExpression;
        this.eventType = eventType;
        this.dedup = dedup;
        this.houseId = houseId;
        this.userId = userId;
        this.requestId = requestId;
        this.batchId = batchId;
        this.cageId = cageId;
        this.rabbitId = rabbitId;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public String getCode() {
        return code;
    }

    public String getEventType() {
        return eventType;
    }

    public String code(StandardEvaluationContext context) {
        String resolved = evaluateText(codeExpression, context);
        return resolved == null || resolved.isBlank() ? code : resolved;
    }

    public boolean hasEventType() {
        return eventType != null && !eventType.isBlank();
    }

    public boolean isDedup() {
        return dedup;
    }

    public Long houseId(StandardEvaluationContext context) {
        return evaluateId(houseId, context);
    }

    public Long userId(StandardEvaluationContext context) {
        return evaluateId(userId, context);
    }

    public Long batchId(StandardEvaluationContext context) {
        return evaluateId(batchId, context);
    }

    public Long cageId(StandardEvaluationContext context) {
        return evaluateId(cageId, context);
    }

    public Long rabbitId(StandardEvaluationContext context) {
        return evaluateId(rabbitId, context);
    }

    public String getTargetType() {
        return targetType;
    }

    public Long targetId(StandardEvaluationContext context) {
        return evaluateId(targetId, context);
    }

    public String requestId(StandardEvaluationContext context) {
        return evaluateText(requestId, context);
    }

    /**
     * 求值失败一律回落成 null，绝不抛出。
     *
     * <p>取舍写在这里：审计标识取不到，最坏结果是一条坐标不全的事件；
     * 让表达式异常冒出去，最坏结果是一次合法的生产写入被审计代码打断。
     * 后者不可接受——注解是加在既有写路径上的，不能反过来变成新的故障源。
     */
    private Long evaluateId(Expression expression, StandardEvaluationContext context) {
        if (expression == null) {
            return null;
        }
        try {
            Number value = expression.getValue(context, Number.class);
            return value == null ? null : value.longValue();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String evaluateText(Expression expression, StandardEvaluationContext context) {
        if (expression == null) {
            return null;
        }
        try {
            return expression.getValue(context, String.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
