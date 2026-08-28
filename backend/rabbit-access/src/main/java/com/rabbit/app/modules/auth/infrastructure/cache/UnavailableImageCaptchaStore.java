package com.rabbit.app.modules.auth.infrastructure.cache;

import com.rabbit.app.modules.auth.service.ImageCaptchaStore;
import com.rabbit.app.modules.auth.service.ImageCaptchaStoreUnavailableException;
import java.time.Duration;

public final class UnavailableImageCaptchaStore implements ImageCaptchaStore {
    private static final String MESSAGE = "Image captcha requires Redis or Valkey";

    @Override
    public void issue(String captchaId, String codeHash, Duration ttl) {
        throw unavailable();
    }

    @Override
    public VerificationResult verifyAndConsume(String captchaId, String submittedCodeHash, int maxAttempts) {
        throw unavailable();
    }

    private ImageCaptchaStoreUnavailableException unavailable() {
        return new ImageCaptchaStoreUnavailableException(MESSAGE);
    }
}
