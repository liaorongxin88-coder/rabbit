package com.rabbit.app.modules.auth.service;

public interface PhoneOneTapProvider {
    String providerId();

    String resolvePhone(String accessToken, String requestId);
}
