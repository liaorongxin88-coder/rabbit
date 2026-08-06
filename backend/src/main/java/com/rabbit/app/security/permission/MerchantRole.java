package com.rabbit.app.security.permission;

import com.rabbit.app.common.BizException;

public enum MerchantRole implements ScopedRole {
    MEMBER(1),
    ADMIN(2),
    OWNER(3);

    private final int rank;

    MerchantRole(int rank) {
        this.rank = rank;
    }

    public static MerchantRole parse(String value) {
        try {
            return MerchantRole.valueOf(normalize(value));
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "商户角色不合法");
        }
    }

    public static MerchantRole fromStored(String value) {
        try {
            return MerchantRole.valueOf(normalize(value));
        } catch (IllegalArgumentException e) {
            throw new BizException(403, "商户角色配置不合法");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int rank() {
        return rank;
    }
}
