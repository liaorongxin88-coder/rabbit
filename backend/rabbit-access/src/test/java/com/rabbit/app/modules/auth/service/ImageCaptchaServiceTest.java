package com.rabbit.app.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.ImageCaptchaResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageCaptchaServiceTest {
    private static final String SECRET = "test-captcha-secret-with-enough-entropy";

    @Test
    void issuesAnImageAndConsumesTheMatchingCodeOnce() {
        FakeStore store = new FakeStore();
        ImageCaptchaService service = service(store);

        ImageCaptchaResponse captcha = service.issue();

        assertTrue(captcha.captchaId().matches("[0-9a-f]{32}"));
        assertFalse(captcha.imageBase64().isBlank());
        assertEquals(300, captcha.expiresInSeconds());
        assertEquals(64, store.lastCodeHash.length());

        service.verifyAndConsume(captcha.captchaId(), "abcd");
        BizException reused = assertThrows(
                BizException.class,
                () -> service.verifyAndConsume(captcha.captchaId(), "ABCD")
        );
        assertEquals(400, reused.getCode());
    }

    @Test
    void wrongCodesLockTheChallengeAfterFiveAttempts() {
        FakeStore store = new FakeStore();
        ImageCaptchaService service = service(store);
        ImageCaptchaResponse captcha = service.issue();

        for (int attempt = 0; attempt < 5; attempt++) {
            BizException error = assertThrows(
                    BizException.class,
                    () -> service.verifyAndConsume(captcha.captchaId(), "WXYZ")
            );
            assertEquals(400, error.getCode());
        }

        BizException locked = assertThrows(
                BizException.class,
                () -> service.verifyAndConsume(captcha.captchaId(), "ABCD")
        );
        assertEquals(400, locked.getCode());
    }

    @Test
    void rejectsMalformedOrExpiredChallengeIds() {
        ImageCaptchaService service = service(new FakeStore());

        BizException malformed = assertThrows(
                BizException.class,
                () -> service.verifyAndConsume("invalid", "ABCD")
        );
        assertEquals(400, malformed.getCode());
    }

    @Test
    void failsClosedWhenTheCacheIsUnavailable() {
        FakeStore store = new FakeStore();
        store.unavailable = true;
        ImageCaptchaService service = service(store);

        BizException issueError = assertThrows(BizException.class, service::issue);
        BizException verifyError = assertThrows(
                BizException.class,
                () -> service.verifyAndConsume("0".repeat(32), "ABCD")
        );
        assertEquals(503, issueError.getCode());
        assertEquals(503, verifyError.getCode());
    }

    @Test
    void disabledCaptchaDoesNotRequireASecretOrBlockLegacyE2eLogin() {
        ImageCaptchaService service = new ImageCaptchaService(
                new FakeStore(), false, "", 4, 300, 5, () -> "ABCD"
        );

        service.verifyAndConsume(null, null);
    }

    private ImageCaptchaService service(FakeStore store) {
        return new ImageCaptchaService(store, true, SECRET, 4, 300, 5, () -> "ABCD");
    }

    private static final class FakeStore implements ImageCaptchaStore {
        private final Map<String, Challenge> challenges = new HashMap<>();
        private String lastCodeHash = "";
        private boolean unavailable;

        @Override
        public void issue(String captchaId, String codeHash, Duration ttl) {
            requireAvailable();
            lastCodeHash = codeHash;
            challenges.put(captchaId, new Challenge(codeHash, 0));
        }

        @Override
        public VerificationResult verifyAndConsume(String captchaId, String submittedCodeHash, int maxAttempts) {
            requireAvailable();
            Challenge challenge = challenges.get(captchaId);
            if (challenge == null) {
                return VerificationResult.MISSING;
            }
            if (!challenge.codeHash.equals(submittedCodeHash)) {
                int attempts = challenge.attempts + 1;
                if (attempts >= maxAttempts) {
                    challenges.remove(captchaId);
                    return VerificationResult.LOCKED;
                }
                challenges.put(captchaId, new Challenge(challenge.codeHash, attempts));
                return VerificationResult.WRONG;
            }
            challenges.remove(captchaId);
            return VerificationResult.VERIFIED;
        }

        private void requireAvailable() {
            if (unavailable) {
                throw new ImageCaptchaStoreUnavailableException("offline");
            }
        }
    }

    private record Challenge(String codeHash, int attempts) {
    }
}
