package com.rabbit.app.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JsonApplicationCacheTest {
    private final FakeCacheBackend backend = new FakeCacheBackend();
    private final JsonApplicationCache cache = new JsonApplicationCache(
            backend,
            new ObjectMapper(),
            CacheProvider.REDIS,
            "rabbit:cache:v1"
    );

    @Test
    void storesTypedJsonWithPrefixAndTtl() {
        CacheKey key = CacheKey.of("rabbit-summary", 42);
        Duration ttl = Duration.ofMinutes(5);

        cache.put(key, new Summary("healthy", 3), ttl);

        assertThat(backend.values)
                .containsEntry("rabbit:cache:v1:rabbit-summary:42", "{\"status\":\"healthy\",\"count\":3}");
        assertThat(backend.lastTtl).isEqualTo(ttl);
        assertThat(cache.get(key, Summary.class)).contains(new Summary("healthy", 3));
    }

    @Test
    void loadsOnMissAndCachesTheResult() {
        AtomicInteger loads = new AtomicInteger();
        CacheKey key = CacheKey.of("rabbit-summary", 42);

        Summary first = cache.getOrLoad(
                key,
                Summary.class,
                Duration.ofMinutes(1),
                () -> new Summary("loaded", loads.incrementAndGet())
        );
        Summary second = cache.getOrLoad(
                key,
                Summary.class,
                Duration.ofMinutes(1),
                () -> new Summary("loaded", loads.incrementAndGet())
        );

        assertThat(first).isEqualTo(new Summary("loaded", 1));
        assertThat(second).isEqualTo(first);
        assertThat(loads).hasValue(1);
    }

    @Test
    void evictsMalformedValuesAndTreatsThemAsMisses() {
        String physicalKey = "rabbit:cache:v1:rabbit-summary:42";
        backend.values.put(physicalKey, "not-json");

        assertThat(cache.get(CacheKey.of("rabbit-summary", 42), Summary.class)).isEmpty();
        assertThat(backend.values).doesNotContainKey(physicalKey);
    }

    @Test
    void evictsJsonNullAndTreatsItAsAMiss() {
        String physicalKey = "rabbit:cache:v1:rabbit-summary:42";
        backend.values.put(physicalKey, "null");

        assertThat(cache.get(CacheKey.of("rabbit-summary", 42), Summary.class)).isEmpty();
        assertThat(backend.values).doesNotContainKey(physicalKey);
    }

    @Test
    void failsOpenWhenBackendIsUnavailable() {
        backend.failure = new CacheBackendException("unavailable", new IllegalStateException("offline"));
        CacheKey key = CacheKey.of("rabbit-summary", 42);

        assertThat(cache.get(key, Summary.class)).isEmpty();
        assertThatCode(() -> cache.put(key, new Summary("healthy", 3), Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> cache.evict(key)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonPositiveTtlBeforeCallingBackend() {
        CacheKey key = CacheKey.of("rabbit-summary", 42);

        assertThatThrownBy(() -> cache.put(key, new Summary("healthy", 3), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cache ttl must be positive");
    }

    private record Summary(String status, int count) {
    }

    private static final class FakeCacheBackend implements CacheBackend {
        private final Map<String, String> values = new HashMap<>();
        private Duration lastTtl;
        private CacheBackendException failure;

        @Override
        public Optional<String> get(String key) {
            failIfConfigured();
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void put(String key, String value, Duration ttl) {
            failIfConfigured();
            values.put(key, value);
            lastTtl = ttl;
        }

        @Override
        public void evict(String key) {
            failIfConfigured();
            values.remove(key);
        }

        @Override
        public String evalValue(String script, List<String> keys, List<String> arguments) {
            failIfConfigured();
            throw new UnsupportedOperationException("not used by this test");
        }

        @Override
        public void close() {
        }

        private void failIfConfigured() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
