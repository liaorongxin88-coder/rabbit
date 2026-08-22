package com.rabbit.app.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class SendSmsCodeRequest {
    @NotBlank(message = "手机号不能为空")
    private String phone;

    private String purpose = "LOGIN_OR_REGISTER";

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}
