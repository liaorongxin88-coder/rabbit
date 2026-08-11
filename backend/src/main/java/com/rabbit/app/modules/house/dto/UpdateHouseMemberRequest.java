package com.rabbit.app.modules.house.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateHouseMemberRequest {
    private String role;
    private String perms;
    private Boolean isAdmin;
    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPerms() {
        return perms;
    }

    public void setPerms(String perms) {
        this.perms = perms;
    }

    public Boolean getIsAdmin() {
        return isAdmin;
    }

    public void setIsAdmin(Boolean admin) {
        isAdmin = admin;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
