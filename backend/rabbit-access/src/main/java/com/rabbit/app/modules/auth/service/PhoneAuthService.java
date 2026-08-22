package com.rabbit.app.modules.auth.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.auth.dto.AuthTokenResponse;
import com.rabbit.app.modules.auth.dto.UserProfileResponse;
import com.rabbit.app.modules.auth.support.PhoneNumbers;
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
        return authenticate(phone, code, SmsVerificationPurpose.LOGIN_OR_REGISTER);
    }

    @Transactional
    public AuthTokenResponse authenticate(
            String phone,
            String code,
            SmsVerificationPurpose purpose
    ) {
        if (!purpose.supportsPhoneAuthentication()) {
            throw new BizException(400, "该验证码用途不能用于手机号登录");
        }
        String verifiedPhone = smsVerificationService.verifyCode(phone, code, purpose);
        return switch (purpose) {
            case LOGIN -> authService.loginPhone(verifiedPhone);
            case REGISTER -> authService.registerPhone(verifiedPhone);
            case LOGIN_OR_REGISTER -> authService.loginOrRegisterPhone(verifiedPhone);
            case RESET_PASSWORD, BIND_PHONE, VERIFY_CURRENT_PHONE ->
                    throw new IllegalStateException("unsupported authentication purpose");
        };
    }

    @Transactional
    public void resetPassword(String phone, String code, String newPassword) {
        String verifiedPhone = smsVerificationService.verifyCode(
                phone,
                code,
                SmsVerificationPurpose.RESET_PASSWORD
        );
        authService.resetPasswordByPhone(verifiedPhone, newPassword);
    }

    public UserProfileResponse updatePhone(
            Long userId,
            String phone,
            String code,
            String currentPassword,
            String currentPhone,
            String currentPhoneCode
    ) {
        String normalizedPhone = PhoneNumbers.normalizeMainlandMobile(phone);
        authService.ensurePhoneAvailable(userId, normalizedPhone);
        AuthService.PhoneChangeAuthorization authorization = authService.authorizePhoneChange(
                userId,
                currentPassword,
                currentPhone
        );
        if (authorization.currentPhoneCodeRequired()) {
            if (currentPhoneCode == null || currentPhoneCode.isBlank()) {
                throw new BizException(400, "原手机号验证码不能为空");
            }
            smsVerificationService.verifyCode(
                    authorization.normalizedCurrentPhone(),
                    currentPhoneCode,
                    SmsVerificationPurpose.VERIFY_CURRENT_PHONE
            );
        }
        String verifiedPhone = smsVerificationService.verifyCode(
                normalizedPhone,
                code,
                SmsVerificationPurpose.BIND_PHONE
        );
        return authService.bindPhone(userId, verifiedPhone, authorization.expectedPhoneHash());
    }
}
