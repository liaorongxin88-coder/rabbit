package com.rabbit.app.modules.auth.service;

import com.rabbit.app.common.BizException;
import java.util.Locale;

public enum SmsVerificationPurpose {
    LOGIN,
    REGISTER,
    LOGIN_OR_REGISTER,
    RESET_PASSWORD,
    BIND_PHONE,
    VERIFY_CURRENT_PHONE;

    public static SmsVerificationPurpose fromApiValue(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim();
        if (normalized.isEmpty()) {
            return LOGIN_OR_REGISTER;
        }
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unsupported) {
            throw new BizException(400, "短信验证码用途不支持");
        }
    }

    public boolean supportsPhoneAuthentication() {
        return this == LOGIN || this == REGISTER || this == LOGIN_OR_REGISTER;
    }

    public String keySegment() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
