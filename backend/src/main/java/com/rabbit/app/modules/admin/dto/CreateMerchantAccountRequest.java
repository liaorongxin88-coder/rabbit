package com.rabbit.app.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateMerchantAccountRequest {
    @NotBlank(message = "登录用户名不能为空")
    @Size(max = 64, message = "登录用户名不能超过64个字符")
    private String userName;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 64, message = "初始密码长度需为6-64个字符")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    @Size(min = 6, max = 64, message = "确认密码长度需为6-64个字符")
    private String confirmPassword;

    private String role;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
