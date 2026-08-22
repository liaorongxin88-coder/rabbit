package com.rabbit.app.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LettuceCacheBackendIT {
    @Test
    void roundTripsAgainstRedisCompatibleServer() {
        String host = System.getProperty("cache.it.host");
        assumeTrue(host != null && !host.isBlank(), "cache.it.host is not configured");

        CacheProperties properties = new CacheProperties();
        properties.setHost(host);
        properties.setPort(Integer.getInteger("cache.it.port", 6379));
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setCommandTimeout(Duration.ofSeconds(2));

        String key = "rabbit:cache:it:" + UUID.randomUUID();
        try (LettuceCacheBackend backend = new LettuceCacheBackend(properties)) {
            assertThat(backend.ping()).isEqualTo("PONG");
            assertThat(backend.get(key)).isEmpty();

            backend.put(key, "works", Duration.ofSeconds(30));
            assertThat(backend.get(key)).contains("works");

            backend.evict(key);
            assertThat(backend.get(key)).isEmpty();
        }
    }
}
