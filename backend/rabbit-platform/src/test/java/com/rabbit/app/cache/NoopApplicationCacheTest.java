package com.rabbit.app.cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 缓存关闭时的空实现，以及 {@code getOrLoad} 的默认逻辑。
 *
 * <p>空实现不是「什么都不做」那么简单：它必须和真实现校验同样的入参。否则本地关着
 * 缓存开发一路顺畅，上线打开 Redis 才发现有个调用方传了 null 或者零 TTL —— 那种
 * 零 TTL 在真实现里会写出一条永不过期的键，属于要人工清理的事故。
 *
 * <p>{@code getOrLoad} 的默认实现是「缓存未命中就回源并回填」，兜底语义写在这里，
 * 所有实现共用。
 */
class NoopApplicationCacheTest {
    private final ApplicationCache cache = new NoopApplicationCache();
    private final CacheKey key = CacheKey.of("test", "1");

    // ---------- 空实现仍然校验入参 ----------

    @Test
    void readingAlwaysMisses() {
        assertTrue(cache.get(key, String.class).isEmpty());
    }

    @Test
    void aNullKeyIsRejectedOnEveryOperation() {
        assertThrows(NullPointerException.class, () -> cache.get(null, String.class));
        assertThrows(NullPointerException.class, () -> cache.put(null, "v", Duration.ofMinutes(1)));
        assertThrows(NullPointerException.class, () -> cache.evict(null));
    }

    @Test
    void aNullValueTypeIsRejected() {
        assertThrows(NullPointerException.class, () -> cache.get(key, null));
    }

    @Test
    void aNullValueIsRejected() {
        assertThrows(NullPointerException.class, () -> cache.put(key, null, Duration.ofMinutes(1)));
    }

    /**
     * 零或负 TTL 在真实现里会写出永不过期的键，空实现必须同样拒绝，
     * 否则这类错误只会在启用缓存的环境暴露。
     */
    @Test
    void aNonPositiveTtlIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> cache.put(key, "v", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> cache.put(key, "v", Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> cache.put(key, "v", null));
    }

    @Test
    void aPositiveTtlIsAccepted() {
        assertDoesNotThrow(() -> cache.put(key, "v", Duration.ofMillis(1)));
        assertDoesNotThrow(() -> cache.evict(key));
    }

    // ---------- getOrLoad 默认实现 ----------

    /**
     * 缓存关闭时每次都要回源，绝不能因为「没缓存」就返回空值。
     */
    @Test
    void everyCallFallsBackToTheLoaderWhenCachingIsOff() {
        AtomicInteger loads = new AtomicInteger();

        String first = cache.getOrLoad(key, String.class, Duration.ofMinutes(1),
                () -> "loaded-" + loads.incrementAndGet());
        String second = cache.getOrLoad(key, String.class, Duration.ofMinutes(1),
                () -> "loaded-" + loads.incrementAndGet());

        assertEquals("loaded-1", first);
        assertEquals("loaded-2", second);
        assertEquals(2, loads.get());
    }

    /**
     * 命中时不该调用回源函数——这正是缓存存在的意义。
     */
    @Test
    void aCacheHitSkipsTheLoader() {
        AtomicInteger loads = new AtomicInteger();
        ApplicationCache hitting = alwaysHitting("cached");

        String value = hitting.getOrLoad(key, String.class, Duration.ofMinutes(1),
                () -> "loaded-" + loads.incrementAndGet());

        assertEquals("cached", value);
        assertEquals(0, loads.get());
    }

    /**
     * 回源返回 null 时明确报错，而不是把 null 写进缓存。写进去之后每次命中都返回 null，
     * 调用方会以为数据真的不存在，而这种「缓存了空值」的故障极难定位。
     */
    @Test
    void aLoaderReturningNullIsRejectedRatherThanCached() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> cache.getOrLoad(key, String.class, Duration.ofMinutes(1), () -> null));

        assertEquals("cache loader returned null", error.getMessage());
    }

    /**
     * 未命中时回源结果要写回缓存，否则 getOrLoad 退化成纯粹的回源，缓存永远不生效。
     */
    @Test
    void aMissWritesTheLoadedValueBack() {
        RecordingCache recording = new RecordingCache();

        recording.getOrLoad(key, String.class, Duration.ofMinutes(5), () -> "fresh");

        assertEquals("fresh", recording.written);
        assertEquals(Duration.ofMinutes(5), recording.writtenTtl);
    }

    private ApplicationCache alwaysHitting(String value) {
        return new ApplicationCache() {
            @Override
            public <T> Optional<T> get(CacheKey key, Class<T> valueType) {
                return Optional.of(valueType.cast(value));
            }

            @Override
            public void put(CacheKey key, Object value, Duration ttl) {
                // 命中路径不会调用。
            }

            @Override
            public void evict(CacheKey key) {
                // 命中路径不会调用。
            }
        };
    }

    /** 记录回填动作，用于验证未命中时确实写回。 */
    private static final class RecordingCache implements ApplicationCache {
        private Object written;
        private Duration writtenTtl;

        @Override
        public <T> Optional<T> get(CacheKey key, Class<T> valueType) {
            return Optional.empty();
        }

        @Override
        public void put(CacheKey key, Object value, Duration ttl) {
            this.written = value;
            this.writtenTtl = ttl;
        }

        @Override
        public void evict(CacheKey key) {
            // 本用例不涉及。
        }
    }
}
