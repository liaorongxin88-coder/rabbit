package com.rabbit.app.security;

import java.util.List;

public class HouseContext {
    private static final ThreadLocal<HouseContext> CTX = new ThreadLocal<HouseContext>();

    private Long userId;
    private Long houseId;
    private String perms;
    private String role;
    private boolean admin;
    private int roleRank;
    private List<String> permissions = List.of();

    public static void set(Long userId, Long houseId, String perms, String role, boolean admin) {
        set(userId, houseId, perms, role, admin, 0, List.of());
    }

    public static void set(
            Long userId,
            Long houseId,
            String perms,
            String role,
            boolean admin,
            int roleRank,
            List<String> permissions
    ) {
        HouseContext context = new HouseContext();
        context.userId = userId;
        context.houseId = houseId;
        context.perms = perms;
        context.role = role;
        context.admin = admin;
        context.roleRank = roleRank;
        context.permissions = permissions == null ? List.of() : List.copyOf(permissions);
        CTX.set(context);
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
