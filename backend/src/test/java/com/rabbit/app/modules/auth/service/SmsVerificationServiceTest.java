package com.rabbit.app.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.SmsCodeSendResponse;
import com.rabbit.app.modules.auth.entity.SmsVerificationCode;
import com.rabbit.app.modules.auth.mapper.SmsVerificationCodeMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.Test;

class SmsVerificationServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T08:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void sendsHashedCodeAndConsumesItOnce() {
        FakeMapper mapper = new FakeMapper();
        RecordingSender sender = new RecordingSender();
        SmsVerificationService service = service(mapper, sender);

        SmsCodeSendResponse response = service.sendCode("+86 138-0013-8000", "127.0.0.1");

        assertEquals("13800138000", sender.phone);
        assertEquals("123456", sender.code);
        assertEquals("SENT", mapper.item.getStatus());
        assertEquals(64, mapper.item.getPhoneHash().length());
        assertNotEquals("13800138000", mapper.item.getPhoneHash());
        assertEquals(64, mapper.item.getCodeHash().length());
        assertNotEquals("123456", mapper.item.getCodeHash());
        assertEquals(300, response.getExpiresInSeconds());
        assertEquals(60, response.getRetryAfterSeconds());

        assertEquals("13800138000", service.verifyCode("13800138000", "123456"));
        assertEquals("CONSUMED", mapper.item.getStatus());

        BizException reused = assertThrows(
                BizException.class,
                () -> service.verifyCode("13800138000", "123456")
        );
        assertEquals(400, reused.getCode());
    }

    @Test
    void recordsFailedAttemptsWithoutConsumingTheCode() {
        FakeMapper mapper = new FakeMapper();
        SmsVerificationService service = service(mapper, new RecordingSender());
        service.sendCode("13800138000", "127.0.0.1");

        BizException error = assertThrows(
                BizException.class,
                () -> service.verifyCode("13800138000", "000000")
        );

        assertEquals(400, error.getCode());
        assertEquals(1, mapper.item.getAttemptCount());
        assertEquals("SENT", mapper.item.getStatus());
    }

    @Test
    void blocksRapidResendsBeforeCallingAliyun() {
        FakeMapper mapper = new FakeMapper();
        mapper.recentPhoneCount = 1;
        RecordingSender sender = new RecordingSender();
        SmsVerificationService service = service(mapper, sender);

        BizException error = assertThrows(
                BizException.class,
                () -> service.sendCode("13800138000", "127.0.0.1")
        );

        assertEquals(429, error.getCode());
        assertEquals(0, sender.calls);
    }

    @Test
    void marksReservationFailedWhenProviderRejectsTheMessage() {
        FakeMapper mapper = new FakeMapper();
        SmsVerificationService service = service(mapper, (phone, code) -> {
            throw new BizException(502, "provider rejected");
        });

        BizException error = assertThrows(
                BizException.class,
                () -> service.sendCode("13800138000", "127.0.0.1")
        );

        assertEquals(502, error.getCode());
        assertEquals("FAILED", mapper.item.getStatus());
    }

    @Test
    void disabledSmsDoesNotRequireASecretAndFailsClosed() {
        FakeMapper mapper = new FakeMapper();
        SmsVerificationService service = new SmsVerificationService(
                mapper,
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
        assertEquals(0, mapper.recentPhoneCount);
    }

    private SmsVerificationService service(FakeMapper mapper, SmsSender sender) {
        return new SmsVerificationService(
                mapper,
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

    private static class FakeMapper implements SmsVerificationCodeMapper {
        SmsVerificationCode item;
        int recentPhoneCount;
        int recentIpCount;

        @Override
        public int insert(SmsVerificationCode item) {
            item.setId(1L);
            this.item = item;
            return 1;
        }

        @Override
        public int countRecentByPhone(String phone, String purpose, Date fromTime) {
            return recentPhoneCount;
        }

        @Override
        public int countRecentByIp(String requestIp, Date fromTime) {
            return recentIpCount;
        }

        @Override
        public SmsVerificationCode selectLatestActiveForUpdate(String phone, String purpose, Date now) {
            if (item == null || !"SENT".equals(item.getStatus()) || !item.getExpiresTime().after(now)) {
                return null;
            }
            return item;
        }

        @Override
        public int markSent(Long id) {
            item.setStatus("SENT");
            return 1;
        }

        @Override
        public int markFailed(Long id) {
            item.setStatus("FAILED");
            return 1;
        }

        @Override
        public int recordFailedAttempt(Long id, int maxAttempts) {
            int attempts = item.getAttemptCount() + 1;
            item.setAttemptCount(attempts);
            if (attempts >= maxAttempts) {
                item.setStatus("LOCKED");
            }
            return 1;
        }

        @Override
        public int markConsumed(Long id, Date consumedTime) {
            if (!"SENT".equals(item.getStatus())) {
                return 0;
            }
            item.setStatus("CONSUMED");
            item.setConsumedTime(consumedTime);
            return 1;
        }

        @Override
        public int deleteCreatedBefore(Date cutoff, int limit) {
            return 0;
        }
    }
}
