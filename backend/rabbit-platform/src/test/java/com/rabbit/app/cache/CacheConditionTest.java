package com.rabbit.app.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

/**
 * 按 {@code app.cache.provider} 选择缓存实现。
 *
 * <p>两个 Condition 必须互斥且覆盖全部取值：都不匹配则容器里没有 ApplicationCache
 * bean，应用起不来；都匹配则 bean 定义冲突。中间任何一个取值落空，都是启动期故障。
 *
 * <p>拼错 provider（比如写成 {@code Redis } 带大写或尾空格）时的行为尤其要钉住 ——
 * 这类笔误如果被静默降级成「不启用缓存」，线上会表现为缓存一直不命中，
 * 但没有任何错误日志。
 */
class CacheConditionTest {
    private final CacheConfiguration.CacheEnabledCondition enabled =
            new CacheConfiguration.CacheEnabledCondition();
    private final CacheConfiguration.CacheDisabledCondition disabled =
            new CacheConfiguration.CacheDisabledCondition();

    @Test
    void redisTurnsTheRealCacheOn() {
        assertTrue(enabled.matches(contextWith("redis"), null));
        assertFalse(disabled.matches(contextWith("redis"), null));
    }

    @Test
    void valkeyTurnsTheRealCacheOn() {
        assertTrue(enabled.matches(contextWith("valkey"), null));
        assertFalse(disabled.matches(contextWith("valkey"), null));
    }

    @Test
    void noneSelectsTheNoopCache() {
        assertTrue(disabled.matches(contextWith("none"), null));
        assertFalse(enabled.matches(contextWith("none"), null));
    }

    /**
     * 没配置时默认关闭，新环境不该因为缺一行配置就起不来。
     */
    @Test
    void anUnsetProviderDefaultsToDisabled() {
        assertTrue(disabled.matches(contextWithoutProvider(), null));
        assertFalse(enabled.matches(contextWithoutProvider(), null));
    }

    @Test
    void theProviderIsCaseInsensitive() {
        assertTrue(enabled.matches(contextWith("REDIS"), null));
        assertTrue(enabled.matches(contextWith("Valkey"), null));
        assertTrue(disabled.matches(contextWith("NONE"), null));
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertTrue(enabled.matches(contextWith("  redis  "), null));
        assertTrue(disabled.matches(contextWith("  none  "), null));
    }

    /**
     * 拼错的 provider 两个条件都不匹配，容器因缺少 ApplicationCache bean 启动失败。
     * 这是有意的：宁可起不来，也好过静默按「不启用」跑，让人对着不命中的缓存查半天。
     */
    @Test
    void aMisspelledProviderMatchesNeitherConditionSoStartupFails() {
        assertFalse(enabled.matches(contextWith("rediss"), null));
        assertFalse(disabled.matches(contextWith("rediss"), null));

        assertFalse(enabled.matches(contextWith("memcached"), null));
        assertFalse(disabled.matches(contextWith("memcached"), null));
    }

    @Test
    void anEmptyProviderMatchesNeitherCondition() {
        assertFalse(enabled.matches(contextWith(""), null));
        assertFalse(disabled.matches(contextWith(""), null));
    }

    private ConditionContext contextWith(String provider) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.cache.provider", provider);
        return contextOf(environment);
    }

    private ConditionContext contextWithoutProvider() {
        return contextOf(new MockEnvironment());
    }

    private ConditionContext contextOf(MockEnvironment environment) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return context;
    }
}
