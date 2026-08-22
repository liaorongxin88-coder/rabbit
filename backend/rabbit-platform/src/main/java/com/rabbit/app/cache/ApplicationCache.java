package com.rabbit.app.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Best-effort cache for data that can always be rebuilt from an authoritative source.
 */
public interface ApplicationCache {
    <T> Optional<T> get(CacheKey key, Class<T> valueType);

    void put(CacheKey key, Object value, Duration ttl);

    void evict(CacheKey key);

    default <T> T getOrLoad(
            CacheKey key,
            Class<T> valueType,
            Duration ttl,
            Supplier<T> loader
    ) {
        return get(key, valueType).orElseGet(() -> {
            T value = Objects.requireNonNull(loader.get(), "cache loader returned null");
            put(key, value, ttl);
            return value;
        });
    }
}
