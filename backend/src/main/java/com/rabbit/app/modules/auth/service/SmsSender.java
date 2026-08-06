package com.rabbit.app.modules.auth.service;

public interface SmsSender {
    void sendVerificationCode(String phone, String code) throws Exception;
}
