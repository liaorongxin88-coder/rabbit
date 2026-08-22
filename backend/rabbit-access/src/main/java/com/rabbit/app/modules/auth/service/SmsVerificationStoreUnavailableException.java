package com.rabbit.app.modules.auth.service;

public class SmsVerificationStoreUnavailableException extends RuntimeException {
    public SmsVerificationStoreUnavailableException(String message) {
        super(message);
    }

    public SmsVerificationStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
