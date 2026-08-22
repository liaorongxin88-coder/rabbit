package com.rabbit.app.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JsonApplicationCache implements ApplicationCache {
    private static final Logger log = LoggerFactory.getLogger(JsonApplicationCache.class);
    private static final long BACKEND_WARNING_INTERVAL_MILLIS = Duration.ofMinutes(1).toMillis();

    private final CacheBackend backend;
    private final ObjectMapper objectMapper;
    private final CacheProvider provider;
    private final String keyPrefix;
    private final AtomicLong lastBackendWarningMillis = new AtomicLong();

    JsonApplicationCache(
            CacheBackend backend,
            ObjectMapper objectMapper,
            CacheProvider provider,
            String keyPrefix
    ) {
        this.backend = backend;
        this.objectMapper = objectMapper;
        this.provider = provider;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public <T> Optional<T> get(CacheKey key, Class<T> valueType) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(valueType, "valueType");
        try {
            Optional<String> value = backend.get(physicalKey(key));
            if (value.isEmpty()) {
                return Optional.empty();
            }
            try {
                T decoded = objectMapper.readValue(value.get(), valueType);
                if (decoded == null) {
                    discardMalformedEntry(key);
                    return Optional.empty();
                }
                return Optional.of(decoded);
            } catch (JsonProcessingException malformed) {
                discardMalformedEntry(key);
                return Optional.empty();
            }
        } catch (CacheBackendException unavailable) {
            logBackendFailure("read", key, unavailable);
            return Optional.empty();
        }
    }

    @Override
    public void put(CacheKey key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        requirePositiveTtl(ttl);
        try {
            backend.put(physicalKey(key), objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException serializationFailure) {
            log.warn("Cache serialization failed open: namespace={}, type={}",
                    key.namespace(), value.getClass().getName());
        } catch (CacheBackendException unavailable) {
            logBackendFailure("write", key, unavailable);
        }
    }

    @Override
    public void evict(CacheKey key) {
        Objects.requireNonNull(key, "key");
        try {
            backend.evict(physicalKey(key));
        } catch (CacheBackendException unavailable) {
            logBackendFailure("eviction", key, unavailable);
        }
    }

    private String physicalKey(CacheKey key) {
        return keyPrefix + ":" + key.value();
    }

    private void discardMalformedEntry(CacheKey key) {
        log.warn("Discarding malformed cache entry: provider={}, namespace={}", provider, key.namespace());
        evict(key);
    }

    private void logBackendFailure(String operation, CacheKey key, CacheBackendException unavailable) {
        Throwable cause = unavailable.getCause() == null ? unavailable : unavailable.getCause();
        long now = System.currentTimeMillis();
        long previous = lastBackendWarningMillis.get();
        if (now - previous >= BACKEND_WARNING_INTERVAL_MILLIS
                && lastBackendWarningMillis.compareAndSet(previous, now)) {
            log.warn("Cache {} failed open: provider={}, namespace={}, error={}",
                    operation, provider, key.namespace(), cause.getClass().getSimpleName());
        } else {
            log.debug("Cache {} failed open: provider={}, namespace={}, error={}",
                    operation, provider, key.namespace(), cause.getClass().getSimpleName());
        }
    }

    private void requirePositiveTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("cache ttl must be positive");
        }
    }
}
