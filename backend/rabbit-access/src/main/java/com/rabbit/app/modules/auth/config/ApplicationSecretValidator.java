package com.rabbit.app.modules.auth.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApplicationSecretValidator {
    private static final int MINIMUM_SECRET_BYTES = 32;

    public ApplicationSecretValidator(
            @Value("${app.jwt.secret:}") String jwtSecret,
            @Value("${app.admin.jwt.secret:}") String adminJwtSecret,
            @Value("${app.auth.phone-hash-secret:}") String phoneHashSecret,
            @Value("${app.sms.enabled:false}") boolean smsEnabled,
            @Value("${app.sms.code-secret:}") String smsCodeSecret,
            @Value("${app.cache.provider:none}") String cacheProvider
    ) {
        requireConfigured("APP_JWT_SECRET", jwtSecret);
        requireConfigured("APP_ADMIN_JWT_SECRET", adminJwtSecret);
        requireDistinctJwtSecrets(jwtSecret, adminJwtSecret);
        requireConfigured("APP_PHONE_HASH_SECRET", phoneHashSecret);
        if (smsEnabled) {
            requireConfigured("APP_SMS_CODE_SECRET", smsCodeSecret);
            requireSmsCache(cacheProvider);
        }
    }

    public static void requireConfigured(String variableName, String secret) {
        if (secret == null || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES
                || isPlaceholder(secret)) {
            throw new IllegalArgumentException(
                    variableName + " must be a non-placeholder secret of at least 32 bytes"
            );
        }
    }

    public static void requireDistinctJwtSecrets(String jwtSecret, String adminJwtSecret) {
        if (jwtSecret != null && jwtSecret.equals(adminJwtSecret)) {
            throw new IllegalArgumentException(
                    "APP_ADMIN_JWT_SECRET must be different from APP_JWT_SECRET"
            );
        }
    }

    private static void requireSmsCache(String cacheProvider) {
        String normalized = cacheProvider == null ? "" : cacheProvider.trim().toLowerCase(Locale.ROOT);
        if (!"redis".equals(normalized) && !"valkey".equals(normalized)) {
            throw new IllegalArgumentException(
                    "APP_CACHE_PROVIDER must be redis or valkey when APP_SMS_ENABLED=true"
            );
        }
    }

    private static boolean isPlaceholder(String secret) {
        String normalized = secret.toLowerCase(Locale.ROOT);
        return normalized.contains("change-me")
                || normalized.contains("changeme")
                || normalized.contains("replace-me")
                || normalized.contains("placeholder");
    }
}
