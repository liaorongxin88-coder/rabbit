package com.rabbit.app.security;

public class PlatformAdminContext {
    private static final ThreadLocal<PlatformAdminPrincipal> CURRENT = new ThreadLocal<PlatformAdminPrincipal>();

    public static void set(Long adminId, String role) {
        CURRENT.set(new PlatformAdminPrincipal(adminId, role));
    }

    public static PlatformAdminPrincipal get() {
        return CURRENT.get();
    }

    public static Long getAdminId() {
        PlatformAdminPrincipal p = CURRENT.get();
        return p == null ? null : p.getAdminId();
    }

    public static String getRole() {
        PlatformAdminPrincipal p = CURRENT.get();
        return p == null ? null : p.getRole();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static class PlatformAdminPrincipal {
        private final Long adminId;
        private final String role;

        PlatformAdminPrincipal(Long adminId, String role) {
            this.adminId = adminId;
            this.role = role;
        }

        public Long getAdminId() {
            return adminId;
        }

        public String getRole() {
            return role;
        }
    }
}
