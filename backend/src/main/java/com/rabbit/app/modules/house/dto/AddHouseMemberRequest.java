package com.rabbit.app.modules.house.dto;

import jakarta.validation.constraints.NotBlank;

public class AddHouseMemberRequest {
    @NotBlank(message = "userName不能为空")
    private String userName;

    private String perms;

    private Boolean isAdmin;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
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
