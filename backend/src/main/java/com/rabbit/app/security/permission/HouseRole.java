package com.rabbit.app.security.permission;

import com.rabbit.app.common.BizException;

public enum HouseRole implements ScopedRole {
    VIEWER(1, "view", false),
    STAFF(2, "edit", false),
    MANAGER(3, "control", false),
    OWNER(4, "control", true),
    MERCHANT_OWNER(5, "control", true);

    private final int rank;
    private final String legacyPermission;
    private final boolean administrator;

    HouseRole(int rank, String legacyPermission, boolean administrator) {
        this.rank = rank;
        this.legacyPermission = legacyPermission;
        this.administrator = administrator;
    }

    public static HouseRole parseAssignable(String value, boolean allowOwner) {
        HouseRole role;
        try {
            role = HouseRole.valueOf(normalize(value));
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "兔场成员角色不合法");
        }
        if (role == MERCHANT_OWNER || (role == OWNER && !allowOwner)) {
            throw new BizException(400, allowOwner ? "兔场成员角色不合法" : "新增成员不能直接设为兔场所有者");
        }
        return role;
    }

    public static HouseRole fromStored(String role, String legacyPermission, Boolean legacyAdmin) {
        if (role != null && !role.isBlank()) {
            try {
                HouseRole parsed = HouseRole.valueOf(normalize(role));
                if (parsed != MERCHANT_OWNER) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through to legacy columns for migrated records.
            }
        }
        if (Boolean.TRUE.equals(legacyAdmin)) {
            return OWNER;
        }
        if ("control".equalsIgnoreCase(legacyPermission)) {
            return MANAGER;
        }
        if ("edit".equalsIgnoreCase(legacyPermission)) {
            return STAFF;
        }
        return VIEWER;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    public String legacyPermission() {
        return legacyPermission;
    }

    public boolean administrator() {
        return administrator;
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
