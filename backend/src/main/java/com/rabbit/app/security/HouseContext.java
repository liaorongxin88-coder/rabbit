package com.rabbit.app.security;

import java.util.List;

public class HouseContext {
    private static final ThreadLocal<HouseContext> CTX = new ThreadLocal<HouseContext>();

    private Long userId;
    private Long houseId;
    private Long merchantId;
    private String perms;
    private String role;
    private boolean admin;
    private int roleRank;
    private List<String> permissions = List.of();

    public static void set(Long userId, Long houseId, String perms, String role, boolean admin) {
        set(userId, houseId, null, perms, role, admin, 0, List.of());
    }

    public static void set(
            Long userId,
            Long houseId,
            Long merchantId,
            String perms,
            String role,
            boolean admin,
            int roleRank,
            List<String> permissions
    ) {
        HouseContext c = new HouseContext();
        c.userId = userId;
        c.houseId = houseId;
        c.merchantId = merchantId;
        c.perms = perms;
        c.role = role;
        c.admin = admin;
        c.roleRank = roleRank;
        c.permissions = permissions == null ? List.of() : List.copyOf(permissions);
        CTX.set(c);
    }

    public static HouseContext get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }

    public Long getUserId() {
        return userId;
    }

    public Long getHouseId() {
        return houseId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public String getPerms() {
        return perms;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin() {
        return admin;
    }

    public int getRoleRank() {
        return roleRank;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}
