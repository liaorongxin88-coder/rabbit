package com.rabbit.app.modules.auth.infrastructure.cache;

import com.rabbit.app.modules.auth.service.SmsVerificationPurpose;
import com.rabbit.app.modules.auth.service.SmsVerificationStore;
import com.rabbit.app.modules.auth.service.SmsVerificationStoreUnavailableException;

public final class UnavailableSmsVerificationStore implements SmsVerificationStore {
    private static final String MESSAGE = "SMS verification requires Redis or Valkey";

    @Override
    public ReserveResult reserve(Reservation reservation) {
        throw unavailable();
    }

    @Override
    public ActivationResult activate(Reservation reservation) {
        throw unavailable();
    }

    @Override
    public void cancel(Reservation reservation) {
        throw unavailable();
    }

    @Override
    public VerificationResult verifyAndConsume(
            String phoneHash,
            SmsVerificationPurpose purpose,
            String submittedCodeHash,
            int maxAttempts
    ) {
        throw unavailable();
    }

    private SmsVerificationStoreUnavailableException unavailable() {
        return new SmsVerificationStoreUnavailableException(MESSAGE);
    }
}
