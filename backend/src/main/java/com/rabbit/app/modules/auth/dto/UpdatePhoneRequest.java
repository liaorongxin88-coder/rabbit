package com.rabbit.app.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdatePhoneRequest {
    @NotBlank(message = "新手机号不能为空")
    private String phone;

    @NotBlank(message = "新手机号验证码不能为空")
    private String code;

    @Size(min = 6, max = 32, message = "当前密码长度需在6-32位")
    private String currentPassword;

    private String currentPhone;

    private String currentPhoneCode;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getCurrentPhone() {
        return currentPhone;
    }

    public void setCurrentPhone(String currentPhone) {
        this.currentPhone = currentPhone;
    }

    public String getCurrentPhoneCode() {
        return currentPhoneCode;
    }

    public void setCurrentPhoneCode(String currentPhoneCode) {
        this.currentPhoneCode = currentPhoneCode;
    }
}
