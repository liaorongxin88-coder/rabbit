package com.rabbit.app.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.support.StandardEvaluationContext;

class TrackedOperationRegistryTest {

    private final TrackedOperationRegistry registry = new TrackedOperationRegistry();

    @Test
    void readsIdentifiersFromPlainParametersAndNestedFields() throws Exception {
        Method method = Sample.class.getMethod("create", Long.class, Long.class, Payload.class, String.class);
        TrackedOperationDescriptor descriptor = registry.describe(method);
        Payload payload = new Payload(77L, 5L, 9L);
        StandardEvaluationContext context = registry.evaluationContext(
                method, new Sample(), new Object[]{3L, 8L, payload, "req-1"});

        assertEquals("weight:create", descriptor.getCode());
        assertEquals("WEIGHT_RECORDED", descriptor.getEventType());
        assertTrue(descriptor.isDedup());
        assertEquals(8L, descriptor.houseId(context));
        assertEquals(3L, descriptor.userId(context));
        assertEquals("req-1", descriptor.requestId(context));
        // 这三个正是参数注解覆盖不到的形状：标识埋在 DTO 字段里。
        assertEquals(77L, descriptor.rabbitId(context));
        assertEquals(5L, descriptor.batchId(context));
        assertEquals(9L, descriptor.cageId(context));
        assertEquals("RABBIT", descriptor.getTargetType());
        assertEquals(77L, descriptor.targetId(context));
    }

    @Test
    void indexedVariablesWorkWhenParameterNamesAreUnavailable() throws Exception {
        Method method = Sample.class.getMethod("indexed", Long.class, String.class);
        TrackedOperationDescriptor descriptor = registry.describe(method);
        StandardEvaluationContext context = registry.evaluationContext(method, new Sample(), new Object[]{4L, "r"});

        assertEquals(4L, descriptor.houseId(context));
    }

    @Test
    void descriptorsAreCachedPerMethod() throws Exception {
        Method method = Sample.class.getMethod("create", Long.class, Long.class, Payload.class, String.class);

        TrackedOperationDescriptor first = registry.describe(method);
        TrackedOperationDescriptor second = registry.describe(method);

        assertSame(first, second, "热路径上不应重复解析注解和表达式");
        assertEquals(1, registry.cachedDescriptorCount());
    }

    @Test
    void unannotatedMethodsAreIgnored() throws Exception {
        assertNull(registry.describe(Sample.class.getMethod("plain")));
        assertNull(registry.describe(null));
    }

    @Test
    void unresolvableExpressionsFallBackToNullInsteadOfBreakingTheWrite() throws Exception {
        Method method = Sample.class.getMethod("create", Long.class, Long.class, Payload.class, String.class);
        TrackedOperationDescriptor descriptor = registry.describe(method);
        // entity 为 null：#r.rabbitId 求值必然失败。审计取不到标识可以接受，
        // 打断一次合法的生产写入不可接受。
        StandardEvaluationContext context = registry.evaluationContext(
                method, new Sample(), new Object[]{3L, 8L, null, "req-1"});

        assertNull(descriptor.rabbitId(context));
        assertEquals(8L, descriptor.houseId(context));
    }

    @Test
    void resultIsAvailableToExpressionsAfterTheCall() throws Exception {
        Method method = Sample.class.getMethod("create", Long.class, Long.class, Payload.class, String.class);
        StandardEvaluationContext context = registry.evaluationContext(
                method, new Sample(), new Object[]{3L, 8L, new Payload(1L, 1L, 1L), "req"});

        registry.bindResult(context, new Payload(42L, 1L, 1L));

        assertEquals(42L, context.lookupVariable("result") instanceof Payload p ? p.getRabbitId() : null);
    }

    static class Sample {
        @TrackedOperation(
                code = "weight:create",
                eventType = "WEIGHT_RECORDED",
                rabbitId = "#r.rabbitId",
                batchId = "#r.batchId",
                cageId = "#r.cageId",
                targetType = "RABBIT",
                targetId = "#r.rabbitId",
                dedup = true
        )
        public Payload create(Long userId, Long houseId, Payload r, String requestId) {
            return r;
        }

        @TrackedOperation(code = "sample:indexed", houseId = "#p0", requestId = "#p1")
        public void indexed(Long anything, String requestId) {
        }

        public void plain() {
        }
    }

    static class Payload {
        private final Long rabbitId;
        private final Long batchId;
        private final Long cageId;

        Payload(Long rabbitId, Long batchId, Long cageId) {
            this.rabbitId = rabbitId;
            this.batchId = batchId;
            this.cageId = cageId;
        }

        public Long getRabbitId() {
            return rabbitId;
        }

        public Long getBatchId() {
            return batchId;
        }

        public Long getCageId() {
            return cageId;
        }
    }
}
