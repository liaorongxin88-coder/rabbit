package com.rabbit.app.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Keeps deterministic child request keys inside the varchar(64) schema limit. */
public final class RequestIdUtil {
    private static final int MAX_LENGTH = 64;

    private RequestIdUtil() {}

    public static String deriveChild(String requestId, Long childId) {
        if (requestId == null) {
            return null;
        }
        String candidate = requestId + "-" + childId;
        if (candidate.length() <= MAX_LENGTH) {
            return candidate;
        }
        return UUID.nameUUIDFromBytes(candidate.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
