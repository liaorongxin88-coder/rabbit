package com.rabbit.app.modules.house.dto;

import jakarta.validation.constraints.NotBlank;

public class HouseInvitationRequest {
    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "角色不能为空")
    private String role;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
