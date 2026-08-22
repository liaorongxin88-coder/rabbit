package com.rabbit.app.modules.auth.infrastructure.cache;

import com.rabbit.app.cache.CacheBackend;
import com.rabbit.app.modules.auth.service.SmsVerificationPurpose;
import com.rabbit.app.modules.auth.service.SmsVerificationStore;
import com.rabbit.app.modules.auth.service.SmsVerificationStoreUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class LettuceSmsVerificationStore implements SmsVerificationStore {
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);
    private static final String RESERVE_SCRIPT = loadScript("reserve.lua");
    private static final String ACTIVATE_SCRIPT = loadScript("activate.lua");
    private static final String CANCEL_SCRIPT = loadScript("cancel.lua");
    private static final String VERIFY_SCRIPT = loadScript("verify-and-consume.lua");

    private final CacheBackend backend;
    private final String keyPrefix;

    public LettuceSmsVerificationStore(CacheBackend backend, String keyPrefix) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix") + ":sms";
    }

    @Override
    public ReserveResult reserve(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        String result = eval(
                RESERVE_SCRIPT,
                List.of(
                        pendingKey(reservation),
                        resendKey(reservation.phoneHash(), reservation.purpose()),
                        phoneRateKey(reservation.phoneHash(), reservation.purpose()),
                        ipRateKey(reservation.requestIpHash())
                ),
                List.of(
                        reservation.token(),
                        reservation.codeHash(),
                        Long.toString(reservation.issuedAtMillis()),
                        Long.toString(reservation.ttl().toMillis()),
                        Long.toString(reservation.resendInterval().toMillis()),
                        Integer.toString(reservation.phoneHourLimit()),
                        Integer.toString(reservation.phoneDayLimit()),
                        Integer.toString(reservation.ipHourLimit()),
                        Long.toString(HOUR.toMillis()),
                        Long.toString(DAY.toMillis())
                )
        );
        return enumValue(ReserveResult.class, result);
    }

    @Override
    public ActivationResult activate(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        String result = eval(
                ACTIVATE_SCRIPT,
                List.of(
                        pendingKey(reservation),
                        challengeKey(reservation.phoneHash(), reservation.purpose())
                ),
                List.of(reservation.token())
        );
        return enumValue(ActivationResult.class, result);
    }

    @Override
    public void cancel(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation");
        eval(
                CANCEL_SCRIPT,
                List.of(
                        pendingKey(reservation),
                        phoneRateKey(reservation.phoneHash(), reservation.purpose()),
                        ipRateKey(reservation.requestIpHash())
                ),
                List.of(reservation.token())
        );
    }

    @Override
    public VerificationResult verifyAndConsume(
            String phoneHash,
            SmsVerificationPurpose purpose,
            String submittedCodeHash,
            int maxAttempts
    ) {
        Objects.requireNonNull(phoneHash, "phoneHash");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(submittedCodeHash, "submittedCodeHash");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        String result = eval(
                VERIFY_SCRIPT,
                List.of(challengeKey(phoneHash, purpose)),
                List.of(submittedCodeHash, Integer.toString(maxAttempts))
        );
        return enumValue(VerificationResult.class, result);
    }

    private String eval(String script, List<String> keys, List<String> arguments) {
        String result;
        try {
            result = backend.evalValue(script, keys, arguments);
        } catch (RuntimeException unavailable) {
            throw new SmsVerificationStoreUnavailableException("SMS cache command failed", unavailable);
        }
        if (result == null || result.isBlank()) {
            throw new SmsVerificationStoreUnavailableException("SMS cache script returned no result");
        }
        return result;
    }

    private String pendingKey(Reservation reservation) {
        return keyPrefix + ":pending:" + reservation.purpose().keySegment()
                + ":" + reservation.phoneHash() + ":" + reservation.token();
    }

    private String challengeKey(String phoneHash, SmsVerificationPurpose purpose) {
        return keyPrefix + ":challenge:" + purpose.keySegment() + ":" + phoneHash;
    }

    private String resendKey(String phoneHash, SmsVerificationPurpose purpose) {
        return keyPrefix + ":resend:" + purpose.keySegment() + ":" + phoneHash;
    }

    private String phoneRateKey(String phoneHash, SmsVerificationPurpose purpose) {
        return keyPrefix + ":rate:phone:" + purpose.keySegment() + ":" + phoneHash;
    }

    private String ipRateKey(String requestIpHash) {
        return keyPrefix + ":rate:ip:" + requestIpHash;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException unknownResult) {
            throw new SmsVerificationStoreUnavailableException(
                    "SMS cache script returned an unknown result: " + value,
                    unknownResult
            );
        }
    }

    private static String loadScript(String name) {
        String path = "/cache/sms/" + name;
        try (InputStream stream = LettuceSmsVerificationStore.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing cache script " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load cache script " + path, error);
        }
    }
}
