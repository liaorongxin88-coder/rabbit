package com.rabbit.app.modules.auth.service;

import java.time.Duration;
import java.util.Objects;

/**
 * Atomic, fail-closed storage for security-sensitive SMS verification state.
 */
public interface SmsVerificationStore {
    ReserveResult reserve(Reservation reservation);

    ActivationResult activate(Reservation reservation);

    void cancel(Reservation reservation);

    VerificationResult verifyAndConsume(
            String phoneHash,
            SmsVerificationPurpose purpose,
            String submittedCodeHash,
            int maxAttempts
    );

    enum ReserveResult {
        RESERVED,
        RESEND_LIMIT,
        PHONE_HOUR_LIMIT,
        PHONE_DAY_LIMIT,
        IP_HOUR_LIMIT
    }

    enum ActivationResult {
        ACTIVATED,
        MISSING,
        STALE
    }

    enum VerificationResult {
        VERIFIED,
        MISSING,
        WRONG,
        LOCKED
    }

    record Reservation(
            String token,
            String phoneHash,
            String requestIpHash,
            SmsVerificationPurpose purpose,
            String codeHash,
            long issuedAtMillis,
            Duration ttl,
            Duration resendInterval,
            int phoneHourLimit,
            int phoneDayLimit,
            int ipHourLimit
    ) {
        public Reservation {
            requireText(token, "token");
            requireText(phoneHash, "phoneHash");
            requireText(requestIpHash, "requestIpHash");
            Objects.requireNonNull(purpose, "purpose");
            requireText(codeHash, "codeHash");
            Objects.requireNonNull(ttl, "ttl");
            Objects.requireNonNull(resendInterval, "resendInterval");
            if (issuedAtMillis <= 0 || ttl.isZero() || ttl.isNegative()
                    || resendInterval.isZero() || resendInterval.isNegative()
                    || phoneHourLimit <= 0 || phoneDayLimit < phoneHourLimit
                    || ipHourLimit <= 0) {
                throw new IllegalArgumentException("Invalid SMS verification reservation");
            }
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }
    }
}
