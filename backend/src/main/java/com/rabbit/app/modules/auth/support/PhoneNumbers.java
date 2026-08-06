package com.rabbit.app.modules.auth.support;

import com.rabbit.app.common.BizException;
import java.util.regex.Pattern;

public final class PhoneNumbers {
    private static final Pattern MAINLAND_MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    private PhoneNumbers() {
    }

    public static String normalizeMainlandMobile(String value) {
        String normalized = value == null ? "" : value.trim().replace(" ", "").replace("-", "");
        if (normalized.startsWith("+86")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("0086")) {
            normalized = normalized.substring(4);
        }
        if (!MAINLAND_MOBILE.matcher(normalized).matches()) {
            throw new BizException(400, "请输入有效手机号");
        }
        return normalized;
    }
}
