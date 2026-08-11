package com.rabbit.app.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.SmsCodeSendResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SmsVerificationServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T08:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void sendsHashedCodeAndConsumesItOnce() {
        FakeStore store = new FakeStore();
        RecordingSender sender = new RecordingSender();
        SmsVerificationService service = service(store, sender);

        SmsCodeSendResponse response = service.sendCode(
                "+86 138-0013-8000",
                SmsVerificationPurpose.LOGIN,
                "127.0.0.1"
        );

        assertEquals("13800138000", sender.phone);
        assertEquals("123456", sender.code);
        assertEquals(64, store.lastReservation.phoneHash().length());
        assertNotEquals("13800138000", store.lastReservation.phoneHash());
        assertEquals(64, store.lastReservation.codeHash().length());
        assertNotEquals("123456", store.lastReservation.codeHash());
        assertEquals(64, store.lastReservation.requestIpHash().length());
        assertEquals(300, response.getExpiresInSeconds());
        assertEquals(60, response.getRetryAfterSeconds());

        assertEquals(
                "13800138000",
                service.verifyCode("13800138000", "123456", SmsVerificationPurpose.LOGIN)
        );
        BizException reused = assertThrows(
                BizException.class,
                () -> service.verifyCode("13800138000", "123456", SmsVerificationPurpose.LOGIN)
        );
        assertEquals(400, reused.getCode());
    }

    @Test
    void isolatesCodesByBusinessPurpose() {
        FakeStore store = new FakeStore();
        SmsVerificationService service = service(store, new RecordingSender());
        service.sendCode("13800138000", SmsVerificationPurpose.RESET_PASSWORD, "127.0.0.1");

        BizException wrongPurpose = assertThrows(
                BizException.class,
                () -> service.verifyCode("13800138000", "123456", SmsVerificationPurpose.LOGIN)
        );

        assertEquals(400, wrongPurpose.getCode());
        assertEquals(
                "13800138000",
                service.verifyCode("13800138000", "123456", SmsVerificationPurpose.RESET_PASSWORD)
        );
    }

    @Test
    void recordsFailedAttemptsAndLocksTheChallenge() {
        FakeStore store = new FakeStore();
        SmsVerificationService service = service(store, new RecordingSender());
        service.sendCode("13800138000", SmsVerificationPurpose.LOGIN, "127.0.0.1");

        for (int i = 0; i < 5; i++) {
            BizException error = assertThrows(
                    BizException.class,
                    () -> service.verifyCode("13800138000", "000000", SmsVerificationPurpose.LOGIN)
            );
            assertEquals(400, error.getCode());
        }

        BizException locked = assertThrows(
                BizException.class,
                () -> service.verifyCode("13800138000", "123456", SmsVerificationPurpose.LOGIN)
        );
        assertEquals(400, locked.getCode());
    }

    @Test
    void blocksRateLimitedRequestsBeforeCallingAliyun() {
        FakeStore store = new FakeStore();
        store.reserveResult = SmsVerificationStore.ReserveResult.RESEND_LIMIT;
        RecordingSender sender = new RecordingSender();
        SmsVerificationService service = service(store, sender);

        BizException error = assertThrows(
                BizException.class,
                () -> service.sendCode("13800138000", SmsVerificationPurpose.LOGIN, "127.0.0.1")
        );

        assertEquals(429, error.getCode());
        assertEquals(0, sender.calls);
    }

    @Test
    void cancelsReservationWhenProviderRejectsTheMessage() {
        FakeStore store = new FakeStore();
        SmsVerificationService service = service(store, (phone, code) -> {
            throw new BizException(502, "provider rejected");
        });

        BizException error = assertThrows(
                BizException.class,
                () -> service.sendCode("13800138000", SmsVerificationPurpose.LOGIN, "127.0.0.1")
        );

        assertEquals(502, error.getCode());
        assertEquals(true, store.cancelled);
    }

    @Test
    void failsClosedWhenCacheIsUnavailable() {
        FakeStore store = new FakeStore();
        store.unavailable = true;
        SmsVerificationService service = service(store, new RecordingSender());

        BizException sendError = assertThrows(
                BizException.class,
                () -> service.sendCode("13800138000", SmsVerificationPurpose.LOGIN, "127.0.0.1")
        );
        BizException verifyError = assertThrows(
                BizException.class,
                () -> service.verifyCode("13800138000", "123456", SmsVerificationPurpose.LOGIN)
        );

        assertEquals(503, sendError.getCode());
        assertEquals(503, verifyError.getCode());
    }

    @Test
    void disabledSmsDoesNotRequireASecretAndFailsClosed() {
        FakeStore store = new FakeStore();
        SmsVerificationService service = new SmsVerificationService(
                store,
                new RecordingSender(),
                new PhoneIdentityService("test-phone-secret-with-enough-entropy"),
                false,
                "",
                6,
                300,
                60,
                5,
                5,
                10,
                20,
                CLOCK,
                () -> "123456"
        );

        BizException sendError = assertThrows(
                BizException.class,
                () -> service.sendCode("13800138000", "127.0.0.1")
        );
        BizException loginError = assertThrows(
                BizException.class,
                () -> service.verifyCode("13800138000", "123456")
        );

        assertEquals(503, sendError.getCode());
        assertEquals(503, loginError.getCode());
        assertEquals(null, store.lastReservation);
    }

    private SmsVerificationService service(FakeStore store, SmsSender sender) {
        return new SmsVerificationService(
                store,
                sender,
                new PhoneIdentityService("test-phone-secret-with-enough-entropy"),
                true,
                "test-sms-secret-with-enough-entropy",
                6,
                300,
                60,
                5,
                5,
                10,
                20,
                CLOCK,
                () -> "123456"
        );
    }

    private static class RecordingSender implements SmsSender {
        int calls;
        String phone;
        String code;

        @Override
        public void sendVerificationCode(String phone, String code) {
            calls++;
            this.phone = phone;
            this.code = code;
        }
    }

    private static class FakeStore implements SmsVerificationStore {
        private final Map<String, ActiveChallenge> active = new HashMap<>();
        private ReserveResult reserveResult = ReserveResult.RESERVED;
        private Reservation lastReservation;
        private boolean cancelled;
        private boolean unavailable;

        @Override
        public ReserveResult reserve(Reservation reservation) {
            requireAvailable();
            lastReservation = reservation;
            return reserveResult;
        }

        @Override
        public ActivationResult activate(Reservation reservation) {
            requireAvailable();
            active.put(key(reservation.phoneHash(), reservation.purpose()),
                    new ActiveChallenge(reservation.codeHash(), 0));
            return ActivationResult.ACTIVATED;
        }

        @Override
        public void cancel(Reservation reservation) {
            requireAvailable();
            cancelled = true;
        }

        @Override
        public VerificationResult verifyAndConsume(
                String phoneHash,
                SmsVerificationPurpose purpose,
                String submittedCodeHash,
                int maxAttempts
        ) {
            requireAvailable();
            String key = key(phoneHash, purpose);
            ActiveChallenge challenge = active.get(key);
            if (challenge == null) {
                return VerificationResult.MISSING;
            }
            if (!challenge.codeHash.equals(submittedCodeHash)) {
                int attempts = challenge.attempts + 1;
                if (attempts >= maxAttempts) {
                    active.remove(key);
                    return VerificationResult.LOCKED;
                }
                active.put(key, new ActiveChallenge(challenge.codeHash, attempts));
                return VerificationResult.WRONG;
            }
            active.remove(key);
            return VerificationResult.VERIFIED;
        }

        private void requireAvailable() {
            if (unavailable) {
                throw new SmsVerificationStoreUnavailableException("offline");
            }
        }

        private static String key(String phoneHash, SmsVerificationPurpose purpose) {
            return purpose.name() + ":" + phoneHash;
        }
    }

    private record ActiveChallenge(String codeHash, int attempts) {
    }
}
