package com.rabbit.app.security;

public class HouseContext {
    private static final ThreadLocal<HouseContext> CTX = new ThreadLocal<HouseContext>();

    private Long userId;
    private Long houseId;
    private String perms;
    private boolean admin;

    public static void set(Long userId, Long houseId, String perms, boolean admin) {
        HouseContext c = new HouseContext();
        c.userId = userId;
        c.houseId = houseId;
        c.perms = perms;
        c.admin = admin;
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

    public String getPerms() {
        return perms;
    }

    public boolean isAdmin() {
        return admin;
    }
}
