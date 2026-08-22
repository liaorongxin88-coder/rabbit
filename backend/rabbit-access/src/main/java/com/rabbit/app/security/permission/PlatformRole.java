package com.rabbit.app.security.permission;

import com.rabbit.app.common.BizException;

public enum PlatformRole implements ScopedRole {
    ADMIN(1),
    SUPER_ADMIN(2);

    private final int rank;

    PlatformRole(int rank) {
        this.rank = rank;
    }

    public static PlatformRole parse(String value) {
        try {
            return PlatformRole.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "平台角色不合法");
        }
    }

    public static PlatformRole fromStored(String value) {
        try {
            return PlatformRole.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(403, "平台角色配置不合法");
        }
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
