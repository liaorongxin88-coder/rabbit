package com.rabbit.app.modules.auth.dto;

public class SmsCodeSendResponse {
    private int expiresInSeconds;
    private int retryAfterSeconds;

    public SmsCodeSendResponse() {
    }

    public SmsCodeSendResponse(int expiresInSeconds, int retryAfterSeconds) {
        this.expiresInSeconds = expiresInSeconds;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(int expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(int retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
