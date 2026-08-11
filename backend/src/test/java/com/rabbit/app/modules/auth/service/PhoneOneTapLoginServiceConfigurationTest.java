package com.rabbit.app.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;

class PhoneOneTapLoginServiceConfigurationTest {
    private static final String STRONG_SECRET = "one-tap-test-secret-with-at-least-32-characters";

    @Test
    void disabledServiceStartsWithoutProviderConfiguration() {
        assertDoesNotThrow(() -> new PhoneOneTapLoginService(
                List.of(),
                mock(PhoneOneTapAttemptService.class),
                false,
                "",
                "",
                0,
                0,
                0
        ));
    }

    @Test
    void enabledServiceRejectsWeakHashSecretsAndInvalidProviderWhitelists() {
        IllegalArgumentException weakSecret = assertThrows(
                IllegalArgumentException.class,
                () -> service("aliyun", "short")
        );
        IllegalArgumentException blankSecret = assertThrows(
                IllegalArgumentException.class,
                () -> service("aliyun", " ".repeat(32))
        );
        IllegalArgumentException blankProviders = assertThrows(
                IllegalArgumentException.class,
                () -> service("", STRONG_SECRET)
        );
        IllegalArgumentException unknownProvider = assertThrows(
                IllegalArgumentException.class,
                () -> service("untrusted", STRONG_SECRET)
        );

        assertEquals("一键登录凭证摘要密钥至少需要32个字符", weakSecret.getMessage());
        assertEquals("一键登录凭证摘要密钥至少需要32个字符", blankSecret.getMessage());
        assertEquals("一键登录服务商白名单配置不正确", blankProviders.getMessage());
        assertEquals("一键登录服务商白名单配置不正确", unknownProvider.getMessage());
    }

    @Test
    void enabledServiceRequiresLeaseToExceedProviderTimeoutsAndSafetyMargin() {
        IllegalArgumentException equalToMinimum = assertThrows(
                IllegalArgumentException.class,
                () -> service("aliyun", STRONG_SECRET, 6, 2000, 3000)
        );

        assertEquals(
                "一键登录处理租约必须大于供应商总超时加1000毫秒",
                equalToMinimum.getMessage()
        );
        assertDoesNotThrow(() -> service("aliyun", STRONG_SECRET, 15, 2000, 3000));
    }

    private PhoneOneTapLoginService service(String allowedProviders, String secret) {
        return service(allowedProviders, secret, 15, 2000, 3000);
    }

    private PhoneOneTapLoginService service(
            String allowedProviders,
            String secret,
            int processingLeaseSeconds,
            int connectTimeoutMs,
            int readTimeoutMs
    ) {
        return new PhoneOneTapLoginService(
                List.of(),
                mock(PhoneOneTapAttemptService.class),
                true,
                allowedProviders,
                secret,
                processingLeaseSeconds,
                connectTimeoutMs,
                readTimeoutMs
        );
    }
}
