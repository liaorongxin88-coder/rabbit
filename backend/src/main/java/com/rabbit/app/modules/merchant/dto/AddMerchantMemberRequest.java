package com.rabbit.app.modules.merchant.dto;

import jakarta.validation.constraints.NotBlank;

public class AddMerchantMemberRequest {
    @NotBlank(message = "userName不能为空")
    private String userName;

    private String role;

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
