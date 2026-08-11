package com.rabbit.app.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.rabbit.app.modules.auth.service.SmsVerificationPurpose;
import com.rabbit.app.modules.auth.service.SmsVerificationStore;
import com.rabbit.app.modules.auth.infrastructure.cache.LettuceSmsVerificationStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LettuceSmsVerificationStoreIT {
    @Test
    void atomicallyActivatesIsolatesAndConsumesChallenges() {
        String host = System.getProperty("cache.it.host");
        assumeTrue(host != null && !host.isBlank(), "cache.it.host is not configured");

        CacheProperties properties = properties(host);
        String prefix = "rabbit:cache:it:" + UUID.randomUUID();
        try (LettuceCacheBackend backend = new LettuceCacheBackend(properties)) {
            SmsVerificationStore store = new LettuceSmsVerificationStore(backend, prefix);
            String phoneHash = hashLikeValue("phone");
            String ipHash = hashLikeValue("ip");
            SmsVerificationStore.Reservation login = reservation(
                    phoneHash,
                    ipHash,
                    SmsVerificationPurpose.LOGIN,
                    hashLikeValue("login-code")
            );
            SmsVerificationStore.Reservation reset = reservation(
                    phoneHash,
                    ipHash,
                    SmsVerificationPurpose.RESET_PASSWORD,
                    hashLikeValue("reset-code")
            );

            assertThat(store.reserve(login)).isEqualTo(SmsVerificationStore.ReserveResult.RESERVED);
            assertThat(store.reserve(reset)).isEqualTo(SmsVerificationStore.ReserveResult.RESERVED);
            assertThat(store.activate(login)).isEqualTo(SmsVerificationStore.ActivationResult.ACTIVATED);
            assertThat(store.activate(reset)).isEqualTo(SmsVerificationStore.ActivationResult.ACTIVATED);

            assertThat(store.verifyAndConsume(
                    phoneHash,
                    SmsVerificationPurpose.LOGIN,
                    reset.codeHash(),
                    5
            )).isEqualTo(SmsVerificationStore.VerificationResult.WRONG);
            assertThat(store.verifyAndConsume(
                    phoneHash,
                    SmsVerificationPurpose.LOGIN,
                    login.codeHash(),
                    5
            )).isEqualTo(SmsVerificationStore.VerificationResult.VERIFIED);
            assertThat(store.verifyAndConsume(
                    phoneHash,
                    SmsVerificationPurpose.LOGIN,
                    login.codeHash(),
                    5
            )).isEqualTo(SmsVerificationStore.VerificationResult.MISSING);
            assertThat(store.verifyAndConsume(
                    phoneHash,
                    SmsVerificationPurpose.RESET_PASSWORD,
                    reset.codeHash(),
                    5
            )).isEqualTo(SmsVerificationStore.VerificationResult.VERIFIED);
        }
    }

    @Test
    void enforcesResendWindowAndMaximumAttempts() {
        String host = System.getProperty("cache.it.host");
        assumeTrue(host != null && !host.isBlank(), "cache.it.host is not configured");

        CacheProperties properties = properties(host);
        String prefix = "rabbit:cache:it:" + UUID.randomUUID();
        try (LettuceCacheBackend backend = new LettuceCacheBackend(properties)) {
            SmsVerificationStore store = new LettuceSmsVerificationStore(backend, prefix);
            String phoneHash = hashLikeValue("phone");
            String ipHash = hashLikeValue("ip");
            SmsVerificationStore.Reservation first = reservation(
                    phoneHash,
                    ipHash,
                    SmsVerificationPurpose.REGISTER,
                    hashLikeValue("code")
            );
            SmsVerificationStore.Reservation duplicate = reservation(
                    phoneHash,
                    ipHash,
                    SmsVerificationPurpose.REGISTER,
                    hashLikeValue("other-code")
            );

            assertThat(store.reserve(first)).isEqualTo(SmsVerificationStore.ReserveResult.RESERVED);
            assertThat(store.reserve(duplicate)).isEqualTo(SmsVerificationStore.ReserveResult.RESEND_LIMIT);
            assertThat(store.activate(first)).isEqualTo(SmsVerificationStore.ActivationResult.ACTIVATED);
            assertThat(store.verifyAndConsume(
                    phoneHash,
                    SmsVerificationPurpose.REGISTER,
                    hashLikeValue("wrong-1"),
                    2
            )).isEqualTo(SmsVerificationStore.VerificationResult.WRONG);
            assertThat(store.verifyAndConsume(
                    phoneHash,
                    SmsVerificationPurpose.REGISTER,
                    hashLikeValue("wrong-2"),
                    2
            )).isEqualTo(SmsVerificationStore.VerificationResult.LOCKED);
            assertThat(store.verifyAndConsume(
                    phoneHash,
                    SmsVerificationPurpose.REGISTER,
                    first.codeHash(),
                    2
            )).isEqualTo(SmsVerificationStore.VerificationResult.MISSING);
        }
    }

    private CacheProperties properties(String host) {
        CacheProperties properties = new CacheProperties();
        properties.setHost(host);
        properties.setPort(Integer.getInteger("cache.it.port", 6379));
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setCommandTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private SmsVerificationStore.Reservation reservation(
            String phoneHash,
            String ipHash,
            SmsVerificationPurpose purpose,
            String codeHash
    ) {
        return new SmsVerificationStore.Reservation(
                UUID.randomUUID().toString(),
                phoneHash,
                ipHash,
                purpose,
                codeHash,
                System.currentTimeMillis(),
                Duration.ofSeconds(30),
                Duration.ofSeconds(10),
                5,
                10,
                20
        );
    }

    private String hashLikeValue(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "")
                + UUID.nameUUIDFromBytes((seed + "-2").getBytes(StandardCharsets.UTF_8))
                        .toString().replace("-", "");
    }
}
