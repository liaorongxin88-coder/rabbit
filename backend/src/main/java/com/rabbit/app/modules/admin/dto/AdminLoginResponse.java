package com.rabbit.app.modules.admin.dto;

public class AdminLoginResponse {
    private String token;
    private Long adminId;
    private String userName;
    private String role;

    public AdminLoginResponse() {
    }

    public AdminLoginResponse(String token, Long adminId, String userName, String role) {
        this.token = token;
        this.adminId = adminId;
        this.userName = userName;
        this.role = role;
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
}
