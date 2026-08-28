package com.rabbit.app.modules.auth.service;

import java.time.Duration;

/**
 * Atomic, fail-closed storage for pre-login image captcha challenges.
 */
public interface ImageCaptchaStore {
    void issue(String captchaId, String codeHash, Duration ttl);

    VerificationResult verifyAndConsume(String captchaId, String submittedCodeHash, int maxAttempts);

    enum VerificationResult {
        VERIFIED,
        MISSING,
        WRONG,
        LOCKED
    }
}
