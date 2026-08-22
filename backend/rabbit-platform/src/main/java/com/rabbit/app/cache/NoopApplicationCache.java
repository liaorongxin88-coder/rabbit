package com.rabbit.app.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

final class NoopApplicationCache implements ApplicationCache {
    @Override
    public <T> Optional<T> get(CacheKey key, Class<T> valueType) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(valueType, "valueType");
        return Optional.empty();
    }

    @Override
    public void put(CacheKey key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("cache ttl must be positive");
        }
    }

    @Override
    public void evict(CacheKey key) {
        Objects.requireNonNull(key, "key");
    }
}
