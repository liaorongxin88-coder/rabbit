package com.rabbit.app.tracking;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * {@link TrackedOperation} 的解析与缓存中心。
 *
 * <p>缓存键是 {@link Method}，不是方法名或签名字符串：Method 实例由 JVM 缓存、
 * hashCode 稳定，且天然区分重载。
 *
 * <p>求值上下文同时提供三种变量名：{@code #userId} 这样的<b>参数名</b>、
 * {@code #p0} 与 {@code #a0} 这样的<b>下标名</b>，以及 {@code #result}。
 * 参数名依赖编译期 {@code -parameters}（spring-boot-starter-parent 默认开启），
 * 下标名是它失效时的兜底——一旦某个模块关掉了该编译参数，表达式退化成
 * 下标写法仍可工作，而不是全线返回 null。
 */
@Component
public class TrackedOperationRegistry {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();

    private final Map<Method, TrackedOperationDescriptor> cache = new ConcurrentHashMap<>();

    /**
     * @return 该方法的描述符；方法没标注注解时返回 null
     */
    public TrackedOperationDescriptor describe(Method method) {
        if (method == null) {
            return null;
        }
        TrackedOperationDescriptor cached = cache.get(method);
        if (cached != null) {
            return cached;
        }
        TrackedOperation annotation = AnnotatedElementUtils.findMergedAnnotation(method, TrackedOperation.class);
        if (annotation == null) {
            return null;
        }
        TrackedOperationDescriptor descriptor = new TrackedOperationDescriptor(
                annotation.code(),
                parse(annotation.codeExpression()),
                annotation.eventType(),
                annotation.dedup(),
                parse(annotation.houseId()),
                parse(annotation.userId()),
                parse(annotation.requestId()),
                parse(annotation.batchId()),
                parse(annotation.cageId()),
                parse(annotation.rabbitId()),
                annotation.targetType(),
                parse(annotation.targetId())
        );
        cache.put(method, descriptor);
        return descriptor;
    }

    public StandardEvaluationContext evaluationContext(Method method, Object target, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext(target);
        String[] names = PARAMETER_NAMES.getParameterNames(method);
        Object[] safeArgs = args == null ? new Object[0] : args;
        for (int i = 0; i < safeArgs.length; i++) {
            context.setVariable("p" + i, safeArgs[i]);
            context.setVariable("a" + i, safeArgs[i]);
            if (names != null && i < names.length && names[i] != null) {
                context.setVariable(names[i], safeArgs[i]);
            }
        }
        return context;
    }

    /**
     * 方法返回后把返回值挂进上下文，让 {@code #result.id} 这类表达式可用——
     * 自增主键只有在插入之后才存在，事件的目标 ID 常常只能从返回值取。
     */
    public void bindResult(StandardEvaluationContext context, Object result) {
        context.setVariable("result", result);
    }

    int cachedDescriptorCount() {
        return cache.size();
    }

    private Expression parse(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        return PARSER.parseExpression(expression);
    }
}
