package com.rabbit.app.modules.admin.dto;

import java.util.List;

public class AdminLoginResponse {
    private String token;
    private Long adminId;
    private String userName;
    private String role;
    private List<String> permissions = List.of();

    public AdminLoginResponse() {
    }

    public AdminLoginResponse(String token, Long adminId, String userName, String role) {
        this(token, adminId, userName, role, List.of());
    }

    public AdminLoginResponse(String token, Long adminId, String userName, String role, List<String> permissions) {
        this.token = token;
        this.adminId = adminId;
        this.userName = userName;
        this.role = role;
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getAdminId() {
        return adminId;
    }

    public void setAdminId(Long adminId) {
        this.adminId = adminId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
