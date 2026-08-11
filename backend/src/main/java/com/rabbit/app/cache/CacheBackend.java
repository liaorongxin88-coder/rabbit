package com.rabbit.app.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface CacheBackend extends AutoCloseable {
    Optional<String> get(String key);

    void put(String key, String value, Duration ttl);

    void evict(String key);

    String evalValue(String script, List<String> keys, List<String> arguments);

    @Override
    void close();
}
