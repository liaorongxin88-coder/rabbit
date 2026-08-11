package com.rabbit.app.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class SendSmsCodeRequest {
    @NotBlank(message = "手机号不能为空")
    private String phone;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
