package com.rabbit.app.modules.auth.service;

public class PhoneOneTapProviderException extends RuntimeException {
    public enum Reason {
        DISABLED,
        MISCONFIGURED,
        REJECTED,
        UNAVAILABLE
    }

    private final Reason reason;

    public PhoneOneTapProviderException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
