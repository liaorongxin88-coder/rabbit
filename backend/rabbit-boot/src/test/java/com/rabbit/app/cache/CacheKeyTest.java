package com.rabbit.app.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CacheKeyTest {
    @Test
    void encodesSegmentsWithoutChangingTheirBoundaries() {
        CacheKey key = CacheKey.of("dashboard-summary", "house:1", "2026 Q1", "100%");

        assertThat(key.value())
                .isEqualTo("dashboard-summary:house%3A1:2026%20Q1:100%25");
        assertThat(key.segments()).containsExactly("house:1", "2026 Q1", "100%");
    }

    @Test
    void defensivelyCopiesSegments() {
        List<String> segments = new java.util.ArrayList<>(List.of("house-1"));

        CacheKey key = new CacheKey("summary", segments);
        segments.set(0, "house-2");

        assertThat(key.segments()).containsExactly("house-1");
    }

    @Test
    void rejectsInvalidNamespacesAndSegments() {
        assertThatThrownBy(() -> CacheKey.of("Dashboard", "house-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CacheKey.of("dashboard"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CacheKey.of("dashboard", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
