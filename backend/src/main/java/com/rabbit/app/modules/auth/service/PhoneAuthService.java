package com.rabbit.app.modules.auth.service;

import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhoneAuthService {
    private final SmsVerificationService smsVerificationService;
    private final AuthService authService;

    public PhoneAuthService(SmsVerificationService smsVerificationService, AuthService authService) {
        this.smsVerificationService = smsVerificationService;
        this.authService = authService;
    }

    @Transactional
    public AuthTokenResponse loginOrRegister(String phone, String code) {
        String verifiedPhone = smsVerificationService.verifyCode(phone, code);
        return authService.loginOrRegisterPhone(verifiedPhone);
    }
}
