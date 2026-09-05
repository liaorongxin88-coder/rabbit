package com.rabbit.app.modules.batch.support;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class BatchWritePayloadHasher {
    private BatchWritePayloadHasher() {}

    public static String decimal(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

    public static String text(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    public static String sha256(String canonicalPayload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                value.append(String.format("%02x", current));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
