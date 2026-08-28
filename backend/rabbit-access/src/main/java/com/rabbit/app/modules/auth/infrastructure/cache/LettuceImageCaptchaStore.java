package com.rabbit.app.modules.auth.infrastructure.cache;

import com.rabbit.app.cache.CacheBackend;
import com.rabbit.app.modules.auth.service.ImageCaptchaStore;
import com.rabbit.app.modules.auth.service.ImageCaptchaStoreUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class LettuceImageCaptchaStore implements ImageCaptchaStore {
    private static final String ISSUE_SCRIPT = loadScript("issue.lua");
    private static final String VERIFY_SCRIPT = loadScript("verify-and-consume.lua");

    private final CacheBackend backend;
    private final String keyPrefix;

    public LettuceImageCaptchaStore(CacheBackend backend, String keyPrefix) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix") + ":captcha";
    }

    @Override
    public void issue(String captchaId, String codeHash, Duration ttl) {
        Objects.requireNonNull(captchaId, "captchaId");
        Objects.requireNonNull(codeHash, "codeHash");
        Objects.requireNonNull(ttl, "ttl");
        String result = eval(
                ISSUE_SCRIPT,
                List.of(challengeKey(captchaId)),
                List.of(codeHash, Long.toString(ttl.toMillis()))
        );
        if (!"ISSUED".equals(result)) {
            throw new ImageCaptchaStoreUnavailableException("Captcha cache script returned an unknown result: " + result);
        }
    }

    @Override
    public VerificationResult verifyAndConsume(String captchaId, String submittedCodeHash, int maxAttempts) {
        Objects.requireNonNull(captchaId, "captchaId");
        Objects.requireNonNull(submittedCodeHash, "submittedCodeHash");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        return enumValue(eval(
                VERIFY_SCRIPT,
                List.of(challengeKey(captchaId)),
                List.of(submittedCodeHash, Integer.toString(maxAttempts))
        ));
    }

    private String challengeKey(String captchaId) {
        return keyPrefix + ":challenge:" + captchaId;
    }

    private String eval(String script, List<String> keys, List<String> arguments) {
        String result;
        try {
            result = backend.evalValue(script, keys, arguments);
        } catch (RuntimeException unavailable) {
            throw new ImageCaptchaStoreUnavailableException("Captcha cache command failed", unavailable);
        }
        if (result == null || result.isBlank()) {
            throw new ImageCaptchaStoreUnavailableException("Captcha cache script returned no result");
        }
        return result;
    }

    private static VerificationResult enumValue(String value) {
        try {
            return VerificationResult.valueOf(value);
        } catch (IllegalArgumentException unknownResult) {
            throw new ImageCaptchaStoreUnavailableException(
                    "Captcha cache script returned an unknown result: " + value,
                    unknownResult
            );
        }
    }

    private static String loadScript(String name) {
        String path = "/cache/captcha/" + name;
        try (InputStream stream = LettuceImageCaptchaStore.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing cache script " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load cache script " + path, error);
        }
    }
}
