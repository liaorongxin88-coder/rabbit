package com.rabbit.app.cache;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record CacheKey(String namespace, List<String> segments) {
    private static final Pattern NAMESPACE = Pattern.compile("^[a-z][a-z0-9-]{0,63}$");
    private static final int MAX_SEGMENT_LENGTH = 256;

    public CacheKey {
        if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("cache namespace must match " + NAMESPACE.pattern());
        }
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("cache key requires at least one segment");
        }
        segments = List.copyOf(segments);
        for (String segment : segments) {
            if (segment == null || segment.isBlank() || segment.length() > MAX_SEGMENT_LENGTH) {
                throw new IllegalArgumentException("cache key segment is blank or too long");
            }
        }
    }

    public static CacheKey of(String namespace, Object... segments) {
        Objects.requireNonNull(segments, "segments");
        return new CacheKey(
                namespace,
                Arrays.stream(segments)
                        .map(segment -> Objects.requireNonNull(segment, "cache key segment").toString())
                        .toList()
        );
    }

    public String value() {
        return namespace + ":" + segments.stream()
                .map(CacheKey::encode)
                .reduce((left, right) -> left + ":" + right)
                .orElseThrow();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
